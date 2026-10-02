# Engineering handoff — start here

**Release preparation:** source is now **0.19.0-rc1 / code 21**. See the [production candidate, build/signing workflow and remaining gates](release-0.19.md). The frozen 0.18 user-test APK and its performance evidence remain unchanged; the candidate is not a signed production delivery.

Updated 2026-10-02. Current local delivery: **Outpost 0.18.0 / code 20**, canonical repo `E:/projects/outpost`, branch `codex/outpost`, app `dev.outpost.app`. Read [validation](validation-0.18.md), [capability inventory](current-state.md), [runtime](inference-runtime.md) and [documentation index](index.md).

## Scope and constraints

- English app/docs/comments/primary fixtures; preserve imported language and historical evidence.
- Inference/numerical tests only on Outpost35/emulator-5582 or the specifically registered owner-authorized Pixel 10 Pro. Preserve Brújula/emulator-5580. Host builds, static inspection and report analysis are allowed; host inference and other phones are not.
- Outpost is a general offline knowledge assistant. OSM supplies place knowledge for chat; maps/navigation are outside scope. Equipment actions and deferred online queues remain proposals.
- Preserve pins, original reference arithmetic/fallback, raw failed evidence, private data and global phone settings. The owner authorized source pushes and GitHub release preparation on 2026-10-02; preserve current private repository visibility.

## Current I8MM addition

0.18 enables `matrixKernel=1` only on the exact Pixel/Bonsai4 compiled/ISA-qualified profile, with six graph workers and `attentionThreads=4`. Vector/single-column decoding remains DotProd; matrix-node audits report actual I8MM use. 65,029,512 numeric comparisons and the final same-APK original-policy versus current-policy comparison pass. Use the current validation for combined measured gains; do not multiply 0.17 and I8MM-only ratios. Weight/model/backend/sampler/precision and limits stay pinned.

Final phases: `i8mm-numeric`, `i8mm-lifecycle`, `stack-confirm`, `-CandidateAdmission`, `-Generate`, plus x86 regression. The 0.18 archive retains its validated receipt; current build outputs belong to release preparation. The Pixel is restored to the original 0.16 pair, so -SkipInstall must reject it until matching current APKs are installed. See `.local/overnight-progress.json` for any later experiment; no work should be active at this release checkpoint.

## Delivery and runtime

Local APK: `dist/outpost-0.18.0-user-test.apk`, SHA256 `450c88214a945d7fdc231a29e1890c23318f905be257ae5708c9806147e67f3c`. Test APK: `0b1ffa84b246c3a30eb4f040062c3338fe7b3500dc7f8abf019f8c4da74fbcd0`. [Manifest](../evidence/releases/0.18.0/manifest.json) binds exact final tests. All earlier frozen APKs remain intact.

The exact Pixel/Bonsai4 preset is **6 decode/6 prompt workers, four logical attention workers, I8MM matrix policy 1, batch 128, width 8, rows 1/4, chunks 32/0, persistent pool, no affinity**. Attention arithmetic/tensor/mask stay original. Eligible multi-column matrices use I8MM; one-column decoding stays DotProd. The final same-APK comparison preserves all logits/tokens and lowers paired median total time about 20–22%, decode 22–24% and prefill 13–17% versus the original 0.16 policy. Process CPU time rises about 3–4%; energy is unmeasured. All outliers and earlier rejected interpretations remain recorded.

`NativeEngine.Configuration` has 15 fields including `attentionThreads` and `matrixKernel`; old constructors default added fields to 0. `attentionThreads=4` requires 6/6 and speculation 0; `matrixKernel=1` requires that fixed-attention profile. Both join cache identity and profile persistence; the ARM key is `q2-arm-i8mm-attn4-v1`. MainActivity must use `Profile.configuration(!m.lowMemory)`. Single-query prefill tails also need the fixed attention policy. Changing the pinned backend requires checking its barrier contract. I8MM matrix execution is now admitted under explicit guards; VNNI remains unimplemented. Hardware capability alone is not an executed kernel.

The frozen 0.18 pair remains the performance reference; current build outputs are 0.19.0-rc1 debug and release candidates. The Pixel has been restored to original frozen 0.16 app `095c5c56995e3de9947d0125a0757387c429191cf0602700f7d5b9b8c036239f` / test `92d2c533b1aaff6267c1607b46f6219351589da83bf62ea3b1b1fa4f0c0fa0d0`, with original stores/settings. Outpost35 is restored too. Therefore `-SkipInstall` must reject the phone until the matching new pair is installed. No test or install workflow should remain active at this checkpoint.

## Safe resumption

