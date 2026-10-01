#include "q2_arm.h"
#include "q2_kernel.h"
#include "ggml-cpu.h"
#include "ggml-cpu-impl.h"
#include "quants.h"
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <stdarg.h>
#include <stdatomic.h>
#include <limits.h>

static _Atomic int prefill_chunk,decode_chunk,prefill_scheduled,decode_scheduled;
void outpost_q2_arm_chunks(int prefill,int decode) {
    atomic_store(&prefill_chunk,prefill);atomic_store(&decode_chunk,decode);
    atomic_store(&prefill_scheduled,0);atomic_store(&decode_scheduled,0);
}
void outpost_q2_arm_schedule_audit(char *out,size_t capacity) {
    snprintf(out,capacity,"{\"prefillChunk\":%d,\"decodeChunk\":%d,\"prefillNodes\":%d,\"decodeNodes\":%d}",
        atomic_load(&prefill_chunk),atomic_load(&decode_chunk),atomic_load(&prefill_scheduled),atomic_load(&decode_scheduled));
}

#if defined(__aarch64__)
#include <arm_neon.h>
#define DOT __attribute__((target("dotprod")))
#define DOT_INLINE DOT __attribute__((always_inline)) static inline
_Static_assert(QK2_0==64 && QK8_0==32,"Pinned Q2/Q8 layouts required");

/* The pinned baseline's emulated dot groups adjacent pairs from both vector
 * halves into each lane. Preserve those lanes AND its fused float accumulation;
 * a plain SDOT substitution changes rounding and can change model outputs. */
