# Pixel 10 Pro evidence — 2026-10-01

[Results](../../../docs/pixel10-results-2026-10-01.md) and [guarded runbook](../../../docs/pixel10-testing.md) own interpretation and reproduction.

`validation.json` binds the successful 23/37/45-check runs, visual review and data/screen restoration. `pilot-analysis.json`, `pilot-review.json` and `paired-summary/` are derived from the raw 32-answer pilot. Review labels are the implementing assistant's attributed decisions, not an automatic grader or independent field evaluation. Source scripts are retained under `analysis-scripts/`.

The initial/final device snapshots contain limited technical metadata. Full serial registration, models, APK backups and private app state are not in Git. The original user databases/preferences stayed on the phone and were restored with matching aggregate file digests. The visual isolation journal is in `evidence/runs/pixel-isolation-20261001T104400Z-4cf26208`.

Build receipts preserve the first preparatory test APK, the actually executed admission/pilot APK, the intermediate keep-awake APK, and the final visual APK. Main/native APK bytes remained unchanged. No different same-version bytes replaced the frozen user-test release.

Two pre-execution stops remain in raw evidence: a PowerShell variable collision before model execution and the visual suite's existing-content guard. The final suite used temporary isolated app data. No personal message/document contents are included here.
