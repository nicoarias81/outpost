# Six decode workers rejected by complete-output confirmation

The6/6-thread candidate passed the matrix suite and the first64-token tuning probes, but its longer manual answer diverged from the4/4 legacy baseline at output token index90. The initial-logit hashes matched; the candidate ended after142 tokens and the reference after162. All raw records, including successful travel pairs, remain unchanged. No production policy was enabled from this run.

The pinned CPU flash-attention implementation partitions the KV range using the decode worker count and then reduces those partials in floating point. Changing that count can change rounding even when the Q2 matrix kernels preserve bits. The next audit records every sampling-logit hash and separates prompt workers from decode workers. Four decode workers are retained as the reference-compatible candidate pending that audit; six prompt workers are tested separately.

Follow-up arm-trace-20261001T132954Z-f9669c2f confirms the first differing distribution at logit index1 with6/6 workers.4/6 and4/4 preserve every distribution in that audit. The corrected long confirmation also compares every sampling-logit hash, not only the initial hash and sampled tokens.
