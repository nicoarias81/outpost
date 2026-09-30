# Current implementation and known gaps

**Outpost 0.13.0 / version code 15**, checked on 2026-09-30. Canonical source: `E:/projects/outpost`, branch `codex/outpost`. The product is an English offline Android chat with an empty initial knowledge library. [Validation](validation-0.13.md) owns exact artifact identities/results; the [roadmap](roadmap.md) owns remaining tasks.

## Capability inventory

| Area | Implemented | Limits / remaining work |
|---|---|---|
| Home | Persistent local chat, streaming answer, bottom composer and Settings | One conversation; no export/history navigation or full mission memory |
| Context | Two recent completed/length-limited turns plus current evidence | Bounded characters/tokens; no history-based retrieval query rewrite |
| Documents | Add file/Add folder; strict UTF-8 TXT/Markdown/CSV and text-bearing PDF | No Office/OCR; bounded input/layout support |
| Folder import | Recursive selected SAF tree, progress/stop/summary, per-item errors, retained paths and unchanged-file deduplication | Snapshots, not sync; older changed snapshots remain searchable; reselect to retry |
| Sources | Exact passage/CSV record/PDF page/OSM element locators, local reader and document removal | Inspection identifies the source, not truth or claim support |
| OSM place knowledge | Bounded XML/Overpass JSON, typed records and coordinates, deterministic place answers in chat, attribution and exact source browser | Implemented bounded named/alias lookup, recorded-locality categories and computed proximity, including clarification, exact citations and conflicting-snapshot handling; 20,000-row scan bound. Maps/navigation/routing remain outside scope |
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

The delivered debug candidate is `dist/outpost-0.13.0-user-test.apk`, with generator weights separate. Place tests passed 63 checks across synthetic cases and a frozen prepared 100-feature public OSM subset. OSM/folder/chat/knowledge regressions and exact final artifact identities are linked from validation. The earlier 0.12 Bonsai source-answer observation is historical. The new bounded place path executes no generator; general chat remains a separate inference path. [Current validation](validation-0.13.md) owns final place-query results; [0.12 validation](validation-0.12.md) preserves the older generation/provider records. Lint has 0 errors and 3 existing upstream warnings.

The evidence-only matrix defaults to v6: 15 fixtures, 12 runnable and 3 blocked. E-06 is fixed: the validator compares the full ABI set, with missing/extra/duplicate/malformed fault-injection coverage. The old regional map/routing blocker was removed; its broader time/preference recommendation case remains separate from deterministic place tests. Historical 0.9 attributed reviews do not score `ChatPrompt` or current OSM recommendations. No broad held-out user study, qualified field validation or bounty acceptance is established.

The owner clarified [OSM place-query scope](osm-place-queries.md): import place data to answer where something is or what is around a named location. Version 0.13 implements that bounded slice. Straight-line distance and recorded locality are distinct from route distance or polygon containment; the generator does not manufacture place results.

## Open product and engineering work

Priorities are realistic conversational/source tasks, claim-to-source alignment and missing/conflicting context; model provisioning and user-test readiness; long/complex PDF quality; import/storage/process recovery; real-region geographic evaluation; then justified adapters/tools/optimizations. Arithmetic, editable mission context, Office/OCR/ZIM, semantic retrieval, action queues, equipment transports, voice and vision are not implemented.

Peak memory, pressure recovery, thermal/battery behavior, physical phone performance and GrapheneOS remain unmeasured. Code license, production signing and public distribution remain open; an existing private remote does not imply a public release.

## Execution boundary

Use **Outpost35 / emulator-5582**, AOSP API 35 x86_64, four virtual CPUs and 4 GiB configured RAM. Preserve Brújula's emulator-5580. Recheck live AVD identity, boot/offline state, installed APK hashes and model readiness before runtime work. ARM compilation does not lift the user's emulator-only restriction. [Handoff](handoff.md) gives the resumption sequence.
