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
DOT __attribute__((noinline))
static void columns(int k,const block_q2_0 *x,const char *ys,size_t stride,int nt,float *out,size_t output_stride) {
    float32x4_t sums[8];for(int t=0;t<nt;t++)sums[t]=vdupq_n_f32(0);
    for(int i=0;i<k/64;i++) {
        const q2_half a=unpack_half(x[i].qs),b=unpack_half(x[i].qs+8);
        const float dx=arm_fp16(x[i].d);
        for(int t=0;t<nt;t++) {
            const block_q8_0 *y=(const block_q8_0 *)(ys+t*stride)+2*i;
            sums[t]=vfmaq_n_f32(sums[t],vcvtq_f32_s32(dot_half(a,y[0].qs,true)),dx*arm_fp16(y[0].d));
            sums[t]=vfmaq_n_f32(sums[t],vcvtq_f32_s32(dot_half(b,y[1].qs,true)),dx*arm_fp16(y[1].d));
        }
    }
    for(int t=0;t<nt;t++){float result=vaddvq_f32(sums[t]);memcpy((char *)out+t*output_stride,&result,sizeof(result));}
}
bool outpost_q2_arm_matmul(const struct ggml_compute_params *p,struct ggml_tensor *dst,int width) {
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
    char *quant=p->wdata;ggml_from_float_t convert=ggml_get_type_traits_cpu(GGML_TYPE_Q8_0)->from_float;
    for(int64_t col=p->ith;col<a->ne[1];col+=p->nth) {
        block_q8_0 *y=(block_q8_0 *)(quant+col*qs);
        convert((const float *)((const char *)a->data+col*a->nb[1]),y,a->ne[0]);
        prepare_activations(y,(int)a->ne[0]);
    }
    ggml_barrier(p->threadpool);
    for(int64_t row=w->ne[1]*p->ith/p->nth;row<w->ne[1]*(p->ith+1)/p->nth;row++)
        for(int64_t col=0;col<a->ne[1];col+=width) {
            int nt=(int)(a->ne[1]-col<width?a->ne[1]-col:width);
            columns((int)w->ne[0],(const block_q2_0 *)((const char *)w->data+row*w->nb[1]),quant+col*qs,qs,nt,
                (float *)((char *)dst->data+col*dst->nb[1])+row,dst->nb[1]);
        }
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
    if(!pool||!plan.work_data||!expected){failed++;goto done;}
    outpost_q2_set_mode(0);outpost_q2_set_batch_width(1);
    if(ggml_graph_compute(g,&plan)!=GGML_STATUS_SUCCESS){failed++;goto done;}
    memcpy(expected,d->data,(size_t)rows*cols*sizeof(float));
    int rounds=benchmark?3:1;
    for(int round=0;round<rounds;round++)for(int order=0;order<5;order++) {
        int choice=(round+order)%5,width=choice==0?1:1<<(choice-1);bool fast=choice!=0;
        outpost_q2_set_mode(fast);outpost_q2_set_batch_width(width);
        double start=arm_ns();int result=ggml_graph_compute(g,&plan);double ns=arm_ns()-start;
        bool parity=memcmp(expected,d->data,(size_t)rows*cols*sizeof(float))==0;
        bool dispatch=outpost_q2_was_used()==fast&&outpost_q2_batch_used()==(fast&&cols>1);
        *comparisons+=rows*cols;if(result!=GGML_STATUS_SUCCESS||!parity||!dispatch)failed++;
        if(benchmark)json_append(out,capacity,used,"%s{\"k\":%d,\"rows\":%d,\"columns\":%d,\"threads\":%d,\"round\":%d,\"fast\":%s,\"width\":%d,\"ns\":%.0f,\"parity\":%s,\"dispatch\":%s}",out[*used-1]=='['?"":",",k,rows,cols,threads,round,fast?"true":"false",width,ns,parity?"true":"false",dispatch?"true":"false");
    }
done:
    free(expected);free(plan.work_data);if(pool)ggml_threadpool_free(pool);ggml_free(ctx);return failed;
}
#endif
void outpost_q2_arm_graph_checks(char *out,size_t capacity) {
#if defined(__aarch64__)
    if(!outpost_q2_available()){snprintf(out,capacity,"{\"supported\":false}");return;}
    ggml_cpu_init();int failed=0,comparisons=0;size_t used=0;
    json_append(out,capacity,&used,"{\"supported\":true,\"benchmarks\":[");
    const int ks[]={64,128,2560,9728},columns_list[]={1,3,9},workers[]={1,4,6};
    for(int k=0;k<4;k++)for(int c=0;c<3;c++)for(int t=0;t<3;t++)for(int pad=0;pad<2;pad++)
        failed+=arm_graph(ks[k],17,columns_list[c],workers[t],pad,false,out,capacity,&used,&comparisons);
    const int shapes[][3]={{2560,4096,1},{2560,4096,64},{2560,9728,16},{9728,2560,16}};
    for(int s=0;s<4;s++)failed+=arm_graph(shapes[s][0],shapes[s][1],shapes[s][2],4,false,true,out,capacity,&used,&comparisons);
    json_append(out,capacity,&used,"],\"comparisons\":%d,\"failures\":%d,\"scope\":\"Bitwise ARM baseline parity, strided inputs, odd row/column tails,1/4/6 workers and rotated real-shape timings. Not model speed.\"}",comparisons,failed);
    outpost_q2_set_mode(0);outpost_q2_set_batch_width(1);
#else
    snprintf(out,capacity,"{\"supported\":false}");
#endif
}
