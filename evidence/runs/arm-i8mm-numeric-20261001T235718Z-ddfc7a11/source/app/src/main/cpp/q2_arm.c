#include "q2_arm.h"
#include "q2_kernel.h"
#include "cpu_caps.h"
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

static _Atomic int i8mm_requested,i8mm_nodes;
bool outpost_q2_arm_i8mm_available(void) {
#if defined(__aarch64__)
    const br_cpu_caps *cpu=br_cpu_detect();const unsigned needed=BR_NEON|BR_DOTPROD|BR_I8MM;
    return cpu->arch==BR_CPU_ARM64&&(cpu->features&needed)==needed;
#else
    return false;
#endif
}
void outpost_q2_arm_i8mm_set(int enabled){atomic_store(&i8mm_requested,enabled&&outpost_q2_arm_i8mm_available());atomic_store(&i8mm_nodes,0);}
void outpost_q2_arm_i8mm_audit(char *out,size_t capacity){
#if defined(__aarch64__)
    const int compiled=1;
#else
    const int compiled=0;
#endif
    snprintf(out,capacity,"{\"compiled\":%s,\"cpuCompatible\":%s,\"requested\":%s,\"matrixNodes\":%d,\"scope\":\"Research-only matrix I8MM path; one-column/vector dispatch remains DotProd. No expanded weights or larger graph workspace.\"}",compiled?"true":"false",outpost_q2_arm_i8mm_available()?"true":"false",atomic_load(&i8mm_requested)?"true":"false",atomic_load(&i8mm_nodes));
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
#define I8MM __attribute__((target("dotprod,i8mm")))
#define I8MM_INLINE I8MM __attribute__((always_inline)) static inline

// One SMMLA lane is one complete old integer partial (eight products). Four
// separate FP32 accumulators preserve the old SDOT/emulated-dot lane history.
// Inputs here already have activation_order/unpack_half's baseline lane order.
static inline void partial_pairs(int8x16_t a0,int8x16_t a1,int8x16_t b0,int8x16_t b1,int8x16_t out[4]) {
    int8x16_t al=vreinterpretq_s8_s32(vzip1q_s32(vreinterpretq_s32_s8(a0),vreinterpretq_s32_s8(a1)));
    int8x16_t ah=vreinterpretq_s8_s32(vzip2q_s32(vreinterpretq_s32_s8(a0),vreinterpretq_s32_s8(a1)));
    int8x16_t bl=vreinterpretq_s8_s32(vzip1q_s32(vreinterpretq_s32_s8(b0),vreinterpretq_s32_s8(b1)));
    int8x16_t bh=vreinterpretq_s8_s32(vzip2q_s32(vreinterpretq_s32_s8(b0),vreinterpretq_s32_s8(b1)));
    out[0]=vcombine_s8(vget_low_s8(al),vget_low_s8(bl));
    out[1]=vcombine_s8(vget_high_s8(al),vget_high_s8(bl));
    out[2]=vcombine_s8(vget_low_s8(ah),vget_low_s8(bh));
    out[3]=vcombine_s8(vget_high_s8(ah),vget_high_s8(bh));
}
static void prepare_i8mm_pair(block_q8_0 *a,block_q8_0 *b,int k) {
    for(int i=0;i<k/32;i++) {
        int8x16_t packed[4];partial_pairs(vld1q_s8(a[i].qs),vld1q_s8(a[i].qs+16),vld1q_s8(b[i].qs),vld1q_s8(b[i].qs+16),packed);
        vst1q_s8(a[i].qs,packed[0]);vst1q_s8(a[i].qs+16,packed[1]);
        vst1q_s8(b[i].qs,packed[2]);vst1q_s8(b[i].qs+16,packed[3]);
    }
}
I8MM_INLINE void i8mm_rows_body(int k,const block_q2_0 *w0,const block_q2_0 *w1,const char *quant,size_t stride,int cols,float *out,size_t output_stride,int rows) {
    const int pairs=cols/2;float32x4_t acc[4][4];
    for(int c=0;c<pairs;c++)for(int lane=0;lane<4;lane++)acc[c][lane]=vdupq_n_f32(0);
    for(int block=0;block<k/64;block++) {
        const float d0=arm_fp16(w0[block].d),d1=arm_fp16(w1[block].d);
        const float32x4_t weight_scale=vcombine_f32(vdup_n_f32(d0),vdup_n_f32(d1));
        #pragma unroll
        for(int half=0;half<2;half++) {
            q2_half a=unpack_half(w0[block].qs+8*half),b=unpack_half(w1[block].qs+8*half);
            int8x16_t weights[4];partial_pairs(a.a,a.b,b.a,b.b,weights);
            #pragma unroll
            for(int c=0;c<pairs;c++) {
                const block_q8_0 *y0=(const block_q8_0 *)(quant+(2*c)*stride)+2*block+half;
                const block_q8_0 *y1=(const block_q8_0 *)(quant+(2*c+1)*stride)+2*block+half;
                const float32x2_t ys={arm_fp16(y0->d),arm_fp16(y1->d)};
                const float32x4_t scale=vmulq_f32(weight_scale,vcombine_f32(ys,ys));
                const int8x16_t values[4]={vld1q_s8(y0->qs),vld1q_s8(y0->qs+16),vld1q_s8(y1->qs),vld1q_s8(y1->qs+16)};
                #pragma unroll
                for(int lane=0;lane<4;lane++) {
                    int32x4_t dot=vmmlaq_s32(vdupq_n_s32(0),weights[lane],values[lane]);
                    acc[c][lane]=vfmaq_f32(acc[c][lane],vcvtq_f32_s32(dot),scale);
                }
            }
        }
    }
    for(int c=0;c<pairs;c++) {
        // ARM vaddvq's pairwise order: (lane0+lane1)+(lane2+lane3).
        float32x4_t sum=vaddq_f32(vaddq_f32(acc[c][0],acc[c][1]),vaddq_f32(acc[c][2],acc[c][3]));
        float values[4];vst1q_f32(values,sum);
        float *col0=(float *)((char *)out+(2*c)*output_stride),*col1=(float *)((char *)out+(2*c+1)*output_stride);
        col0[0]=values[0];col1[0]=values[1];if(rows==2){col0[1]=values[2];col1[1]=values[3];}
    }
}
#define I8MM_COLUMNS(N) \
I8MM __attribute__((noinline)) static void i8mm_columns##N(int k,const block_q2_0 *w0,const block_q2_0 *w1,const char *y,size_t stride,float *out,size_t output_stride,int rows){i8mm_rows_body(k,w0,w1,y,stride,N,out,output_stride,rows);}
I8MM_COLUMNS(2)
I8MM_COLUMNS(4)
I8MM_COLUMNS(6)
I8MM_COLUMNS(8)
#undef I8MM_COLUMNS
static void i8mm_columns(int k,const block_q2_0 *w0,const block_q2_0 *w1,const char *y,size_t stride,int cols,float *out,size_t output_stride,int rows) {
    switch(cols) {
        case 2:i8mm_columns2(k,w0,w1,y,stride,out,output_stride,rows);break;
        case 4:i8mm_columns4(k,w0,w1,y,stride,out,output_stride,rows);break;
        case 6:i8mm_columns6(k,w0,w1,y,stride,out,output_stride,rows);break;
        case 8:i8mm_columns8(k,w0,w1,y,stride,out,output_stride,rows);break;
    }
}
I8MM __attribute__((noinline)) static int i8mm_primitive_checks(void) {
    int failed=0;
    for(int trial=0;trial<32;trial++) {
        int8_t a[16],b[16];for(int i=0;i<16;i++){a[i]=(int8_t)((i*13+trial*7)%255-128);b[i]=(int8_t)((i*17+trial*3)%255-128);}
        int32_t actual[4];vst1q_s32(actual,vmmlaq_s32(vdupq_n_s32(0),vld1q_s8(a),vld1q_s8(b)));
        for(int row=0;row<2;row++)for(int col=0;col<2;col++) {
            int32_t expected=0;for(int k=0;k<8;k++)expected+=(int32_t)a[row*8+k]*b[col*8+k];
            if(actual[row*2+col]!=expected)failed++;
        }
    }
    return failed;
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
                              const char *quant,size_t qs,int width,int decode_rows,bool i8mm,int64_t row,int64_t end) {
    const struct ggml_tensor *w=dst->src[0],*a=dst->src[1];
    if(coverage_visits)for(int64_t r=row;r<end;r++)atomic_fetch_add_explicit(coverage_visits+r,1,memory_order_relaxed);
    if(i8mm) {
        for(int64_t r=row;r<end;r+=2) {
            int nr=end-r>=2?2:1;
            const block_q2_0 *w0=(const block_q2_0 *)((const char *)w->data+r*w->nb[1]);
            const block_q2_0 *w1=nr==2?(const block_q2_0 *)((const char *)w->data+(r+1)*w->nb[1]):w0;
            for(int64_t col=0;col<a->ne[1];col+=width) {
                int nt=(int)(a->ne[1]-col<width?a->ne[1]-col:width),paired=nt&~1;
                float *dst0=(float *)((char *)dst->data+col*dst->nb[1])+r;
                if(paired)i8mm_columns((int)w->ne[0],w0,w1,quant+col*qs,qs,paired,dst0,dst->nb[1],nr);
                if(nt!=paired)for(int rr=0;rr<nr;rr++)columns((int)w->ne[0],rr?w1:w0,quant+(col+paired)*qs,qs,1,(float *)((char *)dst->data+(col+paired)*dst->nb[1])+r+rr,dst->nb[1]);
            }
        }
        return;
    }
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
    const bool i8mm=atomic_load(&i8mm_requested)&&outpost_q2_arm_i8mm_available()&&a->ne[1]>=2&&w->ne[1]>=2&&width>=2;
    if(i8mm&&p->ith==0)atomic_fetch_add(&i8mm_nodes,1);
    char *quant=p->wdata;ggml_from_float_t convert=ggml_get_type_traits_cpu(GGML_TYPE_Q8_0)->from_float;
    const int columns_per_task=i8mm?2:1;
    for(int64_t col=(int64_t)p->ith*columns_per_task;col<a->ne[1];col+=(int64_t)p->nth*columns_per_task) {
        block_q8_0 *y=(block_q8_0 *)(quant+col*qs);
        convert((const float *)((const char *)a->data+col*a->nb[1]),y,a->ne[0]);prepare_activations(y,(int)a->ne[0]);
        if(i8mm&&col+1<a->ne[1]) {
            block_q8_0 *next=(block_q8_0 *)(quant+(col+1)*qs);
            convert((const float *)((const char *)a->data+(col+1)*a->nb[1]),next,a->ne[0]);prepare_activations(next,(int)a->ne[0]);
            prepare_i8mm_pair(y,next,(int)a->ne[0]);
        }
    }
    ggml_barrier(p->threadpool);
    int64_t row=w->ne[1]*p->ith/p->nth,end=w->ne[1]*(p->ith+1)/p->nth;
    if(dynamic) {
        // The first chunk is reserved per worker; subsequent chunks use the
        // backend's shared atomic counter. The preceding barrier publishes it.
        for(int task=p->ith;task<tasks;task=ggml_threadpool_chunk_add(p->threadpool,1)) {
            int64_t begin=(int64_t)task*chunk,limit=begin+chunk;
            compute_row_range(p,dst,quant,qs,width,decode_rows,i8mm,begin,limit<w->ne[1]?limit:w->ne[1]);
        }
    } else compute_row_range(p,dst,quant,qs,width,decode_rows,i8mm,row,end);
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

#if defined(__aarch64__)
static int i8mm_graph(int k,int rows,int cols,int threads,int width,int chunk,bool padded,bool benchmark,char *out,size_t capacity,size_t *used,long long *comparisons) {
    const int wk=padded?k+64:k,ak=padded?k+16:k;
    size_t bytes=ggml_row_size(GGML_TYPE_Q2_0,wk)*(size_t)rows+((size_t)ak+rows)*cols*sizeof(float)+4*1024*1024;
    struct ggml_context *ctx=ggml_init((struct ggml_init_params){bytes,NULL,false});if(!ctx)return 1;
    struct ggml_tensor *ws=ggml_new_tensor_2d(ctx,GGML_TYPE_Q2_0,wk,rows),*as=ggml_new_tensor_2d(ctx,GGML_TYPE_F32,ak,cols);
    struct ggml_tensor *w=padded?ggml_view_2d(ctx,ws,k,rows,ws->nb[1],0):ws,*a=padded?ggml_view_2d(ctx,as,k,cols,as->nb[1],0):as;
    uint32_t seed=71383;
    for(int row=0;row<rows;row++)for(int block=0;block<k/64;block++) {
        block_q2_0 *x=(block_q2_0 *)((char *)w->data+row*w->nb[1])+block;
        x->d=ggml_fp32_to_fp16(((int)(arm_random(&seed)%127)-63)/64.0f);
        for(int j=0;j<16;j++)x->qs[j]=(uint8_t)(arm_random(&seed)>>16);
    }
    for(int c=0;c<cols;c++)for(int j=0;j<k;j++)((float *)((char *)a->data+c*a->nb[1]))[j]=((int)(arm_random(&seed)%2001)-1000)/127.0f;
    struct ggml_tensor *dst=ggml_mul_mat(ctx,w,a);struct ggml_cgraph *graph=ggml_new_graph(ctx);ggml_build_forward_expand(graph,dst);
    struct ggml_threadpool_params options=ggml_threadpool_params_default(threads);struct ggml_threadpool *pool=ggml_threadpool_new(&options);
    struct ggml_cplan plan=ggml_graph_plan(graph,threads,pool);plan.work_data=malloc(plan.work_size?plan.work_size:1);
    float *reference=malloc((size_t)rows*cols*sizeof(float));int failed=0;
    if(!pool||!plan.work_data||!reference){failed++;goto done;}
    outpost_q2_arm_i8mm_set(0);outpost_q2_set_mode(0);outpost_q2_set_batch_width(1);
    if(ggml_graph_compute(graph,&plan)!=GGML_STATUS_SUCCESS){failed++;goto done;}
    memcpy(reference,dst->data,(size_t)rows*cols*sizeof(float));
    for(int round=0;round<(benchmark?3:1);round++)for(int order=0;order<2;order++) {
        int mode=(order+round)%2,repeats=benchmark?2:1;
        outpost_q2_set_mode(1);outpost_q2_set_batch_width(width);outpost_q2_set_row_tiles(1,4);outpost_q2_arm_chunks(chunk,0);outpost_q2_arm_i8mm_set(mode);
        double ns=0;bool same=true,complete=true;
        for(int repeat=0;repeat<repeats;repeat++) {
            memset(dst->data,0xff,(size_t)rows*cols*sizeof(float));
            double start=arm_ns();int status=ggml_graph_compute(graph,&plan);ns+=arm_ns()-start;
            complete&=status==GGML_STATUS_SUCCESS;same&=memcmp(reference,dst->data,(size_t)rows*cols*sizeof(float))==0;
            *comparisons+=(long long)rows*cols;
        }
        int expected=mode&&rows>=2&&cols>=2&&width>=2?repeats:0;
        bool dispatch=atomic_load(&i8mm_nodes)==expected&&outpost_q2_was_used();
        if(!same||!complete||!dispatch)failed++;
        if(benchmark)json_append(out,capacity,used,"%s{\"k\":%d,\"rows\":%d,\"columns\":%d,\"threads\":%d,\"width\":%d,\"chunk\":%d,\"round\":%d,\"i8mm\":%s,\"ns\":%.0f,\"parity\":%s,\"dispatch\":%s}",out[*used-1]=='['?"":",",k,rows,cols,threads,width,chunk,round,mode?"true":"false",ns/repeats,same?"true":"false",dispatch?"true":"false");
    }
done:
    free(reference);free(plan.work_data);if(pool)ggml_threadpool_free(pool);ggml_free(ctx);return failed;
}
#endif
void outpost_q2_arm_i8mm_checks(char *out,size_t capacity) {
#if defined(__aarch64__)
    if(!outpost_q2_arm_i8mm_available()){snprintf(out,capacity,"{\"supported\":false}");return;}
    ggml_cpu_init();int primitive=i8mm_primitive_checks(),failed=primitive;long long comparisons=0;size_t used=0;
    json_append(out,capacity,&used,"{\"supported\":true,\"primitiveComparisons\":128,\"primitiveFailures\":%d,\"benchmarks\":[",primitive);
    const int ks[]={64,128,2560,9728},cols[]={1,2,3,5,8,9,17},workers[]={1,4,6};
    if(!primitive) {
        for(int k=0;k<4;k++)for(int c=0;c<7;c++)for(int t=0;t<3;t++)for(int pad=0;pad<2;pad++)
            failed+=i8mm_graph(ks[k],17,cols[c],workers[t],8,pad?32:0,pad,false,out,capacity,&used,&comparisons);
        for(int rows=1;rows<=3;rows++)for(int width=1;width<=8;width*=2)
            failed+=i8mm_graph(128,rows,9,6,width,0,true,false,out,capacity,&used,&comparisons);
        const int shapes[][3]={{2560,1024,128},{2560,4096,128},{2560,9728,128},{4096,2560,128},{9728,2560,128},{2560,151669,1}};
        if(!failed)for(int s=0;s<6;s++)for(int width=4;width<=8;width*=2)
            failed+=i8mm_graph(shapes[s][0],shapes[s][1],shapes[s][2],6,width,32,false,true,out,capacity,&used,&comparisons);
    }
    json_append(out,capacity,&used,"],\"comparisons\":%lld,\"failures\":%d,\"scope\":\"On-device integer-layout and exact original-GGML matrix comparison, including odd dimensions, strided inputs, workers, fallback and output poisoning. Graph timing includes conversion/packing and is not model speed.\"}",comparisons,failed);
    outpost_q2_arm_i8mm_set(0);outpost_q2_arm_chunks(0,0);outpost_q2_set_mode(0);outpost_q2_set_batch_width(1);outpost_q2_set_row_tiles(1,1);
#else
    snprintf(out,capacity,"{\"supported\":false}");
#endif
}
