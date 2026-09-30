#pragma once
#include <stddef.h>
#ifdef __cplusplus
extern "C" {
#endif
typedef struct {
    int supported, cases, bit_mismatches, dispatch_ok, guard_ok;
    double max_abs, max_rel, scalar_ns, fast_ns;
} outpost_q2_report;
int outpost_q2_available(void);
int outpost_q2_fast_enabled(void);
int outpost_q2_was_used(void);
void outpost_q2_set_mode(int automatic);
const char *outpost_q2_name(void);
void outpost_q2_profile(char *out, size_t size);
outpost_q2_report outpost_q2_test(void);
void outpost_q2_set_batch_width(int width);
int outpost_q2_batch_used(void);
outpost_q2_report outpost_q2_batch_test(void);
void outpost_q2_set_row_tile(int rows);
void outpost_q2_set_row_tiles(int prefill_rows,int decode_rows);
int outpost_q2_rows_used(void);
void outpost_q2_rows_benchmark(char *out,size_t capacity);
void outpost_q2_rows_profile_begin(void);
void outpost_q2_rows_profile_end(char *out,size_t capacity);
#ifdef __cplusplus
}
#endif
