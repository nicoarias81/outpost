#include "q2_kernel.h"
#include "q2_arm.h"
#include "ggml-cpu.h"
#include "ggml-cpu-impl.h"
#include "quants.h"
#include <stdatomic.h>
#include <string.h>
#include <math.h>
#include <stdlib.h>
#include <stdio.h>
#include <stdarg.h>
#include <time.h>
#include <sys/mman.h>
#include <unistd.h>
_Static_assert(QK2_0==64 && QK8_0==32,"Requires pinned Q2_0 g64 and Q8_0 layouts");
#if defined(__x86_64__)
#include <immintrin.h>
#endif

bool __real_ggml_compute_forward_mul_mat_tiled(const struct ggml_compute_params *,struct ggml_tensor *);
void __real_ggml_vec_dot_q2_0_q8_0(int,float *,size_t,const void *,size_t,const void *,size_t,int);
static _Atomic int batch_width=1;
static _Atomic int batch_used;
static _Atomic int row_tile=1,decode_row_tile=1,rows_used,profile_enabled;
struct row_shape {int64_t k,rows,columns,calls;int selected;};
static struct row_shape row_shapes[128];
static int shape_count,shape_overflow;
void outpost_q2_set_row_tiles(int prefill_rows,int decode_rows){atomic_store(&row_tile,prefill_rows==2?2:1);atomic_store(&decode_row_tile,decode_rows==2||decode_rows==4?decode_rows:1);atomic_store(&rows_used,0);}
void outpost_q2_set_row_tile(int rows){outpost_q2_set_row_tiles(rows==2?2:1,rows);}
int outpost_q2_rows_used(void){return atomic_load(&rows_used);}
void outpost_q2_rows_profile_begin(void){shape_count=0;shape_overflow=0;atomic_store(&profile_enabled,1);}
static void note_shape(const struct ggml_compute_params *p,const struct ggml_tensor *w,const struct ggml_tensor *a,int selected){
    if(p->ith || !atomic_load_explicit(&profile_enabled,memory_order_relaxed) || w->type!=GGML_TYPE_Q2_0)return;
    for(int i=0;i<shape_count;i++)if(row_shapes[i].k==w->ne[0]&&row_shapes[i].rows==w->ne[1]&&row_shapes[i].columns==a->ne[1]&&row_shapes[i].selected==selected){row_shapes[i].calls++;return;}
    if(shape_count<128)row_shapes[shape_count++]=(struct row_shape){w->ne[0],w->ne[1],a->ne[1],1,selected};else shape_overflow++;
}
static void append_json(char *out,size_t capacity,size_t *used,const char *fmt,...){
    if(*used>=capacity)return;va_list args;va_start(args,fmt);int n=vsnprintf(out+*used,capacity-*used,fmt,args);va_end(args);
    if(n<0)return;*used+=(size_t)n;if(*used>=capacity){*used=capacity;out[capacity-1]=0;}
}
void outpost_q2_rows_profile_end(char *out,size_t capacity){
    atomic_store(&profile_enabled,0);size_t used=0;append_json(out,capacity,&used,"{\"overflow\":%d,\"shapes\":[",shape_overflow);
    for(int i=0;i<shape_count;i++){const struct row_shape *s=&row_shapes[i];append_json(out,capacity,&used,"%s{\"k\":%lld,\"rows\":%lld,\"columns\":%lld,\"calls\":%lld,\"selectedRows\":%d}",i?",":"",(long long)s->k,(long long)s->rows,(long long)s->columns,(long long)s->calls,s->selected);}
    append_json(out,capacity,&used,"]}");
}
void outpost_q2_set_batch_width(int width) {
    atomic_store(&batch_width,width==2 || width==4 || width==8 ? width : 1);
    atomic_store(&batch_used,0);
}
int outpost_q2_batch_used(void) { return atomic_load(&batch_used); }

