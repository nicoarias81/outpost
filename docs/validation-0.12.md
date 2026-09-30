# Outpost 0.12.0 validation — 2026-09-30

Version code14 adds bounded OSM XML/Overpass JSON knowledge import. The chat prompt, pinned models and native kernels are unchanged. Runtime tests used the dedicated offline Outpost35 x86_64 emulator. ARM64 remains compiled/packaged, without physical-device validation.

## Artifact

- Local candidate: `dist/outpost-0.12.0-user-test.apk`, **28,899,442 bytes**.
- App SHA-256: `d73f5cc489b74cc1825aeb9ca8daf2b60900cb8fbc1f585a5c3768026f4b6df7`.
- Final test APK SHA-256: `9836ab3c7790c37515a52f3cfdc278fce8a5382b79e542c786eab70bf7a3e028`.
- Build UTC: `2026-09-30T15:33:37.2338252Z`; [receipt](../evidence/releases/0.12.0/build-receipt.json).
- Production input fingerprint: `8227e453dba9209f7511f31a33929befea25ef844f460177d172e4dcbd2a0d0a`.
- Final test input fingerprint: `7d5758704d9414f9f33631f8f2f2b9c24e189ffbcd075d796dd16af1b9d87dd0`.
- [Package/evidence manifest](../evidence/releases/0.12.0/manifest.json): ARM64/x86_64 libraries; no mock library, synthetic map fixture or fixture provider/receiver in the user APK.

This is a debug-signed user-test candidate. Generator weights are separate. Local publication checks the successful-build inputs/APK bytes and checksum sidecar; no remote release is implied.

## Verification

| Scope | Result | Evidence |
|---|---|---|
| Final OSM parser/storage/SAF/source UI, model-free | **39 passed** | [Report](../evidence/runs/osm-20260930T153353Z-3889f2fc/osm-checks.json) |
| Real Bonsai 4B OSM answer, same app APK | **41 passed**, including one grounded answer | [Report](../evidence/runs/osm-20260930T151822Z-b8d56075/osm-checks.json) |
| Recursive folder regression | **30 passed** | [Report](../evidence/runs/folders-20260930T153339Z-d503ec10/folder-checks.json) |
| Chat/file/PDF/persistence regression without generation | **35 passed** | [Report](../evidence/runs/chat-20260930T153400Z-16e4d9cc/chat-checks.json) |
| Knowledge/schema/CSV/package/locator regression | **60 passed** | [Report](../evidence/runs/knowledge-20260930T153409Z-18da1cd5/knowledge-checks.json) |
| Build/lint | Successful; 0 errors, 3 existing upstream BouncyCastle warnings | [Lint](../evidence/releases/0.12.0/lint-results-debug.txt) |

Checks cover tagged node/way/relation identity, equal numeric IDs across types, preserved tags/dates/bounds, missing values, node/exported/derived coordinates, incomplete geometry, malformed coordinates, duplicate objects, DTD/entity rejection, nested/oversized input, unsupported/error exports, cancellation, transaction rollback, exact originals, source locators, repeat/changed imports, removal, actual schema4 migration, recursive SAF input and source inspection. The final sequential regressions completed successfully.

## Observed answer and provenance

With an imported **synthetic** pharmacy record, the actual production chat answered that its street is Sample Lane and cited source[1], the matching `node/10` record. The output and full system/user prompt are preserved in the generation report. It produced 33 tokens in 76.152 seconds on this emulator and ended normally. This is one integration observation, not real-place accuracy, a recommendation-quality score or a phone-performance prediction.

The generation run used the **same app SHA-256** as this delivery and test APK `bddd890098f9d788b9363cffb119780342c8dbc439699e5c4d2b353cb48ca3a6`. Afterward, only the test-provider lifecycle changed: the final test APK is the one in the receipt above. The model was not rerun solely for that harness fix. Synthetic records are removed from the app after tests; no sample OSM dataset is seeded or shipped.

Screenshots: [extract browser](../evidence/runs/osm-20260930T153353Z-3889f2fc/osm-browser.png), [feature source](../evidence/runs/osm-20260930T153353Z-3889f2fc/osm-source.png), [real local chat](../evidence/runs/osm-20260930T151822Z-b8d56075/osm-chat.png). Attribution and the exact OSM identity are visible; test content is explicitly synthetic.

## Preserved harness failures

After an OSM run, sequential folder tests encountered an unavailable provider (`folders-20260930T151946Z-3a97b71c`). An attempted ordered-grant setup still stalled during provider readiness (`folders-20260930T152645Z-aea24ab0`); that test was stopped by force-stopping only Outpost on emulator-5582, without clearing data/models. Its instrumentation output records that interruption.

The final fixture provider remains registered in the test APK, protected by MANAGE_DOCUMENTS. An ordered owner receiver grants/revokes fixture URIs and toggles root visibility instead of repeatedly unregistering/re-registering the component. Readiness is checked before traversal. Inactive roots are hidden, grants revoked, and prior import-summary preferences restored after tests. This does not alter production permissions; the user app still declares none.

## Scope and pending work

See [OSM import](osm-import.md) for supported formats and preparation. Limits include 32 MiB, 100,000 objects, 5,000 tagged features, bounded tags/text/references, and explicit rejection of unsupported/error inputs. Original files and typed feature rows are retained; unchanged bytes are deduplicated, while changed extracts are separate snapshots and remain searchable alongside older ones.

No PBF/GeoJSON/compressed reader, geographic nearest-neighbor or ranking engine, full relation/route topology, GPS, rendered map, routing or live availability check is implemented. A known bounding box does not prove complete coverage; absent tags are unknown. Derived centers are approximate and are not entrances. Broader real-region quality, scale, process-death/orphan-file recovery, peak memory and phone/thermal/battery validation remain pending. A valid citation identifies stored content, not its truth or currency.

## Reproduce

Use `scripts/test-osm.ps1` for no-model checks, or add `-Generate` when a genuine source-answer check is needed. Then run `test-folders.ps1 -SkipInstall`, `test-chat.ps1 -SkipInstall`, and `test-knowledge.ps1 -SkipInstall`. All wrappers verify Outpost35 identity, offline state, current build receipt and installed APK hashes. Review the [handoff](handoff.md) and [emulator runbook](emulator-runbook.md) before execution.