DOT_INLINE int8x16_t activation_order(int8x16_t v) {
    const uint8x16_t indices={0,1,8,9,2,3,10,11,4,5,12,13,6,7,14,15};
    return vqtbl1q_s8(v,indices);
}
static inline float arm_fp16(uint16_t bits) { __fp16 value; memcpy(&value,&bits,sizeof(bits)); return (float)value; }
typedef struct {int8x16_t a,b;} q2_half;
DOT_INLINE q2_half unpack_half(const uint8_t *p) {
    const uint8x16_t low={0,0,2,2,0,0,2,2,1,1,3,3,1,1,3,3};
    const uint8x16_t high={4,4,6,6,4,4,6,6,5,5,7,7,5,5,7,7};
    const int8x16_t shifts={0,-2,0,-2,-4,-6,-4,-6,0,-2,0,-2,-4,-6,-4,-6};
    uint8x8_t bytes=vld1_u8(p);uint8x16_t raw=vcombine_u8(bytes,bytes);
    const uint8x16_t mask=vdupq_n_u8(3);const int8x16_t one=vdupq_n_s8(1);
    q2_half q;
    q.a=vsubq_s8(vreinterpretq_s8_u8(vandq_u8(vshlq_u8(vqtbl1q_u8(raw,low),shifts),mask)),one);
    q.b=vsubq_s8(vreinterpretq_s8_u8(vandq_u8(vshlq_u8(vqtbl1q_u8(raw,high),shifts),mask)),one);
    return q;
}
DOT_INLINE int32x4_t dot_half(q2_half q,const int8_t *p,bool prepared) {
    int8x16_t a=vld1q_s8(p),b=vld1q_s8(p+16);
    if(!prepared){a=activation_order(a);b=activation_order(b);}
    return vdotq_s32(vdotq_s32(vdupq_n_s32(0),q.a,a),q.b,b);
}
DOT __attribute__((noinline))
void outpost_q2_arm_dot(int n,float *s,size_t bs,const void *vx,size_t bx,const void *vy,size_t by,int nrc) {
    (void)bs;(void)bx;(void)by;(void)nrc;
    const block_q2_0 *x=vx;const block_q8_0 *y=vy;float32x4_t acc=vdupq_n_f32(0);
    for(int i=0;i<n/64;i++) {
        const float dx=arm_fp16(x[i].d);
        const int32x4_t a=dot_half(unpack_half(x[i].qs),y[2*i].qs,false);
        const int32x4_t b=dot_half(unpack_half(x[i].qs+8),y[2*i+1].qs,false);
        acc=vfmaq_n_f32(acc,vcvtq_f32_s32(a),dx*arm_fp16(y[2*i].d));
        acc=vfmaq_n_f32(acc,vcvtq_f32_s32(b),dx*arm_fp16(y[2*i+1].d));
    }
    *s=vaddvq_f32(acc);
}
DOT static void prepare_activations(block_q8_0 *y,int k) {
    for(int i=0;i<k/32;i++) {
        vst1q_s8(y[i].qs,activation_order(vld1q_s8(y[i].qs)));
        vst1q_s8(y[i].qs+16,activation_order(vld1q_s8(y[i].qs+16)));
    }
}
DOT __attribute__((always_inline))
static inline void columns_body(int k,const block_q2_0 *x,const char *ys,size_t stride,int nt,float *out,size_t output_stride) {
    float32x4_t sums[8];for(int t=0;t<nt;t++)sums[t]=vdupq_n_f32(0);
    for(int i=0;i<k/64;i++) {
        const q2_half a=unpack_half(x[i].qs),b=unpack_half(x[i].qs+8);
        const float dx=arm_fp16(x[i].d);
        #pragma unroll
        for(int t=0;t<nt;t++) {
            const block_q8_0 *y=(const block_q8_0 *)(ys+t*stride)+2*i;
            sums[t]=vfmaq_n_f32(sums[t],vcvtq_f32_s32(dot_half(a,y[0].qs,true)),dx*arm_fp16(y[0].d));
            sums[t]=vfmaq_n_f32(sums[t],vcvtq_f32_s32(dot_half(b,y[1].qs,true)),dx*arm_fp16(y[1].d));
        }
    }
    for(int t=0;t<nt;t++){float result=vaddvq_f32(sums[t]);memcpy((char *)out+t*output_stride,&result,sizeof(result));}
}
#define FIXED_COLUMNS(N) \
DOT __attribute__((noinline)) static void columns##N(int k,const block_q2_0 *x,const char *ys,size_t stride,float *out,size_t output_stride) { columns_body(k,x,ys,stride,N,out,output_stride); }
FIXED_COLUMNS(1)
FIXED_COLUMNS(2)
FIXED_COLUMNS(3)
FIXED_COLUMNS(4)
FIXED_COLUMNS(5)
FIXED_COLUMNS(6)
FIXED_COLUMNS(7)
FIXED_COLUMNS(8)
#undef FIXED_COLUMNS
static void columns(int k,const block_q2_0 *x,const char *ys,size_t stride,int nt,float *out,size_t output_stride) {
    switch(nt) {
#define SELECT_COLUMNS(N) case N: columns##N(k,x,ys,stride,out,output_stride); break;
        SELECT_COLUMNS(1) SELECT_COLUMNS(2) SELECT_COLUMNS(3) SELECT_COLUMNS(4)
        SELECT_COLUMNS(5) SELECT_COLUMNS(6) SELECT_COLUMNS(7) SELECT_COLUMNS(8)
#undef SELECT_COLUMNS
    }
}
DOT_INLINE void decode_rows_body(int k,const char *weights,size_t stride,const block_q8_0 *y,float *out,int nr) {
    float32x4_t sums[4];for(int r=0;r<nr;r++)sums[r]=vdupq_n_f32(0);
    for(int i=0;i<k/64;i++) {
        const int8x16_t a0=vld1q_s8(y[2*i].qs),a1=vld1q_s8(y[2*i].qs+16),b0=vld1q_s8(y[2*i+1].qs),b1=vld1q_s8(y[2*i+1].qs+16);
        const float dy0=arm_fp16(y[2*i].d),dy1=arm_fp16(y[2*i+1].d);
        #pragma unroll
        for(int r=0;r<nr;r++) {
            const block_q2_0 *x=(const block_q2_0 *)(weights+r*stride)+i;
            const q2_half a=unpack_half(x->qs),b=unpack_half(x->qs+8);float dx=arm_fp16(x->d);
            int32x4_t da=vdotq_s32(vdotq_s32(vdupq_n_s32(0),a.a,a0),a.b,a1);
            int32x4_t db=vdotq_s32(vdotq_s32(vdupq_n_s32(0),b.a,b0),b.b,b1);
            sums[r]=vfmaq_n_f32(sums[r],vcvtq_f32_s32(da),dx*dy0);
            sums[r]=vfmaq_n_f32(sums[r],vcvtq_f32_s32(db),dx*dy1);
        }
    }
    for(int r=0;r<nr;r++)out[r]=vaddvq_f32(sums[r]);
}
DOT __attribute__((noinline)) static void decode_rows2(int k,const char *w,size_t stride,const block_q8_0 *y,float *out){decode_rows_body(k,w,stride,y,out,2);}
DOT __attribute__((noinline)) static void decode_rows4(int k,const char *w,size_t stride,const block_q8_0 *y,float *out){decode_rows_body(k,w,stride,y,out,4);}
// Coverage is enabled only by the serialized native research check.
static bool audit_coverage;
static _Atomic int *coverage_visits;
static int64_t coverage_checks;
static void compute_row_range(const struct ggml_compute_params *p,struct ggml_tensor *dst,
                              const char *quant,size_t qs,int width,int decode_rows,int64_t row,int64_t end) {
    const struct ggml_tensor *w=dst->src[0],*a=dst->src[1];
    if(coverage_visits)for(int64_t r=row;r<end;r++)atomic_fetch_add_explicit(coverage_visits+r,1,memory_order_relaxed);
    if(a->ne[1]==1&&(decode_rows==2||decode_rows==4)&&w->ne[1]>=decode_rows*p->nth) {
        for(;row+decode_rows<=end;row+=decode_rows) {
            const char *weights=(const char *)w->data+row*w->nb[1];float *result=(float *)dst->data+row;
            if(decode_rows==2)decode_rows2((int)w->ne[0],weights,w->nb[1],(const block_q8_0 *)quant,result);
            else decode_rows4((int)w->ne[0],weights,w->nb[1],(const block_q8_0 *)quant,result);
        }
    }
    for(;row<end;row++)
        for(int64_t col=0;col<a->ne[1];col+=width) {
            int nt=(int)(a->ne[1]-col<width?a->ne[1]-col:width);
            columns((int)w->ne[0],(const block_q2_0 *)((const char *)w->data+row*w->nb[1]),quant+col*qs,qs,nt,
                (float *)((char *)dst->data+col*dst->nb[1])+row,dst->nb[1]);
        }
}
bool outpost_q2_arm_matmul(const struct ggml_compute_params *p,struct ggml_tensor *dst,int width,int decode_rows) {
    const struct ggml_tensor *w=dst->src[0],*a=dst->src[1];
    if(p->use_ref||!outpost_q2_fast_enabled()||w->type!=GGML_TYPE_Q2_0||a->type!=GGML_TYPE_F32||dst->type!=GGML_TYPE_F32
       ||w->ne[0]!=a->ne[0]||w->ne[0]<=0||w->ne[0]%64||w->ne[0]>INT32_MAX
       ||w->ne[2]!=1||w->ne[3]!=1||a->ne[2]!=1||a->ne[3]!=1
       ||w->nb[0]!=sizeof(block_q2_0)||a->nb[0]!=sizeof(float)||dst->nb[0]!=sizeof(float)
       ||dst->ne[0]!=w->ne[1]||dst->ne[1]!=a->ne[1]||dst->ne[2]!=1||dst->ne[3]!=1
       ||(width!=1&&width!=2&&width!=4&&width!=8))return false;
    const size_t qs=ggml_row_size(GGML_TYPE_Q8_0,a->ne[0]);
    if(!qs||!p->wdata||(size_t)a->ne[1]>p->wsize/qs||w->nb[1]<ggml_row_size(w->type,w->ne[0])
       ||a->nb[1]<(size_t)a->ne[0]*sizeof(float)||dst->nb[1]<(size_t)dst->ne[0]*sizeof(float))return false;
    const int chunk=atomic_load_explicit(a->ne[1]==1?&decode_chunk:&prefill_chunk,memory_order_relaxed);
    const bool valid_chunk=chunk>=16&&chunk<=256&&(chunk&(chunk-1))==0;
    const int64_t task_count=valid_chunk?w->ne[1]/chunk+(w->ne[1]%chunk!=0):0;
    const bool dynamic=valid_chunk&&p->threadpool&&p->nth>1&&w->ne[1]>=(int64_t)chunk*p->nth&&task_count<=INT_MAX-p->nth;
    const int tasks=dynamic?(int)task_count:0;
    if(dynamic&&p->ith==0) {
        ggml_threadpool_chunk_set(p->threadpool,p->nth);
        atomic_fetch_add_explicit(a->ne[1]==1?&decode_scheduled:&prefill_scheduled,1,memory_order_relaxed);
    }
    char *quant=p->wdata;ggml_from_float_t convert=ggml_get_type_traits_cpu(GGML_TYPE_Q8_0)->from_float;
    for(int64_t col=p->ith;col<a->ne[1];col+=p->nth) {
        block_q8_0 *y=(block_q8_0 *)(quant+col*qs);
        convert((const float *)((const char *)a->data+col*a->nb[1]),y,a->ne[0]);
        prepare_activations(y,(int)a->ne[0]);
    }
    ggml_barrier(p->threadpool);
    int64_t row=w->ne[1]*p->ith/p->nth,end=w->ne[1]*(p->ith+1)/p->nth;
    if(dynamic) {
        // The first chunk is reserved per worker; subsequent chunks use the
        // backend's shared atomic counter. The preceding barrier publishes it.
        for(int task=p->ith;task<tasks;task=ggml_threadpool_chunk_add(p->threadpool,1)) {
            int64_t begin=(int64_t)task*chunk,limit=begin+chunk;
            compute_row_range(p,dst,quant,qs,width,decode_rows,begin,limit<w->ne[1]?limit:w->ne[1]);
        }
    } else compute_row_range(p,dst,quant,qs,width,decode_rows,row,end);
    outpost_q2_note_use();return true;
}
#endif

