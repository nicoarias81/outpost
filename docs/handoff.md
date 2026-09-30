# Engineering handoff — start here

Updated **2026-09-30**, baseline **Outpost 0.12.0 / code 14**. Implementation `1d311c7`; evidence `dbdd0b6`; release handoff `23231b3`. The latest documentation consolidation changes no app/runtime/model/fixture bytes. Read [current state](current-state.md), [validation](validation-0.12.md), then the owning domain guide in [the index](index.md).

## Scope and persistent constraints

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

OSM is a knowledge source for chat questions such as where a named park is, which restaurants are around a stated landmark, and where museums can be found. Current 0.12 supports lexical feature/tag lookup and recorded coordinates/dates/attribution; named-place resolution and structured category/proximity queries are the next K-06 slice. Map display/navigation/routing (K-07) are outside this feature and must not block it; GPS is unnecessary for a named-reference query. [Place-query scope](osm-place-queries.md) and ADR-028 own this clarification. Approximate centers and declared bounds do not establish an entrance or complete coverage.

## Evidence and runtime state

The local artifact is `dist/outpost-0.12.0-user-test.apk` with checksum sidecar; exact app/test hashes and build fingerprints are in [validation](validation-0.12.md). Final model-free suites: OSM 39, folders 30, chat 35, knowledge 60. One real synthetic-source OSM answer used identical app bytes and an earlier test APK; do not claim a final-test-APK model rerun. Lint: 0 errors, 3 existing upstream BouncyCastle warnings. The user APK contains neither fixture provider/receiver nor mock library/data.

Native kernels and locked models remain unchanged since the recorded optimization baseline. A runtime-profile key includes Android/device/CPU, app version, kernel identifier and model hash, not arbitrary same-version source changes. An unmatched key falls back to at most 4 threads, batch 128, width 1. The measured 0.9 width 4 result is historical, not current calibration. No custom VNNI/ARM kernel, trained MTP head or dual-model drafter has been integrated.

Latest recorded cleanup left synthetic imports/turns removed and Bonsai 4B selected. This is a checkpoint, not a live guarantee: recheck before work. Do not reimport GB of weights merely to resume.

## Resume checklist

1. Inspect Git status/diff and relevant source. For docs-only changes, verify links/source/receipt; do not build or run models.
2. For runtime work read the [emulator runbook](emulator-runbook.md): confirm AVD name and serial, boot, offline state, installed APK hashes and model readiness. Only one test/install workflow owns the emulator at a time.
3. Resolve tools from ignored `.local/developer-settings.json`. Existing host SDK: `C:/Users/nicoa/Documents/ChatGPT/muna 7/work/bug-hunter-toolchain/sdk`. Python: `C:/Users/nicoa/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`; set `PYTHONUTF8=1`. Reconfigure actual paths on a new host; caches/AVD/models are not in Git.
4. After code changes use `scripts/build.ps1 -Offline` with populated caches. It builds both ABIs/test APK and checks dependency bytes/source fingerprints. `eval/check-build.ps1` verifies current receipt; local publication requires matching production inputs and bytes.
5. Choose focused checks in [development](development.md). OSM/folder/chat/knowledge suites default to no model and use unique runs; `-Generate` is explicit for OSM/chat. Archive fixed-output legacy research evidence before rerunning. Keep behavior and evidence commits separate.

## Test provider recovery

The test-only `FolderDocumentsProvider` stays registered and protected by MANAGE_DOCUMENTS. `FixtureGrantReceiver` synchronously acknowledges ordered read-grant/revoke broadcasts, and `FixtureGrants` requests grants after instrumentation starts and checks readiness. Cleanup revokes grants and hides roots using its active flag. **Do not disable/re-enable the component between suites or use the removed grant Activity**: re-registration caused a provider-unavailable timeout. No production permission expansion is needed. The failed/interrupted runs and final fix are preserved in [validation](validation-0.12.md).

## Next work and known gaps

Follow [roadmap](roadmap.md), preserving existing task IDs. Immediate technical follow-up E-06: fixture v5 declares only x86_64 and the validator reads only the first Gradle ABI string; repair the check and issue a new manifest revision without altering frozen runs. Its current PASS is not complete ABI verification. The next manifest revision must also remove the obsolete K-07/map-routing blocker from the regional place-query fixture under ADR-028; preserve historical manifests/runs and keep its actual missing implementation/corpus/executor status explicit.

Prioritize realistic user tasks and source/answer failures; model provisioning and bounded-chat UX; original-file/process-death/storage recovery; complex PDF and real-region OSM evaluation. Typed arithmetic, editable mission context, Office/OCR/ZIM and broader geographic tools remain pending. Phone trials require explicit scope expansion. No field-quality, peak-RAM, phone-speed, battery/thermal or bounty-compliance claim follows from current checks.

## New optimization reference

The [DeepGEMM-Ascend review](deepgemm-ascend-review.md) pins upstream commit `8491bbb4b8c02a094a2318965f50c70438a3e73c` and compares it with Outpost `c9181ec`. This is static research, not an integrated backend or measured improvement. P-05 proposes a current operation/shape baseline; P-06 proposes activation reuse across output rows, including decode. Actual Bonsai matrix shapes and source provenance are saved as static inspection records. Do not convert the model to FP4, adopt server cache constants or enable speculation on the strength of this review. All execution constraints remain active.

## Historical provenance

0.9 implementation `c51c73f`, evidence integration `449bca6`, line-ending protection `c4deeb9`; 0.10 implementation `aca6d97`, evidence `6091111`; 0.11 implementation `b0f944a`, evidence `acbff0c`. The earlier permission-review quota interruption was resolved before 0.10. Never restore stale staging over this checkout.

The native-controller review of `a52f2b3` remains unacknowledged after empty/length-limited reviewer output. Tested 0.9 width-report corrections do not retroactively grant formal review authority. Historical reports/failed outputs retain their original identities and language, with navigation notices where their old instructions differ from current practice.
