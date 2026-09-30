# Recursive folder import — 0.11

## User flow

Open **Settings → Add folder**, choose a directory in Android's picker, and allow access. Outpost walks the selected directory and its subdirectories and copies supported PDF, TXT, Markdown and CSV files into its private library. **Add file** remains available for individual documents. Case-insensitive extensions and supported hidden files are included.

Document management shows 50 files per page, keeping large collections navigable. The import shows the current relative path and running counts. **Stop import** cancels further work while retaining completed documents. The summary lists imported, already-added, skipped and failed items, with per-item reasons. **Last folder import** reopens the result. Chat shows an import-in-progress link and disables sending while the library worker is preparing documents; a draft can still be entered.

This is a one-time copy, not a synchronized folder or watcher. Changes to the original files do not update the library automatically. Sources are never edited or deleted. A cloud provider must make its files readable locally; a partial/loading directory listing is reported as incomplete rather than accepted as complete.

## Identity, updates and recovery

The shared `DocumentImporter` validates both individual files and folder entries. Schema4 adds `imported_files`, atomically binding a document to its provider/document identity, detected format and SHA-256 of original bytes. Different tree and single-document URI forms resolve to the same provider identity. An unchanged repeat is skipped while its stored document exists. A removed document can be imported again. Different folders with equally named files remain distinct; the relative path is retained in source metadata.

Changed bytes create a new snapshot, preserving older documents/citations. This is not automatic version replacement or conflict resolution. Earlier imports lacking origin bindings are retained; deduplication applies to imports made through the new shared handler. It does not collapse different source files merely because their text happens to match.

Each file commits independently. One damaged, oversized, unsupported or inaccessible item does not discard successful files or stop other readable branches. Cancellation is checked during copying, PDF page/text extraction and before the database commit. Provider calls and the initial PDF parser load may take time before cancellation completes. A stopped import can be retried by choosing the folder again.

Activity destruction requests cancellation. After process interruption, the UI explains that saved documents remain and the folder should be selected again. There is no background service, scheduled scan, automatic restart or loss-free process-death guarantee. The previous PDF orphan-file/crash-recovery limitation remains; imported records and their identity bindings are transactional.

## Bounds and platform access

Existing per-file limits remain: 1 MiB TXT/Markdown/CSV; 10 MiB PDF, 100 pages and bounded extracted text. No OCR or Office adapter is added. Traversal tracks provider IDs to avoid cycles/repeated entries, processes at most 20,000 listed items and 64 directory levels, and rejects paths longer than 2,048 characters. A reached bound is explicitly reported; import smaller subfolders to continue. Up to 200 issue details are shown, with an omitted-detail count beyond that.

The app uses read-only `ACTION_OPEN_DOCUMENT_TREE` grants and adds no storage or network permission. Android controls which directories can be selected; on Android11+, storage roots, the Download root and restricted Android data directories have system restrictions. A regular subfolder such as a trip folder can be selected where the provider permits it. See [Android's Storage Access Framework guide](https://developer.android.com/training/data-storage/shared/documents-files).

## Verification

`scripts/test-folders.ps1` runs only on the offline Outpost35 emulator. It verifies the current build/APK receipt, uses a synthetic Android DocumentsProvider with genuine owner-issued URI grants, tests recursive imports, failure isolation, aliases/cycles, names, unknown sizes, deduplication, changed snapshots, deletion/reimport, cancellation/retry, schema3 migration and Activity picker-result/summary integration. The system picker Intent is intercepted by instrumentation for deterministic selection; this is not a claim of manual picker testing on phones.

The fixture provider is protected by MANAGE_DOCUMENTS, exists only in the test APK and is disabled outside test runs. Grants are issued after instrumentation starts, because starting instrumentation revokes previous transient grants. The test removes only its own library entries, restores existing import-summary preferences, revokes its grants and disables the fixture provider afterward. No production mock corpus or fixture provider is bundled.

Use the [0.11 validation record](validation-0.11.md) for final run identities. Chat generation/prompt/kernel behavior is unchanged, so this release's focused tests do not rerun large model suites or establish new quality/performance claims.
