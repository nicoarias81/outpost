# Knowledge domain and source contracts

Implementation reference for **Outpost 0.13.0**, library schema 5. Public reference material, regional data and personal documents are complementary inputs. The product starts empty and imports through **Settings → Add file / Add folder**. It neither downloads a knowledge base nor scans all phone storage.

## Formats and current limits

| Input | Implemented behavior | Main bounds / exclusions |
|---|---|---|
| TXT / Markdown | Strict UTF-8 text and lexical passage retrieval | 1 MiB per file; no embedded execution |
| CSV | Exact header/value relationships, record locators and literal cells | 1 MiB; 64 columns; 2,000 records including header; no formulas/type inference |
| PDF | Offline page text extraction, private original and original-page rendering | 10 MiB, 100 pages, 750,000 extracted characters and 1 MiB serialized page text; no OCR; encrypted/scanned-only rejected |
| OSM XML / Overpass JSON | Tagged features, names/tags, typed IDs, recorded coordinates/dates, exact element source and attribution | 32 MiB, 100,000 objects, 5,000 tagged features; no PBF/GeoJSON/compressed/change/history format |
| JSON knowledge pack v1 | Developer API for versioned inline text/CSV | 4 MiB, 1–32 documents, each ≤1 MiB; no product pack-import button |
| DOCX/XLSX, ZIM, OCR | Pending | CSV/PDF support does not imply Office or encyclopedia support |

The shared importer selects by extension and validates content. In the current Add file/Add folder flow, `.json` means **Overpass JSON**, not arbitrary JSON or a knowledge pack. Detailed bounds: [CSV/packs](knowledge-packs-v1.md), [PDF/chat](chat-beta.md), [folders](folder-import.md), [OSM](osm-import.md).

## Source identity and resolution

`Evidence.Locator` binds document ID, revision, fragment kind, ordinal and `contentSha256`. The hash has format-specific semantics:

| Format | Locator kind / ordinal | Locator hash |
|---|---|---|
| Text / Markdown | `passage`, one-based indexed fragment | SHA-256 of stored UTF-8 text body |
| CSV | `row`, original record number; header is record 1 | SHA-256 of stored original CSV text body |
| PDF | `page`, original one-based page number | SHA-256 of the serialized extracted-page text body, **not the original PDF bytes** |
| OSM | `element`, one-based stored tagged feature | SHA-256 of the exact original extract file |

`imported_files` separately stores the original-byte hash used for file deduplication, including PDFs. Do not substitute that hash for a PDF locator's content hash. OSM records additionally retain original object type/ID/version; `node/10` and `way/10` are distinct. Prompt citation numbers are temporary per-turn mappings, not stable evidence IDs or confidence scores.

`Library.evidence`, `metadata` and `resolve` open the precise local fragment. Source removal makes a saved reference unavailable rather than rebinding it. Known content date, incorporation time and declared source/license/language remain distinct; unknown dates are not invented from file modification time. Hashes identify content, not truth or an authenticated publisher.

## Snapshots and packs are different lifecycles

For file/folder imports, source identity is provider/document URI identity + detected format + original-byte SHA-256. Equivalent tree/single-document URI forms share identity. Unchanged extant imports are skipped; deleted imports can be added again. Different source files with identical content remain separate. Changed bytes create another document and **both snapshots remain searchable**. There is no newest-version selection or cross-extract OSM entity deduplication.

Folder traversal is a one-time read-only selected scope. It commits each file independently, retains relative paths and reports errors/skips/bounds. Cancel preserves completed imports; reselecting the folder retries and skips those unchanged files. There is no watcher, scheduled sync or automatic process-death resume.

Knowledge-pack v1 uses exact manifest-text identity, atomic validate/index/activate, idempotent same-version retry and retained historical versions. Conflicting same-version content and downgrades are rejected. Only active pack versions appear in ordinary search; archived locators resolve until the whole pack is removed. Pack APIs and host preparation remain available to developers; current Settings can read/remove existing packs but cannot import new ones.

## Database migration

| Upgrade | Added behavior |
|---|---|
| 1 → 2 | Metadata, hashes and versioned packs; preserves original document IDs/import time without inventing content dates |
| 2 → 3 | Removes only the six exact unchanged legacy seed identities/hashes; imports and edited rows survive |
| 3 → 4 | `imported_files` origin bindings; older imports remain without retroactively invented identities |
| 4 → 5 | Separate `osm_features` payload/text rows and element locators |

Migrations are transactional, without wiping/reseeding user data. `chat.db` has its own schema and lifetime. PDF/OSM originals are retained privately and removed with their document; robust orphan-file/process-death/storage-pressure recovery remains open. See [current validation](validation-0.13.md) for exact regression scopes.

## Place questions from imported OSM

The adopted scope is name lookup and category/proximity questions, such as locating a park or finding restaurants near a stated landmark. Structured entity resolution/filtering/distance ordering belongs in the knowledge domain; the generator receives actual records and locators to explain. Outpost 0.13 answers these bounded requests directly using `PlaceQueries`, without generator execution or a schema change. A map, route graph and GPS are not dependencies for named-reference questions. [Scope and acceptance](osm-place-queries.md) define the implemented exact-name/alias, recorded-locality, radius, ordering and collection bounds. Arbitrary language/filters and persistent spatial indexing remain pending.

## Retrieval and remaining contracts

FTS4 retrieves normalized lexical prefixes; the query is bounded to 20 terms, candidate scan to 500 and results to eight fragments. Generation uses at most three. CSV headers stay attached to rows; OSM has a small category-alias set. This does not implement exhaustive record filtering, semantic retrieval, geographic distance/ranking, equipment applicability or conflict resolution.

Future adapters should declare capabilities, coverage/exclusions, exact asset/model/revision applicability, original locators and storage/index costs. User observations need explicit identity, units and timestamps. Typed arithmetic and unit tools must expose inputs/results independently of generated prose. Relative dates must not infer email receipt or attachment download time from import time.

K-02 remains partial: no catalog, publisher signatures, coverage/storage UI, resumable downloader, background updates or rollback UI. OSM bounds do not prove complete regional coverage; absent tags and current conditions remain unknown. [Question families](offline-world-knowledge.md) and [roadmap](roadmap.md) define the broader quality target.

## Candidate integrations

[libzim](https://github.com/openzim/libzim) remains a candidate requiring an Android reader/search/storage spike. Google Maps offline caches have no demonstrated supported import contract here. OSM rendering/routing assets are separate from the imported feature records. Historical feasibility references, including [OSMF tile policy](https://operations.osmfoundation.org/policies/tiles/), must be checked again before choosing a downloader or distribution design; this documentation audit did not revalidate upstream terms.