#if defined(__aarch64__)
static uint32_t arm_random(uint32_t *s){*s=*s*1664525u+1013904223u;return *s;}
static double arm_ns(void){struct timespec t;clock_gettime(CLOCK_MONOTONIC,&t);return t.tv_sec*1e9+t.tv_nsec;}
static void json_append(char *out,size_t capacity,size_t *used,const char *fmt,...) {
    if(*used>=capacity)return;va_list args;va_start(args,fmt);int n=vsnprintf(out+*used,capacity-*used,fmt,args);va_end(args);
    if(n<0)return;*used+=(size_t)n;if(*used>=capacity)out[capacity-1]=0;
}
static int arm_graph(int k,int rows,int cols,int threads,bool padded,bool benchmark,char *out,size_t capacity,size_t *used,int *comparisons) {
    const int wk=padded?k+64:k,ak=padded?k+16:k;
    size_t bytes=ggml_row_size(GGML_TYPE_Q2_0,wk)*(size_t)rows+((size_t)ak+rows)*cols*sizeof(float)+4*1024*1024;
    struct ggml_context *ctx=ggml_init((struct ggml_init_params){bytes,NULL,false});if(!ctx)return 1;
    struct ggml_tensor *ws=ggml_new_tensor_2d(ctx,GGML_TYPE_Q2_0,wk,rows),*as=ggml_new_tensor_2d(ctx,GGML_TYPE_F32,ak,cols);
    struct ggml_tensor *w=padded?ggml_view_2d(ctx,ws,k,rows,ws->nb[1],0):ws,*a=padded?ggml_view_2d(ctx,as,k,cols,as->nb[1],0):as;
    uint32_t seed=7319;
    for(int r=0;r<rows;r++)for(int b=0;b<k/64;b++) {
        block_q2_0 *x=(block_q2_0 *)((char *)w->data+r*w->nb[1])+b;
        x->d=ggml_fp32_to_fp16(((int)(arm_random(&seed)%127)-63)/64.0f);
        for(int j=0;j<16;j++)x->qs[j]=(uint8_t)(arm_random(&seed)>>16);
    }
    for(int c=0;c<cols;c++)for(int j=0;j<k;j++)((float *)((char *)a->data+c*a->nb[1]))[j]=((int)(arm_random(&seed)%2001)-1000)/127.0f;
    struct ggml_tensor *d=ggml_mul_mat(ctx,w,a);struct ggml_cgraph *g=ggml_new_graph(ctx);ggml_build_forward_expand(g,d);
    struct ggml_threadpool_params settings=ggml_threadpool_params_default(threads);struct ggml_threadpool *pool=ggml_threadpool_new(&settings);
    struct ggml_cplan plan=ggml_graph_plan(g,threads,pool);plan.work_data=malloc(plan.work_size?plan.work_size:1);
    float *expected=malloc((size_t)rows*cols*sizeof(float));int failed=0;
    _Atomic int *visits=audit_coverage?calloc((size_t)rows,sizeof(*visits)):NULL;coverage_visits=visits;
    if(!pool||!plan.work_data||!expected||(audit_coverage&&!visits)){failed++;goto done;}
    outpost_q2_set_mode(0);outpost_q2_set_batch_width(1);
    if(ggml_graph_compute(g,&plan)!=GGML_STATUS_SUCCESS){failed++;goto done;}
    memcpy(expected,d->data,(size_t)rows*cols*sizeof(float));
    int rounds=benchmark?3:1;
    for(int round=0;round<rounds;round++)for(int order=0;order<7;order++) {
        int choice=(round+order)%7,width=choice==0?1:(choice>=4?8:1<<(choice-1)),nr=choice>=5?1<<(choice-4):1;bool fast=choice!=0;
        outpost_q2_set_mode(fast);outpost_q2_set_batch_width(width);outpost_q2_set_row_tiles(1,nr);
        int repeats=benchmark?(cols==1?32:3):1;double ns=0;bool parity=true;int result=GGML_STATUS_SUCCESS;
        for(int repeat=0;repeat<repeats;repeat++) {
            if(visits){for(int r=0;r<rows;r++)atomic_store(visits+r,0);memset(d->data,0xff,(size_t)rows*cols*sizeof(float));}
            double start=arm_ns();int status=ggml_graph_compute(g,&plan);ns+=arm_ns()-start;
            if(visits&&fast)for(int r=0;r<rows;r++){coverage_checks++;if(atomic_load(visits+r)!=1)failed++;}
            if(status!=GGML_STATUS_SUCCESS)result=status;
            parity&=memcmp(expected,d->data,(size_t)rows*cols*sizeof(float))==0;
        }
        ns/=repeats;
        bool dispatch=outpost_q2_was_used()==fast&&outpost_q2_batch_used()==(fast&&cols>1)&&outpost_q2_rows_used()==(fast&&cols==1&&nr>1&&rows>=nr*threads);
        *comparisons+=rows*cols*repeats;if(result!=GGML_STATUS_SUCCESS||!parity||!dispatch)failed++;
        if(benchmark){if(*used>=capacity){failed++;goto done;}json_append(out,capacity,used,"%s{\"k\":%d,\"rows\":%d,\"columns\":%d,\"threads\":%d,\"round\":%d,\"fast\":%s,\"width\":%d,\"decodeRows\":%d,\"ns\":%.0f,\"parity\":%s,\"dispatch\":%s}",out[*used-1]=='['?"":",",k,rows,cols,threads,round,fast?"true":"false",width,nr,ns,parity?"true":"false",dispatch?"true":"false");}
    }
done:
    coverage_visits=NULL;free(visits);free(expected);free(plan.work_data);if(pool)ggml_threadpool_free(pool);ggml_free(ctx);return failed;
}
#endif
void outpost_q2_arm_graph_checks(char *out,size_t capacity) {
#if defined(__aarch64__)
    if(!outpost_q2_available()){snprintf(out,capacity,"{\"supported\":false}");return;}
    ggml_cpu_init();int failed=0,comparisons=0;size_t used=0;
    json_append(out,capacity,&used,"{\"supported\":true,\"benchmarks\":[");
    const int ks[]={64,128,2560,9728},columns_list[]={1,2,3,5,6,7,9,17},workers[]={1,4,6};
    for(int k=0;k<4;k++)for(int c=0;c<8;c++)for(int t=0;t<3;t++)for(int pad=0;pad<2;pad++)
        failed+=arm_graph(ks[k],17,columns_list[c],workers[t],pad,false,out,capacity,&used,&comparisons);
    const int shapes[][3]={{2560,1024,1},{2560,4096,1},{2560,9728,1},{4096,2560,1},{9728,2560,1},{2560,151669,1},{2560,4096,64},{2560,9728,16},{9728,2560,16}};
    for(int s=0;s<9;s++)failed+=arm_graph(shapes[s][0],shapes[s][1],shapes[s][2],4,false,true,out,capacity,&used,&comparisons);
    json_append(out,capacity,&used,"],\"comparisons\":%d,\"failures\":%d,\"scope\":\"Bitwise ARM baseline parity, strided inputs, odd row/column tails,1/4/6 workers and rotated real-shape timings. Times average32 graph calls for decode shapes and3 for prefill. Not model speed.\"}",comparisons,failed);
    outpost_q2_set_mode(0);outpost_q2_set_batch_width(1);outpost_q2_set_row_tiles(1,1);
#else
    snprintf(out,capacity,"{\"supported\":false}");
#endif
}

