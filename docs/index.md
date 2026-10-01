# Documentation index

**Outpost 0.15.0 / version code 17**, maintained English documentation updated through **2026-10-01**. Canonical repository: `E:/projects/outpost`. Start with [AGENTS.md](../AGENTS.md), [handoff](handoff.md) and [current validation](validation-0.15.md).

## Reading paths and ownership

| Need | Owning documents |
|---|---|
| Resume work | [Handoff](handoff.md), [current capability inventory](current-state.md) |
| Understand the product | [Overview](project-overview.md), [contextual question families](offline-world-knowledge.md) |
| Find remaining work | [Roadmap](roadmap.md): stable task IDs, status, dependencies and acceptance criteria |
| Understand rationale | [Architecture](architecture.md), [decision register](decisions.md) |
| Use chat and prepare personal files | [Chat/PDF](chat-beta.md), [recursive folders](folder-import.md) |
| Answer questions about places | [OSM place-query behavior and bounds](osm-place-queries.md), [import/preparation/formats/limits](osm-import.md) |
| Change ingestion or storage | [Knowledge contracts](knowledge-base.md), [developer pack v1](knowledge-packs-v1.md), [data boundaries](security-and-data.md) |
| Build or run tests | [Development](development.md), [emulator operating guide](emulator-runbook.md), [dependencies](../THIRD_PARTY.md) |
| Review new kernel ideas | [DeepGEMM-Ascend static review and prioritized experiments](deepgemm-ascend-review.md) |
| Test the authorized Pixel 10 Pro | [Physical-device protocol](pixel10-testing.md), [Pixel results and limitations](pixel10-results-2026-10-01.md) |
| Run/review candidate models | [Candidate testing](candidate-testing.md), [Spark results and research-build identity](spark-candidate-results-2026-10-01.md) |
| Review Spark-X2.5 and storage stalls | [Model admission and NVMe/Engram analysis](spark-x25-storage-review.md) |
| Compare models and memory designs | [Model alternatives and emulator study](model-alternatives.md), [Engram review](engram-review.md), [pinned research metadata](../evidence/research/model-survey-20260930/survey-metadata.json) |
| Inspect actual arithmetic and CPU hotspots | [Encoding/kernel stack audit and app-only profile](inference-stack-audit-2026-10-01.md) |
| Improve inference | [Runtime](inference-runtime.md), [measured optimizations](optimizations.md), [ARM experiment](arm-optimization-2026-10-01.md), [historical x86 row experiment](kernel-rows-0.14.md), [speculation/MTP](speculation.md) |
| Inspect answer-pipeline behavior | [NeMo Relay review and proposed local traces](nemo-relay-review.md) |
| Define/score outcomes | [Evaluation protocol](evaluation.md), [runner/review guide](../eval/README.md), [v8 fixtures](../eval/fixtures-v8.json), [rubric v2](../eval/rubric-v2.md) |
| Verify current delivery | [0.15 validation](validation-0.15.md), [frozen release manifest](../evidence/releases/0.15.0/manifest.json) |
| Trace external/product input | [Bounty snapshot and requirement trace](bounty-31.md), [anonymized discovery](discovery-2026-09-29.md) |
| Understand naming and history | [Migration record](repository-migration.md), [changelog](../CHANGELOG.md), [glossary](glossary.md) |
| Review this documentation pass | [Documentation audit](documentation-audit-2026-09-30.md) |

`current-state.md` owns capability status; `roadmap.md` owns future work; `decisions.md` owns rationale/reconsideration. Release validation owns exact APK/build/run identities. Performance numbers belong in the runtime experiment records with original evidence links. User-flow docs own supported formats and limits. Avoid competing status lists or appended version corrections that leave obsolete instructions above them.

## Status vocabulary

- **Implemented:** present in checked source; not automatically useful/correct in every task.
- **Measured/validated:** bounded observation with linked artifact, workload and environment.
- **Partial:** delivered slice exists but named acceptance criteria remain.
- **Proposed/open:** work or decision still required.
- **Deferred/conditional:** revisit only when the stated evidence or scope condition changes.
- **Historical:** describes its recorded release/session, not current instructions or live state.

ARM build is not ARM runtime evidence; synthetic import checks are not a real-region recommendation study; citations and harness PASS do not prove answer correctness. Product `ChatPrompt` and evidence-only evaluation are different protocols. E-06 now checks the complete packaged ABI set; its former gap remains recorded in the historical documentation audit.

## Historical records

Prior release reports preserve observations, failures, old screen names and original language. Follow current guides for operations. Where a historical link points to a frozen archive, it refers to that snapshot, not newly rerun output.

| Record | Recorded scope |
|---|---|
| [0.1](validation-0.1.md), [0.2](validation-0.2.md), [0.3](validation-0.3.md) | Initial library, generation and Kev experiment |
| [0.4](validation-0.4.md), [0.5](validation-0.5.md), [0.6](validation-0.6.md), [0.7](validation-0.7.md) | Bonsai, Q2 kernels, grouping/cache, speculation/MTP investigation |
| [0.8](validation-0.8.md), [0.8.1](validation-0.8.1.md) | Outpost/English migration and grouped-width/reporting corrections |
| [Pre-0.9 status review](status-review-2026-09-30.md), [0.9](validation-0.9.md) | Prior independent inspection; packs/CSV, executable evaluation and attributed reviews |
| [0.10](validation-0.10.md), [0.11](validation-0.11.md) | Chat/PDF/dual-ABI packaging; recursive folder import |
| [Two domains](two-domain-design.md), [field cases](field-use-cases.md), [travel study](travel-evaluation.md) | Original Spanish design and illustrative question discussion |
| [Strata review](strata-review.md), [device design](device-runtime.md) | Historical optimization/capability hypotheses |
| [Legacy README](../README.legacy-es.md), [original evaluation task record](../odd/tasks/eval-fixture-manifest.md) | Preserved prior operational/specification snapshots |
| [Rubric v1](../eval/rubric-v1.md) | Frozen original scoring definition; v2 governs current attributed reviews |

## Maintenance

For behavior changes, update the owning guide/task/decision and record exact app/test/model/prompt/source identity with results and limitations. Preserve failed experiments and raw evidence; do not translate or rewrite old outputs as current observations. For docs-only updates, check source consistency, local links/anchors, fixture validation and unchanged build/artifact identities; no model rerun is needed.
