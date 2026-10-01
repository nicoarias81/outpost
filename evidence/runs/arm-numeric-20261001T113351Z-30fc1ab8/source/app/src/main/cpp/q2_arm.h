#pragma once
#include <stddef.h>
#include <stdbool.h>
struct ggml_compute_params;
struct ggml_tensor;
#ifdef __cplusplus
extern "C" {
#endif
void outpost_q2_arm_dot(int,float *,size_t,const void *,size_t,const void *,size_t,int);
bool outpost_q2_arm_matmul(const struct ggml_compute_params *,struct ggml_tensor *,int);
void outpost_q2_arm_graph_checks(char *,size_t);
#ifdef __cplusplus
}
#endif
