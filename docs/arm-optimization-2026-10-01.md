# ARM optimization experiment — 2026-10-01

Status: research in progress on the explicitly authorized Pixel 10 Pro. New ARM kernels and persistent workers are compiled but remain disabled in ordinary product calls until confirmation. The frozen 0.14 user-test APK is preserved. Model weights, ChatPrompt, sampling and the product's 120-second/192-token limits are unchanged.

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

## Reproduction and current gates

Build with `scripts/build.ps1 -Offline`, install both verified debug APKs on the registered Pixel, then use `scripts/test-pixel-chat-isolated.ps1 -ArmPhase numeric`. The same wrapper accepts `controller`, `tune`, `lifecycle`, `model` and `confirm`; `-Threads`, `-Width`, `-DecodeRows`, `-PersistentThreads` and `-Affinity` record requested research settings. It preserves and restores original app-private data hashes. Use `-RestoreOnly` if its journal reports a pending restoration. No host model/numerical execution is permitted.

The tuning phase selects prompt workers/width and decode workers separately using reversed-order exploratory probes; only reference-equivalent token/logit results are eligible. Lifecycle checks cover exact caching, cancellation, cold recovery and independent pool resizing. Complete-output confirmation, product-policy adoption, UI/other-model regression and final documentation are still pending at this checkpoint. Do not infer adoption from the numeric or32-token controller PASS.
