# Evaluation and evidence protocol

Current reference: **Outpost 0.15.0**, [validation](validation-0.15.md), [fixtures v8](../eval/fixtures-v8.json), [rubric v2](../eval/rubric-v2.md). Broad field/held-out quality remains open. Scored development experiments from 0.9 are historical; the current product protocol is `ChatPrompt` v1.1, distinct from the matrix's evidence-only `ResearchPrompt`.

## Separate the questions

| Layer | Hold fixed | Evaluate |
|---|---|---|
| Coverage | Mission and needed facts | Does installed material contain applicable information? |
| Retrieval | Corpus/query/expected sources | Relevant fragments/entities, wrong revisions, provenance and latency |
| Inference | Correct bounded evidence/context | Interpretation, supported answer, useful abstention, citation alignment and truncation |
| Tools | Typed inputs/expected results | Arithmetic/units/geometry and applicability, once tools exist |
| Integration | Prepared inputs and complete mission | Import, source inspection, context correction, cancellation and useful outcome |
| Runtime | Model/hash/prompt/sampler/cache/kernel | Numerical behavior, phase timings, memory and stability |

Evaluate [contextual question families](offline-world-knowledge.md) across world, regional and personal information: facts, discovery, recommendations under constraints, comparison/compatibility, explanations, synthesis and bounded troubleshooting. Restaurants and issue-list recall are illustrative fixtures, not two product-defining demos. Vary subjects, wording, ambiguous/missing/conflicting evidence and held-out entities. Source IDs, fluent language or a single successful example do not establish transfer.

## Fixture and quality protocol

The current matrix has 15 fixtures: 12 runnable and 3 blocked. Each declares execution mode, identities, context/turns, expected evidence, supported outcomes, review dimensions and critical failures. Concrete imports and fixed evidence are explicit; expected answers stay outside model input. Blockers describe complete workflows, so implementing PDF pages or OSM storage alone does not unlock the corresponding mission.

[The runner guide](../eval/README.md) owns execution flags, provenance, manifest history and the fixed full-ABI validator contract (E-06). Validate definitions without implying execution. Freeze exact source, manifest, prompt, APK and model identity per run; keep original outputs and failed setup logs. Preserve older records without inventing missing provenance.

Review task completion, applicability, supported claims, citation resolution, context, deterministic correctness, unknown conditions and time to useful information independently. A critical failure fails the fixture even if other scores are high. Refusing a supported simple calculation can also fail usefulness. Unmeasured dimensions stay null with a reason. Attribute assistant versus human/domain-expert review explicitly; held-out qualified review is still required for field claims.

The longer 0.9 prompt candidate was rejected after controlled comparison, and Qwen diagnostics exposed additional citation/conflict/date errors. [0.9 validation](validation-0.9.md) retains these results. They do not score the current chat or establish the external [bounty bar](bounty-31.md).

## Current implementation checks

| Scope | Entry point | Current evidence |
|---|---|---|
| Place name/category/proximity and no-model chat | `test-places.ps1` | 63 checks, including independently checked frozen public OSM identities/order and actual UI |
| OSM parser/storage/SAF/source UI | `test-osm.ps1` | 39 model-free checks; separate 41-check run includes one real synthetic-source answer |
| Recursive folder ingestion | `test-folders.ps1` | 30 checks |
| Chat/files/PDF/persistence | `test-chat.ps1` | 35 model-free checks; older multi-turn generation controls belong to their recorded release |
| Migrations/CSV/packs/locators/runtime policy | `test-knowledge.ps1` | 60 checks |
| Evidence-only fixture matrix | `test-evaluation.ps1` / `eval/review.py` | Attributed 0.9 development reviews; no newly scored broad v7 run claimed |
| Build/package/lint | `build.ps1`, `publish-artifact.ps1 -Verify` | ARM64+x86_64 packaging; 0 lint errors, 3 upstream warnings |

Current identities are in [0.15 validation](validation-0.15.md); [0.14 validation](validation-0.14.md) preserves earlier integration/OSM controls. The new place suite executes no generator and checks a frozen real-data subset as well as synthetic inputs. The older 33-token, 76.152-second Bonsai answer belongs to [0.12](validation-0.12.md); it is not a current timing or broad accuracy claim. Do not combine counts across suites into an accuracy percentage.

## Performance protocol

