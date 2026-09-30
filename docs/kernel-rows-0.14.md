# Q2 row-reuse experiment — Outpost 0.14

Date: 2026-09-30. Outpost35 / emulator-5582, AOSP API 35 x86_64, four virtual CPUs. Model: locked Bonsai 4B Q2_0 g64. Vendor backend/model weights/sampler unchanged. This follows the [DeepGEMM-Ascend review](deepgemm-ascend-review.md); no Ascend code or dependency was imported.

## Outcome

The measured configuration uses 4 decode and 4 prompt threads, batch 128, grouped-column width 4, two output rows for eligible multi-column matrices and the retained single-row decoder. `RuntimeSettings` stores this only for the current build/kernel/device/model. Unmatched keys remain width 1/rows 1/1. Matrix column count selects the path: a one-column prompt tail also uses the retained single-column route.

Three alternating pairs per complete-answer case, with the product 192-token budget, produced identical answers/first-logit hashes and normal EOS. The [final confirmation](../evidence/runs/rows-confirm-20260930T195825Z-d6309daa/rows-checks.json) includes cache invalidation, cancellation/recovery, Qwen fallback, model switching and profile round-trip.

| Case | Baseline median seconds | Candidate median seconds | Speedup | Less elapsed time |
|---|---:|---:|---:|---:|
| confirmation-reservation | 25.085 | 22.742 | 1.103× | 9.34% |
| confirmation-manual | 27.246 | 25.683 | 1.061× | 5.74% |

Sum of case medians: **1.081×**, or **7.46% less native generation-call time**. The reference is the retained kernel with the same 4/4/128/width 4 settings, not the unmeasured width 1 default. Both cases finished normally (81 and 78 tokens). Timing excludes UI/retrieval and does not imply phone speed, energy savings, broad task accuracy or a statistical confidence interval. The two prompts were reused after combined-policy inspection; this is development evidence, not a blinded held-out study.

## Why the policy is split

1. [Decode-only graphs](../evidence/runs/rows-graphs-20260930T192832Z-d5b443f4/rows-checks.json) passed 3,262 comparisons and 108 actual-shape controls. Several single-call matrix timings were noisy; no microbenchmark-only adoption followed.
2. [Decode-only model pairs](../evidence/runs/rows-model-20260930T192908Z-6ea9fcff/rows-checks.json) did not meet the adoption gate. Two/four-row teacher-forced audits each matched all 16 positions bit-for-bit over the vocabulary, but numerical correctness alone was not a speed result.
3. The two-row helper was extended to multi-column matrices. [Prefill controls](../evidence/runs/rows-graphs-20260930T193748Z-2c96c770/rows-checks.json) added 36 paired shape/cache cases. [Short tuning probes](../evidence/runs/rows-model-20260930T193916Z-8c059cbc/rows-checks.json) showed a total-time improvement driven by prefill, while decode did not improve.
4. [Combined-policy complete-answer confirmation](../evidence/runs/rows-confirm-20260930T194852Z-d585992f/rows-checks.json) exposed the decode tradeoff. It was intentionally force-stopped on the owned emulator before completion; the [analyst note](../evidence/runs/rows-confirm-20260930T194852Z-d585992f/analyst-note.json) explains the "Process crashed" log. Partial outputs were preserved and no profile was applied from that run.
5. Multi-/single-column settings were separated. [Phase-dispatch checks](../evidence/runs/rows-graphs-20260930T195817Z-b59b5ffc/rows-checks.json) passed 4,234 comparisons, guard/reference/stride/worker checks and all shape controls. Final confirmation applied only the prefill-oriented policy after the full-answer and lifecycle gates passed.

The original exploratory harness required both total and decode gains. Once the candidate changed to target prefill, adoption used a separately declared complete-answer gate: aggregate total speedup>1.05 and no case total>5% worse, plus exact outputs/logits and lifecycle checks. Earlier records were not rewritten to pass a different rule.

## Implementation and measurement boundaries

`q2_batch.c` reuses Q8 loads and encoded-offset corrections across adjacent output rows while preserving per-output FP16-scale application and accumulation order, with `-ffp-contract=off`. It allocates no larger tensor workspace or expanded model copy. Existing CPU/type/stride/shape/workspace/reference checks remain; too few rows per worker falls back. Decode-only variants remain callable for research but are not in the adopted profile.

Opt-in shape counters observe actual matrix dimensions, calls and selected rows. They are disabled normally. Graph controls use persistent four-thread GGML plans with synthetic values at actual Bonsai shapes. The 64 MiB cache-pressure pass runs outside timing; it is not proof that every cache level is cold. Allocation/setup is outside matrix timing. Detailed quantization/barrier attribution, counter overhead, peak RAM, thermal/energy and ARM/device execution remain pending.

New tests strengthen all grouped widths/tails using the existing batch suite under the prefill-only policy. This test-harness-only addition does not alter the confirmed app bytes; [release validation](validation-0.14.md) records the exact app versus test-APK identities and final numerical count. A pre-canceled native request selects the numeric-test policy without loading a model or generating tokens.

Cache compatibility includes both row settings. The kernel-profile identity is `q2-row-v3-phase`; later calibration keeps dimensions it did not measure. The confirmation reused 133 exact prompt tokens, changed-row reuse was 0, cancellation stopped after 3 tokens, recovery reused 0, and a non-Q2 model used the retained fallback. No automatic tuning runs in product startup and no speculation toggle was restored.

## Reproduce and next work

Use `scripts/test-rows.ps1 -Phase graphs`, `-Phase model`, or `-Phase confirm` after the standard offline emulator/build preflight. `-ApplyProfile` is accepted only for confirmation and saves a profile only after its gate/lifecycle checks. Runs use unique evidence directories and matching source/app/test identities. Use the release checkout/artifacts for exact reproduction; changed source needs new evidence.

Intermediate exploratory runs retain APK/source fingerprints and raw results but do not include complete copies of every intermediate APK/source tree. Do not claim byte-for-byte reconstruction of those earlier builds from the final checkout. The final delivery and confirmation are tied to identical app bytes; its final source, release receipt and local artifact are preserved.

P-05 remains partial for deeper attribution, broader workloads and resource measurement. P-06 delivers this bounded multi-column reuse; per-shape selection, activation-sum caching, prefetch and ARM/VNNI kernels still need separate justified experiments. Keep the unmodified backend/reference fallback and all adverse results.
