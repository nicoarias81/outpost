#include "q2_kernel.h"
#include "q2_dispatch.h"
#include "q2_arm.h"
#include "ggml-cpu.h"
#include "quants.h"
#include <assert.h>
#include <math.h>
#include <pthread.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>
#include <sys/mman.h>
#include <unistd.h>
#if defined(__x86_64__)
#include <immintrin.h>
#endif

void __real_ggml_vec_dot_q2_0_q8_0(int, float *, size_t, const void *, size_t, const void *, size_t, int);
void __wrap_ggml_vec_dot_q2_0_q8_0(int, float *, size_t, const void *, size_t, const void *, size_t, int);
static pthread_once_t detection = PTHREAD_ONCE_INIT;
static br_q2_id selected_kernel;
static _Atomic int automatic_mode = 1;
static _Atomic int used_fast;
// Only actual, validated implementations belong here. Capability descriptors are not implementations.
static const uint32_t compiled = BR_Q2_BIT(BR_Q2_REFERENCE)
#if defined(__x86_64__)
    | BR_Q2_BIT(BR_Q2_AVX2)
#elif defined(__aarch64__)
    | BR_Q2_BIT(BR_Q2_DOTPROD)
#endif
    ;

static void detect(void) {
    selected_kernel = br_q2_choose(*br_cpu_detect(),compiled,compiled,-1);
}

int outpost_q2_available(void) { pthread_once(&detection, detect); return selected_kernel != BR_Q2_REFERENCE; }
int outpost_q2_fast_enabled(void) { return outpost_q2_available() && atomic_load_explicit(&automatic_mode,memory_order_relaxed); }
int outpost_q2_was_used(void) { return atomic_load_explicit(&used_fast, memory_order_relaxed); }
void outpost_q2_note_use(void) { if(!atomic_load_explicit(&used_fast,memory_order_relaxed))atomic_store_explicit(&used_fast,1,memory_order_relaxed); }
void outpost_q2_set_mode(int automatic) {
    atomic_store_explicit(&automatic_mode, automatic != 0, memory_order_relaxed);
    atomic_store_explicit(&used_fast, 0, memory_order_relaxed);
}
const char *outpost_q2_name(void) {
    pthread_once(&detection, detect);
    return br_q2_get(atomic_load_explicit(&automatic_mode, memory_order_relaxed) ? selected_kernel : BR_Q2_REFERENCE)->name;
}
void outpost_q2_profile(char *out,size_t size) {
    pthread_once(&detection,detect);
    br_q2_profile_json(out,size,compiled,compiled,atomic_load_explicit(&automatic_mode,memory_order_relaxed) ? selected_kernel : BR_Q2_REFERENCE);
}

#if defined(__x86_64__)
__attribute__((target("avx2,f16c"), always_inline))
static inline int dot32(const uint8_t *packed, const int8_t *values) {
    const __m128i eight = _mm_loadl_epi64((const __m128i_u *)packed);
    const __m256i lanes = _mm256_cvtepu8_epi32(eight);
    __m256i codes = _mm256_or_si256(lanes, _mm256_slli_epi32(lanes, 6));
    codes = _mm256_or_si256(codes, _mm256_slli_epi32(lanes, 12));
    codes = _mm256_or_si256(codes, _mm256_slli_epi32(lanes, 18));
    codes = _mm256_and_si256(codes, _mm256_set1_epi32(0x03030303));
    const __m256i y = _mm256_loadu_si256((const __m256i_u *)values);
    // Codes are 0..3; pair sums cannot saturate. Subtraction also handles y=-128.
    __m256i pairs = _mm256_sub_epi16(_mm256_maddubs_epi16(codes, y),
        _mm256_maddubs_epi16(_mm256_set1_epi8(1), y));
    __m256i sums = _mm256_madd_epi16(pairs, _mm256_set1_epi16(1));
    __m128i total = _mm_add_epi32(_mm256_castsi256_si128(sums), _mm256_extracti128_si256(sums, 1));
    total = _mm_add_epi32(total, _mm_srli_si128(total, 8));
    total = _mm_add_epi32(total, _mm_srli_si128(total, 4));
    return _mm_cvtsi128_si32(total);
}

