# NeMo Relay static review provenance

The [review](../../../docs/nemo-relay-review.md) evaluates the NVIDIA article and a pinned Relay source snapshot against Outpost `5ca14f1` / 0.14.0.

- [Article source](article-source.json): URL, publication/access dates, byte count and SHA-256 of public HTML fetched into local scratch. HTML is not republished here; timestamps and page chrome can change its hash later.
- [Relay sources](source-manifest.json): upstream commit, metadata/tree identities and revision-bound URLs/hashes for 26 inspected files. The original documents remain upstream; a raw source cache was kept only in local scratch. The source workspace version is distinct from the article tutorial version.
- [Outpost source identity](outpost-source.json): files inspected to identify current metrics and missing lifecycle evidence. These hashes bind findings to source, not execution outcomes.

No upstream tutorial, model, container, package install or code execution was performed. No application, test harness, native engine, device state, settings or model pin changed. E-08 is a proposed experiment, and ATOF/ATIF compatibility is untested. New future trace evidence must distinguish observed execution, fixture verification and attributed answer review.
