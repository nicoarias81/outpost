# Knowledge domain and proposed contracts

Status: TXT/Markdown library implemented; typed contracts and all additional adapters proposed. This is a design specification, not an API already exposed by the app.

## Storage by capability

| Source | Proposed storage and query strategy | Current status |
|---|---|---|
| Text / Markdown | Original text, document metadata, lexical passage index | Implemented in `Library`, without versioned packages |
| PDF manuals and drawings | Original file, page/section locators, extracted text; separate OCR provenance where needed | Pending |
| Wikipedia / Wikivoyage ZIM | Read archive through a ZIM adapter; use available archive indexes; avoid duplicating the entire corpus | Pending |
| OSM regions | Entity and geometry indexes; display layers and route graph as distinct capabilities | Pending |
| User observations | Typed values, units, timestamps, asset identity, and user provenance | Pending |
| Semantic index | Optional encoder-specific index, paired with lexical/entity filters | Deferred until mission retrieval demonstrates a need |

Source reading, text search, place lookup, map rendering, elevation, and routing must be declared separately. A visible map is not proof that the app has a searchable place database or a navigable graph.

## Evidence contract, draft v0

Each result should contain the following fields. IDs are stable within a package version; prompt citation numbers are temporary mappings to those IDs.

| Field | Meaning |
|---|---|
| `id`, `kind` | Stable identity; passage, place, table, route, or observation |
| `packageId`, `packageVersion` | Exact prepared source revision |
| `content` or typed `fields` | Human-readable passage or structured values with units |
| `locator` | Document/page/section, archive entry, or entity ID needed to open the original |
| `provenance` | Publisher or user, source URL when available, license reference |
| `contentDate`, `importedAt` | Distinct content and incorporation dates; unknown values stay null |
| `language`, `scope` | Language, region, asset/model/revision applicability |
| `limitations` | Missing coverage, stale or unknown date, extraction uncertainty, conflict |

A locator must resolve without internet to the exact installed version. Conflicting results remain separate evidence items. Retrieval score indicates relevance, not factual confidence. Record the bounded subset actually sent to inference so an answer can be audited.

## Package manifest, draft v0

A future machine-readable schema should require:

- Schema version, package ID, package version, display title, publisher, and language.
- Content date or an explicit unknown value; package build date and import date separately.
- Geographic/subject coverage, exclusions, and declared capabilities.
- Every asset's relative path, byte count, SHA-256, media type, and source/license reference.
- Index format version and, if applicable, encoder identity/hash/dimension.
- Minimum reader compatibility and estimates for installed and temporary storage.

An unsigned hash manifest provides integrity against accidental corruption but is not publisher authentication. A trust/signature mechanism, if added, needs a separate decision. The current model lock files are not a general knowledge-package manifest.

## Package lifecycle

Proposed states: `staged -> validated -> indexed -> active`, with explicit failure cleanup and an intact previous active version. Uninstall is separate from update. A document's annotations or observations should not disappear just because a regional package is replaced.

Installation must reject unsupported schema versions, escaping paths, missing assets, incorrect hashes, and insufficient capacity before activation. Budget for original files, indexes, model weights, old/new update versions, extraction workspace, and rollback space. Indexing should be cancelable and recoverable after process death.

First implementation: wrap the existing text library behind a small adapter and add one page-aware document adapter. Prove package lifecycle and source opening with these two before generalizing to a large encyclopedia or region.

## Retrieval and local tools

Apply hard constraints first: equipment model/revision, document identity, language, geographic coverage, and required capability. Then rank passages or entities. Preserve exact codes and numbers; a semantic similarity score must not override a mismatched asset model.

Local tools should take explicit typed inputs and return structured results with units, assumptions, and evidence IDs. Begin with arithmetic and unit conversion. Straight-line distance, route distance, elevation difference, and accumulated ascent are different operations and must have distinct outputs. No general shell, arbitrary code executor, or web request interface is proposed.

## Integration references

[libzim](https://github.com/openzim/libzim) is the upstream candidate for ZIM reading/search; Android build footprint, dependencies, licensing compatibility, and actual archive indexing must be assessed in a spike.

For OSM, use regional extracts or a provider explicitly supporting offline packages. The standard raster tile service prohibits offline bulk downloading; it is not the package backend. See the [OSMF tile policy](https://operations.osmfoundation.org/policies/tiles/). Attribution and dataset obligations need to be preserved per source and reviewed when choosing a distribution strategy.

Google Maps' own offline downloads are not an established integration contract for this project. No supported cache-import path has been demonstrated here. Treat Google Maps integration as an open feasibility question, not an available interchangeable adapter. The [earlier source review](two-domain-design.md) records references to revisit before implementation.

## Acceptance for the first package slice

Install, cancel, resume/retry, replace, and remove a test package in the emulator. A corrupted or incompatible update must leave the prior package readable. Search must work with no model loaded. Opening a citation must show the exact source revision and page/section. Existing user text imports must survive the schema migration. Unsupported capabilities and missing coverage must be visible before a generated answer suggests otherwise.
