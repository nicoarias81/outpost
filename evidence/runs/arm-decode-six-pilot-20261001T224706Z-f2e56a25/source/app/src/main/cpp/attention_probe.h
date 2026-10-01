#pragma once
#include <string>

// Per-graph attention controls. Call under execution_mutex, outside an active
// graph. Modes 0..3 are research diagnostics; mode 4 preserves serial arithmetic
// for the measured six-worker product configuration.
// 0 observes the original operation; 1 forces vec-only attention; 2 slices
// verification queries to reproduce four-worker serial attention arithmetic.
// 3 retains six scheduler workers with the same four logical attention workers.
// 4 does this for single-query normal decode/prompt tails; multi-query prefill
// retains the original six-worker operation. It is not a verification mode.
void outpost_attention_reset();
void outpost_attention_begin(int mode, int first_position, int rows);
void outpost_attention_end();
std::string outpost_attention_stats();
struct OutpostAttentionScope {
    OutpostAttentionScope(int mode, int first_position, int rows) {
        outpost_attention_begin(mode, first_position, rows);
    }
    ~OutpostAttentionScope() { outpost_attention_end(); }
};
