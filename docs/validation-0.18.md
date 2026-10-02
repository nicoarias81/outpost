# Outpost 0.18 validation — I8MM prefill with exact original outputs

The exact Pixel/Bonsai4 preset now combines six graph workers, original four-worker attention arithmetic and guarded **2-row I8MM matrix tiles** for multi-column work. Single-column generation retains the existing DotProd decoder. Weights, sampler, KV precision, context and output/deadline limits remain unchanged. Product speculation stays zero.

## Direct combined-stack comparison

Three counterbalanced complete-answer pairs per known synthetic case compare the original 0.16 policy (4 decode/6 prompt, DotProd) with the combined 0.18 policy (6/6, attention 4, I8MM matrices) **inside the same final APK**. Every target logit hash, token, text and stop reason matches. All answers reach natural EOS. This is not a cross-APK measurement or multiplication of older ratios.

| Case | Output tokens | First token: original → current | Decode | Native total | Paired median total reduction |
|---|---:|---:|---:|---:|---:|
| Late arrival | 133 | 10.014 → 8.660s | 22.398 → 17.014s | 32.411 → 25.707s | 20.7% |
| Manual applicability | 162 | 11.935 → 10.273s | 28.031 → 22.001s | 39.965 → 32.385s | 19.9% |
| GPS and downloaded maps | 154 | 8.763 → 7.313s | 25.687 → 19.692s | 34.529 → 27.004s | 22.1% |

Times are per-arm medians; the last column uses paired ratios. [Raw comparison](../evidence/runs/arm-stack-confirm-20261002T003255Z-92f4ea1b/arm-checks.json), [validated metrics](../evidence/research/i8mm-20261002/combined-summary.json), [predeclared protocol](../evidence/research/i8mm-20261002/combined-protocol.json), [analysis tool](../eval/summarize-stack.py). All cases have lower paired median total time. Process CPU times, individual measurements and conditions remain inspectable; they are not energy measurements.

## I8MM contribution and rejected interpretations

The separate [I8MM confirmation](../evidence/runs/arm-i8mm-confirm-20261002T001500Z-b3cad0c3/arm-checks.json) compares against the already faster 0.17 policy with attention 4 and six workers. Paired median prefill reductions are about 15%; native totals fall 4.5–6.5%. Decode is unchanged by design. [Validated metrics](../evidence/research/i8mm-20261002/confirmation-summary.json). The pilot had one slower total-time case despite shorter prefill; it is preserved, along with all confirmation outliers.

Earlier six-worker verification-only experiments had exact outputs but mixed timing and were not enabled as product speculation. The staged cost-controller sketch was not integrated. Optimizing ordinary decoding changed the draft/verify break-even point; future speculative work must compare against this new baseline.

These are warm-page, USB-powered observations on one registered phone, with uncontrolled background/scheduling/thermal variation. The CPU-time increase from 0.17 and the CPU-time reduction in the I8MM-only comparison do not establish battery savings. No quality-score improvement, universal device speedup or field/bounty acceptance is claimed. Existing model/source-binding mistakes remain unchanged.

## Arithmetic, storage and guards

The file remains Q2_0 g64: 64 two-bit codes plus one FP16 scale, 18 bytes per block. Activations are quantized to the existing Q8_0 workspace. Pair preparation permutes those bytes in place, leaving FP16 scales intact; there is no expanded model copy or larger graph workspace.

