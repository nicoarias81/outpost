# Engineering handoff — start here

Updated **2026-09-30**, baseline **Outpost 0.14.0 / code 16**. The 0.13 place-query slice adds deterministic place answers, a v6 evaluation identity and full-ABI validation. Version 0.14 adds guarded row reuse and phase-separated runtime profiles. The general chat prompt/model pins remain unchanged. [Kernel record](kernel-rows-0.14.md) separates rejected candidates from the measured prefill-only profile. Read the validation record for precise artifact/run identities. Read [current state](current-state.md), [validation](validation-0.14.md), then the owning domain guide in [the index](index.md).

## Scope and persistent constraints

Delivery checkpoints: OSM implementation `a804044`, evidence `9f3d36c`; kernel implementation `a1019a3`, evidence `37f009c`. The local0.14 artifact is checksum-verified and installed on Outpost35 with the measured Bonsai4 profile. All synthetic/public test imports and test turns are cleaned by their suites; no phone execution or remote publication occurred.

- Work in `E:/projects/outpost`, branch `codex/outpost`, app `dev.outpost.app`, native `outpost_engine`. Check Git state and ongoing work first; older C-workspace delivery/staging folders are historical, not current source.
- English UI, maintained docs/comments and primary fixtures. Preserve original imported/historical language and deliberate multilingual probes.
- Execute models/numerical/runtime tests only in **Outpost35 / emulator-5582**. Host build/static/header/hash checks are allowed. Preserve old Brújula and emulator-5580. Physical Pixels remain unauthorized; ARM64 is packaged but not runtime-validated.
- The product serves traveler, farmer, field engineer, mountaineer and driver question types. Vitalik's restaurant example is not a vegan/restaurant specialization. Public, regional and personal knowledge are complementary; inference and knowledge remain separate technical domains.
- Discovery about reflection, reconnect tasks and equipment is not authorization to send, connect or operate external systems. Preserve pinned dependencies, reference fallback, private data and failed evidence. Public distribution/license/signing remain separate; the existing remote is private.

## Product baseline

Chat is home. One send retrieves and streams; the local conversation persists, with two bounded recent completed/limited turns as model context. `ChatPrompt` v1.1 permits general knowledge without fabricating personal/live sources; `ResearchPrompt` remains an independent evidence-only evaluation protocol. Product chat always sets speculation depth 0.

Settings owns Add file/Add folder, document reading/removal, locked offline-model import/selection and confirmed New chat. No prototype tabs, mock knowledge, benchmark metrics, Kev or speculation controls remain in the product. New chat deletes the conversation, not documents. Generator weights are separate from the APK.

Supported files: strict UTF-8 text/Markdown/CSV, bounded text-bearing PDF, OSM XML/Overpass JSON. Folders recurse through readable subdirectories with progress, stop/retry, per-item outcomes and retained paths. Unchanged source/format/raw-byte identity skips duplicates; changed snapshots remain **separately searchable**, without automatic newest-version selection. Current `.json` picker input means Overpass, not a knowledge pack. Existing developer packs retain their distinct atomic activation/version-retention contract.

Library schema 5 adds `osm_features` after metadata/packs (2), precise seed cleanup (3) and import origins (4). Upgrades preserve imported/edited data. PDF originals/page text and OSM exact files/features remain inspectable. The PDF locator hash is extracted-page serialization; the separate import binding stores raw PDF bytes' hash. OSM locators use the original extract hash. See [knowledge](knowledge-base.md), [chat/PDF](chat-beta.md), [folders](folder-import.md) and [OSM](osm-import.md).

OSM is a knowledge source for chat questions such as where a named park is, which restaurants are around a stated landmark, and where museums can be found. The place feature introduced in 0.13 resolves exact recorded names/aliases, categories by recorded locality, and proximity to a named reference with deterministic distances/order. These direct chat answers need no generator; they carry inspectable OSM citations and visible ambiguity/conflict/missing-data results. The bounded scan is not a full spatial database or unrestricted language interpreter. Map display/navigation/routing (K-07) are outside this feature and must not block it; GPS is unnecessary for a named-reference query. [Place-query scope](osm-place-queries.md) and ADR-028 own this clarification. Approximate centers and declared bounds do not establish an entrance or complete coverage.

