# Pixel attention parity repair — 2026-10-01

This is the earlier TND-02b research record. [0.17 validation](validation-0.17.md) owns the subsequent normal-decoder improvement and current delivery; the APK identities below are historical research pairs.

TND-02b has a bounded successful repair. The original serial-versus-batch divergence is reproduced, an attention-only ablation removes it, and an opt-in per-query attention wrapper preserves the original serial logits. All **640 candidate logit vectors** match bit for bit across five prefixes, four batch widths and two orders. The 64-token sampled oracle probes at depths 1/3/7 now preserve every distribution, token, text and stop reason. Product speculation remains depth 0; no new APK is published.

## Cause and correction

The pinned backend uses split-KV attention for a single query when the padded KV extent reaches 512. It divides KV cells into worker-sized partitions, accumulates and combines partials. At 2–8 query rows it uses the unsplit vector path. Floating-point/F16 accumulation and reduction order differ. KV extent also grows in 256-cell blocks, changing the serial partition boundaries as a batch crosses a boundary. These are actual observed query/KV shapes, not context-size labels inferred from prompt length.

At prefix 253, the original batched route agrees with serial in only 3/16 positions per repeat; single-row repeats agree in all 16. The candidate agrees in all 16 for every width. Prefixes 509, 765, 1,066 and 1,666 exercise later boundaries and larger contexts. For the separate prefix-509 ablation, setting `use_ref` on **attention only** makes its serial and batch distributions equal. That ablation changes the original serial distributions and is not the adopted experimental correction.

The owned [wrapper](../app/src/main/cpp/attention_probe.cpp) calls the unchanged original attention implementation once per verification query, with four workers and the exact per-position padded KV extent. Query/mask/output tensor views retain their strides. An explicit barrier prevents the next query overwriting partials before all workers finish their reduction. The matrix operations elsewhere in the Transformer remain batched. Prompt processing stays at six workers; verification temporarily uses four and restores the configured count afterwards. Normal single-token generation is unchanged.

The narrow guard requires F32 Q, F16 K/V, head dimension 128, 32 query heads / 8 KV heads, one stream, 2–8 queries, dense causal masks, bounded positions, matching layouts and sufficient scratch. Unsupported cases use the original operation and record a rejection in research diagnostics. No rejection occurs in the measured candidate cases. This is a research route for the pinned Bonsai/CPU contract, not an arbitrary-model/backend guarantee. There is no vendor edit, backend/model pin change, new drafter, sampler change or new precision format.

## Measurements and limits

Median forward time in milliseconds for the same **16 forced continuation positions**, two reversed width orders, four workers:

| Prefix tokens | 1 row | 2 rows | 4 rows | 8 rows | Serial / 8-row time |
|---|---:|---:|---:|---:|---:|
| 253 | 2689.67 | 1832.97 | 1528.45 | 1418.79 | 1.90x |
| 509 | 2957.71 | 2241.11 | 1877.21 | 1743.27 | 1.70x |
| 765 | 3314.23 | 2556.36 | 2162.30 | 2035.42 | 1.63x |
| 1066 | 3589.66 | 3021.55 | 2449.01 | 2312.70 | 1.55x |
| 1666 | 4060.81 | 3322.76 | 2919.13 | 2773.93 | 1.46x |

All candidate positions are bit-identical to the original serial reference, including boundary-crossing batches. Prefixes are truncations of a common 1,666-token prefill, not five independent natural-language tasks. These times exclude proposals, sampling, callbacks, reset and comparison work. Full-logit observation and mask/shape diagnostics remain part of this research setup. Two repeats, warm pages, USB power and uncontrolled background/thermal conditions do not establish a universal app speedup or battery-life improvement. The emulator regression ran briefly on the host during the Pixel campaign; model execution for these curves was on the Pixel.

Single bounded 64-token generation observations from the trace suite:

| Probe | Emitted tokens | Decode seconds | Total seconds | Accepted/proposed |
|---|---:|---:|---:|---:|
| spec/plain | 64 | 10.147 | 20.548 | 0/0 |
| spec/oracle1 | 64 | 8.315 | 20.477 | 31/31 |
| spec/oracle3 | 64 | 6.881 | 19.793 | 47/47 |
| spec/oracle7 | 64 | 6.410 | 19.715 | 55/55 |
| spec/request-lookup | 64 | 11.499 | 27.972 | 10/18 |

