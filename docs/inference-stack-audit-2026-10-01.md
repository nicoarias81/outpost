# Inference stack audit and CPU profile — 2026-10-01

The product remains Outpost 0.15.0. This audit profiles the exact released app bytes on the registered Pixel; only the instrumentation APK changes. It separates storage, arithmetic, dispatch and observed CPU work before proposing another optimization.

## Actual execution contract

| Layer | Bonsai 4B on the admitted Pixel profile |
|---|---|
| Container and identity | Locked GGUF, Q2_0 g64; model SHA256 in bonsai-lock.json |
| Weight storage | 64 two-bit codes and one FP16 scale per 18-byte block: 2.25 effective bits per matrix weight |
| Live matrix inputs | Float activations dynamically converted to Q8_0: signed INT8 values with FP16 block scales |
| Matrix arithmetic | Packed Q2 codes unpacked into signed INT8 lanes; ARM SDOT multiplies INT8 by INT8 into INT32. Products of scales and lane accumulation use FP32, with explicit fused accumulation |
| Other operations | Mixed backend operations; the whole transformer does not run in two-bit arithmetic |
| KV cache | Backend defaults remain F16 for K and V, context 2048; weight quantization does not quantize the cache |
| Prefill | Six prompt workers, batch/ubatch 128, up to eight columns per kernel call; one-column tails can use the decoder row policy |
| Decode | Four workers and four-row reuse, persistent session-owned pools; product speculation remains 0 |
| Eligibility | ARM64, actual DotProd CPU capability, linked implementation, policy and compatible shape/strides/workspace must all match; otherwise retain fallback |
| Numerical contract | Preserve the pinned reference's lane grouping, scale/FMA sequence and reduction. Full per-step distributions matter, not just the first logits or a short answer |

This is compressed weight storage plus INT8 arithmetic, not native two-bit hardware multiplication. I8MM capability alone is not an implemented I8MM path. GPU/NPU kernels and native NVFP4 are not part of this Android CPU implementation. See [runtime](inference-runtime.md) and [release validation](validation-0.15.md).

## Reading the supplied GPU example