__attribute__((target("avx2,f16c"), noinline))
static void q2_avx2(int n, float *s, size_t bs, const void *vx, size_t bx, const void *vy, size_t by, int nrc) {
    (void)bs; (void)bx; (void)by;
    assert(n % 64 == 0 && nrc == 1);
    const block_q2_0 *x = vx;
    const block_q8_0 *y = vy;
    float sum = 0;
    for (int i = 0; i < n/64; i++) {
        const int a = dot32(x[i].qs, y[2*i].qs);
        const int b = dot32(x[i].qs+8, y[2*i+1].qs);
        // Keep the scalar kernel's scale application and accumulation order.
        float inner = 0;
        inner += _cvtsh_ss(y[2*i].d) * a;
        inner += _cvtsh_ss(y[2*i+1].d) * b;
        sum += _cvtsh_ss(x[i].d) * inner;
    }
    *s = sum;
}
#endif

void __wrap_ggml_vec_dot_q2_0_q8_0(int n, float *s, size_t bs, const void *vx, size_t bx, const void *vy, size_t by, int nrc) {
#if defined(__aarch64__)
    if(outpost_q2_fast_enabled()&&selected_kernel==BR_Q2_DOTPROD&&n>=0&&n%64==0&&nrc==1) {
        outpost_q2_note_use();outpost_q2_arm_dot(n,s,bs,vx,bx,vy,by,nrc);return;
    }
#endif
#if defined(__x86_64__)
    if (outpost_q2_available() && selected_kernel == BR_Q2_AVX2 && atomic_load_explicit(&automatic_mode, memory_order_relaxed)) {
        if (!atomic_load_explicit(&used_fast, memory_order_relaxed)) atomic_store_explicit(&used_fast, 1, memory_order_relaxed);
        q2_avx2(n, s, bs, vx, bx, vy, by, nrc);
        return;
    }
#endif
    __real_ggml_vec_dot_q2_0_q8_0(n, s, bs, vx, bx, vy, by, nrc);
}

#if defined(__x86_64__) || defined(__aarch64__)
#if defined(__aarch64__)
#define tested_dot outpost_q2_arm_dot
#else
#define tested_dot q2_avx2
#endif
static uint32_t random32(uint32_t *state) {
    uint32_t x = *state; x ^= x<<13; x ^= x>>17; x ^= x<<5; return *state=x;
}
static void compare(outpost_q2_report *r, int n, const void *x, const void *y) {
    float original=0, output[3]={12345.0f,0,-6789.0f};
    __real_ggml_vec_dot_q2_0_q8_0(n,&original,0,x,0,y,0,1);
    tested_dot(n,&output[1],0,x,0,y,0,1);
    r->cases++;
    if(memcmp(&original,&output[1],sizeof(float)) || output[0]!=12345.0f || output[2]!=-6789.0f) r->bit_mismatches++;
    const double error=fabs((double)original-output[1]);
    if(error>r->max_abs) r->max_abs=error;
    const double relative=error/fmax(1e-30,fabs((double)original));
    if(relative>r->max_rel) r->max_rel=relative;
}
static double now_ns(void) { struct timespec t; clock_gettime(CLOCK_MONOTONIC,&t); return t.tv_sec*1e9+t.tv_nsec; }
static double timed(ggml_vec_dot_t fn,block_q2_0 *x,block_q8_0 *y) {
    volatile float sink=0; float result=0; const int iterations=20000;
    double start=now_ns();
    for(int i=0;i<iterations;i++) { x[0].qs[0]^=1; fn(4096,&result,0,x,0,y,0,1); sink+=result; }
    (void)sink; return (now_ns()-start)/iterations;
}
static double median3(double a,double b,double c) { return fmax(fmin(a,b),fmin(fmax(a,b),c)); }
#endif

