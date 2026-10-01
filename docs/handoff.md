# Engineering handoff — start here

Updated 2026-10-02. Current local delivery: **Outpost 0.17.0 / code 19**, canonical repo `E:/projects/outpost`, branch `codex/outpost`, app `dev.outpost.app`. Read [validation](validation-0.17.md), [capability inventory](current-state.md), [runtime](inference-runtime.md) and [documentation index](index.md).

## Scope and constraints

- English app/docs/comments/primary fixtures; preserve imported language and historical evidence.
- Inference/numerical tests only on Outpost35/emulator-5582 or the specifically registered owner-authorized Pixel 10 Pro. Preserve Brújula/emulator-5580. Host builds, static inspection and report analysis are allowed; host inference and other phones are not.
- Outpost is a general offline knowledge assistant. OSM supplies place knowledge for chat; maps/navigation are outside scope. Equipment actions and deferred online queues remain proposals.
- Preserve pins, original reference arithmetic/fallback, raw failed evidence, private data and global phone settings. No remote publication or push is authorized by these records.

## Delivery and runtime

Local APK: `dist/outpost-0.17.0-user-test.apk`, SHA256 `6ae162f07dc719540b2256d249b312e56d2e70e45d22794b8dc60f8cf8f66ca2`. Test APK: `20da12c961e265229b1b3996fc604dd55884c355e4bc2d02f47e22c549f641d3`. [Manifest](../evidence/releases/0.17.0/manifest.json) binds exact final tests. All earlier frozen APKs remain intact.

The exact Pixel/Bonsai4 preset is **6 decode/6 prompt workers, four logical attention workers, batch 128, matrix width 8, rows 1/4, chunks 32/0, persistent pool, no affinity**. The normal attention tensor/mask stays unchanged; four workers perform original arithmetic while two synchronize. Multi-query prefill keeps its original operation. Three counterbalanced pairs per question preserve all logits/tokens and reduce decode about 20%, total time 13–15%. CPU time increased 11–12%; energy is unmeasured. Pilot outliers and rejected verification-only timing remain recorded.

`NativeEngine.Configuration` has 14 fields including `attentionThreads`; old constructors default it to 0. Value 4 requires 6/6 and speculation 0, joins cache identity and profile persistence, and uses ARM key `q2-arm-dot-attn4-v1`. MainActivity must use `Profile.configuration(!m.lowMemory)`. Single-query prefill tails also need the fixed attention policy. Changing the pinned backend requires checking its barrier contract. I8MM/VNNI remain unimplemented; hardware capability is not an executed kernel.

The current source/build APKs are the validated 0.17 pair. The Pixel has been restored to original frozen 0.16 app `095c5c56995e3de9947d0125a0757387c429191cf0602700f7d5b9b8c036239f` / test `92d2c533b1aaff6267c1607b46f6219351589da83bf62ea3b1b1fa4f0c0fa0d0`, with original stores/settings. Outpost35 is restored too. Therefore `-SkipInstall` must reject the phone until the matching new pair is installed. No test or install workflow should remain active at this checkpoint.

## Safe resumption

1. Inspect Git status, `.local/overnight-progress.json` and `.local/pixel-ui-active.json`. Restore a pending phone journal with `test-pixel-chat-isolated.ps1 -RestoreOnly` before other app work. One workflow per target.
2. Read [Pixel protocol](pixel10-testing.md) or [emulator guide](emulator-runbook.md). Resolve tools from ignored `.local/developer-settings.json`; Python is `C:/Users/nicoa/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`, with `PYTHONUTF8=1`. Do not recopy verified GB models.
3. Build via `scripts/build.ps1 -Offline`, verify `eval/check-build.ps1`, and publish only a new artifact version. Never replace frozen bytes under an existing version.
4. Research comparison phases are `decode-six-pilot` and `decode-six-confirm`; production-state checks use `decode-six-lifecycle`. Always use the Pixel isolation wrapper. Model admission uses `-CandidateAdmission`; actual chat uses `-Generate`. The wrapper restores private stores in finally. The archived 0.17 driver also restores older debug APKs with `-d`; inspect live hashes before reusing any backup.
5. Test visibility uses standard show-when-locked/keep-screen-on flags and an empty isolated store. It never disables authentication or opens original private data. Preserve global radios/power/security settings.
6. Final validation: 34 lifecycle, 60 complete-answer integration, 51 UI and 23 model-admission controls, plus x86 numeric/dispatch/matrix/real-decoder regression. Source/APK receipt and raw reports own exact identities.

## Product contracts

Chat is home; Settings owns file/folder import, inspection/removal, pinned model setup and confirmed New chat. App/library starts empty; fixtures stay in the test APK. `ChatPrompt` v1.1 is separate from evidence-only `ResearchPrompt`. Context 2048, output 192, deadline 120s; product speculation 0.

Strict UTF-8 TXT/Markdown/CSV, text-bearing PDF and OSM XML/Overpass JSON are supported. Folder selection recurses with per-item outcomes/cancellation. Imports are snapshots: unchanged source+bytes skip, changed versions stay separately searchable. Packs retain their separate activation semantics. Library schema 5 preserves source identities and locators. OSM named-place/category/proximity answers are bounded deterministic knowledge queries, not a navigation engine.

## Research and remaining work

[Attention repair](attention-parity-2026-10-01.md) and [initial speculation study](speculation-pixel-2026-10-01.md) preserve the path to this change. Verification modes 2/3 are research only. TND-03 must measure actual proposals against the **new normal 6/6 + attention 4 baseline** and preserve arithmetic in both serial and verification routes; older 4/6 cost tables cannot justify adoption. No new drafter/controller or product speculation was enabled. A local draft cost-policy sketch was not integrated or executed.

Next performance candidates include reducing synchronization waste while preserving the four-way reduction, and testing guarded I8MM prefill kernels with identical per-lane accumulation. These are pending, not measured improvements. Keep latency, CPU time, storage effects and actual energy separate. [Roadmap](roadmap.md) owns other task IDs.

Known content failures remain: model facts, row/range filtering, applicability and citation binding. Continue structured-source operations, representative personal/regional data, real picker/folder/OSM coverage, process-death/storage-pressure recovery and broader device/energy tests. OCR/Office/ZIM, trained MTP/Engram and other models remain separate work. Pixel page size 4096; 16KiB/GrapheneOS unvalidated. Keep the fixture provider registered; cleanup hides roots and revokes grants rather than disabling it.
