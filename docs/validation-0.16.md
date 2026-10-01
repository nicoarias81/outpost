# Outpost 0.16 validation — dynamic prefill row scheduling

The registered Pixel/Bonsai4 profile now uses a shared queue of 32 matrix rows for multi-column Q2 operations. Four decode/six prompt workers, batch 128, width 8, row groups 1/4, persistent pools and no affinity remain unchanged. Single-column matrices retain static scheduling. Other hardware/OS/model keys default to static row scheduling. Weights, backend, prompts, sampling, cache precision and product limits are unchanged.

## Complete-output comparison

Six pairs per known synthetic question across two campaigns using the identical research app APK. The second campaign reverses both case and arm order. Every emitted token, full text, stop reason and per-step sampling-logit hash matches. Both answers reach natural EOS within the normal 120-second deadline and 192-token limit.

| Case | Output tokens | Median prefill: static → queued | First token | Complete native total |
|---|---:|---:|---:|---:|
| Late arrival | 133 | 15.425 → 13.521s | 15.631 → 13.732s | 40.535 → 38.725s |
| Manual applicability | 162 | 17.936 → 15.584s | 18.146 → 15.793s | 49.938 → 48.649s |

The comparison changes only row scheduling inside the same research build; it is not a cross-APK timing study. The first campaign improved prefill but showed conflicting total-time medians and paired observations during thermal/order drift. Before the single repeat, [the combined admission rule](../evidence/research/row-schedule-20261001/reversed-repeat-protocol.json) was recorded: more than 5% prefill speedup in both cases, no median total-time regression in either case, exact parity and lifecycle success. The pooled result passes that rule. [First campaign](../evidence/runs/arm-row-confirm-20261001T154033Z-e72d8318/arm-checks.json), [reversed campaign](../evidence/runs/arm-row-confirm-reverse-20261001T155657Z-a8a9eb76/arm-checks.json), [combined metrics](../evidence/research/row-schedule-20261001/confirmation-metrics.json).

Android scheduling/thermal state varies and the phone is USB-powered. Raw per-call conditions, observations and paired ratios remain inspectable. This is bounded evidence for two known workloads; no universal phone speedup, power saving or answer-quality improvement is claimed. Do not multiply these ratios by historical 0.15 results from different comparison conditions.

## Correctness and lifecycle

- [Ownership/numeric admission](../evidence/runs/arm-row-numeric-20261001T153430Z-99bc2c0a/arm-checks.json): 553,176 atomic row-visit checks, 3,226,860 values across chunk/worker/tail/stride cases, plus 144,384,240 existing real-shape matrix comparisons and 16,241 direct vector/guard controls; zero failures. Poisoned output buffers detect unwritten values; row counters detect duplicate writes.
- [Tuning](../evidence/runs/arm-row-tune-20261001T153454Z-ce87c0bd/arm-checks.json): two reversed rounds of static, prefill-only, decode-only and combined candidates. 32/0 had the best exploratory total. Decode queue variants did not justify adoption.
- [Final lifecycle](../evidence/runs/arm-row-lifecycle-20261001T162122Z-b4acd4ef/arm-checks.json): cache invalidation on policy changes, exact cache reuse, cancellation after three tokens, cold recovery, return to static scheduling, actual selected queue execution and product-profile persistence.
- [Final real chat](../evidence/runs/chat-20261001T162039Z-83cce61f/chat-checks.json): 48 controls pass. The actual product requests use 32-row prefill queues; chat/history, cancellation, synthetic TXT/CSV/PDF import and source inspection pass. Existing personal data is restored by the isolation wrapper.
- [Final candidate admission](../evidence/runs/candidate-admission-20261001T162209Z-ea325500/candidate-checks.json) retains pinned Spark/Bonsai/Qwen compatibility. [Final x86 regression](../evidence/runs/x86-regression-20261001T162031Z-55049363/run.json) passes numeric/dispatch/batch and a real Bonsai decoder check, then restores the original emulator APKs. Emulator-5580 is untouched.

## Product integration and observed UI requests

The first 0.16 UI run [failed its actual-path check](../evidence/runs/chat-20261001T161524Z-cdd8aad0/analyst-note.md): Settings selected 32 rows, but MainActivity reconstructed configuration through an older constructor and lost the queue fields. MainActivity now delegates to `Profile.configuration`, preserving the low-memory cache flag and product speculation policy. The final UI run proves nonzero queued-node execution on all three real chat requests. The failed build/run remains preserved and was not published.

| Real chat control | Output tokens | First token | Native total |
|---|---:|---:|---:|
| first-turn | 2 | 6.190s | 6.392s |
| follow-up | 2 | 6.337s | 6.544s |
| with-document | 21 | 4.858s | 7.234s |

These are single UI observations with synthetic data, not paired cross-version speed estimates or field-quality scores. All six captures were visually reviewed; English chat, Settings, documents, PDF and cited answers remain readable. Final native libraries are byte-identical to the confirmed research libraries on both ABIs. The final build has zero lint errors and three unchanged upstream warnings.

## Implementation and artifact identity

Each worker reserves an initial chunk, then claims another through the pinned backend's atomic task counter. Thread 0 initializes that counter before the existing activation barrier. Small matrices, one worker and zero chunks retain static scheduling; incompatible types/layouts retain the backend fallback. Invalid public configuration values are rejected before execution. No new graph workspace or expanded model copy is allocated. The per-output arithmetic and attention worker counts are unchanged.

`NativeEngine.Configuration` adds `prefillChunk` and `decodeChunk`; older constructors default both to 0. Values are 0 or powers of two from 16 through 256. They belong to cache compatibility and saved runtime profiles. ARM calibration key is now `q2-arm-dot-queue-v1`; x86 keeps its previous kernel identity. The setting names classify matrices by activation-column count: multi-column versus single-column. Single-column prompt tails use the decode policy too. Diagnostic node counts prove executed paths, not phase wall time.

The local user-test artifact is `dist/outpost-0.16.0-user-test.apk`, app SHA256 `095c5c56995e3de9947d0125a0757387c429191cf0602700f7d5b9b8c036239f`; test APK SHA256 `92d2c533b1aaff6267c1607b46f6219351589da83bf62ea3b1b1fa4f0c0fa0d0`. [Manifest](../evidence/releases/0.16.0/manifest.json), [build receipt](../evidence/releases/0.16.0/build-receipt.json). Build/lint passes for both ABIs. The frozen 0.15 artifact remains intact. No remote publication or release signing change is included.

Reproduction uses the registered-device isolation wrapper with `-ArmPhase row-numeric`, `row-tune`, `row-confirm`, `row-confirm-reverse` or `row-lifecycle`. Confirmation/lifecycle select `-PrefillChunk 32 -DecodeChunk 0`; keep 4/6 workers, width 8, decodeRows 4 and persistent workers. [Analysis tool](../eval/summarize-row-schedule.py) can combine the two campaign directories. Read [the Pixel protocol](pixel10-testing.md) before installing or running.
