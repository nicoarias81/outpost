#pragma once
#include "cpu_caps.h"
#include <stddef.h>
#ifdef __cplusplus
extern "C" {
#endif
typedef enum {
    BR_Q2_REFERENCE, BR_Q2_AVX2, BR_Q2_AVX_VNNI, BR_Q2_AVX512_VNNI,
    BR_Q2_NEON, BR_Q2_DOTPROD, BR_Q2_I8MM, BR_Q2_COUNT
} br_q2_id;
#define BR_Q2_BIT(id) (1u << (id))
typedef struct {
    br_q2_id id;
    const char *name;
    br_cpu_arch arch;
    uint32_t required;
} br_q2_candidate;
const br_q2_candidate *br_q2_get(br_q2_id id);
int br_q2_cpu_compatible(br_cpu_caps caps, br_q2_id id);
// compiled = implementation actually linked; enabled = approved for this build.
// preferred is an optional measured winner, never an override of eligibility.
br_q2_id br_q2_choose(br_cpu_caps caps, uint32_t compiled, uint32_t enabled, int preferred);
int br_q2_policy_checks(int *failures);
void br_q2_profile_json(char *out, size_t size, uint32_t compiled, uint32_t enabled, br_q2_id selected);
#ifdef __cplusplus
}
#endif
