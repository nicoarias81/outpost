# Current implementation and known gaps

**Outpost 0.18.0 / version code 20**, checked on 2026-10-02. Canonical source: `E:/projects/outpost`, branch `codex/outpost`. The product is an English offline Android chat with an empty initial knowledge library. [Validation](validation-0.18.md) owns exact artifact identities/results; the [roadmap](roadmap.md) owns remaining tasks.

The owner authorized the connected Pixel 10 Pro on 2026-10-01. [The Pixel protocol](pixel10-testing.md) records preparation, run identities and current physical validation status. Historical emulator results below are not phone results.

The exact Pixel/Bonsai4 profile now uses six decode/six prompt workers with four logical attention partitions and guarded I8MM prefill. Complete paired answers retain every original logit and token while reducing generation latency; see current validation for timing and CPU-time limits. Product speculation remains disabled. The validated 0.18 artifact is local; the phone is restored to its original 0.16 pair after tests, so verify installed hashes before -SkipInstall.

## Capability inventory

| Area | Implemented | Limits / remaining work |
|---|---|---|
| Home | Persistent local chat, streaming answer, bottom composer and Settings | One conversation; no export/history navigation or full mission memory |
| Context | Two recent completed/length-limited turns plus current evidence; bounded user-context refinements for place queries | General retrieval retries with the previous user question only when the first lexical search is empty; no unrestricted query rewriting or full mission memory |
| Documents | Add file/Add folder; strict UTF-8 TXT/Markdown/CSV and text-bearing PDF | No Office/OCR; bounded input/layout support |
| Folder import | Recursive selected SAF tree, progress/stop/summary, per-item errors, retained paths and unchanged-file deduplication | Snapshots, not sync; older changed snapshots remain searchable; reselect to retry |
| Sources | Exact passage/CSV record/PDF page/OSM element locators, local reader and document removal | Inspection identifies the source, not truth or claim support |
| OSM place knowledge | Bounded XML/Overpass JSON, typed records and coordinates, deterministic place answers in chat, attribution and exact source browser | Implemented bounded named/alias lookup, recorded-locality categories and computed proximity, including clarification, exact citations and conflicting-snapshot handling; 20,000-row scan bound. Maps/navigation/routing remain outside scope |
| Database | Library schema 5; transactional 1→5 upgrades; separate chat schema 1 | Full process-death/orphan-file/storage-pressure recovery pending |
| Empty-library policy | Fresh storage empty; schema 3 removes only six unchanged legacy seed identities/hashes | Imported/edited data preserved; synthetic corpus only in test APK |
| Packs | Developer text/CSV v1 lifecycle; existing packs readable/removable; atomic activation and retained versions | No current product pack-import button, catalog, signatures, coverage/storage UI or downloader |
| Generation | Locked Bonsai 4B/1.7 and Qwen 1.5B, local JNI; source-assisted or general knowledge chat | No generator download/bundled GGUF; quality/citation/conflict/truncation failures remain possible |
| Runtime | Guarded x86 AVX2/F16C and ARM DotProd Q2 paths, prepared activation/column/decode-row reuse, persistent ARM workers, guarded dynamic prefill row queues, exact I8MM matrix tiles and cache | Exact Pixel/Bonsai 4B preset validated; other keys remain conservative. I8MM is admitted only under the current matrix/device/model guards; VNNI/GPU/NPU and broader phone/energy work remain open |
| Calibration | Device/OS/app-version/kernel/model key, separate prompt/decode workers and row settings | Pixel/Bonsai4 uses 6 decode/6 prompt with four logical attention workers, I8MM matrix policy 1, batch 128, width 8, rows 1/4, chunks 32/0 and no affinity; unmatched keys remain conservative |
| Research controls | Evidence-only evaluation, native Kev and speculation tests retained | Removed from product UI; chat forces speculation depth 0; locked4B has no MTP heads |
| ABI packaging | Complete ARM64 and x86_64 build/link/package | x86_64 emulator checks and 23 Pixel 10 Pro ARM64 admission controls; 32 historical physical pilot answers and 54 current chat/import/PDF checks are recorded separately; no other-phone/GrapheneOS claim |
| Connectivity | No declared product permissions, INTERNET or GMS dependency | External picker providers may require their own connectivity before import |

## Current evidence

