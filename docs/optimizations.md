# Optimization record

Status: historical measurements plus the new 0.14 row-reuse experiment. The owned multi-column kernel changed; pinned backend/model weights did not. Current measured settings are local to Outpost35/Bonsai 4B; unmeasured device/model keys retain conservative defaults. See [0.14 experiment](kernel-rows-0.14.md) and [runtime](inference-runtime.md). All model execution and numerical tests cited here ran in an AOSP Android 15 x86_64 emulator configured with four logical CPUs and 4 GiB RAM. Results are not phone-performance claims.

## Measurements and attribution

| Change | Controlled observation | Decision and limitation |
|---|---|---|
| AVX2/F16C Q2 g64 dot product, 0.5 | About 5–6x dot microbenchmark speedup; 16,241 vector/edge/guard checks matched the reference | Retain; microbenchmark improvement is not whole-app speedup |
| Kernel plus shorter prompt and sampling changes, 0.4 -> 0.5 | Four Bonsai 4B controls previously hit 120 s without text; later controls completed in roughly 37–51 s | Combined change; do not attribute the entire gain to the kernel |
| Thread and batch calibration, 0.6 | Candidate confirmation improved median total by only 0.27%, below the 5% adoption threshold | Keep 4 decode threads, 4 prompt threads, batch 128 for this environment |
| Grouped prefill, 0.6 | Width 1 -> 4: prefill 22.951 -> 17.646 s; total 27.256 -> 21.956 s | 23.1% less prefill time and 19.4% less total time in this control |
| Prompt cache, 0.6 | Cold 30.253 s; related question with 256 reused tokens 7.105 s; exact repeat with 293 reused tokens 3.013 s | Retain compatible prefixes; workload-specific reuse, not a universal multiplier |
| Cache memory, 0.6 | 1,496,272 vs 1,184,160 KiB post-run PSS | About 305 MiB retention cost; not a peak-memory measurement |
| Grouped profile confirmation, 0.7 | Width 1 median total 22.935 s; width 4 18.465 s | Preserve width 4 in this build/device/model profile |
| Grouped prefill width 4 -> 8, 0.8 | Three independent alternating sweeps, three rounds each with rotating order, 222-token prompt and 32-token output. Total-time gain of width 8 over width 4 across the three sweeps: 4.6%, 2.2% and 1.5%; prefill 5.6%, 3.5% and 3.7%. Recorded run: prefill median 19,082 -> 18,374 ms and total median 25,259 -> 24,884 ms for widths 4 and 8 | Keep the width-8 capability, but the calibration selects width 4 for this build/device/model profile. The 4 -> 8 step never cleared the 5% working threshold in three sweeps, so the batch phase now prefers the narrowest grouping within 5% of the fastest and the saved profile is the narrower one. Width 8 stays available and would be selected if a future sweep separates it from run-to-run noise |
| Final context speculation, 0.7 | Long control median decode 21.664 -> 19.818 s | 8.5% less decode time in the historical research control; product chat now forces it off |
| Grouped-path numerical identity, 0.8 | Teacher-forced audit, 24 positions per width: widths 2, 4 and 8 each report bit-identical positions 24/24, max logit difference 0, mean KL 0 | Confirms the grouped path is not merely text-compatible but numerically identical on the audited positions; it does not make whole-model equality universal |
| Context speculation on the field missions, 0.8 | Paired runs, adaptive depth 3 against baseline, on the five field fixtures: decode 7,322 -> 7,118; 2,385 -> 2,508; 10,426 -> 10,419; 11,850 -> 11,897; 1,270 -> 1,586 ms | Retain as research-only; product chat forces it off. Aggregate decode is 0.8% slower; the worst pair regresses 24.9% (1,270→1,586 ms), the lowest paired elapsed time is 2.8% lower (7,322→7,118 ms) but launched no proposals, so it is not speculative acceleration; all five keep identical text. These are historical single pairs, not a current performance guarantee |

Primary evidence for the rows through 0.7: [dot](../evidence/0.7-before-outpost/optimization/kernel-numeric.json), [0.5 report](validation-0.5.md), [calibration](../evidence/0.7-before-outpost/strata/runtime-calibrate.json), [prefill](../evidence/0.7-before-outpost/strata/runtime-batch.json), [cache](../evidence/0.7-before-outpost/strata/runtime-cache.json), [0.7 profile](../evidence/0.7-before-outpost/speculation/speculation-profile.json), [speculation](../evidence/0.7-before-outpost/speculation/speculation-benchmark.json).

Evidence for the 0.8 rows, all committed: [width sweep](../evidence/archive/pre-0.9/strata/runtime-batch.json), [calibration](../evidence/archive/pre-0.9/strata/runtime-calibrate.json), [grouped-path guard](../evidence/archive/pre-0.9/optimization/kernel-batch.json), [dot vectors](../evidence/archive/pre-0.9/optimization/kernel-numeric.json), [batched-path audit](../evidence/archive/pre-0.9/speculation/speculation-audit.json), [speculation on the field missions](../evidence/archive/pre-0.9/speculation/speculation-missions.json), [speculation timing control](../evidence/archive/pre-0.9/speculation/speculation-benchmark.json), and the [0.8.1 validation record](validation-0.8.1.md). The committed sweep selects width 4; an earlier sweep of the same control selected width 8 under the rule that existed then, and a third sweep is retained only as local, git-ignored output.