## Evidence and runtime state

The local artifact is `dist/outpost-0.14.0-user-test.apk` with checksum sidecar; exact app/test hashes and build fingerprints are in [validation](validation-0.14.md). Place checks passed 63, including three frozen real-data workflows and an actual absent-model chat context. Existing import/chat/knowledge regressions remain required and are recorded by exact APK identity. No new model-generation result is implied by deterministic place answers. Lint: 0 errors, 3 existing upstream BouncyCastle warnings. The user APK contains neither fixture provider/receiver nor mock library/data.

Owned native row-reuse kernels changed in 0.14; backend and model pins are unchanged. The `q2-row-v3-phase` profile key includes device/CPU, app version and model hash. The validated Outpost35/Bonsai 4B setting is 4/4 threads, batch 128, width 4, multi-column rowTile 2 and single-column decodeRows 1. Other/unmatched keys default to at most 4 threads, batch 128, width 1 and rows 1/1. The 0.9 profile remains historical. No custom VNNI/ARM kernel, trained MTP head or dual-model drafter has been integrated.

Latest recorded cleanup left synthetic imports/turns removed and Bonsai 4B selected. This is a checkpoint, not a live guarantee: recheck before work. Do not reimport GB of weights merely to resume.

## Resume checklist

1. Inspect Git status/diff and relevant source. For docs-only changes, verify links/source/receipt; do not build or run models.
2. For runtime work read the [emulator runbook](emulator-runbook.md): confirm AVD name and serial, boot, offline state, installed APK hashes and model readiness. Only one test/install workflow owns the emulator at a time.
3. Resolve tools from ignored `.local/developer-settings.json`. Existing host SDK: `C:/Users/nicoa/Documents/ChatGPT/muna 7/work/bug-hunter-toolchain/sdk`. Python: `C:/Users/nicoa/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`; set `PYTHONUTF8=1`. Reconfigure actual paths on a new host; caches/AVD/models are not in Git.
4. After code changes use `scripts/build.ps1 -Offline` with populated caches. It builds both ABIs/test APK and checks dependency bytes/source fingerprints. `eval/check-build.ps1` verifies current receipt; local publication requires matching production inputs and bytes.
5. Choose focused checks in [development](development.md). OSM/folder/chat/knowledge suites default to no model and use unique runs; `-Generate` is explicit for OSM/chat. Archive fixed-output legacy research evidence before rerunning. Keep behavior and evidence commits separate.

## Test provider recovery

The test-only `FolderDocumentsProvider` stays registered and protected by MANAGE_DOCUMENTS. `FixtureGrantReceiver` synchronously acknowledges ordered read-grant/revoke broadcasts, and `FixtureGrants` requests grants after instrumentation starts and checks readiness. Cleanup revokes grants and hides roots using its active flag. **Do not disable/re-enable the component between suites or use the removed grant Activity**: re-registration caused a provider-unavailable timeout. No production permission expansion is needed. The failed/interrupted runs and final fix are preserved in [validation](validation-0.14.md).

## Model-selection research checkpoint

The owner requested alternatives to Bonsai and exploration of Engram. The [model survey](model-alternatives.md) prioritizes LFM2.5-1.2B QAD and Qwen3.5-2B, then LFM2.5-2.6B and Gemma 4 E2B. Public revisions/artifact metadata and pinned-backend static compatibility are recorded; no candidate weights were downloaded or executed. E-07 covers research admission; E-05 remains the answer comparison. New profiles need template/sampler and hybrid-state lifecycle validation despite backend architecture support. Production pins, selected model, APK and emulator state were not changed by this research.

