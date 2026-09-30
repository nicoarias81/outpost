# Current implementation and known gaps

**Outpost 0.12.0 / version code 14.** The current product is chat-first, in English, with an empty knowledge library. [Chat/document behavior](chat-beta.md) describes the implementation; [validation](validation-0.12.md) owns current run identities, results and limitations.

| Area | Current state |
|---|---|
| Home | Persistent local conversation, streaming replies, bottom composer, Settings icon; no demo tabs or benchmark/reviewer UI |
| Folder import | Implemented: recursive SAF traversal, per-file failure isolation, progress, stop/retry, unchanged-file deduplication and retained paths; one-time copy, no sync |
| Documents | Settings import/manage for TXT, Markdown, CSV and text-bearing PDF; originals/source locators inspectable |
| Seed removal | Schema-3 cleanup plus schema-4 import identity and schema-5 OSM feature migration removes exact unchanged built-in notes only; fresh library empty; imports/edited records preserved |
| OSM | Bounded OSM XML/Overpass JSON feature import, coordinates/metadata/attribution, lexical retrieval and exact element sources; PBF, maps, geographic ranking, GPS and routes pending |
| PDF | Bounded page-aware extraction, original private file, original page rendering; encrypted/scanned-only PDFs rejected; no OCR |
| Conversation | Local turn persistence, bounded recent context, stop/interruption status, confirmed deletion independent of documents |
| Generation | Pinned Bonsai 4B/1.7B and Qwen 1.5B, local JNI; document sources where relevant, general model knowledge otherwise |
| Native runtime | Existing x86 optimized kernel/reference fallback, cache and per-build/model/device profiles; no numerical kernel change |
| Experimental controls | Removed from product UI; native research tests retained; chat forces speculation off |
| Packages | Existing local versioned packs remain readable/removable; lifecycle API retained, no pack-import UI in this user flow |
| Evaluation | New chat/import checks plus explicit test-only evidence corpus; previous quality results remain historical |
| ABI packaging | Complete ARM64 and x86_64 build; runtime validation only in x86_64 emulator |
| Network/GMS | No INTERNET permission or Google Play Services dependency |

The existing model-quality gaps still matter: unsupported synthesis, wrong citation alignment, incorrect interpretation and truncation are possible after successful retrieval. A source reference or successful harness is not proof of correctness. Small multi-turn smoke checks do not establish field usefulness or broad conversational quality.

Pending: representative user-task review; long/complex PDF and table extraction quality; OCR/Office/ZIM and broader geographic support; typed arithmetic; persistent editable mission context; storage-pressure/process-death import recovery; broader conversation navigation; model provisioning UX; release signing/license/public distribution. Phone performance, peak memory, thermal/battery behavior and GrapheneOS remain unmeasured.

Dedicated runtime target: Outpost35 / `emulator-5582`, AOSP API35, x86_64, four CPUs, 4 GiB configured RAM. Preserve Brújula's `emulator-5580`. Recheck live identity/offline state/installed hashes before testing. ARM compilation does not lift the user's emulator-only execution restriction.

The canonical repository is `E:/projects/outpost`, branch `codex/outpost`, with an existing private remote. The earlier permission-review quota failure was resolved in this session: 0.9 evidence and the prepared documentation were integrated before the 0.10 changes. No public push/release or physical-device execution is implied.
