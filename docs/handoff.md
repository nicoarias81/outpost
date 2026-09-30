# Engineering handoff — start here

Updated 2026-09-30. **Outpost 0.11.0 / code13**, moving toward mobile user tests. Previous 0.10 implementation: `aca6d97`; prior evidence: `6091111`. Current implementation: `b0f944a`; current evidence: `acbff0c`. The folder-import work is described below. Read [current state](current-state.md), [chat/document design](chat-beta.md), [validation](validation-0.11.md), then the relevant domain guide. The user now wants the app to open on chat, import files through Settings, and remove mock/demo/prototype UI.

## Canonical work and constraints

- Work in `E:/projects/outpost`, branch `codex/outpost`; app `dev.outpost.app`, native `outpost_engine`. Check actual Git status/history and other work before editing. Preserve old Brújula and its emulator.
- English product UI, maintained docs/comments and primary fixtures. Historical and deliberately bilingual evidence retains its original language.
- All model/numerical execution stays in Outpost35 / `emulator-5582`. Host build/static inspection is allowed; physical Pixel execution remains outside scope. ARM64 is now compiled and packaged, not runtime-validated.
- Preserve dependency/model hashes, fallback and failed evidence. Public/reference, regional and personal content are complementary sources; inference and knowledge remain the two technical domains.
- Vitalik's example describes contextual question types, not a vegan/restaurant vertical. Original contexts remain traveler, farmer, field engineer, mountaineer and driver. Discovery suggestions about reflection/actions/equipment are not external-action authorization.
- Do not push, publish publicly, operate equipment/accounts or erase user data based on this handoff. Existing remote is private. Code license, release signing and broader user/device distribution remain separate decisions.

## Folder import added in 0.11

Settings and Documents now offer **Add folder** using Android's native directory picker. `FolderImporter` traverses subdirectories, isolates per-item failures, reports progress/results and supports stop/retry. `DocumentImporter` shares validation with single-file import. Schema4 preserves existing rows and adds transactional source/format/original-byte identity bindings, preventing duplicate unchanged imports. Changed bytes create another snapshot; this is not sync. Relative paths distinguish equally named files. Read [folder-import](folder-import.md) for limits, cancellation/recovery and exact semantics.

Use `scripts/test-folders.ps1`, then the no-generation chat/import and knowledge regression suites. The test-only DocumentsProvider needs genuine grants after instrumentation starts; the script/test lifecycle revokes them and disables the provider afterward. Do not add MANAGE_DOCUMENTS or broad storage permissions to the product APK. No native/prompt change or new phone/model performance claim is part of this feature.

## Existing chat baseline

The home screen is now a real local conversation: one send action performs retrieval and streaming generation; recent turns provide bounded follow-up context, and history persists privately. Settings owns TXT/CSV/Markdown/PDF import, document opening/removal, verified offline-model setup and New chat. There is no Explore/Library/Status bottom navigation, mock corpus, reviewer/speculation control or performance dashboard in the product UI. Unused prototype resources were removed.

Library schema3 removes exact unchanged historical seed IDs/hashes while preserving imports, packs and edited records. Fresh databases start empty. Seed JSON and retrieval controls moved to the test APK. PdfBox-Android extracts page text; exact originals are copied privately and rendered with Android PdfRenderer. Scanned-only/encrypted/invalid PDFs receive import errors. Limits and recovery gaps are explicit in [chat-beta](chat-beta.md).

The native kernel implementation and locked weights are unchanged. Chat uses `ChatPrompt`; evidence-only research retains `ResearchPrompt`. Sources are optional in general chat and remain inspectable when supplied. The chat always disables experimental speculation. New builds have independent runtime-profile keys; do not silently reuse an old profile or infer ARM timings from x86.

## Resume and validate

1. Read [validation](validation-0.11.md) for exact artifact hashes, passed/failing runs, prompt identities and scope. Historical 0.9 scores do not score the new chat prompt.
2. Read [emulator runbook](emulator-runbook.md). Verify AVD name as well as serial, completed boot, offline settings, installed app/test hashes and model readiness. The old `emulator-5580` must remain untouched.
3. For UI/import/migration use `scripts/test-chat.ps1`; add `-Generate` only when actual conversation behavior changed. Use `scripts/test-knowledge.ps1` for locator/CSV/transaction regressions. New run folders are unique; archive fixed-output legacy tests before rerunning.
4. SDK/JDK/Gradle/Python are configured in ignored `.local/developer-settings.json`. On this host the SDK is `C:/Users/nicoa/Documents/ChatGPT/muna 7/work/bug-hunter-toolchain/sdk`. Python is `C:/Users/nicoa/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`; set `PYTHONUTF8=1` for host scripts. Do not reimport several GB of already verified model files unnecessarily.
5. `scripts/build.ps1 -Offline` requires populated caches and checks source fingerprints plus PDF dependency byte identities. Local publication requires the successful build receipt. Commit behavior and regenerated evidence separately.

## Source entry points

`MainActivity`: chat/Settings/documents and lifecycle. `FolderImporter`/`DocumentImporter`: bounded SAF traversal and shared one-file ingestion. `ChatStore`: private conversation DB. `ChatPrompt`: bounded follow-up/source request. `Library`/`Evidence`/`CsvTable`: source identities, schema migration, retrieval and removal. `PdfImporter`: bounded extraction. `ChatChecks`/`KnowledgeChecks`: current product and data regression. `TestLibrary`: explicit isolated mock corpus for historical research; never seed product storage.

The old search/reviewer/speculation UI tests are retired or routed to current chat checks. Native generation, Kev, kernel/cache/speculation controls remain separate developer tests. Full ARM compilation is new, but custom ARM/VNNI kernels and device execution still need work. Locked Bonsai 4B has zero MTP heads.

## Provenance and review history

0.9 implementation: `c51c73f`. Its evidence was preserved in `449bca6`; exact evidence line endings are protected by `c4deeb9`. The prior automatic-review quota block was resolved before this work. Prepared 0.9 docs were applied; the old C-workspace delivery remains a historical snapshot, not the current working source. Do not copy old staging folders over E.

The earlier native-controller review of `a52f2b3` remains unacknowledged after `reviewer-empty-output` failures. 0.9 fixed practical width-report ambiguity with tested explicit metrics, but that does not retroactively grant formal controller authority. Keep behavior and generated evidence commits separate to avoid the previous review-context overflow.

## Next work

Prioritize observed conversation/source failures and realistic user tasks, then model provisioning and mobile-device validation when explicitly authorized. Evaluate long/complex PDF extraction, claim-to-source alignment, missing/conflicting information, bounded context and recovery. OCR/Office/ZIM/OSM, arithmetic tools and editable mission context remain pending. Record facts and failures, not just green harness counts. No field-quality, peak-RAM, battery, thermal, phone-speed or bounty compliance claim is established.
