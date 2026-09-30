# DeepGEMM-Ascend review for Outpost

Review date: **2026-09-30**. Upstream commit **`8491bbb4b8c02a094a2318965f50c70438a3e73c`**, committed at `2026-09-30T01:07:52Z`; Outpost baseline **`c9181ec`, 0.12.0 / code 14**. This is a static source review and a set of proposed experiments, not a new performance result or an adopted dependency.

**Recommendation:** use its approach to workload description, data reuse, bounded layouts and measurement to guide our own Q2 kernels. Do not integrate the Ascend library, replace Bonsai's format with FP4, or change the model based on this review. The highest-priority candidate is reusing activation work across output rows, including single-token decode, after establishing a current baseline.

## Scope and compatibility

The [pinned upstream README](https://github.com/deepseek-ai/DeepGEMM-Ascend/blob/8491bbb4b8c02a094a2318965f50c70438a3e73c/README.md) describes Ascend 950, CANN 9.20/Bisheng, `torch_npu`, Python and DeepJIT, with BF16/FP8/FP4 GEMM and DeepSeek-specific MQA/MegaMoE/mHC operations. Reported throughput is for that Ascend workload, not Android CPU inference. The source uses explicit Ascend memory spaces, matrix instructions and synchronization. There is no Q2_0 g64, NEON or VNNI implementation to transplant into Outpost.

Outpost uses a pinned llama.cpp CPU backend, Q2_0 g64 weights and Q8_0 activations in its custom dot/matrix path. It packages ARM64/x86_64 but only x86_64 emulator execution has been validated. CANN kernels are not a backend for the intended Pixel phones. A future phone accelerator backend would require its own supported runtime, operators, data-transfer measurements and fallback; merely having an NPU is insufficient.

Upstream publishes an MIT license. No upstream code was copied into the app, no submodules/dependencies were installed, and no upstream build/test code or model was executed. The review downloaded 86 files for inspection; [provenance](../evidence/research/deepgemm-ascend-20260930/upstream-provenance.json) pins their identities. It is not a claim of a complete security or correctness audit of every file.

## Concrete findings and transferability

| Upstream observation | Relevant Outpost opportunity | Boundary |
|---|---|---|
| [GemmDesc/GemmConfig](https://github.com/deepseek-ai/DeepGEMM-Ascend/blob/8491bbb4b8c02a094a2318965f50c70438a3e73c/csrc/jit_kernels/gemm_config.hpp) separates dimensions/types/strides from tiling/pipeline policy | Describe each operation by token count, K, output rows, types/strides and phase; choose a small measured set of CPU variants | Keep CPU/OS capability, compiled availability and selected policy separate |
| [Heuristics](https://github.com/deepseek-ai/DeepGEMM-Ascend/blob/8491bbb4b8c02a094a2318965f50c70438a3e73c/csrc/jit_kernels/heuristics.hpp) fits tiles to buffers and estimates compute, memory, scale-transfer and output costs | Avoid one global width for every prompt length/matrix; prune impossible scratch/register candidates before paired measurement | Its hardware constants are Ascend-specific; this is cost-based selection, not a transferable phone autotuner. Selection caching and some cost terms remain TODOs upstream |
| [FP8 kernel](https://github.com/deepseek-ai/DeepGEMM-Ascend/blob/8491bbb4b8c02a094a2318965f50c70438a3e73c/deep_gemm/include/deep_gemm/fp8_gemm.hpp) retains eligible operands/scales and overlaps explicit load/compute stages | Reuse Q8 activations, correction sums and unpacked Q2 blocks within bounded CPU tiles | CPU caches/registers are not Ascend scratchpads; extra threads or buffers can cost more than they save |
| [FP4 conversion](https://github.com/deepseek-ai/DeepGEMM-Ascend/blob/8491bbb4b8c02a094a2318965f50c70438a3e73c/deep_gemm/include/deep_gemm/fp8_dequant_gemm.hpp) combines LUT conversion with the next kernel's required layout | Consider a bounded tile-local unpack/layout experiment instead of repeatedly materializing awkward intermediate data | E2M1→E4M3 conversion is not a Bonsai quantization algorithm; our current register unpack may already be cheaper |
| [Scheduler](https://github.com/deepseek-ai/DeepGEMM-Ascend/blob/8491bbb4b8c02a094a2318965f50c70438a3e73c/deep_gemm/include/deep_gemm/scheduler.hpp) orders tiles for locality and handles tails explicitly | Compare loop/tile order and static worker partition on real Bonsai shapes | Do not copy Ascend swizzle constants or assume more workers are better |
| [Epilogues](https://github.com/deepseek-ai/DeepGEMM-Ascend/blob/8491bbb4b8c02a094a2318965f50c70438a3e73c/deep_gemm/include/deep_gemm/epilogue/operators.hpp) and [MegaMoE](https://github.com/deepseek-ai/DeepGEMM-Ascend/blob/8491bbb4b8c02a094a2318965f50c70438a3e73c/deep_gemm/include/deep_gemm/mega_moe.hpp) fuse some postprocessing/communication stages | Investigate repeated activation quantization or compatible FFN intermediates only if profiling identifies material cost | This crosses the GGML graph/workspace boundary. It is higher-risk than a dot/matrix microkernel and is not MTP |
| [Profiler](https://github.com/deepseek-ai/DeepGEMM-Ascend/blob/8491bbb4b8c02a094a2318965f50c70438a3e73c/deep_gemm/testing/bench.py) separates warmup/measurement, defaults to L2 flushing and records device pipeline metrics | Add cache-resident versus streaming matrix controls, phase timings and path-coverage counters | Do not flush CPU caches during product inference. Emulator PMU/timing is not phone bandwidth or energy evidence |
| [Tests](https://github.com/deepseek-ai/DeepGEMM-Ascend/blob/8491bbb4b8c02a094a2318965f50c70438a3e73c/tests/common.py) exercise layouts/tails and disable unintended CPU fallback during NPU checks | Require the intended custom path to execute in benchmarks; record why other shapes fall back | Production must retain our reference fallback. Upstream numeric thresholds do not replace our parity contract |

These mappings are our hypotheses, not performance claims made by the upstream authors about Bonsai or phones.

## What the Outpost source actually does

[q2_batch.c](../app/src/main/cpp/q2_batch.c) quantizes activation columns once for an operation, synchronizes workers, assigns weight rows to workers and then groups 2/4/8 token columns. `multi()` handles **one weight row at a time**: each Q2 block is unpacked once and reused across the token group. It still loads the corresponding Q8 activation and computes its offset correction for each weight row. The grouped hook requires at least two columns; it does not cover ordinary one-token decode.

[q2_kernel.c](../app/src/main/cpp/q2_kernel.c) implements the single-dot AVX2/F16C path. Its integer calculation subtracts the activation contribution corresponding to the encoded weight offset. This repeats work across output rows. The existing tests deliberately include all four two-bit codes, extreme signed activations, misalignment and guard pages; do not optimize under an unverified assumption that a particular code never appears.

Our current vector microbenchmark repeatedly computes a 4,096-element dot. The real-model checks are valuable, but there is no per-shape timing/traffic inventory. [RuntimeSettings](../app/src/main/java/dev/outpost/app/RuntimeSettings.java) persists one thread/batch/width profile per device/OS/app-version/model key. An unmatched current key falls back to width 1. Historical 0.9 width 4 gains are not a measured 0.12 profile. Recalibration or explicit diagnostic width must be recorded before attributing a gain to a new kernel.

## Actual Bonsai 4B matrix shapes

A host-side **GGUF header-only inspection** found 398 tensors: 253 Q2_0 matrices and 145 F32 tensors. No tensor payload was scanned or inference executed. File size matched the locked 1,137,806,656 bytes; the recorded model hash is from the lock, not a fresh full-file rehash. [Static result](../evidence/research/deepgemm-ascend-20260930/bonsai4-static-shapes.json) and [inspection script](../evidence/research/deepgemm-ascend-20260930/inspect-bonsai-shapes.py) preserve the method.

Here K is the reduction dimension and N the number of output rows; these are GGML `[K,N]` dimensions, not prompt-token counts.

| K | N | Matrix count | Example |
|---:|---:|---:|---|
| 2,560 | 1,024 | 72 | Attention K/V |
| 2,560 | 4,096 | 36 | Attention Q |
| 2,560 | 9,728 | 72 | FFN gate/up |
| 4,096 | 2,560 | 36 | Attention output |
| 9,728 | 2,560 | 36 | FFN down |
| 2,560 | 151,669 | 1 | Token embedding tensor; benchmark projection only when graph tracing confirms that use |

This identifies benchmark shapes, not which operation currently dominates elapsed time. Include actual token-column counts of 1, 2, 4, 8, 32 and 128 plus tails/partial batches. A large server GEMM is not representative of one-token decode.

## Proposed experiments, in order

### DG-01 — Establish operation and cache baselines (roadmap P-05)

Add test-only operation accounting around the owned hooks: phase, shapes/types/strides, calls, eligible/selected/fallback path, quantization/barrier/kernel time and scratch bytes. Counters/timers must be per-thread or aggregated outside the inner loop, with their own overhead control. Avoid shipping profiling overhead in chat.

Measure the actual shapes above in cache-resident and streaming controls, then cold/partial/exact-cache model requests. Label unavailable PMU counters instead of inventing memory bandwidth. Use explicit current configuration and paired workloads before deciding whether arithmetic, unpacking, cache traffic, scheduling or another graph operation dominates. This baseline comes before a new global policy.

### DG-02 — Reuse activations across output rows (roadmap P-06)

Prototype a small R×T tile: initially R=2 or 4 output rows, T=1 for decode; then selected small multi-token cases. Load a Q8 block and its correction once per token/block, and reuse it across R rows. Reuse each unpacked Q2 block across T tokens where useful. Keep each output's FP16-scale application and floating-point accumulation order unchanged.

The correction can be written as `sum(code_i * y_i) - sum(y_i)`. This is an algebraic observation, not a benchmark. Prefer register reuse first. A separate cached correction array would add workspace and validation: do not append bytes to GGML `p->wdata` without a checked allocation contract. Precomputing the correction was already an open idea after Strata; the new, more concrete candidate combines it with output-row tiling and a decode path.

Risks: register spills, extra weight streams, cache pressure, insufficient rows per worker and setup overhead. R×T does not imply an R×T speedup. Preserve reference/shape/stride/workspace guards and current semantics for unsupported inputs.

### DG-03 — Choose variants by shape and phase (roadmap P-05/P-06)

Use a small ahead-of-time family with measured choices for decode, small batches and prefill. Outpost does not need to package a C++ JIT/compiler to adopt specialization. Include numerical kernel identity, shape/stride family and policy version in any future persisted selection contract; avoid reusing a profile after a same-version kernel change.

This is finer-grained than a global width sweep, not a reason to force width 8 or widen beyond 8. Existing measurements already show diminishing gains from 4→8. Keep a bounded tuning budget and adopt only useful end-to-end improvements beyond noise.

### DG-04 — Bounded unpack/layout and prefetch (conditional)

If DG-01 isolates repeated unpack/load cost, compare the current register unpack with a small tile-local representation, explicit loop ordering or bounded prefetch distances. Measure setup, tail handling and scratch bytes as part of total cost. Do not expand the entire model to INT8/FP16 in RAM.

Q2 g64 stores 18 bytes per 64 values including the scale. An INT8 tile retaining that scale needs 66 bytes: approximately 3.67× for those matrices. Expanding all observed Q2 payloads that way would require about 4.15 GB just for the converted matrices, before KV, original mapping and app memory. These are static size calculations, not measured RSS/PSS. Repacking can improve alignment but must preserve the pinned source file and account for temporary/resident memory.

### DG-05 — Graph fusion and ARM kernels (conditional)

Check whether Q/K/V or FFN gate/up operations redundantly quantize the same activation and whether fusing a compatible producer/consumer would remove significant traffic. Current hooks do not establish safe cross-node buffer reuse; graph lifetime, aliasing, cancellation and output parity need explicit design. MegaMoE's multi-NPU communication scheduler is not useful to the current dense single-device model.

For phones, the unresolved direct implementation work remains P-02: guarded ARM NEON/DotProd/I8MM candidates according to detected capabilities and actual compiled support. VNNI remains a separate x86 candidate (P-03); Ascend contributes neither implementation. Build/static analysis is allowed, but runtime validation must remain in an authorized emulator until the user changes the device scope. An x86 emulator gain is not a Pixel gain.

## Model and inference conclusions

FP4 here is E2M1 with hardware-specific scaling/layout, not Bonsai's two-bit codes plus FP16 group scales. Converting our checkpoint would increase representation cost and change its runtime contract without adding learned capability. The library supplies kernels, not a better Bonsai checkpoint, a method to turn a dense model into MoE, or compatible MTP heads. The locked 4B still has zero MTP layers; speculation remains a separate research track and disabled in product chat.

Ternary zeros also do not establish profitable structured sparsity. No zero-block distribution was measured here. A sparse representation must beat index/packing/branch/load overhead on this checkpoint and target; generic sparse-loading claims do not provide that result.

## Acceptance and resumption

Start with DG-01, then the smallest DG-02 decode tile. Retain original weights/backend and a selectable reference. Require integer/extreme/unaligned/tail/guard tests, real GGML shapes and workers, workspace/fallback correctness, teacher-forced logit comparison, cache/cancel/model-switch checks and representative chat/evidence quality controls. First preserve current bitwise arithmetic; any relaxation needs a separate explicit numerical decision.

Run repeated alternating pairs with fixed model, prompts, output budgets, cache state and configuration. Report first-token, prefill/decode/total time, actual output length/stop reason, peak memory with a stated sampling method, and hot/cold controls. A 5% threshold is only a working rule; no deployment from a dot-only win or changed output length. Keep evidence and implementation commits separate.

**No runtime gain is established by this review.** No app source, model, calibration, backend, dependency or emulator state was changed. The finding is a prioritized, source-linked experiment plan; [optimizations](optimizations.md), [runtime](inference-runtime.md) and [roadmap](roadmap.md) remain the owning implementation/measurement references.