Controls differ in prompt, output length, cache state, sampling, and host load. Do not multiply gains across rows. The grouped prefill experiment used a fixed 304-token input and a 32-token output budget; truncated benchmark output does not establish task success.

## Kernel design

`q2_kernel.c` wraps `ggml_vec_dot_q2_0_q8_0`. The custom function unpacks ternary weights and computes against Q8_0 activations using AVX2/F16C when CPU and OS support permit. It preserves the reference floating-point accumulation order and compiles with `-ffp-contract=off`. Reference execution remains available for unsupported hardware and comparison.

`q2_batch.c` wraps `ggml_compute_forward_mul_mat_tiled`. It reuses unpacked weight blocks across two, four or eight activation columns, calls GGML's activation quantizer, synchronizes workers, and partitions output rows. It checks tensor types, dimensions, strides, workspace, and selected path before entering the custom implementation. Unsupported shapes and single-token operations use the existing path.

Grouped prefill passed 10,023 bitwise comparisons including real GGML graphs, odd tails, and 1/2/4 workers across widths 1, 2, 4 and 8. This validates those tested operations. It does not prove that every whole-model batched graph has bitwise-identical logits; [speculation auditing](speculation.md) exposed a case that does not.

The grouped path reuses each unpacked weight block across `width` activation columns. Because it applies each activation block's own scale before accumulating in float, the width only changes how many columns share one weight unpack; the arithmetic per output element is unchanged, so it is designed to retain each output element's arithmetic order. Width 8 was still covered by guard/tail/worker and model audits; the design argument is not universal whole-model numerical proof.

## Lessons from Strata

