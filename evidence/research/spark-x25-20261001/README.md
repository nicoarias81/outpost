# Spark-X2.5 and storage research evidence

Baseline: Outpost `a6742d5` / 0.14.0, inspected 2026-10-01. See the [owning review](../../../docs/spark-x25-storage-review.md).

- [Source manifest](source-manifest.json) pins XHToken's repository and records hashes/URLs for the official NVIDIA pages and libnvme definitions. Fetched source/HTML bytes remain in local scratch; raw pages are not republished here.
- [Model metadata](model-metadata.json) pins four official HF repositories, configurations, generation defaults, template text and artifact metadata. Expected LFS hashes are publisher declarations, not local verification of full weights.
- [Candidate files](candidates.json) identifies the two Q4_K_M candidates. It is research input, not a runtime import allowlist.
- [Static analysis](static-analysis.json) binds backend/app inspection to source hashes and records theoretical KV and storage-fraction arithmetic. These calculations are not inference benchmarks or memory measurements.
- [Attributed PSA](user-report.json) separates user-supplied observations from facts independently corroborated in official documentation. No original post URL or raw benchmark logs were supplied.

No tensor files downloaded; no remote model code, storage benchmark, model inference, NVMe query or setting change executed. No physical-device work occurred. Full GGUF template/load, answer quality, runtime speed and RAM remain pending. Preserve this checkpoint when later measurements become available rather than rewriting reported observations as independently verified results.
