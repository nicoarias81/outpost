# Documentation index

Outpost 0.8.1, version code 10. Maintained English documentation, updated on 2026-09-30. The standalone local repository is `E:\projects\outpost`; historical Brújula evidence is archived separately. Start with the [current validation record](validation-0.8.1.md) for the current build.

## Start here when taking over

Read [AGENTS.md](../AGENTS.md) and [the engineering handoff](handoff.md). The latest scope clarification is [question families](offline-world-knowledge.md); the anonymized [discovery input](discovery-2026-09-29.md) is recorded alongside it. These updates are maintained here in the repository; earlier external proposal files are not the source of truth.

## Reading paths

| Purpose | Read in this order |
|---|---|
| Review progress since the handoff | [Independent status review, 2026-09-30](status-review-2026-09-30.md): verified state, recomputed performance, provenance corrections and next work |
| Understand the project | [Overview](project-overview.md), [current state](current-state.md), [roadmap](roadmap.md) |
| Define the questions Outpost should answer | [Contextual question families](offline-world-knowledge.md) |
| Trace the external acceptance bar | [Bounty record (poidh #31)](bounty-31.md) — verbatim bounty text, provenance and requirement trace; the bar is external and not under project control |
| Define or score the evaluation | [Evaluation protocol](evaluation.md), [fixture manifest](../eval/fixtures-v1.json) (15 fixtures: 11 runnable, 4 blocked), [rubric](../eval/rubric-v1.md), [validator guide](../eval/README.md) — the manifest and rubric define the benchmark; no fixture has been executed as a scored run, and the validator exiting 0 proves internal consistency only |
| Review new user input | [Personal knowledge and field-work discovery](discovery-2026-09-29.md) |
| Change the architecture | [Architecture](architecture.md), [knowledge contracts](knowledge-base.md), [decisions](decisions.md) |
| Work on performance | [Runtime](inference-runtime.md), [optimizations](optimizations.md), [speculation](speculation.md), [evaluation](evaluation.md) |
| Operate the emulator | [Emulator runbook](emulator-runbook.md): bootstrap, preflight, models, evidence and recovery |
| Build or reproduce results | [Development](development.md), [evaluation](evaluation.md), [dependencies](../THIRD_PARTY.md) |
| Prepare a standalone repository | [Migration and English baseline](repository-migration.md), [handoff](handoff.md) |
| Handle local data | [Security and data](security-and-data.md), [knowledge contracts](knowledge-base.md) |
| Decode terminology or release history | [Glossary](glossary.md), [changelog](../CHANGELOG.md) |

## Status vocabulary

- **Implemented:** present in the checked source. This alone does not imply adequate product quality.
- **Measured:** supported by a linked artifact, with a stated workload and environment.
- **Proposed:** a design or task that has not been implemented.
- **Deferred:** intentionally not being integrated under the current evidence or constraints.
- **Open:** a decision or investigation still required.

`current-state.md` owns the capability inventory. `roadmap.md` owns future work and acceptance criteria. `decisions.md` owns rationale and reconsideration conditions. Performance numbers belong in `optimizations.md` and `speculation.md`, linked to original evidence. Avoid introducing competing status lists in new documents.

## Historical records

The following reports remain in their original Spanish to preserve the experimental record. Their statements about future work are historical, not the current backlog. Use the English documents above for current status.

| Record | Scope |
|---|---|
| [0.1](validation-0.1.md) | Initial offline library |
| [0.2](validation-0.2.md) | Local generation |
| [0.3](validation-0.3.md) | Kev review experiment |
| [0.4](validation-0.4.md) | Bonsai model integration |
| [0.5](validation-0.5.md) | Ternary dot kernel and prompt/sampling work |
| [0.6](validation-0.6.md) | Grouped prefill, calibration, and cache |
| [0.8](validation-0.8.md) | 0.8.0 baseline: Outpost rename, English migration, and fresh validation |
| [0.7](validation-0.7.md) | Context speculation and MTP feasibility |
| [Two domains](two-domain-design.md), [field use cases](field-use-cases.md), [travel evaluation](travel-evaluation.md) | Earlier product and knowledge design |
| [Strata review](strata-review.md), [device runtime](device-runtime.md) | Earlier optimization hypotheses and capability design |
| [Original README](../README.legacy-es.md) | Full operational snapshot before English documentation |

## Maintenance

For each behavior change, update the owning document, its linked decision when applicable, and the relevant task status. Record the exact model hash, app version, test configuration, and limitations with measurements. Preserve failed results as well as successful ones. Translate historical reports as separate clearly labeled translations if needed; do not rewrite their observations to match later results.
