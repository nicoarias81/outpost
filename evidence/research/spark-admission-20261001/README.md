# Executed Spark research evidence

See the [results and limits](../../../docs/spark-candidate-results-2026-10-01.md) and [reproduction guide](../../../docs/candidate-testing.md). This checkpoint advances research admission, not product model selection or field acceptance.

- `verified-model-header.json` records static GGUF metadata plus the full verified weight identity. The weights themselves remain outside Git/APK.
- `metrics.json` and `answers-for-review.json` are derived by `eval/summarize-candidates.py` from the exact report hashes recorded inside. They describe execution; they do not score correctness.
- `pilot-review.json`, `heldout-review.json`, and `timing-review.json` contain attributed candidate-review-v1 judgments with text/report hashes. The implementing assistant also authored the fixtures and review; this is not independent expert evaluation. Repeated timing texts and identical fixed/retrieval outputs are not extra independent quality samples.
- `compact-cache-decision.json` preserves the memory observation and failed first-logit-equivalence gate. Same short text does not establish equivalent distributions or general quality.
- `provisioning.json` separates binary-transfer failures from model behavior.
- `build-receipt.json`, `validation.json`, and `emulator.json` bind the same-version research APK pair, checks, and final offline/Bonsai-selected state. They do not replace the frozen release0.14 identities.

Original run reports, partial failures, source snapshots and synthetic UI captures remain in `evidence/runs/candidate-*` and the linked chat regression run. Intermediate/final research APK bytes are retained locally under ignored `.local/candidate-apks`; they are not published artifacts. No phone, host model inference, NVMe policy change, Relay deployment or new product model promotion occurred.