1. Freeze app/test APKs, backend, model SHA-256, source snapshot, full prompt/tokens, sampler, output budget and timeout.
2. Record ABI/CPU/OS features, threads, batch/width, emulator configuration and host contention.
3. Separate model-cold, context-cold, partial-cache and exact-cache cases; identify excluded warmup.
4. Alternate baseline/candidate order over repeated pairs, report medians and individual runs, and investigate variance.
5. Record load/prepare/prefill/decode/first-token/total time, reused tokens, output count/text and stop reason. For speculation record proposals, acceptance, verification cost and disablement.
6. Review quality and numerical behavior separately. Changed output length and token-limit truncation affect timing; parity with a wrong answer is not success.
7. Preserve adverse results, rejected candidates and oracle/cost estimates under their actual identities.

First token, useful source and completed useful answer are different metrics. Existing small controls have no confidence intervals; 5% is a working adoption threshold, not a significance test. Post-run PSS is not peak RAM, and emulator time establishes neither phone speed nor battery/thermal behavior.

## Historical evidence and failures

Use [optimizations](optimizations.md) and [speculation](speculation.md) for measured native history and original evidence links. Their cases include the [cache UI mismatch](../evidence/0.7-before-outpost/strata/cache-initial-ui-mismatch.json), [initial speculative UI mismatch](../evidence/0.7-before-outpost/speculation/initial-v1/speculation-ui.json), [batched logit differences](../evidence/0.7-before-outpost/speculation/speculation-energy-audit.json), [mission failures](../evidence/0.7-before-outpost/speculation/mission-review.json) and [unfavorable drafter estimate](../evidence/0.7-before-outpost/speculation/speculation-draft-cost.json). The filename energy-audit describes a numerical fixture, not energy measurement.

`evidence/0.7-before-outpost`, `evidence/archive/0.8.0`, `evidence/archive/pre-0.9`, `evidence/archive/0.9.0`, immutable `evidence/runs` and `evidence/releases` preserve different checkpoints. The 0.8.0 reconstruction has a PROVENANCE record. Raw archive/run/release line endings are protected in `.gitattributes`. Legacy scripts still overwrite fixed outputs: archive before rerunning and keep regenerated evidence commits separate from behavior changes.

A documentation audit needs source/link/identity checks, not another model run. A harness PASS, citation marker, classifier score and reviewed task outcome remain different observations.

## Row optimization evidence in 0.14

[The row-kernel record](kernel-rows-0.14.md) separates synthetic full-matrix measurements, tuning prompts, an intentionally interrupted combined-policy run and final phase-separated confirmation. Confirmation uses three alternating pairs per case and the product 192-token cap, requires normal EOS and exact output/first-logit parity, and saves a local profile only after lifecycle checks. The confirmation prompts were reused after inspecting combined-policy behavior; they are development controls, not a blinded held-out study. Native generation-call total is not tap-to-answer UI latency. Weight/model quality and OSM answer speed are separate questions.

## Proposed lifecycle trace experiment

The [NeMo Relay review](nemo-relay-review.md) identifies a gap between existing per-run identities/native timings and a correlated trace of the complete answer pipeline. E-08 proposes local events around route selection, retrieval attempts, evidence clipping, generation, persistence and UI completion, while keeping task verification/review separate. Measure tracing overhead and incomplete/canceled runs before using the traces for comparisons. This is a design checkpoint, not a new exporter or result; it does not change current test commands or install external infrastructure.

## Executed candidate study — 2026-10-01

[Spark/Bonsai results](spark-candidate-results-2026-10-01.md) use a separate frozen test asset and `chat-v1.1`, not the evidence-only matrix. Eight development questions were run with fixed evidence and isolated SQLite retrieval;24 predeclared questions were run with fixed evidence. Greedy diagnostic reviews are attributed to the implementing assistant and use descriptive candidate-review-v1 labels, not rubric-v2 numeric scores or independent field evaluation. Two sampled timing controls use one fixed seed and three repeated pairs; source/quality errors prevent a clean model-promotion claim. Preserve failed admission/cache experiments and distinguish paired timing repetitions from unique quality examples.

The harness captures native-call/memory/fault diagnostics with measured sampling gaps, not the complete E-08 route/retrieval/UI lifecycle. Profile overhead and host contention remain limitations. Future sampled-policy studies need additional seeds and fresh cases; do not tune on this reserved set and reclassify it as unseen evidence.