Oracle rows supply the known reference answer and are an optimistic diagnostic, not a deployable prediction method. Request-local lookup is the existing untrained suffix lookup; it did not show a benefit in this observation (11.499s decode versus 10.147s normal). These are single observations with trace overhead, not a matched multi-run promotion campaign. The cost-aware depth selector, representative end-to-end benefit, broader prompts, low-memory/energy behavior and other devices remain pending. Matching the baseline does not establish answer quality.

## Validation and evidence

- [Predeclared protocol](../evidence/research/attention-study-20261001/protocol.json), [validated summary](../evidence/research/attention-study-20261001/summary.json), [analysis script](../eval/summarize-attention-study.py).
- Build and lint passed for both ABIs (zero errors, three existing dependency warnings); 326 curve and 67 sampled/lifecycle controls passed.
- [Attention curves](../evidence/runs/arm-attention-curve-20261001T211621Z-f2f231d9/arm-checks.json): original shape observations, attention-only ablation, five exact prefixes, 1/2/4/8 rows, both orders, actual dispatch and scratch/mask guards.
- [Sampled trace and lifecycle](../evidence/runs/arm-attention-trace-20261001T213815Z-c16ccbea/arm-checks.json): depths 1/3/7, request-local lookup, deliberately wrong proposals, EOS, cancellation with un-emitted confirmed samples, recovery, exact cache reuse, paused pools and independently recomputed cost fallback.
- Normal 64-token manual generation matches the prior [unchanged 0.16 baseline trace](../evidence/runs/arm-spec-trace-20261001T172327Z-3fde5ca0/arm-checks.json) in prompt, all hashes, tokens, text and stop reason. That previous study already matched the frozen release confirmation prefix.
- [Outpost35 regression](../evidence/runs/x86-regression-20261001T212126Z-76ca795f/run.json): numeric, dispatch, matrix and actual-model scalar/AVX2 paths pass; original emulator APKs restored. The attention candidate itself is measured on ARM, not admitted on x86. Brújula/emulator-5580 remains untouched.

## Recorded artifacts and historical reproduction

Recorded research app SHA256: `e5617e651fc2fb72cbc11496e982b0f9efa3025dc3975c0fa3c857fcf3a0ba5c`. Test APK: `879cf303d2cf19d9ba75844adf833a8ef18f03dfe8ec62fe642bc1ec8ebf5de8`. These are labeled 0.16.0 / code 18 research artifacts, distinct from the frozen release; [build receipt](../evidence/research/attention-study-20261001/build-receipt-initial.json) and run snapshots bind source to results. Both ABIs build; lint has no errors and the existing warnings.

The Pixel retains frozen app `095c5c56995e3de9947d0125a0757387c429191cf0602700f7d5b9b8c036239f` / test `92d2c533b1aaff6267c1607b46f6219351589da83bf62ea3b1b1fa4f0c0fa0d0`. [Final device record](../evidence/research/attention-study-20261001/final-device.json) confirms installation hashes, private-state restoration and unchanged global settings. Exact research APKs/receipt are also byte-verified in ignored `.local/attention-study-20261001`; the original release backup remains `.local/spec-study/baseline`. `-SkipInstall` must reject the restored phone until the matching research pair is installed.

Historical reproduction requires the matching source snapshot and app/test pair; do not combine old binaries with current tests. Use the registered-Pixel isolation wrapper with `attention-curve` or `attention-trace` after verifying/installing the current research APK pair. The archived [trial driver](../evidence/research/attention-study-20261001/run-trial.ps1) restores the saved baseline APKs in finally. Its ignored backup paths are local host state, not portable dependencies. `causalCurveForTests` mode 0 observes, mode 1 forces attention-only reference, mode 2 slices; truncated-prefix curves discard their context to prevent stale cached logits. Whole sampled generation selects mode 2 only through a test setter; product code never selects it.

Next: TND-03 can now measure real proposal acceptance and choose depths using complete round costs on this parity-preserving route. Keep the original reference available and require repeated end-to-end task benefit before product admission. Wider verification parallelism needs fixed four-way numerical partitions independent of scheduler size; simply switching attention to six workers would reintroduce the earlier drift.
