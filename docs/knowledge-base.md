# Knowledge domain and source contracts

Outpost 0.10 implements text/Markdown/CSV/PDF, typed passage/record/page locators and schema-3 migration with an empty product library. Bounded JSON pack APIs remain available for existing data and developer tooling; the product imports individual documents through Settings. The authoritative implemented format is [knowledge-pack v1](knowledge-packs-v1.md). Broader contracts below remain design direction.

## Complementary content layers

World/reference knowledge, regional/activity packages and personal documents are complementary sources. [Contextual question families](offline-world-knowledge.md) evaluate retrieval, recommendations and synthesis across them. Choose adapters for required evidence; neither a file finder nor a POI catalog defines the product.

| Source | Current behavior / remaining design |
|---|---|
| Text/Markdown | Original text, metadata, lexical passages and exact versioned locators implemented |
| CSV records | Header/value relationships and record locators implemented; literal cells, no formulas/type inference; limits in format guide |
| PDF manuals/drawings | Bounded text extraction, original page locators and rendering implemented; OCR, robust table/layout interpretation and broad document evaluation remain pending |
| DOCX/XLSX | Pending document structure or sheet/row/cell relationships; CSV support does not imply Office support |
| Wikipedia/Wikivoyage ZIM | Pending bounded archive-reader/search spike, archive-specific locators and index/storage measurements |
| OSM regions | Pending entity/geometry lookup; map rendering and route graphs are separate capabilities |
| User observations | Pending typed units/time/equipment identity and explicit user provenance |
| Semantic index | Deferred until lexical/entity retrieval misses justify encoder/index cost |

## Implemented evidence identity

`Evidence.Locator` binds document ID, revision, fragment kind (`passage`, `row` or `page`), ordinal and SHA-256 of the original text. `Library.evidence`, `metadata` and `resolve` expose exact local source identity. Prompt citation numbers are temporary mappings, not stable evidence IDs or confidence scores. Source/version retention prevents a package update from silently rebinding an existing locator; removal makes it unavailable.

Stored metadata separates known content date from incorporation time and retains format/language/source/license labels. Unknown content dates remain unknown during migration. Declared labels are not publisher authentication; hashes identify bytes, not truth. Malformed Unicode, oversized/structurally excessive JSON and expanding CSV inputs are rejected within documented bounds.

The reader shows original text, revision/hash and the selected CSV record. Row numbers count the header as record 1; quoted newlines remain within one record. This enables verification independently of generation. It does not guarantee complete retrieval of every matching record or correct model interpretation.

## Bounded package lifecycle

Schema v1 is a local JSON file containing text/CSV bodies and their hashes. Validation and indexing precede atomic activation. Identical version imports are idempotent; conflicting same-version manifests and downgrades are rejected. Failed validation/cancellation retains the old active version. Archived versions are excluded from ordinary search but remain resolvable until removal. The manifest's exact JSON text defines identity; reformatting changes that identity.

This slice replaces the earlier proposed text-plus-PDF first step with text-plus-CSV because record identity and date distinctions had a concrete runnable fixture. A bounded page-aware PDF slice was added in 0.10; broader K-04 quality acceptance remains open. The [61-check run](../evidence/runs/knowledge-20260930T122749Z-f6084045/knowledge-checks.json) covers persistence, migration rollback, parsing limits, lifecycle and source inspection.

K-02 remains partial: no catalog, signatures, geographic/asset coverage, storage estimates, resumable downloader, background updates or rollback UI. Process-death/resume behavior is not comprehensively validated. Retained versions consume storage until removal. Future archive extraction must reject escaping paths; current JSON packs extract no filesystem paths.

## Proposed contract extensions

Source-specific adapters should expose declared capabilities, asset/model/revision applicability, coverage/exclusions, stale/unknown-date limitations and inspectable original locators. Future packages need reader/index compatibility, original/index/temporary byte costs, build dates, and encoder identity where relevant. A signed publisher identity is separate from a content hash.

Personal-file recall may add filenames, user-confirmed project/equipment and actually supplied receipt/download dates. File modification, source date, measurement date and Outpost import time are different. A relative-date query must not invent dates or silently discard undated files. No email/account access is implied by importing a local file.

Apply hard applicability constraints before relevance ranking; preserve exact IDs and units. The current lexical system does not implement every such structured constraint. Typed arithmetic/unit tools remain proposed and must expose inputs, assumptions and evidence independently of generated prose. No arbitrary shell, source-triggered action or web executor is implemented.

## Integration references to revisit before adapters

[libzim](https://github.com/openzim/libzim) remains a candidate requiring Android build, storage/index and dependency review. Regional OSM extracts need source attribution and declared coverage; standard raster tiles are not an offline bulk package backend ([OSMF policy](https://operations.osmfoundation.org/policies/tiles/)). Google Maps offline caches have no demonstrated supported import contract here. These are historical feasibility references, not newly verified integration guarantees; recheck upstream terms and capabilities before implementation.