#if defined(__x86_64__)
__attribute__((target("avx2,f16c"),always_inline))
static inline __m256i unpack32(const uint8_t *p) {
    __m256i a=_mm256_cvtepu8_epi32(_mm_loadl_epi64((const __m128i_u *)p));
    __m256i b=_mm256_or_si256(a,_mm256_slli_epi32(a,6));
    b=_mm256_or_si256(b,_mm256_slli_epi32(a,12));
    return _mm256_and_si256(_mm256_or_si256(b,_mm256_slli_epi32(a,18)),_mm256_set1_epi32(0x03030303));
}
__attribute__((target("avx2,f16c"),always_inline))
static inline int dot32(__m256i codes,const int8_t *q) {
    const __m256i y=_mm256_loadu_si256((const __m256i_u *)q);
    const __m256i pairs=_mm256_sub_epi16(_mm256_maddubs_epi16(codes,y),_mm256_maddubs_epi16(_mm256_set1_epi8(1),y));
    const __m256i sums=_mm256_madd_epi16(pairs,_mm256_set1_epi16(1));
    __m128i total=_mm_add_epi32(_mm256_castsi256_si128(sums),_mm256_extracti128_si256(sums,1));
    total=_mm_add_epi32(total,_mm_srli_si128(total,8));
    return _mm_cvtsi128_si32(_mm_add_epi32(total,_mm_srli_si128(total,4)));
}
__attribute__((target("avx2,f16c"),noinline))
static void multi(int n,const block_q2_0 *x,const char *ys,size_t stride,int nt,float *out,size_t out_stride) {
    float sum[8]={0};
    for(int b=0;b<n/64;b++) {
        const __m256i lo=unpack32(x[b].qs),hi=unpack32(x[b].qs+8);
        const float scale=_cvtsh_ss(x[b].d);
        for(int t=0;t<nt;t++) {
            const block_q8_0 *y=(const block_q8_0 *)(ys+t*stride)+2*b;
            float inner=0;
            inner+=_cvtsh_ss(y[0].d)*dot32(lo,y[0].qs);
            inner+=_cvtsh_ss(y[1].d)*dot32(hi,y[1].qs);
            sum[t]+=scale*inner;
        }
    }
    for(int t=0;t<nt;t++) memcpy((char *)out+t*out_stride,&sum[t],sizeof(float));
}

__attribute__((target("avx2,f16c"),always_inline))
static inline int prepared_dot(__m256i codes,__m256i values,__m256i correction){
    __m256i pairs=_mm256_sub_epi16(_mm256_maddubs_epi16(codes,values),correction);
    __m256i sums=_mm256_madd_epi16(pairs,_mm256_set1_epi16(1));
    __m128i total=_mm_add_epi32(_mm256_castsi256_si128(sums),_mm256_extracti128_si256(sums,1));
    total=_mm_add_epi32(total,_mm_srli_si128(total,8));
    return _mm_cvtsi128_si32(_mm_add_epi32(total,_mm_srli_si128(total,4)));
}
// Compile-time row counts let the compiler keep independent accumulators in registers.
#define ROW_KERNEL(R) \
__attribute__((target("avx2,f16c"),noinline)) \
static void rows##R(int n,const char *weights,size_t stride,const block_q8_0 *y,float *out){ \
    float sums[R]={0}; \
    for(int b=0;b<n/64;b++){ \
        const __m256i y0=_mm256_loadu_si256((const __m256i_u *)y[2*b].qs),y1=_mm256_loadu_si256((const __m256i_u *)y[2*b+1].qs); \
        const __m256i c0=_mm256_maddubs_epi16(_mm256_set1_epi8(1),y0),c1=_mm256_maddubs_epi16(_mm256_set1_epi8(1),y1); \
        const float d0=_cvtsh_ss(y[2*b].d),d1=_cvtsh_ss(y[2*b+1].d); \
        for(int r=0;r<R;r++){ \
            const block_q2_0 *x=(const block_q2_0 *)(weights+r*stride)+b; \
            float inner=0;inner+=d0*prepared_dot(unpack32(x->qs),y0,c0);inner+=d1*prepared_dot(unpack32(x->qs+8),y1,c1); \
            sums[r]+=_cvtsh_ss(x->d)*inner; \
        } \
    } \
    memcpy(out,sums,sizeof(sums)); \
}
ROW_KERNEL(2)
ROW_KERNEL(4)
#undef ROW_KERNEL

