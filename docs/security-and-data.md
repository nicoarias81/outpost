# Local data, provenance and trust boundaries

Current product controls through **Outpost 0.18.0**, with 0.19.0-rc1 release preparation, checked against the implementation. This is not a security certification.

## Application boundary

The [product manifest](../app/src/main/AndroidManifest.xml) declares no permissions, disables application backup and has no service, analytics, account, cloud inference, synchronization or arbitrary tool executor. The launcher Activity is exported for normal app launch. Documents/models enter through Android's picker and become app-private copies. External document providers may use their own network connection; offline availability requires their bytes to be locally readable at import time.

The frozen 0.18 user-test candidate is debug-signed. The [0.19 production candidate](release-0.19.md) is explicitly non-debuggable, has a seven-function native export allowlist and rejects experimental generation policies; research head assets are debug-only. Test instrumentation remains a separate APK. The separate release QA package uses a debug certificate solely for testing. Production signing, exact signed-device acceptance and distribution remain open. Backup settings are not a promise of protection against device compromise or all data loss.

## Input controls

| Input / risk | Current boundary | Remaining limitation |
|---|---|---|
| Incorrect model/layout | Exact size/hash allowlist for pinned profiles before use | Backend/parser updates still require compatibility review |
| Text/CSV/pack expansion | Strict Unicode, byte/record/cell/JSON structure limits; literal CSV cells | No arbitrary Office/archive support |
| PDF | Bounded original/page/text/scratch sizes; pinned extraction; original page inspection | No OCR or guaranteed reading order; initial parsing may delay cancel |
| OSM XML/JSON | Bounded bytes/objects/tags/references/text/depth; DTD/entity rejection; ID/coordinate/date validation | Imported source is not authenticated; bounded extract does not guarantee completeness |
| Search syntax | Normalized letters/digits form terms; parameterized SQL | Future structured filtering/query syntax needs its own validation |
| Source prompt injection | Sources framed as data and role delimiters sanitized; no tool/network executor | Model adherence and claim support are not guaranteed |
| Stale/wrong source | Exact locators, content/import date distinction, source reader | No automatic revision applicability or freshest-snapshot selection |
| Native lifetime | Token/time limits, cancellation, invalidation, lifecycle cache release | Peak RAM and storage/memory-pressure recovery are unmeasured |

Source URLs, PDF actions and OSM tags do not trigger a network fetch. OSM import requests no location access. Recorded coordinates, opening hours or equipment instructions do not establish current conditions or authorization. Full bounds and hash meanings live in [knowledge contracts](knowledge-base.md).

## Import and retention

Selected-folder access is read-only; sources are never changed/deleted. There is no broad storage permission, watcher or background sync. URI/format/raw-byte identity skips unchanged files. Changed files create additional searchable snapshots. Per-file transactions retain completed work after a sibling fails or the user cancels; a retry rereads/hashes files and skips completed unchanged imports.

JSON knowledge packs contain bounded inline text/CSV, not filesystem paths to extract. Validate/index/activate is atomic; failed/canceled/conflicting updates retain the previous active version. Archived versions remain resolvable until whole-pack removal, and consume storage. Declared source/license fields and checksums do not authenticate a publisher.

Conversation rows live in private `chat.db`, independently of the knowledge library. Completed, limited, canceled and interrupted states remain distinguishable. **New chat** confirms deletion of that conversation and leaves documents intact. Removing a document clears its index/metadata/origin bindings and any private PDF/OSM original; removing a pack clears all its versions. Existing source references then become unavailable. There is no chat export, multi-conversation manager, field notebook or retention scheduler.

File copies and database transactions are not crash-atomic together. Process death can leave orphaned private originals/staging files; comprehensive cleanup and storage-pressure handling remain pending. Do not claim loss-free automatic import resume. The folder summary explains interruption and asks the user to select the folder again.

## Test-only provider and evidence

`FolderDocumentsProvider` belongs to the test APK, is protected by `android.permission.MANAGE_DOCUMENTS`, and serves synthetic fixture data only. It remains registered. An ordered `FixtureGrantReceiver` broadcast grants/revokes read access to the app and toggles root visibility; inactive roots are hidden. `FixtureGrants` obtains grants after instrumentation starts and checks readability. Repeated provider component disable/enable is obsolete and caused the documented sequential-suite failure. No fixture Activity is used in the current harness.

Test wrappers revoke fixture grants and hide roots afterward. They clean their own imported records and restore the previous import-summary state. Do not add broad product permissions to repair a fixture problem. Exact lifecycle/failure evidence is in [0.12 validation](validation-0.12.md).

JSON/screenshots can contain personal text, locations, asset IDs or local paths. Inspect evidence before committing/sharing. Current product/data/evaluation suites use unique run folders; legacy runtime scripts still overwrite fixed files and require archival first. Keep GGUFs, AVD disks, private app data, signing material, SDKs and caches out of Git. Review records must remain attributed and bound to exact result bytes.

## Proposed tools and connected work

Inference must treat content as read-only. Future local tools need an independent allowlist, typed parameters, budgets and applicability checks against confirmed context. Equipment transports, account connectors and an action ledger are proposals, not present features or authorization. A reconnect event cannot approve a destination/payload or retry an unknown non-idempotent result. Public distribution and source-license review remain separate decisions in the [roadmap](roadmap.md).
