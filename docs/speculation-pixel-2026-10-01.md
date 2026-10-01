# Pixel speculation trace and verification study — 2026-10-01

TND-01 is implemented and bounded checks pass. TND-02 now has physical Pixel measurements. The numerical admission gate fails, so no new speculative controller or product acceleration is admitted. The phone retains the validated Outpost 0.16 release with speculation depth 0. No TandemLLM code, trained drafter, new model or vendor patch is imported.

## What changed in the research checkout

- Every target sample can now record its output index, token, raw-logit hash, source row and route. Initial, ordinary and speculative samples share the audit contract. Un-emitted EOS/pending/cancelled decisions stay visible separately from delivered tokens.
- Round accounting includes anchor serialization/callback, proposal work, target evaluation, target sampling, rejected-KV removal and accepted-token serialization/callback. The controller uses the complete round cost minus measured hash/trace overhead. Remaining instrumentation overhead is not corrected. This is not UI paint timing.
- Terminal and interrupted rounds do not train the controller. Completed windows count actually committed tokens. Existing result fields retain their legacy scopes; use the new diagnostics for comparable whole-round costs.
- The bounded native curve evaluates the same 16 teacher-forced continuation positions at widths 1, 2, 4, 8, in two reversed rounds, from the same cached prefix. It reports exact float equality, logit hashes, top1 and absolute differences separately from evaluation time. Comparison/copy/reset work is outside evaluation timing. Both 4 and 6 verify-worker configurations are inspected, retaining 4 decode workers and the original 6-worker prefill state.
- Curve cleanup removes its forced suffix, restores configured worker counts, clears its abort callback and pauses pools. Error/cancellation invalidates context. Model/numerical execution remains restricted to the registered Pixel and Outpost35.

## Numerical result and forward-cost opportunity

Median evaluation time for 16 positions with 6 verify workers; 1-row calls still use 4 decode workers:

| Cached prefix tokens | Rows 1 | Rows 2 | Rows 4 | Rows 8 | Rows 8 bit-identical positions, each repeat |
|---|---:|---:|---:|---:|---:|
| 66 | 2329.8ms | 1087.6ms | 845.8ms | 766.1ms | 16/16 |
| 466 | 2706.6ms | 1289.6ms | 1037.2ms | 998.0ms | 0/16 |
| 1066 | 3543.8ms | 1849.6ms | 1566.3ms | 1406.5ms | 0/16 |
| 1666 | 4020.1ms | 2410.3ms | 2024.1ms | 1857.4ms | 0/16 |

This suggests useful amortization, but is an optimistic forward-only diagnostic: it excludes drafting, sampling, emission, and real proposal rejection. It is not a measured 2–3x app speedup. Actual prefixes are 66, 466, 1066 and 1666 tokens, not nominal context-bin labels. [Raw curve run](../evidence/runs/arm-spec-curve-20261001T172606Z-51996de8/arm-checks.json), [validated summary](../evidence/research/spec-study-20261001/summary.json), [analysis tool](../eval/summarize-spec-study.py).

At 66 tokens, all batches preserve every tested position in both worker configurations. At 466/1066/1666, batches 2/4/8 preserve zero of 16 logit vectors per repeat, although every tested top1 token still agrees. Changing verify workers from 6 to 4 does not restore bit parity. The repeated serial controls remain bit-identical throughout.

The real sampled manual probe is stricter: supplied-reference proposals at depths 1/3/7 change the first verified distribution (sample index 1) and all three change the 64-token output. These oracle inputs are the baseline's tokens; once a batch diverges they are not guaranteed perfect predictions. The actual request-local lookup preserves this probe's text but changes its first distribution at index 7. Matching text or argmax alone is insufficient. [Trace run](../evidence/runs/arm-spec-trace-20261001T172327Z-3fde5ca0/arm-checks.json).

The unchanged normal route matches all 64 tokens and logit hashes from the corresponding confirmed 0.16 reference prefix. [Cross-check](../evidence/research/spec-study-20261001/baseline-and-source-check.json). This identifies a serial-versus-batch numerical difference, rather than silently admitting a changed ordinary sampler.

