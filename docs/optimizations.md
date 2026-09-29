# Optimization record

Status: measured history and current decisions through 0.7.0. All model execution and numerical tests cited here ran in an AOSP Android 15 x86_64 emulator configured with four logical CPUs and 4 GiB RAM. Results are not phone-performance claims.

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
| Final context speculation, 0.7 | Long control median decode 21.664 -> 19.818 s | 8.5% less decode time; optional and default off |

Primary evidence: [dot](../evidence/0.7-before-outpost/optimization/kernel-numeric.json), [0.5 report](validation-0.5.md), [calibration](../evidence/0.7-before-outpost/strata/runtime-calibrate.json), [prefill](../evidence/0.7-before-outpost/strata/runtime-batch.json), [cache](../evidence/0.7-before-outpost/strata/runtime-cache.json), [0.7 profile](../evidence/0.7-before-outpost/speculation/speculation-profile.json), [speculation](../evidence/0.7-before-outpost/speculation/speculation-benchmark.json).

Controls differ in prompt, output length, cache state, sampling, and host load. Do not multiply gains across rows. The grouped prefill experiment used a fixed 304-token input and a 32-token output budget; truncated benchmark output does not establish task success.

## Kernel design

`q2_kernel.c` wraps `ggml_vec_dot_q2_0_q8_0`. The custom function unpacks ternary weights and computes against Q8_0 activations using AVX2/F16C when CPU and OS support permit. It preserves the reference floating-point accumulation order and compiles with `-ffp-contract=off`. Reference execution remains available for unsupported hardware and comparison.

`q2_batch.c` wraps `ggml_compute_forward_mul_mat_tiled`. It reuses unpacked weight blocks across two or four activation columns, calls GGML's activation quantizer, synchronizes workers, and partitions output rows. It checks tensor types, dimensions, strides, workspace, and selected path before entering the custom implementation. Unsupported shapes and single-token operations use the existing path.

Grouped prefill passed 7,932 bitwise comparisons including real GGML graphs, odd tails, and 1/2/4 workers. This validates those tested operations. It does not prove that every whole-model batched graph has bitwise-identical logits; [speculation auditing](speculation.md) exposed a case that does not.

## Lessons from Strata

[Strata](https://github.com/Niko1221/Strata) motivated device calibration, reuse across tokens, caching, and speculation investigations. Our [historical review](strata-review.md) records the earlier hypotheses; it does not pin an upstream commit. No Strata engine, kernel, ActQ layout, or model weights were imported. Its current upstream workload and performance figures are not this application's benchmark. Pin the inspected revision if future work depends on specific Strata code.

The transferable lesson is to measure the dominant cost on the actual execution path. Prefill reuse, cache lifetime, and decode work affect different phases. A smaller file is not automatically a faster model when its quantized operation falls back to an unsuitable kernel.

## What was rejected or narrowed

- Arbitrary token-prefix cache reuse was narrowed to complete cold-execution batch boundaries.
- Recomputing the final prompt token for an exact cache hit was replaced by retaining final-prompt logits after a visible mismatch.
- Early speculation on four-token suffixes was narrowed to eight-token matches and delayed activation; short answers could regress.
- Bonsai 1.7B as a live drafter was not integrated after the measured cost estimate showed no margin even under optimistic acceptance.
- Capability detection did not justify enabling VNNI, ARM, GPU, or NPU paths without implementations and evidence.

## Next optimization experiments

| Hypothesis | Required experiment | Adoption criterion |
|---|---|---|
| Native ARM kernels help the intended phones | Implement guarded reference/NEON/DotProd/I8MM variants, compile complete ARM app, execute only in an authorized emulator first | Numerical checks, fallback/lifecycle tests, and full-workload benefit; phone measurements remain a later scope change |
| VNNI improves the current dot path | Distinguish AVX-VNNI and AVX-512 requirements; test signedness, accumulation limits, packing costs, and tails | Improvement beyond noise on compatible hardware; no regression on baseline dispatch |
| Context reuse benefits real field work | Freeze a multi-question mission and package version, compare cold/partial/exact reuse | Useful outcome preserved, measured latency benefit, acceptable retained memory |
| Smarter evidence selection lowers prefill | Compare bounded prompt budgets while holding mission evidence coverage constant | Faster useful answers without more omissions or citation errors |
| GPU/NPU offload is worthwhile | Prototype a concrete backend and measure transfers, supported operations, RAM, and integration complexity | End-to-end benefit and fallback integrity; no theoretical-throughput claims |

These experiments are pending. Start with a hypothesis and baseline, alternate paired runs, preserve raw results, and use the [evaluation protocol](evaluation.md). A nominal 5% improvement is only a working adoption threshold; repeated evidence must also show it exceeds run-to-run noise.
