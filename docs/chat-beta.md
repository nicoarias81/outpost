# Chat and local documents

Current behavior in Outpost 0.14.0; introduced in 0.10. [Latest validation](validation-0.14.md) owns current run identities. Recursive/OSM import extend preparation without changing the chat prompt or native kernels.

## Product flow

The launcher opens the current conversation with a bottom message composer and a Settings icon. Sending performs retrieval and streaming inference in one action. Settings contains document import/management, offline-model setup and confirmed conversation deletion. Prototype diagnostics, Kev controls, speculation controls, seed content and bottom navigation are removed from the product interface. Synthetic corpora and retrieval controls live only in the instrumented test APK.

The user requested progress toward mobile user testing and explicitly authorized the connected Pixel 10 Pro on 2026-10-01. The build packages ARM64 and x86_64; [physical validation](pixel10-results-2026-10-01.md) now includes 45 chat/import/PDF controls and native model admission. Use [the guarded Pixel protocol](pixel10-testing.md) and isolate personal state before visual tests. Broader provider/folder/OSM, field quality and release checks remain in the roadmap.

## Conversation and inference

`ChatStore` owns a separate private `chat.db`. A turn stores the user message, response, completion status and exact source locators. Successful, truncated, stopped and interrupted replies remain distinguishable. Startup marks an unfinished turn interrupted. New chat deletes conversation rows after confirmation; imported documents remain untouched.

`ChatPrompt` v1.1 selects query-centered excerpts from long passages/pages and uses the current message, a bounded recent conversation and up to three retrieved source excerpts. It uses two completed/length-limited turns, with 240 question and 400 answer characters each; current question 600, source excerpt 600 and title 100 characters. Retrieval uses the current message, without a history-based query rewrite. Earlier numeric citations are stripped from history because source numbering is specific to each turn. Canceled/failed drafts do not become asserted conversation context. Recent length-limited replies can provide continuation context. This is not full conversation recall or a persistent mission-memory system.

When no relevant document exists, the generator may answer from model knowledge; it is instructed not to invent a personal document, reference, live condition, local place or opening time. This changes the earlier evidence-only product gate. The older `ResearchPrompt` remains for controlled evidence-only research fixtures. Neither model instructions nor in-range citation indices establish factual support.

Native generation, locks, kernels, model pins and the 2,048-token context remain unchanged. The UI allows 192 output tokens and the existing 120-second deadline. Conversation/retrieval length bounds are character bounds; native token validation remains authoritative. The product always disables experimental context speculation; native research controls remain separate.

## Empty library and upgrades

Current library schema is 5 and chat schema is 1. The schema 2→3 step removes only exact unchanged historical seed identities when upgrading schema 2. Matching uses both the original ID and content hash; imports, knowledge packs and edited records are retained. Schema-1 imports still migrate transactionally before the seed cleanup. A new database never installs seed content. Test corpora reside under `app/src/androidTest/assets` and are explicitly inserted into isolated fixture databases by `TestLibrary`.

Existing versioned packs remain readable and removable as a whole; pack-import controls are no longer exposed in this first user flow. Removing an ordinary document deletes its index/text and, for PDF/OSM, its private original file and associated feature rows where applicable. Existing chat references become unavailable rather than rebinding. Removing a pack deletes all its versions after a confirmation that states that scope.

## Import and source reading

TXT/Markdown/CSV uses the existing bounded strict UTF-8 and CSV validation. PDFs use pinned PdfBox-Android for offline extraction, retaining one text entry per original page and a private byte-for-byte copy of the original. Page locators retain the original page number even when other pages have no text. The reader uses Android PdfRenderer for the original page and also exposes extracted text.

Bounds: 10 MiB original PDF, 100 pages, 750,000 extracted characters and 1 MiB serialized extracted page text; parser scratch storage is bounded separately. Invalid/encrypted documents and documents with no readable text are rejected. A text-bearing PDF may contain pages without text; the reader shows that limitation for those pages. There is no OCR, arbitrary Office import, formula evaluation, embedded-action execution or network fetch.

PDF extraction is not a proof of correct reading order or table recognition. Original pages stay inspectable. Interrupted file-copy/index transactions can leave unreferenced private PDF files; comprehensive crash/orphan cleanup and storage-pressure recovery remain follow-up work. No unsupported loss-free process-death import guarantee is made.

## Verification boundaries

`scripts/test-chat.ps1` checks empty startup, precise seed cleanup, existing-data preservation, conversation persistence, Settings, actual TXT/CSV/PDF import paths, PDF rejection and original page rendering. `-Generate` adds actual Bonsai 4B multi-turn, imported-source, stop and restart checks. Both use unique evidence directories and matching build/APK identities; no physical phones are targeted.

`scripts/test-knowledge.ps1` retains migration, CSV, locator, transaction, package and policy regression coverage. Evidence-only comparison fixtures use the explicit test corpus in the current v5 manifest (schema 2). Old search/reviewer UI phases were retired; use chat checks for the product, native runtime/cache tests for exact-prefix controls, and native Kev tests for classifier research. Do not interpret the old 0.9 quality scores as evaluations of the new chat prompt.


## Current document preparation

Settings/Documents offer Add file and Add folder. Recursive folder import, progress/cancel/retry, summaries, source paths and unchanged-file identity are described in [folder-import](folder-import.md). Schema 4 adds origin bindings without deleting current content; schema 5 adds OSM features. Add file/Add folder accepts bounded OSM XML/Overpass JSON as described in [OSM import](osm-import.md). `.json` files in this picker are Overpass exports, not developer knowledge packs. Document lists show 50 entries per page. While the library worker imports a batch, chat displays progress access and disables send; the conversation/model protocol is unchanged.

## Direct place answers and measured runtime

Since 0.13, bounded OSM name/category/proximity requests are answered directly with exact sources and computed distances, without a generator; general chat remains unchanged. In 0.14, matching measured runtime profiles can select guarded multi-column row reuse while retaining the single-column decoder. Both row dimensions participate in cache compatibility; product speculation remains off. See [place behavior](osm-place-queries.md) and [kernel measurements](kernel-rows-0.14.md).