## Execution and lifecycle controls

The trace suite passes 43 execution controls, including 561 native lookup/acceptance/controller/toy-sampling checks. Deliberately wrong proposals cause real rejections. Cancellation delivers exactly 3 tokens while retaining 9 target-sample events: 6 un-emitted decisions are explicitly marked. Recovery and an exact cached repeat match the normal 64-token trace.

The curve suite passes 72 execution controls. The [edge suite](../evidence/runs/arm-spec-edges-20261001T173720Z-0313bb42/arm-checks.json) passes 27: sampled EOS is recorded without being emitted, successful verification leaves an exact reusable prompt prefix in the short control, diagnostics-off retains output with empty recorded-event arrays, and the adaptive cost decision matches an independent reconstruction from recorded round costs. The bad-proposal adaptive probe disables after 3 windows, accepting 0 of 9 proposed tokens.

Normal x86 behavior also passes the [numeric/dispatch/batch/real-decoder regression](../evidence/runs/x86-regression-20261001T172317Z-d412c015/run.json), after which original Outpost35 APKs are restored. Brújula/emulator-5580 remains untouched. Execution PASS does not turn the failed speculation-parity gate into an admission PASS.

## Most direct next hypothesis

In the pinned backend, [ops.cpp](https://github.com/ggml-org/llama.cpp/blob/86ea01d05ec237f89b78b41c8c1ee0f908141ac7/ggml/src/ggml-cpu/ops.cpp#L9261) selects split-KV attention for one query with KV extent at least 512, while multiple queries use a different reduction path, sometimes tiled. The observed short/long split and failure of the 4-worker diagnostic are consistent with this shape-dependent arithmetic. Prompt-token count is not the padded KV extent. No attention ablation was executed, so this is a source-supported hypothesis, not exclusive causal proof.

Next: TND-02b should record actual query/KV shapes around padding boundaries and isolate the attention reduction while keeping the pinned reference available. A compatible batched route must retain the ordinary path's reduction and masking semantics, including boundary crossings. Whole-model traces must confirm any repair. Do not claim that Q2 projection row invariance alone fixes attention, accept a one-ulp exception, or adopt a controller from forward-only timing. TND-03/TND-04 remain pending that prerequisite.

## Artifact and resumption state

Research app SHA256 is `509384984532b696e30948fccf992c1433618425e0925caabb5dddb7e53b032b`, labeled 0.16.0 but different from the frozen release. Trace/curve use test APK `36f055313619f6eac2edf4f18dbbe2becdd27496fcf9cab428d18aff73b4a0f3`; the edge extension uses `424c53df056be98b7a0d9308e753bf761f412400f417c430e7e567b389147e97` with identical app bytes. [Current research build receipt](../evidence/research/spec-study-20261001/build-receipt.json). Builds cover both ABIs; lint has zero errors and the same three upstream warnings. An initial Java array-length syntax error was corrected before phone execution.

The installed Pixel app/test pair is restored to frozen 0.16: app `095c5c56995e3de9947d0125a0757387c429191cf0602700f7d5b9b8c036239f`, test `92d2c533b1aaff6267c1607b46f6219351589da83bf62ea3b1b1fa4f0c0fa0d0`. Original private data is hash-verified by each isolation journal. The current build outputs/receipt remain the research pair and match the current source. Therefore `-SkipInstall` against the restored phone must reject them until the research pair is explicitly installed. No new version was published and the frozen artifact was not overwritten.

Use the Pixel isolation wrapper's `spec-trace`, `spec-curve` and `spec-edges` phases after installing matching research app/test bytes. The stored [trial driver](../evidence/research/spec-study-20261001/run-trial.ps1) shows baseline-APK restoration in finally; its `.local/spec-study/baseline` paths are machine state, not portable dependencies. Keep the phone's original files and global settings intact. The [TandemLLM review](tandemllm-review.md) and [speculation guide](speculation.md) remain the conceptual/history references.
