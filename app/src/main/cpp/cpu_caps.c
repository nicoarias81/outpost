#include "cpu_caps.h"
#include <pthread.h>
#include <unistd.h>
#if defined(__x86_64__)
#include <cpuid.h>
#include <immintrin.h>
#elif defined(__aarch64__)
#include <sys/auxv.h>
#include <asm/hwcap.h>
// Linux arm64 UAPI bits used by the pure decoder below.
_Static_assert(HWCAP_ASIMD == (1UL << 1), "NEON capability changed");
_Static_assert(HWCAP_ASIMDDP == (1UL << 20), "DotProd capability changed");
_Static_assert(HWCAP2_I8MM == (1UL << 13), "I8MM capability changed");
#endif

br_cpu_caps br_cpu_decode_x86(br_x86_raw r) {
    br_cpu_caps c = {BR_CPU_X86_64, 0, 0};
    if (r.leaf1_ecx & (1u << 28)) c.features |= BR_AVX;
    if (r.leaf1_ecx & (1u << 29)) c.features |= BR_F16C;
    const uint32_t save = (1u << 26) | (1u << 27);
    if ((r.leaf1_ecx & save) == save && (r.xcr0 & 6) == 6) c.features |= BR_OS_AVX;
    if ((c.features & BR_OS_AVX) && (r.xcr0 & 0xe6) == 0xe6) c.features |= BR_OS_ZMM;
    if (r.leaf7_0_ebx & (1u << 5)) c.features |= BR_AVX2;
    if (r.leaf7_0_ebx & (1u << 16)) c.features |= BR_AVX512_F;
    if (r.leaf7_0_ebx & (1u << 30)) c.features |= BR_AVX512_BW;
    if (r.leaf7_0_ebx & (1u << 31)) c.features |= BR_AVX512_VL;
    if (r.leaf7_0_ecx & (1u << 11)) c.features |= BR_AVX512_VNNI;
    if (r.leaf7_1_eax & (1u << 4)) c.features |= BR_AVX_VNNI;
    return c;
}

br_cpu_caps br_cpu_decode_arm64(uint64_t hwcap, uint64_t hwcap2) {
    br_cpu_caps c = {BR_CPU_ARM64, 0, 0};
    if (hwcap & (1ULL << 1)) c.features |= BR_NEON;
    if (hwcap & (1ULL << 20)) c.features |= BR_DOTPROD;
    if (hwcap2 & (1ULL << 13)) c.features |= BR_I8MM;
    return c;
}

static br_cpu_caps detected;
static pthread_once_t once = PTHREAD_ONCE_INIT;
#if defined(__x86_64__)
__attribute__((target("xsave")))
static uint64_t read_xcr0(void) { return _xgetbv(0); }
#endif
static void detect(void) {
#if defined(__x86_64__)
    br_x86_raw raw = {0};
    unsigned a, b, c, d;
    if (__get_cpuid(1, &a, &b, &c, &d)) {
        raw.leaf1_ecx = c;
        const unsigned save = (1u << 26) | (1u << 27);
        // XGETBV itself is unsafe without these prerequisites.
        if ((c & save) == save) raw.xcr0 = read_xcr0();
    }
    if (__get_cpuid_count(7, 0, &a, &b, &c, &d)) {
        unsigned max_subleaf = a;
        raw.leaf7_0_ebx = b; raw.leaf7_0_ecx = c;
        if (max_subleaf >= 1 && __get_cpuid_count(7, 1, &a, &b, &c, &d)) raw.leaf7_1_eax = a;
    }
    detected = br_cpu_decode_x86(raw);
#elif defined(__aarch64__)
    detected = br_cpu_decode_arm64(getauxval(AT_HWCAP), getauxval(AT_HWCAP2));
#else
    detected.arch = BR_CPU_OTHER;
#endif
    long n = sysconf(_SC_NPROCESSORS_ONLN);
    detected.online_cpus = n > 0 && n < 65536 ? (int)n : 1;
}
const br_cpu_caps *br_cpu_detect(void) { pthread_once(&once, detect); return &detected; }
const char *br_cpu_arch_name(br_cpu_arch a) {
    return a == BR_CPU_X86_64 ? "x86_64" : a == BR_CPU_ARM64 ? "arm64-v8a" : "other";
}
