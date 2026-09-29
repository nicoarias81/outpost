#pragma once
#include <stdint.h>
#ifdef __cplusplus
extern "C" {
#endif

typedef enum { BR_CPU_OTHER, BR_CPU_X86_64, BR_CPU_ARM64 } br_cpu_arch;
enum {
    BR_AVX = 1u << 0, BR_F16C = 1u << 1, BR_AVX2 = 1u << 2,
    BR_OS_AVX = 1u << 3, BR_OS_ZMM = 1u << 4,
    BR_AVX_VNNI = 1u << 5, BR_AVX512_VNNI = 1u << 6,
    BR_AVX512_F = 1u << 7, BR_AVX512_BW = 1u << 8, BR_AVX512_VL = 1u << 9,
    BR_NEON = 1u << 10, BR_DOTPROD = 1u << 11, BR_I8MM = 1u << 12
};
typedef struct { br_cpu_arch arch; uint32_t features; int online_cpus; } br_cpu_caps;
typedef struct {
    uint32_t leaf1_ecx, leaf7_0_ebx, leaf7_0_ecx, leaf7_1_eax;
    uint64_t xcr0;
} br_x86_raw;

// Pure decoders are also tested with synthetic profiles; they execute no optional ISA.
br_cpu_caps br_cpu_decode_x86(br_x86_raw raw);
br_cpu_caps br_cpu_decode_arm64(uint64_t hwcap, uint64_t hwcap2);
const br_cpu_caps *br_cpu_detect(void);
const char *br_cpu_arch_name(br_cpu_arch arch);
#ifdef __cplusplus
}
#endif