__attribute__((target("avx2,f16c"),noinline))
static void multi_rows2(int n,const char *weights,size_t row_stride,const char *ys,size_t col_stride,int nt,float *out,size_t out_stride){
    float sum0[8]={0},sum1[8]={0};
    for(int b=0;b<n/64;b++){
        const block_q2_0 *x0=(const block_q2_0 *)weights+b,*x1=(const block_q2_0 *)(weights+row_stride)+b;
        const __m256i lo0=unpack32(x0->qs),hi0=unpack32(x0->qs+8),lo1=unpack32(x1->qs),hi1=unpack32(x1->qs+8);
        const float dx0=_cvtsh_ss(x0->d),dx1=_cvtsh_ss(x1->d);
        for(int t=0;t<nt;t++){
            const block_q8_0 *y=(const block_q8_0 *)(ys+t*col_stride)+2*b;
            const __m256i y0=_mm256_loadu_si256((const __m256i_u *)y[0].qs),y1=_mm256_loadu_si256((const __m256i_u *)y[1].qs);
            const __m256i c0=_mm256_maddubs_epi16(_mm256_set1_epi8(1),y0),c1=_mm256_maddubs_epi16(_mm256_set1_epi8(1),y1);
            const float dy0=_cvtsh_ss(y[0].d),dy1=_cvtsh_ss(y[1].d);
            float inner0=0,inner1=0;
            inner0+=dy0*prepared_dot(lo0,y0,c0);inner0+=dy1*prepared_dot(hi0,y1,c1);
            inner1+=dy0*prepared_dot(lo1,y0,c0);inner1+=dy1*prepared_dot(hi1,y1,c1);
            sum0[t]+=dx0*inner0;sum1[t]+=dx1*inner1;
        }
    }
    for(int t=0;t<nt;t++){memcpy((char *)out+t*out_stride,&sum0[t],sizeof(float));memcpy((char *)out+t*out_stride+sizeof(float),&sum1[t],sizeof(float));}
}
#endif

bool __wrap_ggml_compute_forward_mul_mat_tiled(const struct ggml_compute_params *p,struct ggml_tensor *dst) {
#if defined(__aarch64__)
    int arm_rows=atomic_load_explicit(&decode_row_tile,memory_order_relaxed);
    if(outpost_q2_arm_matmul(p,dst,atomic_load_explicit(&batch_width,memory_order_relaxed),arm_rows)) {
        if(dst->src[1]->ne[1]>1)atomic_store_explicit(&batch_used,1,memory_order_relaxed);
        if(dst->src[1]->ne[1]==1&&arm_rows>1&&dst->src[0]->ne[1]>=arm_rows*p->nth)atomic_store_explicit(&rows_used,1,memory_order_relaxed);
        note_shape(p,dst->src[0],dst->src[1],1);return true;
    }
#endif
#if defined(__x86_64__)
    const struct ggml_tensor *w=dst->src[0],*a=dst->src[1];
    int width=atomic_load_explicit(&batch_width,memory_order_relaxed);
    int rows=atomic_load_explicit(a->ne[1]==1?&decode_row_tile:&row_tile,memory_order_relaxed);
    bool use_rows=rows>1&&((a->ne[1]==1&&w->ne[1]>=rows*p->nth)||(rows==2&&width>1&&a->ne[1]>=2&&w->ne[1]>=2*p->nth));
    if((use_rows || (width>1&&a->ne[1]>=2)) && !p->use_ref && outpost_q2_fast_enabled() && w->type==GGML_TYPE_Q2_0 && a->type==GGML_TYPE_F32 && dst->type==GGML_TYPE_F32
        && w->ne[0]==a->ne[0] && w->ne[0]%64==0 && w->ne[0]<=INT32_MAX
        && w->ne[2]==1 && w->ne[3]==1 && a->ne[2]==1 && a->ne[3]==1
        && w->nb[0]==sizeof(block_q2_0) && a->nb[0]==sizeof(float) && dst->nb[0]==sizeof(float)
        && dst->ne[0]==w->ne[1] && dst->ne[1]==a->ne[1] && dst->ne[2]==1 && dst->ne[3]==1) {
        const size_t qs=ggml_row_size(GGML_TYPE_Q8_0,a->ne[0]);
        if(qs && (size_t)a->ne[1]<=p->wsize/qs && w->nb[1]>=ggml_row_size(w->type,w->ne[0])
            && a->nb[1]>=a->ne[0]*sizeof(float) && dst->nb[1]>=dst->ne[0]*sizeof(float)) {
            char *quant=p->wdata;
            ggml_from_float_t convert=ggml_get_type_traits_cpu(GGML_TYPE_Q8_0)->from_float;
            for(int64_t col=p->ith;col<a->ne[1];col+=p->nth)
                convert((const float *)((const char *)a->data+col*a->nb[1]),quant+col*qs,a->ne[0]);
            ggml_barrier(p->threadpool);
            if(use_rows){
                const int64_t end=w->ne[1]*(p->ith+1)/p->nth;int64_t row=w->ne[1]*p->ith/p->nth;
                if(a->ne[1]>1){
                    for(;row+2<=end;row+=2)for(int64_t col=0;col<a->ne[1];col+=width){
                        int nt=(int)(a->ne[1]-col<width?a->ne[1]-col:width);
                        multi_rows2((int)w->ne[0],(const char *)w->data+row*w->nb[1],w->nb[1],quant+col*qs,qs,nt,(float *)((char *)dst->data+col*dst->nb[1])+row,dst->nb[1]);
                    }
                    for(;row<end;row++)for(int64_t col=0;col<a->ne[1];col+=width){
                        int nt=(int)(a->ne[1]-col<width?a->ne[1]-col:width);
                        multi((int)w->ne[0],(const block_q2_0 *)((const char *)w->data+row*w->nb[1]),quant+col*qs,qs,nt,(float *)((char *)dst->data+col*dst->nb[1])+row,dst->nb[1]);
                    }
                    atomic_store_explicit(&rows_used,1,memory_order_relaxed);atomic_store_explicit(&batch_used,1,memory_order_relaxed);note_shape(p,w,a,2);return true;
                }
                for(;row+rows<=end;row+=rows){
                    const char *weights=(const char *)w->data+row*w->nb[1];float *output=(float *)dst->data+row;
                    if(rows==4)rows4((int)w->ne[0],weights,w->nb[1],(const block_q8_0 *)quant,output);else rows2((int)w->ne[0],weights,w->nb[1],(const block_q8_0 *)quant,output);
                }
                for(;row<end;row++)multi((int)w->ne[0],(const block_q2_0 *)((const char *)w->data+row*w->nb[1]),quant,qs,1,(float *)dst->data+row,dst->nb[1]);
                atomic_store_explicit(&rows_used,1,memory_order_relaxed);note_shape(p,w,a,rows);return true;
            }
            for(int64_t row=w->ne[1]*p->ith/p->nth;row<w->ne[1]*(p->ith+1)/p->nth;row++) {
                for(int64_t col=0;col<a->ne[1];col+=width) {
                    const int nt=(int)(a->ne[1]-col<width ? a->ne[1]-col : width);
                    multi((int)w->ne[0],(const block_q2_0 *)((const char *)w->data+row*w->nb[1]),quant+col*qs,qs,nt,
                        (float *)((char *)dst->data+col*dst->nb[1]+row*sizeof(float)),dst->nb[1]);
                }
            }
            atomic_store_explicit(&batch_used,1,memory_order_relaxed);
            note_shape(p,w,a,0);
            return true;
        }
    }
#endif
    note_shape(p,dst->src[0],dst->src[1],0);
    return __real_ggml_compute_forward_mul_mat_tiled(p,dst);
}