outpost_q2_report outpost_q2_test(void) {
    outpost_q2_report r={0}; r.supported=outpost_q2_available();
#if defined(__x86_64__) || defined(__aarch64__)
    if(!r.supported) return r;
    ggml_cpu_init();
    const int lengths[]={0,64,128,576,4096,12288};
    const int max_blocks=12288/64;
    unsigned char *raw_x=malloc(max_blocks*sizeof(block_q2_0)+32), *raw_y=malloc(max_blocks*2*sizeof(block_q8_0)+32);
    if(!raw_x || !raw_y) { free(raw_x); free(raw_y); return r; }
    block_q2_0 *x=(block_q2_0 *)(raw_x+2);
    block_q8_0 *y=(block_q8_0 *)(raw_y+6);
    uint32_t random=0x1a2b3c4d;
    for(int trial=0;trial<16000;trial++) {
        int n=lengths[trial%6];
        for(int i=0;i<n/64;i++) {
            x[i].d=(uint16_t)((random32(&random)%0x7c00) | ((random32(&random)&1)<<15));
            for(int j=0;j<16;j++) x[i].qs[j]=(uint8_t)random32(&random);
            for(int k=0;k<2;k++) {
                y[2*i+k].d=(uint16_t)((random32(&random)%0x7c00) | ((random32(&random)&1)<<15));
                for(int j=0;j<32;j++) y[2*i+k].qs[j]=(int8_t)random32(&random);
            }
        }
        compare(&r,n,x,y);
    }
    const uint16_t scales[]={0,0x8000,1,0x8001,0x03ff,0x0400,0x3c00,0xbc00,0x7bff,0xfbff};
    const int values[]={-128,-127,-1,0,1,127};
    for(int q=0;q<4;q++) for(int v=0;v<6;v++) for(int d=0;d<10;d++) {
        x[0].d=scales[d]; memset(x[0].qs,q*0x55,16);
        for(int k=0;k<2;k++) { y[k].d=scales[(d+k+1)%10]; memset(y[k].qs,values[v],32); }
        compare(&r,64,x,y);
    }
    // Last legal vector ends immediately before an inaccessible page.
    const size_t page=(size_t)sysconf(_SC_PAGESIZE);
    unsigned char *gx=mmap(NULL,2*page,PROT_READ|PROT_WRITE,MAP_PRIVATE|MAP_ANONYMOUS,-1,0);
    unsigned char *gy=mmap(NULL,2*page,PROT_READ|PROT_WRITE,MAP_PRIVATE|MAP_ANONYMOUS,-1,0);
    if(gx!=MAP_FAILED && gy!=MAP_FAILED && mprotect(gx+page,page,PROT_NONE)==0 && mprotect(gy+page,page,PROT_NONE)==0) {
        void *px=gx+page-sizeof(block_q2_0), *py=gy+page-2*sizeof(block_q8_0);
        memcpy(px,x,sizeof(block_q2_0)); memcpy(py,y,2*sizeof(block_q8_0)); compare(&r,64,px,py); r.guard_ok=1;
    }
    if(gx!=MAP_FAILED) munmap(gx,2*page);
    if(gy!=MAP_FAILED) munmap(gy,2*page);
    float reference=0, dispatched=0;
    __real_ggml_vec_dot_q2_0_q8_0(64,&reference,0,x,0,y,0,1);
    outpost_q2_set_mode(0);
    ggml_get_type_traits_cpu(GGML_TYPE_Q2_0)->vec_dot(64,&dispatched,0,x,0,y,0,1);
    int fallback_ok=!memcmp(&reference,&dispatched,4) && !outpost_q2_was_used();
    outpost_q2_set_mode(1);
    ggml_get_type_traits_cpu(GGML_TYPE_Q2_0)->vec_dot(64,&dispatched,0,x,0,y,0,1);
    r.dispatch_ok=fallback_ok && !memcmp(&reference,&dispatched,4) && outpost_q2_was_used();
    for(int i=0;i<64;i++) {
        x[i].d=ggml_fp32_to_fp16(0.125f);
        for(int j=0;j<16;j++) x[i].qs[j]=(uint8_t)random32(&random);
        for(int k=0;k<2;k++) { y[2*i+k].d=ggml_fp32_to_fp16(0.0625f); for(int j=0;j<32;j++) y[2*i+k].qs[j]=(int8_t)random32(&random); }
    }
    double scalar[3],fast[3];
    for(int i=0;i<3;i++) {
        if(i%2) { fast[i]=timed(tested_dot,x,y); scalar[i]=timed(__real_ggml_vec_dot_q2_0_q8_0,x,y); }
        else { scalar[i]=timed(__real_ggml_vec_dot_q2_0_q8_0,x,y); fast[i]=timed(tested_dot,x,y); }
    }
    r.scalar_ns=median3(scalar[0],scalar[1],scalar[2]); r.fast_ns=median3(fast[0],fast[1],fast[2]);
    free(raw_x); free(raw_y); outpost_q2_set_mode(1);
#endif
    return r;
}