void outpost_q2_arm_schedule_checks(char *out,size_t capacity) {
#if defined(__aarch64__)
    if(!outpost_q2_available()){snprintf(out,capacity,"{\"supported\":false}");return;}
    ggml_cpu_init();int failed=0,comparisons=0;size_t used=0;coverage_checks=0;audit_coverage=true;
    json_append(out,capacity,&used,"{\"supported\":true,\"benchmarks\":[");
    const int chunks[]={0,16,32,64,128,256},rows[]={1,3,17,33,67,129,257,513,1541},workers[]={1,4,6},cols[]={1,9};
    for(int ch=0;ch<6;ch++)for(int r=0;r<9;r++)for(int t=0;t<3;t++)for(int c=0;c<2;c++) {
        outpost_q2_arm_chunks(chunks[ch],chunks[ch]);
        failed+=arm_graph(64,rows[r],cols[c],workers[t],true,false,out,capacity,&used,&comparisons);
    }
    audit_coverage=false;
    json_append(out,capacity,&used,"],\"comparisons\":%d,\"rowCoverageChecks\":%lld,\"failures\":%d,\"scope\":\"Poisoned outputs and atomic per-row visit counts: every owned row exactly once, static fallback, odd tails, padded inputs, 1/4/6 workers and chunk sizes 0/16/32/64/128/256. No timing claim.\"}",comparisons,(long long)coverage_checks,failed);
    // The caller immediately follows this with the existing large real-shape suite.
    outpost_q2_arm_chunks(64,64);outpost_q2_set_mode(0);
#else
    snprintf(out,capacity,"{\"supported\":false}");
#endif
}
