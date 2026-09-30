# Knowledge packs, CSV and source identity

Format introduced in 0.9 and retained in 0.12 as a developer/backend contract for inline text and CSV. PDF and OSM are separate file-import adapters, not supported pack payloads. Office and ZIM remain pending.

## Import and inspect

The current product has no Library tab or pack-import button. Settings → Add file/Add folder imports ordinary documents; a `.json` there must be an Overpass export. Existing packs can be read and removed as a whole. Developers can prepare packs with the host builder and exercise `KnowledgePack`/`Library.installPack` through instrumented tests. A JSON pack is at most 4 MiB and contains 1–32 documents, each at most 1 MiB. The example [field kit](../examples/knowledge/field-kit-v1.json) contains only synthetic demonstration material.

Import bounds JSON nesting/structure before parsing, then validates schema, identifiers, declared formats, content hashes and CSV structure before activation. Package documents and the active-version switch are committed together in SQLite. Failed validation or cooperative cancellation leaves the previous active package intact. Retrying an identical package version is idempotent; a different file with the same package ID/version is rejected. Older versions cannot silently replace a newer installed version.

Source and license labels are declared metadata, not independent verification or publisher authentication. The importer does not download anything or execute source content. Import only material the user intends to keep locally; redistribution rights are not determined by the tool.

## Versioned source locators

Every pack document has a revision, SHA-256 of its exact UTF-8 body, format, language, content date when supplied, and incorporation time. Other adapters have different [hash/locator semantics](knowledge-base.md), especially PDF and OSM. An evidence locator combines document ID, revision, fragment kind, ordinal and content hash. It identifies a passage or CSV record, not a confidence score.

Opening a search result resolves that exact locator. Package updates retain older source versions for existing references, while ordinary search and document management display only the active pack version. This differs from individual-file snapshots, whose old versions remain searchable. The reader labels archived versions. Removing a pack removes all its retained versions, so references to those sources become unavailable rather than rebinding to another document.

The schema 1→2 migration introduced this contract; the current database is schema 5. Legacy databases upgrade transactionally through the steps described in [knowledge contracts](knowledge-base.md), including schema 2 without deleting/reseeding imports or changing their IDs. Original incorporation dates are retained; missing content dates are not invented. Migration tests cover both success and a deliberately failing intermediate ALTER TABLE with rollback of schema changes and preservation of the old rows/version.

## CSV semantics and bounds

CSV import preserves quoted commas, escaped quotes, quoted newlines, empty values, leading zeros and literal formulas. It does not evaluate formulas or macros, infer numeric/date types, or claim Excel support.

- Header names must be non-empty and distinct ignoring case, at most 120 characters each.
- At most 64 columns and 2,000 records, including the header.
- At most 16,384 characters per cell and rendered indexed record.
- At most 2,097,152 rendered index characters per document, preventing short input with large repeated headers from expanding without bounds.
- Every data record must have the same column count as the header.

Each data record becomes a retrieved fragment with its original header/value relationships. Record numbers count the header as record 1; a quoted newline does not create another record. The reader shows the selected record and its locator, then the original CSV. This provides source inspection; it does not guarantee that the model correctly interprets the row or that a broad query retrieves every matching record.

## Pack schema

Required root fields: integer `schemaVersion: 1`, restricted string `id`, positive integer `version`, `title`, `source`, `license`, `language`, and `documents`. `contentDate` is optional/unknown or an ISO date.

Each document declares `id`, `title`, `category`, optional `format` (`text` or `csv`), `body`, and lowercase SHA-256 of that exact UTF-8 body. Optional `url` is a displayed reference, not a fetch instruction. Optional document `contentDate` overrides the package date.

Identifiers use lowercase ASCII letters/digits and dot, underscore or hyphen, up to 64 characters; they are database identifiers, not paths. Pack files are not ZIP archives and cannot extract arbitrary filesystem paths. Whole-manifest identity currently uses the exact JSON text, so changing formatting also requires a new version if the same ID/version has already been installed.

Build a pack on the host, before offline use:

```powershell
. ./scripts/environment.ps1
$OutpostPython = (Get-OutpostSettings).pythonExecutable
& $OutpostPython scripts/make-knowledge-pack.py --id my-field-kit --version 1 --title "My field kit" --source "My documents" --license "User-provided material" --language en --output .local/my-field-kit.json examples/knowledge/field-note.md examples/knowledge/issues.csv
```

The builder refuses to overwrite an existing output. The Android importer remains the final validation step, including CSV/index bounds.

## Lifecycle limits

This implementation supports atomic validate/index/activate, version retention, cancel/retry, and removal. It does not provide a public package catalog, publisher signatures, a resumable byte-range downloader, background updates, geographic coverage metadata, disk-usage estimates, a package rollback UI, OCR, spreadsheet recalculation or a generic local-tool execution API. Retained versions consume space until the pack is removed. These limits keep roadmap K-02 explicitly bounded rather than marking every planned package-management capability complete.

Use `scripts/test-knowledge.ps1` for migration, transaction, source identity, CSV and UI checks. Results go to a new directory under `evidence/runs/`. The screenshots and synthetic test pack do not validate a real equipment procedure.
