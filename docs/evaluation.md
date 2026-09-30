# Evaluation and evidence protocol

Status: executable development evaluation and attributed review in Outpost 0.9; broader field/held-out validation remains pending. Historical Brújula results remain archived under `evidence/0.7-before-outpost`. Fresh English migration checks are described in [validation-0.8.md](validation-0.8.md); they are not substitutes for controlled optimization benchmarks.

## Separate the questions

| Layer | Hold fixed | Evaluate |
|---|---|---|
| Knowledge coverage | Mission and required facts | Whether the installed package contains applicable information |
| Retrieval | Corpus, query, expected source IDs | Relevant passages/entities, wrong-revision filtering, provenance, latency |
| Inference | Correct bounded evidence and confirmed context | Task understanding, supported answer, useful abstention, citations, truncation |
| Tools | Typed inputs and expected results | Arithmetic/units/geometry correctness and applicability |
| Integration | Complete mission and installed packages | Preparation, source inspection, context correction, cancellation, useful outcome |
| Runtime | Model/hash, prompt/tokens, sampler, cache and kernel settings | Numerical behavior, load/prefill/decode, RAM, stability |

## Question families across varied evidence

Evaluate the [type of contextual questions](offline-world-knowledge.md): specific facts, discovery, recommendations under constraints, comparisons/compatibility, explanations, cross-source synthesis and bounded troubleshooting. Sample different topics, locations where applicable, wording and evidence conditions. World, regional and personal material are content dimensions of this matrix, not separate products or two fixed demos.

Use restaurant recommendations and issue-list recall only as illustrative fixtures. Test supported reasoning, applicability, exact values, useful ranking criteria, source navigation, proportionate clarification and missing-data behavior across domains. Separate time to useful evidence from generation latency; do not let success on one familiar example stand in for broad utility.

## Mission fixture format

*(Formerly "Mission fixture format to implement"; the format described below is now implemented.)*

Each fixture should identify its domain, version, package hashes, source locators, initial context, user turns, missing facts, expected tool operations, supported outcomes, and critical errors. Record language and prompt template version explicitly. Include positive cases, absent-data cases, conflicting revisions, misleading nearby text, and a corrected user observation.

Use a held-out set after development fixtures are stable. Domain-specific operational references need qualified review before claims of real field usefulness. Current equipment IDs and procedures in runtime fixtures are fictitious.

Rubric dimensions: task completion; evidence applicability; supported claims; citation resolution; context handling; deterministic result correctness; handling of unknown conditions; and time to useful information. Keep critical failures separate from an averaged score. Refusing a supported simple calculation is also a quality failure.

### Implementation status (2026-09-30)

[Fixtures v2](../eval/fixtures-v2.json) defines 15 fixtures, 12 runnable and 3 blocked, with explicit retrieval/fixed-evidence modes. [Rubric v2](../eval/rubric-v2.md) and [the runner guide](../eval/README.md) define attributed review and immutable provenance. The production prompt builder selects actual supplied evidence; expected answers are not model context. V1 remains frozen for historical comparison.

The v1 prompt comparison and v2 baseline/diagnostics were executed and reviewed by the implementation assistant. Exact run links, budgets, build identities, outcomes and limits are in [validation-0.9](validation-0.9.md). The longer prompt was rejected; citation and conflict errors remain. Complete execution and bounded assertions are separate from reviewed quality. Unmeasured time-to-useful-information remains null with a reason.

The external bar remains in [bounty-31](bounty-31.md); no blinded/qualified study or bounty acceptance is established by development review.

## Performance protocol

1. Freeze app/build identity, backend revision, model SHA-256, input source version, rendered prompt/tokens, sampler, output budget, and timeout.
2. Record ABI/CPU/OS features, thread counts, batch and matrix widths, emulator RAM/CPU configuration, and whether the host is contended.
3. Separate model-cold, context-cold, partial-cache, and exact-cache cases. Exclude warmup explicitly where the test does so.
4. Alternate baseline/candidate order over repeated pairs. Compare medians and individual runs; investigate variance rather than selecting the fastest run.
5. Record load, prepare, prefill, decode, first-token and total time, reused tokens, output text/count, and stop reason. For speculation include proposal/acceptance counts, verification time and disablement.
6. Inspect answer quality and numerical behavior independently. A different output length can change latency; token-budget truncation must be visible.
7. Preserve failures and superseded candidates. Label estimates and oracle controls separately from executable production paths.