outpost_q2_report outpost_q2_batch_test(void) {
    outpost_q2_report r={0}; r.supported=outpost_q2_available();
#if defined(__x86_64__)
    if(!r.supported) return r;
    const int lengths[]={64,128,576,4096,12288};
    unsigned char xb[192*sizeof(block_q2_0)+32], yb[4*(384*sizeof(block_q8_0)+16)+32];
    block_q2_0 *x=(block_q2_0 *)(xb+2);
    char *y=(char *)yb+6;
    uint32_t state=0xa13456;
#define NEXT() (state=state*1664525u+1013904223u)
    for(int trial=0;trial<1500;trial++) {
        int n=lengths[trial%5],nt=1+trial%4;
        size_t stride=(size_t)(n/32)*sizeof(block_q8_0)+8;
        for(int b=0;b<n/64;b++) {
            uint16_t magnitude=(uint16_t)(NEXT()%0x7c00),sign=(uint16_t)((NEXT()>>16)&0x8000);
            x[b].d=magnitude | sign;
            for(int j=0;j<16;j++) x[b].qs[j]=(uint8_t)(NEXT()>>16);
        }
        for(int t=0;t<nt;t++) {
            block_q8_0 *q=(block_q8_0 *)(y+t*stride);
            for(int b=0;b<n/32;b++) {
                uint16_t magnitude=(uint16_t)(NEXT()%0x7c00),sign=(uint16_t)((NEXT()>>16)&0x8000);
                q[b].d=magnitude | sign;
                for(int j=0;j<32;j++) q[b].qs[j]=(int8_t)(NEXT()>>16);
            }
        }
        float output[12]; for(int i=0;i<12;i++) output[i]=12345;
        multi(n,x,y,stride,nt,&output[1],3*sizeof(float));
        for(int t=0;t<nt;t++) {
            float expected=0;
            __real_ggml_vec_dot_q2_0_q8_0(n,&expected,0,x,0,y+t*stride,0,1);
            r.cases++;
            if(memcmp(&expected,&output[t*3+1],4) || output[t*3]!=12345 || output[t*3+2]!=12345) r.bit_mismatches++;
            double error=fabs((double)expected-output[t*3+1]); if(error>r.max_abs) r.max_abs=error;
        }
    }
    // Verify the linker hook, quantization workspace, thread partition and odd tails in a real GGML graph.
    ggml_cpu_init();
    r.dispatch_ok=1;
    const int cols[]={1,2,3,4,5,9,17};
    for(int ci=0;ci<7;ci++) for(int threads=1;threads<=4;threads*=2) {
        struct ggml_init_params init={4*1024*1024,NULL,false};
        struct ggml_context *ctx=ggml_init(init);
        if(!ctx) { r.dispatch_ok=0; continue; }
        struct ggml_tensor *w=ggml_new_tensor_2d(ctx,GGML_TYPE_Q2_0,128,17);
        struct ggml_tensor *a=ggml_new_tensor_2d(ctx,GGML_TYPE_F32,128,cols[ci]);
        block_q2_0 *weights=w->data;
        for(int b=0;b<34;b++) { weights[b].d=ggml_fp32_to_fp16(0.125f); for(int j=0;j<16;j++) weights[b].qs[j]=(uint8_t)(NEXT()>>16); }
        for(int j=0;j<128*cols[ci];j++) ((float *)a->data)[j]=((int)(NEXT()%2001)-1000)/137.0f;
        struct ggml_tensor *dst=ggml_mul_mat(ctx,w,a);
        struct ggml_cgraph *g=ggml_new_graph(ctx); ggml_build_forward_expand(g,dst);
        float expected[17*17];
        outpost_q2_set_batch_width(1);
        if(ggml_graph_compute_with_ctx(ctx,g,threads)!=GGML_STATUS_SUCCESS) r.dispatch_ok=0;
        memcpy(expected,dst->data,17*cols[ci]*sizeof(float));
        for(int width=2;width<=8;width*=2) {
            outpost_q2_set_batch_width(width);
            if(ggml_graph_compute_with_ctx(ctx,g,threads)!=GGML_STATUS_SUCCESS) r.dispatch_ok=0;
            if(outpost_q2_batch_used()!=(cols[ci]>=2)) r.dispatch_ok=0;
            for(int j=0;j<17*cols[ci];j++) {
                r.cases++;
                if(memcmp(&expected[j],(float *)dst->data+j,sizeof(float))) r.bit_mismatches++;
            }
        }
        ggml_free(ctx);
    }
    outpost_q2_set_batch_width(1);
#undef NEXT
#endif
    return r;
}