[Engram](engram-review.md) is trained conditional memory, distinct from imported evidence and mission memory. The inspected official demo is not a small trained checkpoint; X-07 is conditional. Published LFM DSpark and Gemma assistants make X-01 more concrete for those targets, without providing a Bonsai MTP head or Android speed evidence. Preserve the emulator-only constraint.

## Answer-pipeline observability research

The [NeMo Relay review](nemo-relay-review.md) pins upstream source at `872972c600599a6e9085c4e1799d07b980a1dab5` and maps its lifecycle-tracing method onto Outpost. E-08 proposes local correlated traces for route selection, retrieval fallback, source clipping, native generation, persistence and visible completion, with separate quality review. No Relay SDK, exporter, new trace implementation or network endpoint was added. Existing native timings/run identities remain intact; E-07 model admission can proceed independently.

## Next work and known gaps

Follow [roadmap](roadmap.md), preserving task IDs. E-06 is fixed and v7 declares both packaged ABIs. Six host-only identity controls cover valid/reordered and missing/extra/duplicate/malformed sets. The obsolete K-07 dependency is removed from the regional fixture; its broader time/preference recommendation task remains blocked. P-05/P-06 now have shape/graph/model evidence and a bounded prefill-only adoption. Further timing/pressure/ARM work remains. Do not infer a kernel gain from deterministic place replies or extrapolate the emulator profile to a phone.

Prioritize realistic user tasks and source/answer failures; model provisioning and bounded-chat UX; original-file/process-death/storage recovery; complex PDF and real-region OSM evaluation. Typed arithmetic, editable mission context, Office/OCR/ZIM and broader geographic tools remain pending. Phone trials require explicit scope expansion. No field-quality, peak-RAM, phone-speed, battery/thermal or bounty-compliance claim follows from current checks.

## New optimization reference

The [DeepGEMM-Ascend review](deepgemm-ascend-review.md) pins upstream commit `8491bbb4b8c02a094a2318965f50c70438a3e73c` and compares it with Outpost `c9181ec`. The upstream library remains a static reference and was not integrated. The subsequent [owned row-kernel experiment](kernel-rows-0.14.md) measured actual shapes/model requests, rejected grouped decode, and adopted only the separately confirmed multi-column policy. Actual Bonsai matrix shapes and source provenance are saved as static inspection records. Do not convert the model to FP4, adopt server cache constants or enable speculation on the strength of this review. All execution constraints remain active.

## Historical provenance

0.9 implementation `c51c73f`, evidence integration `449bca6`, line-ending protection `c4deeb9`; 0.10 implementation `aca6d97`, evidence `6091111`; 0.11 implementation `b0f944a`, evidence `acbff0c`. The earlier permission-review quota interruption was resolved before 0.10. Never restore stale staging over this checkout.

The native-controller review of `a52f2b3` remains unacknowledged after empty/length-limited reviewer output. Tested 0.9 width-report corrections do not retroactively grant formal review authority. Historical reports/failed outputs retain their original identities and language, with navigation notices where their old instructions differ from current practice.

## Current validation/resumption

The 0.14 validation record owns final app/test hashes, kernel runs and integration regressions. Use `test-rows.ps1 -Phase graphs` for numerical/shape controls; `-Phase model` is exploratory combined-policy tuning; `-Phase confirm` compares the phase-separated candidate with the retained kernel using complete answers. `-ApplyProfile` is allowed only with confirmation and saves settings only after timing/parity/lifecycle gates pass. Runtime scripts must not run concurrently.

The combined-policy confirmation `rows-confirm-20260930T194852Z-d585992f` was intentionally stopped after the long-answer decode regression became clear. Its "Process crashed" instrumentation line records that targeted force-stop, not an unexplained native fault. Raw partial results remain intact; no profile was applied from it. Final confirmation `rows-confirm-20260930T195825Z-d6309daa` completed and applied the phase-separated profile.