[0.15 validation](validation-0.15.md) records144,384,240 matrix comparisons and complete-output/per-step-logit confirmation, followed by final artifact regressions and restored private data. It superseded the initial phone reference-path measurements. [0.16 validation](validation-0.16.md) owns prefill row scheduling; [0.17 validation](validation-0.17.md) owns the worker/attention change; [0.18 validation](validation-0.18.md) adds I8MM prefill, a direct combined-stack comparison and current release checks. Historical results remain unchanged.

The [Pixel 10 Pro validation](pixel10-results-2026-10-01.md) adds 23 native admission checks, 32 pilot answers and 45 chat/import/PDF checks on ARM64/Android 17. That initial study used the backend Q2 reference and had four Bonsai deadline returns; the 0.15 result above measures the adopted runtime separately. Spark remains research-only; both models have quality failures. Phone UI tests ran with isolated temporary state and restored original data hashes. Mixed screen/foreground conditions, USB power and profiling overhead limit timing/energy conclusions; other phones, 16 KiB pages, full provider/folder/OSM phone suites and field acceptance remain pending.

The previous 0.14 debug candidate was `dist/outpost-0.14.0-user-test.apk`; current delivery is `dist/outpost-0.18.0-user-test.apk`, with generator weights separate. Place tests passed 63 checks across synthetic cases and a frozen prepared 100-feature public OSM subset. OSM/folder/chat/knowledge regressions and exact final artifact identities are linked from validation. Kernel 0.14 added measured x86 row-policy optimization with separate confirmation and lifecycle evidence in [current validation](validation-0.14.md). The earlier 0.12 Bonsai source-answer observation is historical. The new bounded place path executes no generator; general chat remains a separate inference path. [Current validation](validation-0.18.md) owns the latest runtime/UI regressions; [0.12 validation](validation-0.12.md) preserves the older generation/provider records. Lint has 0 errors and 3 existing upstream warnings.

The evidence-only matrix defaults to v11: 15 fixtures, 12 runnable and 3 blocked. E-06 is fixed: the validator compares the full ABI set, with missing/extra/duplicate/malformed fault-injection coverage. The old regional map/routing blocker was removed; its broader time/preference recommendation case remains separate from deterministic place tests. Historical 0.9 attributed reviews do not score `ChatPrompt` or current OSM recommendations. No broad held-out user study, qualified field validation or bounty acceptance is established.

The owner clarified [OSM place-query scope](osm-place-queries.md): import place data to answer where something is or what is around a named location. The bounded place slice introduced in 0.13 remains unchanged in 0.17. Straight-line distance and recorded locality are distinct from route distance or polygon containment; the generator does not manufacture place results.

## Open product and engineering work

Priorities are realistic conversational/source tasks, claim-to-source alignment and missing/conflicting context; model provisioning and user-test readiness; long/complex PDF quality; import/storage/process recovery; real-region geographic evaluation; then justified adapters/tools/optimizations. Arithmetic, editable mission context, Office/OCR/ZIM, semantic retrieval, action queues, equipment transports, voice and vision are not implemented.

Peak memory, pressure recovery, actual energy/battery behavior, broader phone performance and GrapheneOS remain unvalidated. Bounded latency on the registered Pixel is measured above. Code license, production signing and public distribution remain open; an existing private remote does not imply a public release.

## Execution boundary

Use **Outpost35 / emulator-5582**, AOSP API 35 x86_64, four virtual CPUs and 4 GiB configured RAM. Preserve Brújula's emulator-5580. Recheck live AVD identity, boot/offline state, installed APK hashes and model readiness before runtime work. The owner separately authorized the registered Pixel 10 Pro; use its guarded protocol for physical execution. Host inference and other phones remain outside scope. [Handoff](handoff.md) gives the resumption sequence.

## Model exploration checkpoint

[Spark-X2.5 research admission and comparison](spark-candidate-results-2026-10-01.md) is implemented and executed on Outpost35. The candidate stays outside the three-model product selector. The emulator uses a separately hashed same-version research build; the published 0.14 APK remains frozen. Greedy reserved-case review found 13/24 fully supported outcomes for each model, with material failures; no default replacement or phone claim follows. Compact cache remains experimental after failed first-logit equivalence. Other model candidates, cold storage profiling and full lifecycle tracing remain pending.