#if defined(__x86_64__)
static double row_clock_ns(void){struct timespec t;clock_gettime(CLOCK_MONOTONIC,&t);return t.tv_sec*1e9+t.tv_nsec;}
static uint32_t row_random(uint32_t *state){*state=*state*1664525u+1013904223u;return *state;}
static int graph_rows_check(int k,int n,int columns,int threads,bool padded,int *comparisons){
    size_t bytes=(size_t)(k+64)*n+((size_t)k+n)*columns*sizeof(float)+4*1024*1024;
    struct ggml_context *ctx=ggml_init((struct ggml_init_params){bytes,NULL,false});if(!ctx)return 1;
    struct ggml_tensor *storage=ggml_new_tensor_2d(ctx,GGML_TYPE_Q2_0,padded?k+64:k,n);
    struct ggml_tensor *w=padded?ggml_view_2d(ctx,storage,k,n,storage->nb[1],0):storage;
    struct ggml_tensor *a=ggml_new_tensor_2d(ctx,GGML_TYPE_F32,k,columns);
    uint32_t seed=1234;
    for(int row=0;row<n;row++){block_q2_0 *x=(block_q2_0 *)((char *)w->data+row*w->nb[1]);for(int b=0;b<k/64;b++){x[b].d=ggml_fp32_to_fp16(0.125f);for(int j=0;j<16;j++)x[b].qs[j]=(uint8_t)(row_random(&seed)>>16);}}
    for(int i=0;i<k*columns;i++)((float *)a->data)[i]=((int)(row_random(&seed)%2001)-1000)/127.0f;
    struct ggml_tensor *d=ggml_mul_mat(ctx,w,a);struct ggml_cgraph *g=ggml_new_graph(ctx);ggml_build_forward_expand(g,d);
    float *expected=malloc((size_t)n*columns*sizeof(float));int failed=expected?0:1;
    if(expected){outpost_q2_set_row_tile(1);outpost_q2_set_batch_width(4);
        if(ggml_graph_compute_with_ctx(ctx,g,threads)!=GGML_STATUS_SUCCESS)failed++;
        memcpy(expected,d->data,(size_t)n*columns*sizeof(float));
        for(int rows=2;rows<=4;rows*=2){outpost_q2_set_row_tile(rows);if(ggml_graph_compute_with_ctx(ctx,g,threads)!=GGML_STATUS_SUCCESS)failed++;
            if(outpost_q2_rows_used()!=((columns==1&&n>=rows*threads)||(rows==2&&columns>1&&n>=2*threads)))failed++;
            for(int i=0;i<n*columns;i++){(*comparisons)++;if(memcmp(expected+i,(float *)d->data+i,sizeof(float)))failed++;}
        }
        // Forced reference must bypass the custom row path while preserving values.
        outpost_q2_set_mode(0);outpost_q2_set_row_tile(4);if(ggml_graph_compute_with_ctx(ctx,g,threads)!=GGML_STATUS_SUCCESS||outpost_q2_rows_used())failed++;
        for(int i=0;i<n*columns;i++){(*comparisons)++;if(memcmp(expected+i,(float *)d->data+i,sizeof(float)))failed++;}
        outpost_q2_set_mode(1);
        for(int phase=0;phase<2;phase++){
            outpost_q2_set_row_tiles(phase==0?2:1,phase==0?1:2);
            if(ggml_graph_compute_with_ctx(ctx,g,threads)!=GGML_STATUS_SUCCESS)failed++;
            bool selected=n>=2*threads&&(phase==0?columns>1:columns==1);
            if(outpost_q2_rows_used()!=selected)failed++;
            for(int i=0;i<n*columns;i++){(*comparisons)++;if(memcmp(expected+i,(float *)d->data+i,sizeof(float)))failed++;}
        }
        free(expected);
    }
    ggml_free(ctx);return failed;
}
static int prefill_row_benchmarks(char *out,size_t capacity,size_t *used,volatile unsigned char *pressure){
    const int shapes[][3]={{2560,4096,128},{2560,9728,32},{9728,2560,32}};int failed=0;bool first=true;uint32_t seed=4221;
    append_json(out,capacity,used,"],\"prefillBenchmarks\":[");
    for(int s=0;s<3;s++){
        int k=shapes[s][0],n=shapes[s][1],columns=shapes[s][2];size_t bytes=ggml_row_size(GGML_TYPE_Q2_0,k)*(size_t)n+(size_t)(k+n)*columns*sizeof(float)+4*1024*1024;
        struct ggml_context *ctx=ggml_init((struct ggml_init_params){bytes,NULL,false});if(!ctx){failed++;continue;}
        struct ggml_tensor *w=ggml_new_tensor_2d(ctx,GGML_TYPE_Q2_0,k,n),*a=ggml_new_tensor_2d(ctx,GGML_TYPE_F32,k,columns),*d=ggml_mul_mat(ctx,w,a);
        struct ggml_cgraph *g=ggml_new_graph(ctx);ggml_build_forward_expand(g,d);
        block_q2_0 *weights=w->data;for(size_t b=0;b<(size_t)k*n/64;b++){weights[b].d=ggml_fp32_to_fp16(0.125f);for(int j=0;j<16;j++)weights[b].qs[j]=(uint8_t)(row_random(&seed)>>16);}
        for(int i=0;i<k*columns;i++)((float *)a->data)[i]=((int)(row_random(&seed)%2001)-1000)/127.0f;
        struct ggml_threadpool_params settings=ggml_threadpool_params_default(4);struct ggml_threadpool *pool=ggml_threadpool_new(&settings);
        struct ggml_cplan plan=ggml_graph_plan(g,4,pool);plan.work_data=malloc(plan.work_size?plan.work_size:1);float *expected=malloc((size_t)n*columns*sizeof(float));
        if(!pool||!plan.work_data||!expected){failed++;}else{
            outpost_q2_set_row_tile(1);outpost_q2_set_batch_width(4);if(ggml_graph_compute(g,&plan)!=GGML_STATUS_SUCCESS)failed++;memcpy(expected,d->data,(size_t)n*columns*sizeof(float));
            for(int cache=0;cache<2;cache++)for(int round=0;round<3;round++)for(int order=0;order<2;order++){
                int rows=1+((round+order)%2);double timings[3];bool parity=true,dispatch=true;outpost_q2_set_row_tile(rows);
                for(int repeat=0;repeat<3;repeat++){
                    if(cache&&pressure)for(size_t i=0;i<64*1024*1024;i+=64)pressure[i]++;
                    double start=row_clock_ns();if(ggml_graph_compute(g,&plan)!=GGML_STATUS_SUCCESS)failed++;timings[repeat]=row_clock_ns()-start;
                    parity&=!memcmp(expected,d->data,(size_t)n*columns*sizeof(float));dispatch&=outpost_q2_rows_used()==(rows==2);
                }
                double ns=fmax(fmin(timings[0],timings[1]),fmin(fmax(timings[0],timings[1]),timings[2]));
                if(!parity||!dispatch)failed++;
                append_json(out,capacity,used,"%s{\"k\":%d,\"outputs\":%d,\"columns\":%d,\"threads\":4,\"matrixWidth\":4,\"rowTile\":%d,\"pressureMiB\":%d,\"round\":%d,\"medianOfThreeNs\":%.0f,\"parity\":%s,\"dispatch\":%s}",first?"":",",k,n,columns,rows,cache&&pressure?64:0,round,ns,parity?"true":"false",dispatch?"true":"false");first=false;
            }
        }free(expected);free(plan.work_data);if(pool)ggml_threadpool_free(pool);ggml_free(ctx);
    }
    return failed;
}
#endif

