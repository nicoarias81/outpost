#include "q2_dispatch.h"
#include <stdio.h>
#include <string.h>

#define X86_BASE (BR_AVX | BR_F16C | BR_AVX2 | BR_OS_AVX)
// Requirements describe Q2_0 g64 x Q8_0 candidates, not every use of these ISAs.
static const br_q2_candidate candidates[BR_Q2_COUNT] = {
    {BR_Q2_REFERENCE, "Q2_0 reference", BR_CPU_OTHER, 0},
    {BR_Q2_AVX2, "Q2_0 AVX2/F16C", BR_CPU_X86_64, X86_BASE},
    {BR_Q2_AVX_VNNI, "Q2_0 AVX-VNNI", BR_CPU_X86_64, X86_BASE | BR_AVX_VNNI},
    {BR_Q2_AVX512_VNNI, "Q2_0 AVX-512 VNNI/VL", BR_CPU_X86_64,
        X86_BASE | BR_OS_ZMM | BR_AVX512_F | BR_AVX512_BW | BR_AVX512_VL | BR_AVX512_VNNI},
    {BR_Q2_NEON, "Q2_0 NEON", BR_CPU_ARM64, BR_NEON},
    {BR_Q2_DOTPROD, "Q2_0 NEON DotProd", BR_CPU_ARM64, BR_NEON | BR_DOTPROD},
    {BR_Q2_I8MM, "Q2_0 NEON I8MM", BR_CPU_ARM64, BR_NEON | BR_I8MM}
};
const br_q2_candidate *br_q2_get(br_q2_id id) {
    return id >= BR_Q2_REFERENCE && id < BR_Q2_COUNT ? &candidates[id] : NULL;
}
int br_q2_cpu_compatible(br_cpu_caps caps, br_q2_id id) {
    const br_q2_candidate *c = br_q2_get(id);
    return c && (id == BR_Q2_REFERENCE || (caps.arch == c->arch && (caps.features & c->required) == c->required));
}
static int eligible(br_cpu_caps caps, uint32_t compiled, uint32_t enabled, int id) {
    return id >= 0 && id < BR_Q2_COUNT && (compiled & enabled & BR_Q2_BIT(id)) && br_q2_cpu_compatible(caps, (br_q2_id)id);
}
br_q2_id br_q2_choose(br_cpu_caps caps, uint32_t compiled, uint32_t enabled, int preferred) {
    if (eligible(caps, compiled, enabled, preferred)) return (br_q2_id)preferred;
    // Default order is only a policy, not a claim about the fastest ISA on a device.
    const br_q2_id order[] = {BR_Q2_AVX_VNNI, BR_Q2_AVX512_VNNI, BR_Q2_AVX2, BR_Q2_I8MM, BR_Q2_DOTPROD, BR_Q2_NEON};
    for (size_t i=0; i<sizeof(order)/sizeof(order[0]); i++)
        if (eligible(caps, compiled, enabled, order[i])) return order[i];
    return BR_Q2_REFERENCE; // The original GGML function is always linked.
}

void br_q2_profile_json(char *out, size_t size, uint32_t compiled, uint32_t enabled, br_q2_id selected) {
    if (!size) return;
    const br_cpu_caps *caps = br_cpu_detect();
    int n = snprintf(out,size,"{\"abi\":\"%s\",\"onlineCpus\":%d,\"featureMask\":%u,\"selected\":\"%s\",\"candidates\":[",
        br_cpu_arch_name(caps->arch),caps->online_cpus,caps->features,br_q2_get(selected)->name);
    size_t used = n > 0 && (size_t)n < size ? (size_t)n : size-1;
    for (int i=0; i<BR_Q2_COUNT && used<size-1; i++) {
        n = snprintf(out+used,size-used,"%s{\"id\":%d,\"name\":\"%s\",\"cpuCompatible\":%s,\"compiled\":%s,\"enabled\":%s,\"selected\":%s}",
            i ? "," : "",i,candidates[i].name,br_q2_cpu_compatible(*caps,(br_q2_id)i) ? "true":"false",
            compiled & BR_Q2_BIT(i) ? "true":"false",enabled & BR_Q2_BIT(i) ? "true":"false",i == (int)selected ? "true":"false");
        used += n > 0 && (size_t)n < size-used ? (size_t)n : size-used-1;
    }
    if (used < size-1) snprintf(out+used,size-used,"]}");
}

