# Working on Outpost

Read [README.md](README.md), then the relevant guide: [architecture](docs/architecture.md), [build/release](docs/build.md) or [testing](docs/testing.md). Keep this small documentation set current; do not add per-session handoffs, version reports or duplicate status pages. Historical reasoning remains in Git and raw evidence remains under `evidence/`.

- Work in the Outpost repository (`E:/projects/outpost` on the current host). Use English for maintained UI, documentation, comments and primary fixtures; preserve imported languages and historical raw output.
- Outpost is an offline lookup and research assistant for practical explanation, comparison, synthesis and reasoning. Product targets include Android/GrapheneOS, no Google Play Services or runtime network dependency, an environment of at most 12 GB RAM and a complete offline footprint within 50 GB. Treat these as acceptance goals until their evidence exists; do not infer them from model size or a 16 GB Pixel run.
- Present Outpost as an independent product in the README, guides, repository metadata and release notes; omit external campaign names, links and endorsements.
- Outpost is a general offline knowledge assistant. Inference and knowledge ingestion are separate responsibilities. OSM provides chat knowledge, not navigation; equipment actions and deferred external messaging are not implemented or authorized by a source document.
- Inference/numerical execution is allowed only on Outpost35 (`emulator-5582`) and the specifically registered owner-authorized Pixel 10 Pro. Host builds/static analysis are allowed; preserve Brújula `emulator-5580`. Read the target/isolation/recovery instructions in [testing](docs/testing.md) before device work.
- License original project code under AGPL-3.0-only. Preserve LICENSE, NOTICE and all third-party terms.
- Preserve private data, phone settings, pinned dependencies/models, reference fallbacks and raw evidence, including failures. One workflow per target. Never uninstall or clear personal storage to bypass signing/install failures.
- Keep imports as snapshots, preserve exact citation identity and keep the product library empty initially. Fixtures/providers belong to test APKs. Keep fixture providers registered; cleanup hides roots and revokes grants.
- Gate native changes by CPU/OS support, compiled availability, shape and numerical validation. Preserve the fixed-attention barrier contract and original accumulation order; backend changes require re-audit. Capability detection is not proof that a kernel executed.
- Run focused checks. For docs-only work, verify links, fixtures and unchanged build receipts; do not rebuild or rerun models. A harness PASS is not an answer-quality claim.
- Models, SDKs, AVDs, private documents, caches and signing material stay outside Git. The owner made the repository public and approved the RC2 QA prerelease. Source pushes use the existing repository; do not change visibility. Production ID/key selection remains pending, and the QA certificate is not a production signing identity.

## Documentation rules

Use [ASD-STE100 Issue 9](https://www.asd-ste100.org/assets/files/ASD-STE100_ISSUE9.pdf) as the writing reference for the README and the three guides.

- Use a maximum of 20 words in a procedural sentence.
- Use a maximum of 25 words in a descriptive sentence.
- Give one instruction in each sentence, except for simultaneous actions.
- Use the active voice.
- Put a necessary condition before its instruction.
- Use one topic in each paragraph.
- Keep each paragraph within six sentences.
- Use the specified [technical terms](docs/architecture.md#technical-terms) consistently.
- Keep exact code symbols, file paths, commands, interface labels and measured values.
- Do not rewrite third-party license text or historical test output for this style change.

The project term list identifies software nouns and verbs outside the general dictionary. A sentence-length screen does not establish full vocabulary or grammar conformity. A complete conformity statement needs review against both parts of the standard. Do not claim certification from a local style check.
