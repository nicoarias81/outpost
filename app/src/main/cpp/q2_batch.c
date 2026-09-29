#include "q2_kernel.h"
#include "ggml-cpu.h"
#include "ggml-cpu-impl.h"
#include "quants.h"
#include <stdatomic.h>
#include <string.h>
#include <math.h>
#include <stdlib.h>
_Static_assert(QK2_0==64 && QK8_0==32,"Requires pinned Q2_0 g64 and Q8_0 layouts");
#if defined(__x86_64__)
#include <immintrin.h>
#endif

bool __real_ggml_compute_forward_mul_mat_tiled(const struct ggml_compute_params *,struct ggml_tensor *);
void __real_ggml_vec_dot_q2_0_q8_0(int,float *,size_t,const void *,size_t,const void *,size_t,int);
static _Atomic int batch_width=1;
static _Atomic int batch_used;
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
#endif

bool __wrap_ggml_compute_forward_mul_mat_tiled(const struct ggml_compute_params *p,struct ggml_tensor *dst) {
#if defined(__x86_64__)
    const struct ggml_tensor *w=dst->src[0],*a=dst->src[1];
    int width=atomic_load_explicit(&batch_width,memory_order_relaxed);
    if(width>1 && !p->use_ref && outpost_q2_fast_enabled() && w->type==GGML_TYPE_Q2_0 && a->type==GGML_TYPE_F32 && dst->type==GGML_TYPE_F32
        && w->ne[0]==a->ne[0] && w->ne[0]%64==0 && w->ne[0]<=INT32_MAX && a->ne[1]>=2
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
            for(int64_t row=w->ne[1]*p->ith/p->nth;row<w->ne[1]*(p->ith+1)/p->nth;row++) {
                for(int64_t col=0;col<a->ne[1];col+=width) {
                    const int nt=(int)(a->ne[1]-col<width ? a->ne[1]-col : width);
                    multi((int)w->ne[0],(const block_q2_0 *)((const char *)w->data+row*w->nb[1]),quant+col*qs,qs,nt,
                        (float *)((char *)dst->data+col*dst->nb[1]+row*sizeof(float)),dst->nb[1]);
                }
            }
            atomic_store_explicit(&batch_used,1,memory_order_relaxed);
            return true;
        }
    }
#endif
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