void outpost_q2_rows_benchmark(char *out,size_t capacity){
    size_t used=0;int comparisons=0,failed=0,guard=0;
    if(!outpost_q2_available()){snprintf(out,capacity,"{\"supported\":false}");return;}
#if defined(__x86_64__)
    ggml_cpu_init();outpost_q2_set_mode(1);outpost_q2_set_batch_width(1);
    uint32_t seed=0xa2718;
    const int lengths[]={64,128,2560,4096,9728};
    for(int trial=0;trial<300;trial++){
        int k=lengths[trial%5];size_t stride=(size_t)k/64*sizeof(block_q2_0)+18;
        char *raw=malloc(4*stride+2);char *weights=raw?raw+2:NULL;block_q8_0 *y=malloc((size_t)k/32*sizeof(block_q8_0));
        if(!raw||!y){failed++;free(raw);free(y);break;}
        for(int r=0;r<4;r++){block_q2_0 *x=(block_q2_0 *)(weights+r*stride);for(int b=0;b<k/64;b++){x[b].d=(uint16_t)(row_random(&seed)%0x7c00)|((row_random(&seed)&1)<<15);for(int j=0;j<16;j++)x[b].qs[j]=(uint8_t)(row_random(&seed)>>16);}}
        for(int b=0;b<k/32;b++){y[b].d=(uint16_t)(row_random(&seed)%0x7c00)|((row_random(&seed)&1)<<15);for(int j=0;j<32;j++)y[b].qs[j]=(int8_t)(row_random(&seed)>>16);}
        for(int r=2;r<=4;r*=2){float actual[6]={12345,0,0,0,0,-12345};if(r==2)rows2(k,weights,stride,y,actual+1);else rows4(k,weights,stride,y,actual+1);
            for(int row=0;row<r;row++){float expected=0;__real_ggml_vec_dot_q2_0_q8_0(k,&expected,0,weights+row*stride,0,y,0,1);comparisons++;if(memcmp(&expected,actual+row+1,4))failed++;}
            if(actual[0]!=12345||actual[5]!=-12345||(r==2&&(actual[3]!=0||actual[4]!=0)))failed++;
        }free(raw);free(y);
    }
    const size_t page=(size_t)sysconf(_SC_PAGESIZE);char *gx=mmap(NULL,page*2,PROT_READ|PROT_WRITE,MAP_PRIVATE|MAP_ANONYMOUS,-1,0),*gy=mmap(NULL,page*2,PROT_READ|PROT_WRITE,MAP_PRIVATE|MAP_ANONYMOUS,-1,0);
    if(gx!=MAP_FAILED&&gy!=MAP_FAILED&&mprotect(gx+page,page,PROT_NONE)==0&&mprotect(gy+page,page,PROT_NONE)==0){
        block_q2_0 *x=(block_q2_0 *)(gx+page-4*sizeof(block_q2_0));block_q8_0 *y=(block_q8_0 *)(gy+page-2*sizeof(block_q8_0));
        for(int i=0;i<4;i++){x[i].d=ggml_fp32_to_fp16(0.125f);memset(x[i].qs,255,sizeof(x[i].qs));}
        for(int i=0;i<2;i++){y[i].d=ggml_fp32_to_fp16(0.25f);memset(y[i].qs,-128,sizeof(y[i].qs));}
        float actual[4];rows4(64,(const char *)x,sizeof(*x),y,actual);guard=1;
        for(int i=0;i<4;i++){float expected=0;__real_ggml_vec_dot_q2_0_q8_0(64,&expected,0,x+i,0,y,0,1);comparisons++;if(memcmp(&expected,actual+i,4))failed++;}
    }else failed++;
    if(gx!=MAP_FAILED)munmap(gx,page*2);if(gy!=MAP_FAILED)munmap(gy,page*2);
    for(int threads=1;threads<=4;threads*=2)for(int n=1;n<=17;n+=8)for(int cols=1;cols<=3;cols++)failed+=graph_rows_check(2560,n,cols,threads,true,&comparisons);
    append_json(out,capacity,&used,"{\"supported\":true,\"comparisons\":%d,\"failures\":%d,\"guardPassed\":%s,\"benchmarks\":[",comparisons,failed,guard?"true":"false");
    const int shapes[][2]={{2560,1024},{2560,4096},{2560,9728},{4096,2560},{9728,2560},{2560,151669}};
    volatile unsigned char *pressure=malloc(64*1024*1024);if(pressure)memset((void *)pressure,0,64*1024*1024);
    bool first=true;int benchmark_failures=0;
    for(int s=0;s<6;s++){
        int k=shapes[s][0],n=shapes[s][1];size_t bytes=ggml_row_size(GGML_TYPE_Q2_0,k)*(size_t)n+(size_t)(k+n)*sizeof(float)+4*1024*1024;
        struct ggml_context *ctx=ggml_init((struct ggml_init_params){bytes,NULL,false});if(!ctx){benchmark_failures++;continue;}
        struct ggml_tensor *w=ggml_new_tensor_2d(ctx,GGML_TYPE_Q2_0,k,n),*a=ggml_new_tensor_2d(ctx,GGML_TYPE_F32,k,1),*d=ggml_mul_mat(ctx,w,a);
        struct ggml_cgraph *g=ggml_new_graph(ctx);ggml_build_forward_expand(g,d);
        block_q2_0 *weights=w->data;for(size_t b=0;b<(size_t)k*n/64;b++){weights[b].d=ggml_fp32_to_fp16(0.125f);for(int j=0;j<16;j++)weights[b].qs[j]=(uint8_t)(row_random(&seed)>>16);}
        for(int i=0;i<k;i++)((float *)a->data)[i]=((int)(row_random(&seed)%2001)-1000)/127.0f;
        struct ggml_threadpool_params settings=ggml_threadpool_params_default(4);struct ggml_threadpool *pool=ggml_threadpool_new(&settings);
        struct ggml_cplan plan=ggml_graph_plan(g,4,pool);plan.work_data=malloc(plan.work_size?plan.work_size:1);float *expected=malloc((size_t)n*sizeof(float));
        if(!pool||!plan.work_data||!expected){benchmark_failures++;}else{
            outpost_q2_set_row_tile(1);outpost_q2_set_batch_width(1);if(ggml_graph_compute(g,&plan)!=GGML_STATUS_SUCCESS)benchmark_failures++;memcpy(expected,d->data,(size_t)n*sizeof(float));
            for(int cache=0;cache<2;cache++)for(int round=0;round<3;round++)for(int order=0;order<3;order++){
                int rows=1<<((round+order)%3);outpost_q2_set_row_tile(rows);
                // Disturb caches outside timing; not a claim that every cache level is cold.
                if(cache&&pressure)for(size_t i=0;i<64*1024*1024;i+=64)pressure[i]++;
                double start=row_clock_ns();int status=ggml_graph_compute(g,&plan);double ns=row_clock_ns()-start;
                bool parity=!memcmp(expected,d->data,(size_t)n*sizeof(float));bool dispatch=outpost_q2_rows_used()==(rows>1);
                if(status!=GGML_STATUS_SUCCESS||!parity||!dispatch)benchmark_failures++;
                append_json(out,capacity,&used,"%s{\"k\":%d,\"outputs\":%d,\"threads\":4,\"rowTile\":%d,\"pressureMiB\":%d,\"round\":%d,\"elapsedNs\":%.0f,\"parity\":%s,\"dispatch\":%s}",first?"":",",k,n,rows,cache&&pressure?64:0,round,ns,parity?"true":"false",dispatch?"true":"false");first=false;
            }
        }
        free(expected);free(plan.work_data);if(pool)ggml_threadpool_free(pool);ggml_free(ctx);
    }
    benchmark_failures+=prefill_row_benchmarks(out,capacity,&used,pressure);
    free((void *)pressure);outpost_q2_set_row_tile(1);outpost_q2_set_batch_width(1);
    append_json(out,capacity,&used,"],\"benchmarkFailures\":%d,\"cachePressureAllocated\":%s,\"scope\":\"Actual Bonsai matrix shapes with synthetic weights/activations, persistent four-thread GGML plan. Three rotating rounds; setup and 64 MiB cache pressure excluded from timing. Emulator only.\"}",benchmark_failures,pressure?"true":"false");
#else
    snprintf(out,capacity,"{\"supported\":false}");
#endif
}
