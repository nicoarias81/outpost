# Outpost 0.11.0 validation — 2026-09-30

Version code13 adds recursive selected-folder import and shared one-file validation. Existing chat/model/prompt/kernel behavior is unchanged. All execution was in the offline Outpost35 x86_64 emulator; no model inference or physical-phone test was performed for this feature.

## Artifact identity

- Local candidate: `dist/outpost-0.11.0-user-test.apk`, **28,832,902 bytes**.
- App SHA-256: `13a4af10de9dbc71a7bb1f3a706ed176362c051df9b63b41272aa7c1620b472d`.
- Test APK SHA-256: `86b713e52fbf617503163af91265e5f229aa70d8897142ca02c1588c3860afa9`.
- Build UTC: `2026-09-30T14:40:56.4547641Z`; [receipt](../evidence/releases/0.11.0/build-receipt.json).
- Production input fingerprint: `39686c2d9a9fa613cf8352afa6870782acfb0c1eea031e0a549daeb1d2ed7524`.
- Test input fingerprint: `ba5c672745cd3cb94e5143fc8749481af17caf66063a0b424e31c115fb38c4a1`.
- APK includes ARM64 and x86_64 libraries, with no mock library, fixture provider or grant Activity in the user APK; [manifest](../evidence/releases/0.11.0/manifest.json).

The artifact is debug-signed and omits generator GGUF weights. The publisher checks successful-build inputs/APK bytes and the sidecar. ARM packaging remains build evidence only; prior physical-device and broader quality limitations still apply.

## Focused verification

| Check | Result | Evidence |
|---|---|---|
| Recursive provider traversal and UI | **30 passed** | [Folder run](../evidence/runs/folders-20260930T144058Z-b881b76a/folder-checks.json) |
| Chat/file/PDF/persistence regression without model execution | **35 passed** | [Chat/import run](../evidence/runs/chat-20260930T144112Z-69024c36/chat-checks.json) |
| Library/migration/CSV/package/locator regression | **60 passed** | [Knowledge run](../evidence/runs/knowledge-20260930T144121Z-2880e058/knowledge-checks.json) |
| Build/lint | Successful, 0 errors; 3 previously recorded upstream BouncyCastle warnings | [Lint](../evidence/releases/0.11.0/lint-results-debug.txt) |

The mixed nested tree imported six valid documents, reported three deliberate failures (unreadable subfolder, malformed CSV, oversized text), and skipped four unsupported/virtual/repeated entries. Traversal continued through readable siblings. Both same-name text files remained distinct, hidden Markdown and unknown file-size metadata were handled, and the nested PDF retained a page locator. A second import added zero duplicates and recognized all six unchanged files.

Additional checks cover canonical tree/single-document identity, deleting and reimporting a file, changed bytes retaining older snapshots/citations, an empty folder, cancellation after one successful file and retrying the remaining five, and actual schema3-to4 preservation. The Activity's Add folder action launches the directory-picker contract, imports the selected tree and shows the English summary. A separate 60-file tree imports all files and remains navigable in 50-item document pages.

Screenshots: [Settings](../evidence/runs/folders-20260930T144058Z-b881b76a/folder-settings.png), [summary](../evidence/runs/folders-20260930T144058Z-b881b76a/folder-summary.png), [paged documents](../evidence/runs/folders-20260930T144058Z-b881b76a/folder-paged.png). These are emulator captures, not design mockups.

## Test access and preserved setup failures

The tests use a real Android DocumentsProvider holding synthetic files, protected by MANAGE_DOCUMENTS. Its own test-package Activity issues read-only URI grants after instrumentation starts. The system picker Intent is intercepted by an ActivityMonitor for deterministic selection, so this is not a claim of manually testing every Android file-picker UI. The test restores prior import-summary preferences, removes only its own imported rows, revokes grants and disables the fixture provider afterward. No broad storage permission is added to the product.

Initial test setup could not change the provider component through Android shell. That setup failure is preserved in `folders-20260930T142102Z-b8c766d4`. Later pre-instrumentation grants were revoked when Android started instrumentation, producing the preserved denied-root outcomes in `folders-20260930T142327Z-7ed875a7` and `folders-20260930T142653Z-2175a143`. Owner-issued grants were moved inside the running test; the production permission boundary was not weakened. Subsequent successful runs and the final hashes above remain separate records.

## Scope and remaining limits

This is one-time snapshot import, not synchronization or a background watcher. Existing per-file limits remain. Traversal bounds are 20,000 listed items, 64 directory levels and 2,048-character paths; reaching a bound is reported, and only the first 200 detailed issues are retained. The 60-file test is not a large-corpus memory/latency benchmark or exhaustive limit test. Changed input creates another snapshot; old imports without source bindings cannot be retroactively identified by URI.

Provider reads or initial PDF parsing may delay cancellation. Completed files remain after cancellation/interruption; selecting the directory again skips unchanged completed snapshots. Automatic process-death resumption and comprehensive PDF orphan-file cleanup remain unimplemented. Scanned-only/encrypted/invalid PDFs and unsupported formats retain their earlier limitations. Chat quality, kernels, peak memory, battery, thermal and phone behavior were not reevaluated by these no-model import checks.

Read [folder behavior](folder-import.md), [handoff](handoff.md) and [emulator instructions](emulator-runbook.md) before resuming. Reproduce with `scripts/test-folders.ps1`, then `test-chat.ps1 -SkipInstall` and `test-knowledge.ps1 -SkipInstall` on the dedicated offline emulator.