[Strata](https://github.com/Niko1221/Strata) motivated device calibration, reuse across tokens, caching, and speculation investigations. Our [historical review](strata-review.md) records the earlier hypotheses; it does not pin an upstream commit. No Strata engine, kernel, ActQ layout, or model weights were imported. Its current upstream workload and performance figures are not this application's benchmark. Pin the inspected revision if future work depends on specific Strata code.

The transferable lesson is to measure the dominant cost on the actual execution path. Prefill reuse, cache lifetime, and decode work affect different phases. A smaller file is not automatically a faster model when its quantized operation falls back to an unsuitable kernel.

## What was rejected or narrowed

- Arbitrary token-prefix cache reuse was narrowed to complete cold-execution batch boundaries.
- Recomputing the final prompt token for an exact cache hit was replaced by retaining final-prompt logits after a visible mismatch.
- Early speculation on four-token suffixes was narrowed to eight-token matches and delayed activation; short answers could regress.
- Bonsai 1.7B as a live drafter was not integrated after the measured cost estimate showed no margin even under optimistic acceptance.
- Capability detection did not justify enabling VNNI, ARM, GPU, or NPU paths without implementations and evidence.
- Caching the activation byte-sum that `dot32` subtracts was considered but not implemented or benchmarked. Reusing the value could avoid repeated arithmetic at the cost of loads, storage and cache pressure. Instruction counts alone do not establish cycle cost or end-to-end benefit; this remains an unmeasured hypothesis rather than a demonstrated zero-benefit result.
- Grouping wider than 8 was not pursued: the measured 4 -> 8 step returned 3.5% to 5.6% of prefill against 8.1% for 2 -> 4, a clearly diminishing return once the weight unpack is already amortised across four columns, and its total-time gain never cleared the working threshold in three sweeps.
- Promoting context speculation to a default was rejected on the representative workload. A synthetic verbatim-copy control showed 1.215x decode speedup (17.73% less decode time) with 53 of 54 drafts accepted, but the five field fixtures show 0.80x, 0.95x, 1.00x, 1.00x and 1.03x, an aggregate 0.8% regression. A copy task is the best possible case for suffix drafting and is not a proxy for field answers. Historical 0.7 evidence and native research controls remain; the product toggle was removed in 0.10 and chat forces speculation off.

## DeepGEMM-Ascend review — 2026-09-30

The [pinned source review](deepgemm-ascend-review.md) identifies transferable approaches to shape-dependent selection, activation/weight reuse, bounded layouts and cache-aware measurement. Ascend950/CANN code is not an Android CPU backend and its FP4 is not Bonsai Q2. No dependency, model, kernel or runtime policy changed, and no new speedup was measured.

Prioritize an operation/shape baseline (P-05), then a guarded multi-row Q2 candidate including single-token decode (P-06). Existing grouping reuses a weight row across tokens; the proposed tile also reuses activations/correction work across output rows. Model header inspection provides actual matrix shapes for the experiment. Keep tile-local unpack/prefetch and graph fusion conditional on that profile; avoid full-model dequantization and copying server hardware constants. This extends the open activation-correction hypothesis from Strata rather than claiming it was already benchmarked.

## Next optimization experiments

| Hypothesis | Required experiment | Adoption criterion |
|---|---|---|
| Native ARM kernels help the intended phones | Implement guarded reference/NEON/DotProd/I8MM variants, use the already linked/packaged ARM app, execute new kernels only in an authorized emulator first | Numerical checks, fallback/lifecycle tests, and full-workload benefit; phone measurements remain a later scope change |
| VNNI improves the current dot path | Distinguish AVX-VNNI and AVX-512 requirements; test signedness, accumulation limits, packing costs, and tails | Improvement beyond noise on compatible hardware; no regression on baseline dispatch |
| Context reuse benefits real field work | Freeze a multi-question mission and package version, compare cold/partial/exact reuse | Useful outcome preserved, measured latency benefit, acceptable retained memory |
| Smarter evidence selection lowers prefill | Compare bounded prompt budgets while holding mission evidence coverage constant | Faster useful answers without more omissions or citation errors |
| GPU/NPU offload is worthwhile | Prototype a concrete backend and measure transfers, supported operations, RAM, and integration complexity | End-to-end benefit and fallback integrity; no theoretical-throughput claims |

These experiments are pending. Start with a hypothesis and baseline, alternate paired runs, preserve raw results, and use the [evaluation protocol](evaluation.md). A nominal 5% improvement is only a working adoption threshold; repeated evidence must also show it exceeds run-to-run noise.

## Measurement caveats found in 0.8

- The 32-token output budget in the grouped-prefill control makes prefill look like 77% of total time. At the 96-token budget real answers actually use, decode is the larger share (15.8 s decode against 10.0 s prefill in the traveler fixture). A lever that only shortens prefill is worth less on realistic answers than the short control suggests, and a lever that only shortens decode is worth more.
- The mission fixtures run baseline and optimised once each, in a fixed order, and are not alternated. Between two sessions the same fixture's decode time moved by up to 10% with no code or profile mechanism to explain it. Mission totals were therefore not used as an adoption basis for the width change; the alternated three-round sweep was. Treat a single unaltered mission pair as directional only.
- A synthetic micro-task can invert the sign of a real effect. Suffix drafting showed 1.215x decode speedup (17.73% less decode time) on a verbatim-copy control and a 0.8% loss across the five field fixtures. When a lever's mechanism favours one output shape, that shape must not be the only control.
- Calibration phases used to persist a whole profile after measuring only one dimension, so a thread and batch calibration saved width 1 and the speculation profile diagnostic reset threads, prompt threads and batch to 4/4/128. Each phase now preserves the dimensions it did not measure, and the diagnostic persists nothing. A calibrated value is only meaningful for the configuration it was measured in, so investigate dimensions separately and re-confirm their saved combination rather than letting the last writer win.

## 0.9 remeasurement and report precision

The [0.9 width sweep](../evidence/archive/0.9.0/strata/runtime-batch.json) retained parity and selected width 4 at 4/4 threads, batch 128: median total 27,596 ms at width 1 and 24,015 ms selected, a 1.149x speedup or 12.98% elapsed-time reduction. Width 8 measured 22,991 ms, 4.26% less than width 4; narrowest-within-5% retains width 4. Fastest, candidate and selected values are now distinct fields with tested fallback semantics. This is an existing-kernel control, not a new kernel improvement over 0.8.1. See [0.9 validation](validation-0.9.md) for exact scope and quality results. Its former integration quota block was resolved before 0.10; this measurement remains historical.

## Adopted row-reuse slice in 0.14

The original decode-only two/four-row candidates passed numerical checks but did not justify adoption. Extending two-row reuse to multi-column work improved prompt evaluation; complete-answer controls then exposed a decode tradeoff, so the policies were separated. The final measured profile keeps the old single-token decoder and enables two-row reuse only for eligible multi-column matrices. Exact results, source/APK identities, rejected/partial controls and timing interpretation live in [the experiment record](kernel-rows-0.14.md). Do not apply server Ascend constants or emulator timings to phones.

## Model and trained-memory alternatives

The [model survey](model-alternatives.md) expands optimization beyond Bonsai Q2 kernels: compact QAD checkpoints, hybrid models, lookup-heavy Gemma variants and target-specific trained drafters. These are source/config research findings, not performance results. Preserve per-model/kernel profiles and compare full task completion plus memory before promotion. [Engram](engram-review.md) requires trained weights; its server offload results are not an Android optimization claim.

## Storage and model admission follow-up — 2026-10-01

The [Spark-X2.5/storage review](spark-x25-storage-review.md) adds an official small-model candidate and a proposed P-07 study of memory residency and storage stalls. Sequential bandwidth, file size and active parameters cannot establish random-lookup latency. The user-reported NVMe A/B/A timings remain external unverified observations, not Outpost results; no host/device tuning or runtime experiment was performed.
