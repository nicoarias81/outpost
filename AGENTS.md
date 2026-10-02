# Working on Outpost

Read [README.md](README.md), then the relevant guide: [architecture](docs/architecture.md), [build/release](docs/build.md) or [testing](docs/testing.md). Keep this small documentation set current; do not add per-session handoffs, version reports or duplicate status pages. Historical reasoning remains in Git and raw evidence remains under `evidence/`.

- Work in the Outpost repository (`E:/projects/outpost` on the current host). Use English for maintained UI, documentation, comments and primary fixtures; preserve imported languages and historical raw output.
- Outpost is a general offline knowledge assistant. Inference and knowledge ingestion are separate responsibilities. OSM provides chat knowledge, not navigation; equipment actions and deferred external messaging are not implemented or authorized by a source document.
- Inference/numerical execution is allowed only on Outpost35 (`emulator-5582`) and the specifically registered owner-authorized Pixel 10 Pro. Host builds/static analysis are allowed; preserve Brújula `emulator-5580`. Read the target/isolation/recovery instructions in [testing](docs/testing.md) before device work.
- Preserve private data, phone settings, pinned dependencies/models, reference fallbacks and raw evidence, including failures. One workflow per target. Never uninstall or clear personal storage to bypass signing/install failures.
- Keep imports as snapshots, preserve exact citation identity and keep the product library empty initially. Fixtures/providers belong to test APKs. Keep fixture providers registered; cleanup hides roots and revokes grants.
- Gate native changes by CPU/OS support, compiled availability, shape and numerical validation. Preserve the fixed-attention barrier contract and original accumulation order; backend changes require re-audit. Capability detection is not proof that a kernel executed.
- Run focused checks. For docs-only work, verify links, fixtures and unchanged build receipts; do not rebuild or rerun models. A harness PASS is not an answer-quality claim.
- Models, SDKs, AVDs, private documents, caches and signing material stay outside Git. The owner authorized source pushes and GitHub Releases in the existing private repository; do not change visibility. Production ID/key selection remains pending, and the QA certificate is not a production signing identity.