For each original integer partial, the kernel uses `vmmlaq_s32`/SMMLA to evaluate two weight rows against two activation columns. Four separate FP32 accumulator histories preserve the original per-lane FMA sequence and pairwise final reduction. It is INT8×INT8→INT32 arithmetic followed by original FP32 accumulation, not native two-bit multiplication. [Arm intrinsic reference](https://arm-software.github.io/acle/neon_intrinsics/advsimd.html), [owned implementation](../app/src/main/cpp/q2_arm.c).

NEON, DotProd and I8MM runtime capability, linked implementation, compatible Q2/F32 layouts/strides, workspace size and multi-column shape are all required. One-column work and incompatible cases keep the reference/DotProd routes. [Static disassembly](../evidence/research/i8mm-20261002/instruction-audit-018.json) confirms SMMLA is confined to the attributed I8MM functions; runtime ISA guards are separately exercised on the phone. The old dispatch registry describes vector-dot selection; `kernelProfile.matrixI8mm` separately describes matrix availability, while per-request matrix-node counters prove actual execution.

`Configuration` now has 15 fields: `matrixKernel=0` retains DotProd and 1 selects the admitted I8MM policy with fixed attention. Older constructors default it to 0. The setting belongs to context-cache identity and persisted profiles. ARM profile key becomes `q2-arm-i8mm-attn4-v1`; other device/model keys remain conservative. The pinned vendor is unmodified.

## Final-APK checks

- [Numeric/shape suite](../evidence/runs/arm-i8mm-numeric-20261002T003134Z-66bced59/arm-checks.json): 128 actual signed-matrix layout controls and 65,029,512 exact original-GGML value comparisons. Strided inputs, poisoned outputs, odd rows/columns, six/seven-column tails, worker counts, widths, row queues and single-column fallback pass.
- [Matrix lifecycle](../evidence/runs/arm-i8mm-lifecycle-20261002T003150Z-1a68ce8c/arm-checks.json): 27 controls cover production profile/factory, persistence, matrix-policy-only cache invalidation, exact reuse, cancellation/recovery, return to DotProd and invalid combinations. Exact prefix hits correctly execute no I8MM work.
- [Final combined answers](../evidence/runs/arm-stack-confirm-20261002T003255Z-92f4ea1b/arm-checks.json) use the production configuration fields, not research selectors; full target distributions and outputs remain unchanged.
- [Pinned-model admission](../evidence/runs/candidate-admission-20261002T004321Z-6bf07b07/candidate-checks.json): 23 controls retain Spark/Bonsai/Qwen compatibility. [x86 regression](../evidence/runs/x86-regression-20261002T003138Z-bd39cc6f/run.json) passes numeric/dispatch/matrix and real scalar/AVX2 decoding, then restores original emulator APKs.
- [Real chat](../evidence/runs/chat-20261002T004710Z-0b8437e2/chat-checks.json): 54 controls pass and actual I8MM plus fixed-attention execution is observed. English chat, Settings, TXT/CSV/PDF imports, history, cancellation and sources remain usable. All six synthetic screenshots were visually reviewed.

Single UI observations, not paired version-speed estimates:

| Control | Tokens | First text | Native total | I8MM nodes |
|---|---:|---:|---:|---:|
| first-turn | 2 | 5.515s | 5.674s | 498 |
| follow-up | 2 | 5.298s | 5.461s | 498 |
| with-document | 21 | 3.836s | 5.894s | 498 |

## Artifact, restoration and reproduction

Local artifact: `dist/outpost-0.18.0-user-test.apk`, code 20. App SHA256 `450c88214a945d7fdc231a29e1890c23318f905be257ae5708c9806147e67f3c`; test APK `0b1ffa84b246c3a30eb4f040062c3338fe7b3500dc7f8abf019f8c4da74fbcd0`. [Manifest](../evidence/releases/0.18.0/manifest.json), [build receipt](../evidence/releases/0.18.0/build-receipt.json), [final device record](../evidence/releases/0.18.0/final-device.json). Both ABIs build with zero lint errors and the same three dependency warnings. Fixtures v11 updates identity only; old definitions/evidence remain preserved.

The phone's original frozen 0.16 app/test pair, private stores and global settings are restored after tests. Outpost35 is also restored; Brújula/emulator-5580 is untouched. Frozen 0.17 and earlier artifacts remain intact. No model/backend replacement, public distribution, remote push or signing-policy change occurred.

Use `i8mm-numeric`, `i8mm-model`, `i8mm-confirm`, `i8mm-lifecycle` or `stack-confirm` through the registered-Pixel isolation wrapper with matching APKs. Model admission uses `-CandidateAdmission`; actual chat uses `-Generate`. Archived drivers restore older debug APKs explicitly with `-d`; do not substitute another device or overwrite a pending private-state backup. [Handoff](handoff.md), [Pixel protocol](pixel10-testing.md).
