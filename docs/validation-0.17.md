# Outpost 0.17 validation — six workers with original attention arithmetic

The exact registered Pixel/Bonsai4 profile now uses **six decode/six prompt workers** while preserving the original **four attention partitions** for single-query operations. The original attention function and tensor/mask remain unchanged; four workers perform its arithmetic and two participate in synchronization. Multi-query prefill retains its existing six-worker operation. Other hardware/OS/model keys remain conservative. Product speculation stays zero.

## Matched complete-answer results

Three counterbalanced pairs per known synthetic question. Every sampling-logit hash, emitted token, full text and stop reason matches the original 4/6 profile. All answers reach natural EOS within the unchanged 120-second/192-token budget. Times below are per-arm medians; the last column is the median paired reduction.

| Case | Output tokens | Decode: original → candidate | Complete native total | Paired total reduction |
|---|---:|---:|---:|---:|
| Late arrival | 133 | 23.682 → 18.695s | 35.300 → 30.387s | 13.8% |
| Manual applicability | 162 | 29.432 → 23.393s | 43.069 → 38.232s | 13.1% |
| GPS and downloaded maps | 154 | 26.875 → 21.873s | 36.636 → 31.628s | 15.1% |

Paired median decode reductions are 20.8%, 20.5%, 19.6%. The [predeclared rule](../evidence/research/overnight-performance-20261001/protocol-decode-six.json) requires at least 5% lower median decode time in each substantive case and no median total-time regression above 3%; all three cases pass. [Confirmation](../evidence/runs/arm-decode-six-confirm-20261001T222511Z-64ae8cc6/arm-checks.json), [validated paired metrics](../evidence/research/overnight-performance-20261001/decode-six-confirm-summary.json), [analysis tool](../eval/summarize-decode-six.py).

The first [pilot](../evidence/runs/arm-decode-six-pilot-20261001T221733Z-adbd276a/arm-checks.json) was mixed: travel/manual improved while the GPS case was slower, including a much longer prefill. It is preserved in full. Counterbalanced repetition resolved the admission question; no sample was deleted. Earlier verification-only six-worker curves also contained large stalls and were not admitted as a speculative product gain. [Their summary](../evidence/research/overnight-performance-20261001/six-summary.json).

These are warm-model, USB-powered observations on one phone with background/scheduling/thermal variance. Prompt processing is essentially unchanged; the result concerns generation after the first token. Process CPU-time paired medians increased approximately 11–12%; this is **not an energy measurement**, and no battery-life improvement is claimed. Do not combine these ratios with older releases' absolute times or present a universal device speedup. Exact equivalence preserves baseline model errors; it does not improve factual quality.

## Final-APK integration and lifecycle

The performance confirmation used a research build with a test selector. Integration replaces that selector with explicit `attentionThreads=4` in `NativeEngine.Configuration` and the versioned runtime profile. The final native libraries therefore differ from that research APK. The actual 0.17 APK independently passes:

- [34 lifecycle controls](../evidence/runs/arm-decode-six-lifecycle-20261001T224406Z-0d8d91e9/arm-checks.json): normal-reference equivalence, exact prefix reuse, cancellation after three tokens, recovery, sleeping workers, profile persistence, a 641-token prompt with a one-token prefill tail, and cache invalidation when only logical attention policy changes.
- [60 complete-answer/control checks](../evidence/runs/arm-decode-six-pilot-20261001T224706Z-f2e56a25/arm-checks.json): the actual configuration field preserves all original sequences and again improves decode in each case. This is a single-pair integration check, not a replacement for the three-pair confirmation. [Summary](../evidence/research/overnight-performance-20261001/decode-six-017-summary.json).
- [51 real chat/import/source controls](../evidence/runs/chat-20261001T225935Z-2e81b8df/chat-checks.json): the selected profile is 6/6 with attention 4, and every generated UI answer records actual fixed-attention execution. English chat, Settings, synthetic TXT/CSV/PDF and source inspection remain usable. All six screenshots were visually reviewed.
- [23 pinned-model compatibility controls](../evidence/runs/candidate-admission-20261001T225617Z-0133cc13/candidate-checks.json): Spark/Bonsai/Qwen behavior remains supported by the existing protocols. [x86 regression](../evidence/runs/x86-regression-20261001T224726Z-39f66dd6/run.json): numeric/dispatch/matrix and actual scalar/AVX2 model paths pass; original Outpost35 APKs restored.

Single final UI observations, with synthetic data and no cross-version timing claim:

| Control | Tokens | First text | Native total | Fixed-attention nodes |
|---|---:|---:|---:|---:|
| first-turn | 2 | 10.205s | 10.387s | 72 |
| follow-up | 2 | 6.831s | 7.031s | 72 |
| with-document | 21 | 5.079s | 7.801s | 756 |

## Runtime contract

The owned [attention wrapper](../app/src/main/cpp/attention_probe.cpp) selects the unchanged backend single-query function using four logical workers inside a six-worker graph. Its two synchronization-only workers enter the original internal barrier and the wrapper exit barrier. Scratch planned for six workers accommodates four. The pinned backend has one internal pool barrier in both single-query branches; a backend/threadpool change requires re-auditing that contract and re-running full parity tests.

Normal decoding uses the original tensor, mask and padded KV extent, so it does not reconstruct dense cache layout. This differs from the earlier multi-query verification experiment, which slices queries and reconstructs each serial extent. Multi-query prefill and ordinary original-mode fallback stay available. No backend/model pin, sampler, quantization, KV precision, context size, output cap or deadline changed.

`Configuration` now has 14 fields; old constructors default `attentionThreads` to 0. The admitted value 4 requires physical 6/6 and speculation 0. It belongs to context-cache identity and persisted profiles. The ARM calibration key is `q2-arm-dot-attn4-v1`; x86 retains its prior key. MainActivity continues to use `Profile.configuration`, preserving every field. Speculation with this production policy is deliberately not admitted; future speculative work must compare against the new faster normal decoder, not the old 4/6 timings.

## Artifact and phone state

Local user-test APK: `dist/outpost-0.17.0-user-test.apk`, code 19. App SHA256: `6ae162f07dc719540b2256d249b312e56d2e70e45d22794b8dc60f8cf8f66ca2`. Test APK SHA256: `20da12c961e265229b1b3996fc604dd55884c355e4bc2d02f47e22c549f641d3`. [Manifest](../evidence/releases/0.17.0/manifest.json), [build receipt](../evidence/releases/0.17.0/build-receipt.json). Both ABIs build; lint has zero errors and three existing dependency warnings. Fixtures advance to v10 solely for app identity; definitions and old scores are preserved.

The phone is restored to its frozen 0.16 app/test pair and original private stores after validation. [Final device record](../evidence/releases/0.17.0/final-device.json) verifies the installed hashes, private-state journal and unchanged radio/screen settings. Frozen 0.14/0.15/0.16 artifacts remain intact. No public publication, remote push, new signing policy or model download occurred.

Reproduction: install both matching APKs, then use the registered-Pixel isolation wrapper with `decode-six-pilot`, `decode-six-confirm`, `decode-six-lifecycle`, `-CandidateAdmission` or `-Generate`. The archived [product trial driver](../evidence/research/overnight-performance-20261001/run-product.ps1) restores the saved debug baseline with an explicit downgrade. Its backup paths are local state. [Pixel protocol](pixel10-testing.md), [handoff](handoff.md).
