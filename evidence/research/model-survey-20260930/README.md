# Model and Engram research evidence

Captured 2026-09-30 against Outpost `511d6af`, backend `86ea01d05ec237f89b78b41c8c1ee0f908141ac7`.

- [Survey metadata](survey-metadata.json): public repository revisions, metadata/config/license/card hashes, extracted text configurations, publisher artifact names/sizes/LFS hashes and failed endpoint probes. Raw API/card/config bytes were downloaded to local scratch, not committed. Revision-bound URLs permit retrieval; API endpoints are mutable snapshots. An HTTP 401 on a guessed name does not establish a globally absent or gated model.
- [Candidates](candidates.json): exact proposed files and expected publisher hashes. This is neither a production lock nor a list of runtime-validated models. Unsloth conversions are distinguished from author-published artifacts.
- [Static analysis](static-analysis.json): local backend/app source hashes and relevant line matches; config-only cache estimates and Engram demo memory arithmetic. No allocation, speed, quality or numerical inference measurement.
- [Metadata collector](collect-public-metadata.py): standard-library, bounded public metadata/source downloader. Run with `python collect-public-metadata.py --output <new-scratch-directory>` to repeat discovery. It fetches current public revisions, so later results can differ; it does not download model tensors or execute imported code. Re-running it does not reproduce old mutable API bytes automatically.

The [model review](../../../docs/model-alternatives.md) owns interpretation and experiment order; [Engram review](../../../docs/engram-review.md) owns the memory distinction. Current app/model pins, packaged artifact, installed emulator and settings remain unchanged. Model execution and any future neural experiments remain emulator-only unless separately scoped by the user.
