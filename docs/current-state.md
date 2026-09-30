# Current implementation and known gaps

**Outpost 0.12.0 / version code 14**, checked on 2026-09-30. Canonical source: `E:/projects/outpost`, branch `codex/outpost`. The product is an English offline Android chat with an empty initial knowledge library. [Validation](validation-0.12.md) owns exact artifact identities/results; the [roadmap](roadmap.md) owns remaining tasks.

## Capability inventory

| Area | Implemented | Limits / remaining work |
|---|---|---|
| Home | Persistent local chat, streaming answer, bottom composer and Settings | One conversation; no export/history navigation or full mission memory |
| Context | Two recent completed/length-limited turns plus current evidence | Bounded characters/tokens; no history-based retrieval query rewrite |
| Documents | Add file/Add folder; strict UTF-8 TXT/Markdown/CSV and text-bearing PDF | No Office/OCR; bounded input/layout support |
| Folder import | Recursive selected SAF tree, progress/stop/summary, per-item errors, retained paths and unchanged-file deduplication | Snapshots, not sync; older changed snapshots remain searchable; reselect to retry |
| Sources | Exact passage/CSV record/PDF page/OSM element locators, local reader and document removal | Inspection identifies the source, not truth or claim support |
| OSM | Bounded XML/Overpass JSON, typed features/tags/IDs/dates/coordinates, attribution and paginated browser | No PBF/GeoJSON, geographic ranking, GPS, maps, full topology, routes or live availability |
| Database | Library schema 5; transactional 1→5 upgrades; separate chat schema 1 | Full process-death/orphan-file/storage-pressure recovery pending |
| Empty-library policy | Fresh storage empty; schema 3 removes only six unchanged legacy seed identities/hashes | Imported/edited data preserved; synthetic corpus only in test APK |
| Packs | Developer text/CSV v1 lifecycle; existing packs readable/removable; atomic activation and retained versions | No current product pack-import button, catalog, signatures, coverage/storage UI or downloader |
| Generation | Locked Bonsai 4B/1.7 and Qwen 1.5B, local JNI; source-assisted or general knowledge chat | No generator download/bundled GGUF; quality/citation/conflict/truncation failures remain possible |
| Runtime | Existing AVX2/F16C Q2 optimization/reference fallback, grouped widths and exact-compatible cache | Custom VNNI/ARM kernels pending; no new numerical optimization since 0.8.1 |
| Calibration | Device/OS/app-version/model key; conservative unmeasured fallback | A previous version's measured width 4 is not a current measured profile |
| Research controls | Evidence-only evaluation, native Kev and speculation tests retained | Removed from product UI; chat forces speculation depth 0; locked4B has no MTP heads |
| ABI packaging | Complete ARM64 and x86_64 build/link/package | Only x86_64 emulator runtime validation; no phone/GrapheneOS claim |
| Connectivity | No declared product permissions, INTERNET or GMS dependency | External picker providers may require their own connectivity before import |

## Current evidence

The delivered debug candidate is `dist/outpost-0.12.0-user-test.apk`, **28,899,442 bytes**, with generator weights separate. Current no-model suites passed OSM 39, folders 30, chat 35 and knowledge 60 checks. One separate real Bonsai 4B OSM answer used the **same app bytes and an earlier test APK**; final harness-only changes were verified without rerunning the model. [0.12 validation](validation-0.12.md) preserves that distinction, the full answer and provider failures. Lint has 0 errors and 3 existing upstream warnings.

The evidence-only matrix defaults to v5: 15 fixtures, 12 runnable and 3 blocked. Its validator passes but has a known incomplete multi-ABI identity check (E-06). Historical 0.9 attributed reviews do not score `ChatPrompt` or current OSM recommendations. No broad held-out user study, qualified field validation or bounty acceptance is established.

## Open product and engineering work

Priorities are realistic conversational/source tasks, claim-to-source alignment and missing/conflicting context; model provisioning and user-test readiness; long/complex PDF quality; import/storage/process recovery; real-region geographic evaluation; then justified adapters/tools/optimizations. Arithmetic, editable mission context, Office/OCR/ZIM, semantic retrieval, action queues, equipment transports, voice and vision are not implemented.

Peak memory, pressure recovery, thermal/battery behavior, physical phone performance and GrapheneOS remain unmeasured. Code license, production signing and public distribution remain open; an existing private remote does not imply a public release.

## Execution boundary

Use **Outpost35 / emulator-5582**, AOSP API 35 x86_64, four virtual CPUs and 4 GiB configured RAM. Preserve Brújula's emulator-5580. Recheck live AVD identity, boot/offline state, installed APK hashes and model readiness before runtime work. ARM compilation does not lift the user's emulator-only restriction. [Handoff](handoff.md) gives the resumption sequence.
