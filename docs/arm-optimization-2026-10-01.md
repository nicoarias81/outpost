# ARM optimization experiment — 2026-10-01

Status: bounded adoption in Outpost 0.15 after full confirmation and final regressions. The exact tested Pixel/Bonsai preset uses 4 decode workers, 6 prompt workers, batch 128, width 8, rows 1/4 and persistent workers without affinity. The frozen 0.14 APK is preserved. Model weights, ChatPrompt, sampling and product limits remain unchanged. [Release validation](validation-0.15.md) owns final artifacts and measurements.

## Implementation and numerical contract

The existing ARM reference already uses baseline NEON; its generic build emulates dot products. It is not a scalar kernel. Native SDOT groups products into different lanes from that emulation. The new kernel permutes weights/activations so that each lane, fused float accumulation and final reduction preserve the pinned ARM reference's bits. Simply enabling DotProd globally would not preserve that grouping.

The matrix path prepares activations once, reuses unpacked weights across 1–8 columns, specializes column counts to retain accumulators in registers, and optionally reuses activations across 2/4 decode rows. Type/layout/workspace guards retain the original backend fallback. Hardware capability and linked implementation remain separate; I8MM is not implemented.

Session-owned thread pools are an independent experiment. They are reused across graphs and requests, paused after every request/cache release, resized when necessary and freed on close. Optional affinity affects only Outpost's own threads; caller affinity is restored, unsupported masks fall back, and no system governor/priority policy changes. Four-core pinning was slower than the unpinned combined path in the first controller probe and is not selected.

## Completed evidence

- [Initial numeric pass](../evidence/runs/arm-numeric-20261001T113351Z-30fc1ab8/arm-checks.json): 16,241 vector/edge/guard cases and 7,044,720 matrix values, zero bit differences. Direct dot microbenchmark approximately 1.41×; multi-column shape probes roughly 2×. These are not whole-model speed claims.
- [Expanded numeric pass](../evidence/runs/arm-numeric-20261001T114809Z-7f2313f2/arm-checks.json): 144,384,240 comparisons with zero failures, strided/tail/worker coverage and nine shape groups including the vocabulary projection. Decode row grouping remains numerically exact.
- [Controller probe](../evidence/runs/arm-controller-20261001T122026Z-ae50971f/arm-checks.json): 47 controls passed; two reversed-order rounds of five variants generated identical 32-token sequences and initial-logit hashes. Legacy totals were31.744/96.096 seconds, combined kernel/pools17.255/20.912 seconds, combined fixed-affinity21.950/24.033 seconds. Large variation prevents a universal speedup claim; complete-output confirmation remains required.
- [Original model attempt](../evidence/runs/arm-model-20261001T113519Z-ced1f437/arm-checks.json) stopped at the old reference's120-second deadline before trying the candidate. It is preserved as a baseline limitation, not a candidate failure. Paired research calls now use an explicit per-session300-second deadline for both paths, retaining the192-token cap. Normal app sessions still default to120 seconds. EOS and token-cap results must remain distinguished.

Two earlier attempts stopped at the unlocked-screen precondition without executing model work. The dedicated test Activity now uses Android's standard show-when-locked/turn-screen-on APIs and checks actual resume/focus/interactive state. It can display only benchmark status over keyguard, never personal app content; it does not disable device authentication. Actual keyguard state remains recorded. See [Android's Activity API](https://developer.android.com/reference/android/app/Activity#setShowWhenLocked(boolean)).

## Full-trace rejection and adoption

The exploratory 6/6-worker recommendation passed bounded tuning but failed [long confirmation](../evidence/runs/arm-confirm-20261001T125824Z-99b315d4/arm-checks.json) at output token 90 in the manual case. The backend partitions decode attention's KV reduction by worker count, changing rounding. [Per-step trace](../evidence/runs/arm-trace-20261001T132954Z-f9669c2f/arm-checks.json) found drift from distribution index 1 with 6 decode workers; 4/6 and 4/4 preserve every distribution in that probe. The rejected run is not product evidence of adoption.

The corrected [confirmation](../evidence/runs/arm-confirm-20261001T133232Z-a8581810/arm-checks.json) passed 79 controls and compares all sampling-logit hashes, tokens, stop reasons and full outputs across three rotated pairs. Both questions end normally: 133 travel tokens and 162 manual tokens. Median native totals improve 205.048→35.519 seconds and 262.629→42.322 seconds; first-token medians improve 41.081→12.377 and 45.369→14.132 seconds. These are matched-output, combined-runtime measurements on this phone, not a DotProd-only or universal speedup. [Exact metrics](../evidence/research/arm-optimization-20261001/confirmation-metrics.json).

Final 0.15 repeats lifecycle and per-step trace, passes Spark/Bonsai/Qwen admission and 45 chat/import/PDF controls, and passes x86 numeric/dispatch/batch/decoder regression before restoring the emulator's old APKs. The real chat produced the same short answers in 9.385/9.813 seconds and the same cited PDF answer in 11.350 seconds. Original app-private data was restored after isolated tests; no global radio/governor/security change was made.

## Reproduction and current gates

Build with `scripts/build.ps1 -Offline`, install both verified debug APKs on the registered Pixel, then use `scripts/test-pixel-chat-isolated.ps1 -ArmPhase numeric`. The same wrapper accepts `controller`, `tune`, `lifecycle`, `trace`, `model` and `confirm`; `-Threads`, `-PromptThreads`, `-Width`, `-DecodeRows`, `-PersistentThreads` and `-Affinity` record requested research settings. It preserves and restores original app-private data hashes. Use `-RestoreOnly` if its journal reports a pending restoration. No host model/numerical execution is permitted.

Tuning probes are exploratory; the rejected six-worker result shows that initial hashes and short token sequences cannot establish full equivalence. Adoption requires the complete per-step trace gate and regressions recorded above. Lifecycle checks cover exact caching, cancellation, recovery and independent pool resizing. Other phones, additional model-specific presets, I8MM, memory pressure and controlled energy remain open. Test-only per-session deadline extension and logit tracing are off in normal product calls.