The useful principle is to evaluate the complete encoding/runtime/kernel/device/shape combination. NVIDIA documents distinct NVFP4 W4A4 and W4A16 paths; activation quantization and the selected backend distinguish them. The checkpoint is an input to dispatch, not its sole decision maker. [NVIDIA quantization documentation](https://docs.nvidia.com/nemo/rl/nightly/guides/quantization-aware-rl.html).

The claim that all Blackwell chips cannot run each other's kernels is too broad. Architecture-specific targets have restrictions, while compatible cubins and portable PTX have documented compatibility paths. Compatibility is also different from optimal performance. [NVIDIA compatibility guide](https://docs.nvidia.com/cuda/blackwell-compatibility-guide/index.html).

Prefill often benefits from compute throughput and decode often from memory bandwidth, but these are hypotheses to measure. Unpacking, attention, scheduling, barriers and small shapes can dominate. No result here validates the quoted GPU/model experiment or converts advertised bandwidth into predicted phone token rates.

## App-only profile

[Run](../evidence/runs/arm-profile-20261001T144541Z-ebc2e06c/arm-checks.json) passed 23 controls. Two synthetic, known questions each have an eight-token warmup and one 64-token pair, with sampling order reversed between cases. Both arms use the admitted 4/6,width 8,rows 1/4,persistent/no-affinity configuration and full-logit tracing; only one arm runs the profiler. All sampled distributions, emitted tokens, outputs and stop reasons match within each pair. They reach the 64-token research cap, not complete natural answers.

The test APK starts Android's own simpleperf as its own app UID with `--in-app`, `cpu-clock:u`, 100 Hz and an explicit monotonic clock. No stacks, other processes, kernel samples or global profiling/security/radio/power changes are requested. It stops its own child in finally. Initial permission probes failed before sampling until the supported in-app attachment form was used; no system-setting workaround was applied. The original app data was restored with matching hashes in [the isolation journal](../evidence/runs/pixel-isolation-20261001T144536Z-d219416d/isolation.json).

There are 8,188 and9,107 samples, zero reported losses. Host symbol attribution checks the native ELF build ID against each recorded engine DSO. [Raw-derived summary](../evidence/research/stack-audit-20261001/cpu-summary-v2.json), [build identity](../evidence/research/stack-audit-20261001/build-receipt.json), [analysis tool](../eval/summarize-cpu-profile.py).

| Function / observable phase | Case0 CPU sample share | Case1 CPU sample share |
|---|---:|---:|
| columns8, before first text |81.42%|81.76%|
| graph worker, before first text |13.85%|13.07%|
| decode_rows4, after first text |70.70%|64.94%|
| graph worker, after first text |12.14%|16.72%|
| explicit ggml_barrier, after first text |8.17%|8.47%|

These are weighted user CPU samples across all threads, not percentages of request wall time. The first Java text callback divides preparation/prefill/initial sampling from subsequent decode/sampling/callbacks. It is not an exact native graph boundary. The hot graph-worker addresses include an inlined barrier's load/compare/yield loop, confirmed by [assembly](../evidence/research/stack-audit-20261001/graph-barrier-disassembly.txt). Not every sample in that function is necessarily waiting.

Control/sample totals are 15.409/16.075s and 21.929/18.019s. This is one pair per case with uncontrolled scheduling and USB power; it does not establish profiler overhead or a new speedup. Do not compare these capped answers with the complete 0.15 confirmation answers.

## Half-block scheduling experiment: not adopted

The prefill kernel dominates sampled CPU work. Its assembly spills constants/temporaries inside the loop. A research variant completes one 32-value half-block across all eight columns before the next half-block, reducing live vectors while preserving the FMA sequence for each output. [The new assembly](../evidence/research/stack-audit-20261001/half-loop-disassembly.txt) has stack saves/restores at function entry/exit but none inside its inner loop.

[Numeric admission](../evidence/runs/arm-halfnumeric-20261001T145528Z-282db62a/arm-checks.json) passes 16,241 vector/guard cases and 144,384,240 matrix-value comparisons with zero bit differences. [Complete-output comparison](../evidence/runs/arm-halfloop-20261001T145550Z-f6fe32ca/arm-checks.json) runs three rotated pairs per question with identical 4/6 workers, width 8, rows 1/4, persistent pools, no affinity, 120-second deadlines and 192-token caps. Both questions reach natural EOS with all token IDs, text, stop reasons and per-step logit hashes identical. [Metrics](../evidence/research/stack-audit-20261001/half-loop-metrics.json).

| Case | Median prefill: current → candidate | First token | Complete native total |
|---|---:|---:|---:|
| Case0 (133 tokens, EOS) | 12.451 → 12.717s | 12.634 → 12.866s | 35.701 → 35.900s |
| Case1 (162 tokens, EOS) | 14.057 → 16.693s | 14.253 → 16.884s | 42.893 → 46.254s |

Decision: retain the 0.15 kernel. Removing spills did not deliver the required repeatable prefill gain. The numerical result is successful, but speed admission is not. The first pair also shows substantial scheduling/thermal variation in unchanged decode work; do not attribute all end-to-end variation to this prefill-only change. All rejected source files, run identities and outputs remain in the captured run directories. The variant and selector were removed from maintained product sources after this experiment; no new kernel is enabled by this audit.

The trial used research app SHA256 `7deab018b81d4a26759ce13d4e6ccf8863354c054445225c90cf26fc89a81523` and test APK `1070de63e816b0b6a11985e7a9eae12d60c9e3c82935fe977499984a2c49994a`. It did not overwrite the frozen 0.15 artifact. [Restoration](../evidence/research/stack-audit-20261001/trial-restoration.json) verifies the original product/profiling APKs and personal data. Reproducing this rejected experiment requires its captured source snapshots; `halfnumeric`/`halfloop` are not current wrapper phases.

Barrier/load balancing, prepared-activation layout, KV precision and alternative models remain independent experiments. A KV/model quantization change needs fresh quality evidence; it cannot claim exact-equivalence admission from a kernel-only check. NVFP4 server kernels are not a drop-in phone optimization.

Reproduce with `scripts/test-pixel-chat-isolated.ps1 -ArmPhase profile -Threads 4 -PromptThreads 6 -Width 8 -DecodeRows 4 -PersistentThreads` after building/installing matching app/test bytes. Use `eval/summarize-cpu-profile.py` with the run directory, local NDK, matching unstripped engine and a new output path. [Simpleperf reference](https://android.googlesource.com/platform/system/extras/+/refs/heads/main/simpleperf/doc/executable_commands_reference.md).

## Next measured hypothesis: balance matrix rows without changing attention workers

The current wrapper assigns equal contiguous row counts to each worker. The profile finds substantial barrier activity, while the registered CPU has heterogeneous cores and Android may migrate workers. A bounded next experiment can distribute independent matrix-row chunks through the pinned backend's existing `ggml_threadpool_chunk_set/add` helpers. Initialize the counter before the existing activation barrier; test chunk sizes that preserve four-row decode groups and handle the final tail exactly once. Keep four decode/six prompt workers so this does not repeat the rejected attention-reduction change from the earlier study.

This was proposed at the audit checkpoint; it is now implemented and admitted in [0.16](validation-0.16.md). The acceptance plan required coverage/no-duplicate-row controls across uneven shapes, exact numeric/full-logit parity, cancellation/cache regressions, and paired end-to-end benefit. Queue atomics and poorer cache locality may erase any reduction in waiting. Samples at barriers do not by themselves establish recoverable wall time. No fixed CPU affinity or system scheduler change is implied.

The exact unstripped 0.15 ELF is retained locally at `.local/stack-audit/baseline/liboutpost_engine.so`, build ID `0373d6b13986c6bc89ac194944bd9299711412de`. It is required for full native symbol replay and stays outside Git. Raw samples and resolved summaries are tracked.

Final build/lint passed for both ABIs. [Container comparison](../evidence/research/stack-audit-20261001/rebuild-container-comparison.json) finds identical entry payloads with different incremental ZIP ordering. The exact tested APK and its original valid receipt are restored in local build outputs; the frozen release and installed product retain their original SHA256.