Do not infer battery life, thermal sustainability, real-phone latency, or peak memory from emulator wall time or post-run PSS. There are no confidence intervals in the existing small paired controls; the 5% adoption threshold is a working rule, not a statistical significance test.

## Existing tests and evidence

| Area | Entry point | Evidence |
|---|---|---|
| Knowledge/migration/CSV/packs | `test-knowledge.ps1` | [Final 61 checks](../evidence/runs/knowledge-20260930T122749Z-f6084045/knowledge-checks.json) |
| Contextual fixture execution | `test-evaluation.ps1`, `eval/review.py` | [0.9 outcomes and provenance](validation-0.9.md) |
| Library/import/UI | `test-emulator.ps1` | [checks.json](../evidence/0.7-before-outpost/checks.json), UI screenshots |
| Qwen generation/cancel/recovery | `test-generation.ps1` | [generation-checks.json](../evidence/0.7-before-outpost/generation-checks.json) |
| Kev controls | `test-review.ps1` | [review-checks.json](../evidence/0.7-before-outpost/review-checks.json), [0.3 interpretation](validation-0.3.md) |
| Three generator comparison | `test-bonsai.ps1` | [summary](../evidence/0.7-before-outpost/bonsai-summary.json), raw per-model outputs |
| Dot numerical checks | `test-kernel.ps1 -Phase numeric` | [16,241 checks](../evidence/0.7-before-outpost/optimization/kernel-numeric.json) |
| Dispatch policy | `test-kernel.ps1 -Phase dispatch` | [64 synthetic checks](../evidence/0.7-before-outpost/optimization/kernel-dispatch.json) |
| Grouped numerical checks | `test-kernel.ps1 -Phase batch` | [7,932 comparisons](../evidence/0.7-before-outpost/optimization/kernel-batch.json) |
| Native compile portability | `check-native-portability.ps1` | [ten objects](../evidence/0.7-before-outpost/optimization/portability.json), no ARM execution |
| Calibration / grouping / cache | `test-runtime.ps1 -Phase ...` | [calibration](../evidence/0.7-before-outpost/strata/runtime-calibrate.json), [grouping](../evidence/0.7-before-outpost/strata/runtime-batch.json), [cache](../evidence/0.7-before-outpost/strata/runtime-cache.json) |
| Speculation units and runtime | `test-speculation.ps1 -Phase ...` | [units](../evidence/0.7-before-outpost/speculation/speculation-unit.json), [benchmark](../evidence/0.7-before-outpost/speculation/speculation-benchmark.json), [guard](../evidence/0.7-before-outpost/speculation/speculation-guard.json), [UI](../evidence/0.7-before-outpost/speculation/speculation-ui.json) |
| MTP/checkpoint inspection | `audit-spec-models.py` | [model audit](../evidence/0.7-before-outpost/speculation/model-audit.json) |

The 0.7 report records 48 functional checks and 17 Qwen generation checks passing, plus the specific kernel/speculation checks above. These counts describe different scopes and should not be combined into a claim of overall product accuracy.

Some scripts overwrite fixed evidence filenames. Archive a run before repeating it if it is the only release record. The 0.9 evaluation and knowledge runners use immutable run directories; evaluation additionally snapshots input/source/build metadata. Applying this convention to legacy scripts remains E-03.

## Preserved adverse findings

- [Initial cache UI mismatch](../evidence/0.7-before-outpost/strata/cache-initial-ui-mismatch.json).
- [Initial speculative UI mismatch](../evidence/0.7-before-outpost/speculation/initial-v1/speculation-ui.json).
- [Batched logit differences](../evidence/0.7-before-outpost/speculation/speculation-energy-audit.json).
- [Traveler and mountaineer content failures](../evidence/0.7-before-outpost/speculation/mission-review.json).
- [Optimistic auxiliary-drafter cost estimate](../evidence/0.7-before-outpost/speculation/speculation-draft-cost.json).

The final mission harness may report successful execution and parity while the content review records failures. Both records are required. The result of a classifier, a keyword match, and a correct mission outcome are different observations.

## 0.10 split between product checks and historical evidence controls

Current chat behavior is verified by `scripts/test-chat.ps1`, including optional genuine multi-turn/source/cancel/restart generation. Those are plumbing/regression controls with synthetic files, not the broad contextual-quality benchmark. [Fixtures v3](../eval/fixtures-v3.json) keeps the earlier evidence-only matrix with explicit test-APK corpus provenance and current app identity. `ResearchPrompt` and `ChatPrompt` are different protocols; no old fixture score transfers automatically to product chat. Long/complex documents and representative human user tasks remain open.