1. Inspect Git status, `.local/overnight-progress.json` and `.local/pixel-ui-active.json`. Restore a pending phone journal with `test-pixel-chat-isolated.ps1 -RestoreOnly` before other app work. One workflow per target.
2. Read [Pixel protocol](pixel10-testing.md) or [emulator guide](emulator-runbook.md). Resolve tools from ignored `.local/developer-settings.json`; Python is `C:/Users/nicoa/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`, with `PYTHONUTF8=1`. Do not recopy verified GB models.
3. Build via `scripts/build.ps1 -Offline`, verify `eval/check-build.ps1`, and publish only a new artifact version. Never replace frozen bytes under an existing version.
4. Current comparisons use `i8mm-model`, `i8mm-confirm` and `stack-confirm`; numeric/lifecycle checks use `i8mm-numeric` and `i8mm-lifecycle`. Older `decode-six-*` phases belong to the worker-only study. Always use the Pixel isolation wrapper. Model admission uses `-CandidateAdmission`; actual chat uses `-Generate`. The wrapper restores private stores in finally. The archived product driver also restores older debug APKs with `-d`; inspect live hashes before reusing any backup.
5. Test visibility uses standard show-when-locked/keep-screen-on flags and an empty isolated store. It never disables authentication or opens original private data. Preserve global radios/power/security settings.
6. Final validation: 27 matrix lifecycle controls, 140 combined-answer controls, 54 UI and 23 model-admission controls, plus x86 numeric/dispatch/matrix/real-decoder regression. Source/APK receipt and raw reports own exact identities.

## Product contracts

Chat is home; Settings owns file/folder import, inspection/removal, pinned model setup and confirmed New chat. App/library starts empty; fixtures stay in the test APK. `ChatPrompt` v1.1 is separate from evidence-only `ResearchPrompt`. Context 2048, output 192, deadline 120s; product speculation 0.

Strict UTF-8 TXT/Markdown/CSV, text-bearing PDF and OSM XML/Overpass JSON are supported. Folder selection recurses with per-item outcomes/cancellation. Imports are snapshots: unchanged source+bytes skip, changed versions stay separately searchable. Packs retain their separate activation semantics. Library schema 5 preserves source identities and locators. OSM named-place/category/proximity answers are bounded deterministic knowledge queries, not a navigation engine.

## Research and remaining work

[Attention repair](attention-parity-2026-10-01.md) and [initial speculation study](speculation-pixel-2026-10-01.md) preserve the path to this change. Verification modes 2/3 are research only. TND-03 must measure actual proposals against the **new normal 6/6 + attention 4 + I8MM baseline** and preserve arithmetic in both serial and verification routes; older 4/6 cost tables cannot justify adoption. No new drafter/controller or product speculation was enabled. A local draft cost-policy sketch was not integrated or executed.

I8MM prefill is now implemented and validated; retain its original-lane accumulation and in-place activation contract. Next candidates include reducing fixed-attention synchronization waste and broader hardware/energy work. Keep latency, CPU time, storage effects and actual energy separate. [Roadmap](roadmap.md) owns other task IDs.

Known content failures remain: model facts, row/range filtering, applicability and citation binding. Continue structured-source operations, representative personal/regional data, real picker/folder/OSM coverage, process-death/storage-pressure recovery and broader device/energy tests. OCR/Office/ZIM, trained MTP/Engram and other models remain separate work. Pixel page size 4096; 16KiB/GrapheneOS unvalidated. Keep the fixture provider registered; cleanup hides roots and revokes grants rather than disabling it.

## Release candidate resumption

Start with [0.19 release closure](release-0.19.md). The current release receipt/audit is archived under `evidence/releases/0.19.0-rc1`; unsigned local APK/AAB are frozen in dist. Separate non-debuggable `dev.outpost.app.releaseqa` and its test package remain on Outpost35 with synthetic data. Original app/test APKs and radios are unchanged; Pixel was not touched in this pass. Debug build and research assets remain available, v12 fixtures are current. No active device workflow remains after the recorded final run. Channel, code-license policy and production signing identity require owner decisions. Final signed-device acceptance, field quality and reliability gates remain open; do not resume kernel experiments as the next release task.

## GitHub distribution and Pixel release acceptance

The owner authorized source upload and GitHub Releases on 2026-10-02. The existing repository remains private. [Expanded Pixel acceptance](release-acceptance-2026-10-02.md) records 73 accepted controls and the remaining distinction between QA and a production-signed APK. [First-user installation](getting-started.md) describes exact model preparation. The production application ID/key proposal is still awaiting the owner; do not create a key, change app identity or publish an unsigned artifact as production. The dedicated QA installation is synthetic and separate from the original phone app.