int br_q2_policy_checks(int *failures) {
    int count=0; *failures=0;
#define CHECK(condition) do { count++; if (!(condition)) (*failures)++; } while (0)
    const uint32_t all = (1u << BR_Q2_COUNT)-1;
    const uint32_t current = BR_Q2_BIT(BR_Q2_REFERENCE) | BR_Q2_BIT(BR_Q2_AVX2);
    br_x86_raw raw = {(1u<<26)|(1u<<27)|(1u<<28)|(1u<<29),1u<<5,0,0,6};
    br_cpu_caps x = br_cpu_decode_x86(raw);
    CHECK(br_q2_choose(x,current,current,-1) == BR_Q2_AVX2);
    raw.leaf7_1_eax = 1u<<4; x = br_cpu_decode_x86(raw);
    CHECK(br_q2_choose(x,all,all,-1) == BR_Q2_AVX_VNNI);
    CHECK(br_q2_choose(x,current,current,-1) == BR_Q2_AVX2); // Hardware alone cannot activate missing code.
    CHECK(br_q2_choose(x,all,current,-1) == BR_Q2_AVX2);     // Linked but unapproved stays disabled.
    raw.leaf7_1_eax=0; raw.leaf7_0_ecx=1u<<11;
    raw.leaf7_0_ebx |= (1u<<16)|(1u<<30)|(1u<<31); raw.xcr0=0xe6;
    x=br_cpu_decode_x86(raw);
    CHECK(br_q2_choose(x,all,all,-1) == BR_Q2_AVX512_VNNI);
    raw.xcr0=6; x=br_cpu_decode_x86(raw);
    CHECK(br_q2_choose(x,all,all,BR_Q2_AVX512_VNNI) == BR_Q2_AVX2); // OS lacks ZMM/opmask state.
    raw.xcr0=2; x=br_cpu_decode_x86(raw);
    CHECK(br_q2_choose(x,all,all,-1) == BR_Q2_REFERENCE);
    raw.xcr0=0xe6; raw.leaf1_ecx &= ~(1u<<27); x=br_cpu_decode_x86(raw);
    CHECK(!(x.features & (BR_OS_AVX|BR_OS_ZMM)));
    CHECK(br_q2_choose(x,all,all,-1) == BR_Q2_REFERENCE);
    br_cpu_caps arm=br_cpu_decode_arm64(1ULL<<1,0);
    CHECK(br_q2_choose(arm,all,all,-1) == BR_Q2_NEON);
    arm=br_cpu_decode_arm64((1ULL<<1)|(1ULL<<20),0);
    CHECK(br_q2_choose(arm,all,all,-1) == BR_Q2_DOTPROD);
    arm=br_cpu_decode_arm64((1ULL<<1)|(1ULL<<20),1ULL<<13);
    CHECK(br_q2_choose(arm,all,all,-1) == BR_Q2_I8MM);
    CHECK(br_q2_choose(arm,current,current,-1) == BR_Q2_REFERENCE);
    CHECK(br_q2_choose(arm,all,all,BR_Q2_DOTPROD) == BR_Q2_DOTPROD); // A measured choice can beat default priority.
    arm=br_cpu_decode_arm64((1ULL<<1)|(1ULL<<20),1ULL<<9); // SVE I8MM is a different capability.
    CHECK(!(arm.features & BR_I8MM));
    CHECK(br_q2_choose(arm,all,all,-1) == BR_Q2_DOTPROD);
    CHECK(br_q2_choose((br_cpu_caps){BR_CPU_OTHER,0xffffffffu,1},all,all,-1) == BR_Q2_REFERENCE);
    for (int id=1; id<BR_Q2_COUNT; id++) {
        br_cpu_caps c={candidates[id].arch,candidates[id].required,1};
        uint32_t one=BR_Q2_BIT(id) | BR_Q2_BIT(BR_Q2_REFERENCE);
        CHECK(br_q2_choose(c,one,one,id) == (br_q2_id)id);
        CHECK(br_q2_choose(c,one,0,id) == BR_Q2_REFERENCE);
        CHECK(br_q2_choose(c,0,one,id) == BR_Q2_REFERENCE);
        for (int bit=0; bit<13; bit++) if (c.features & (1u<<bit)) {
            br_cpu_caps missing=c; missing.features &= ~(1u<<bit);
            CHECK(br_q2_choose(missing,one,one,id) == BR_Q2_REFERENCE);
        }
        c.arch = c.arch == BR_CPU_X86_64 ? BR_CPU_ARM64 : BR_CPU_X86_64;
        CHECK(br_q2_choose(c,one,one,id) == BR_Q2_REFERENCE);
    }
#undef CHECK
    return count;
}
