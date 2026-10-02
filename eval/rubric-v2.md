# Evaluation rubric v2

Version: 2. Date: 2026-09-30. This file defines how Outpost answers are scored against the
fixtures frozen with each run (current default: [fixtures-v5.json](fixtures-v5.json); original executable definition: [fixtures-v2.json](fixtures-v2.json)): the rubric dimensions, the
critical-failure policy, what each evidence type proves, the language rule, a bounty-relative
comparison protocol, and the minimum shape of a recorded score.

The **fixture definitions live in the manifest** captured with the run. This rubric remains version 2 for revisions 2–5; navigation was refreshed on 2026-09-30 without changing scoring semantics. Product chat is a separate protocol; see [the runner guide](https://github.com/nicoarias81/outpost/blob/b6aac40022a2d01298782312ddc6cf44a498bbac/eval/README.md). This file
must not duplicate them: per-fixture critical failures, evidence conditions, and expected outcomes
are declared there, and this file only defines how they are judged. The evaluation protocol and
performance-measurement rules it builds on live in
[../docs/evaluation.md](https://github.com/nicoarias81/outpost/blob/b6aac40022a2d01298782312ddc6cf44a498bbac/docs/evaluation.md).

## Rubric dimensions

These are the eight review dimensions declared per fixture in the manifest (`review_dimensions`).
Each is scored 0–3. Every score must be anchored to something a scorer can inspect for an offline
application: the retrieved passages, the emitted citation indices, the full source a citation
opens, the recorded output text, the recorded timings, and the fixture's declared expectations.

### `task-completion`

Label: the answer accomplishes the fixture's declared successful outcome.

- **3** — The recorded output fully satisfies the fixture's `successful_outcome` using the
  installed evidence, with nothing required added and nothing forbidden added.
- **2** — The outcome is mostly satisfied but is incomplete, partially truncated, or contains
  minor additions the fixture does not ask for.
- **1** — The outcome is attempted but materially missed: the right topic, the wrong answer, or a
  required element is absent.
- **0** — The task is failed: refused although the installed evidence supports it, answered in the
  wrong language (see the [language rule](#language-rule)), or the outcome is not achieved at all.

Evidence the scorer must point at: the recorded output text compared against the fixture's
`successful_outcome` and declared expectations in the manifest.

### `evidence-applicability`

Label: the evidence the answer uses actually applies to the question as asked.

- **3** — Every piece of evidence used is applicable; inapplicable or misleading nearby material
  is not presented as an answer.
- **2** — Applicable evidence is used, but with minor over-reach (for example, leaning on
  topically adjacent text for a detail it does not contain).
- **1** — The answer relies substantially on inapplicable or misleading nearby evidence.
- **0** — The answer presents inapplicable or misleading nearby text as the answer, or ignores
  applicable evidence that the fixture declares must be used.

Evidence the scorer must point at: the retrieved passages shown to the model, compared against the
fixture's `evidence_conditions` and the documents actually cited.

### `supported-claims`

Label: every factual claim in the answer is traceable to retrieved evidence or is explicitly
marked as inference.

- **3** — All claims are supported by retrieved evidence or clearly flagged as model-generated
  inference (for example, arithmetic the notes do not contain).
- **2** — Claims are mostly supported, with one small unsupported detail.
- **1** — The answer mixes supported claims with unsupported assertions presented as sourced.
- **0** — The answer asserts facts absent from all evidence as if they were sourced, including
  parametric model knowledge presented as retrieval.

Evidence the scorer must point at: the recorded output text checked claim-by-claim against the
retrieved passages.

### `citation-resolution`

Label: emitted citation indices resolve to the exact local source that supports the claim.

- **3** — Every emitted `[n]` index is in range and resolves to the exact local source/version
  that supports the associated claim.
- **2** — All indices are in range, but at least one resolves to a source that only partly
  supports the claim.
- **1** — Indices are present but at least one points at the wrong source for its claim.
- **0** — An index is out of range, dangling, or decorative (a citation with no inspectable
  backing source).

Evidence the scorer must point at: the emitted citation indices in the recorded output, and the
full source each citation opens in the local corpus. An index in range proves resolution only,
never claim support (ADR-011, [../docs/decisions.md](https://github.com/nicoarias81/outpost/blob/b6aac40022a2d01298782312ddc6cf44a498bbac/docs/decisions.md)).

### `context-handling`

Label: the answer uses confirmed context correctly and asks only for context that materially
changes the outcome.

- **3** — Confirmed context is applied exactly; conflicting or obsolete records are kept separate;
  clarification is asked when — and only when — the fixture declares it is required.
- **2** — Context is handled correctly with minor clumsiness, such as restating rather than using
  a correction.
- **1** — The answer mishandles context: it ignores a declared correction, conflates two record
  meanings, or asks for context that does not change the answer.
- **0** — The answer invents context, silently resolves a declared conflict, or fails to ask a
  clarification the fixture marks as required.

Evidence the scorer must point at: the recorded output compared against the fixture's
`initial_context`, `clarification_required` flag, and `evidence_conditions`.

### `deterministic-result-correctness`

Label: numeric, unit, and logic results in the answer are exact and inspectable.

- **3** — Every computable result is exact, with inspectable inputs and units.
- **2** — Results are correct but presented without the inputs or units needed to inspect them.
- **1** — A result is correct in method but wrong in execution, or presented with the wrong unit.
- **0** — A computable result is wrong.

Evidence the scorer must point at: the recorded output values, and the fixture's
`executable_checks` details in the manifest for the expected values.

### `unknown-handling`

Label: the answer states absence, abstains, or clarifies when the evidence does not support an
answer — and answers when it does.

- **3** — Absence is stated plainly and precisely where coverage is absent; nothing is invented;
  and nothing is refused where the evidence does support an answer.
- **2** — Absence is stated, but hedged or imprecisely scoped.
- **1** — The answer neither states the absence clearly nor invents a substantive answer (for
  example, a vague hedge that implies coverage exists).
- **0** — The answer fabricates content where coverage is absent, or refuses a supported task.
  Refusing a task when the evidence supports it is a quality failure, not a safe outcome (see the
  [critical-failure policy](#critical-failure-policy)).

Evidence the scorer must point at: the recorded output compared against the fixture's
`evidence_conditions` (in particular `absent-coverage` and `held-out-entity`) and `holdout` flag.

### `time-to-useful-information`

Label: how quickly the user reaches the first useful, inspectable element of the answer —
separately from completed-answer latency.

- **3** — Useful information (a source, an exact value, a stated absence) is available well within
  the runtime budget declared in the manifest.
- **2** — Useful information arrives within the budget but late relative to the budget.
- **1** — Useful information arrives only near the deadline, or the answer completes without a
  clearly identifiable useful element before truncation.
- **0** — No useful information within the measured budget.
- **Not measured (`null`)** — First-useful-information timing was not measured. Supply a reason; do not substitute first-token time or invent a score. Exclude unmeasured dimensions from any descriptive average and disclose the missing measurement.

Evidence the scorer must point at: the recorded timings and their declared measurement scope
(model-cold, context-cold, partial-cache, exact-cache), as separated in
[../docs/evaluation.md](https://github.com/nicoarias81/outpost/blob/b6aac40022a2d01298782312ddc6cf44a498bbac/docs/evaluation.md).

## Critical-failure policy

Critical failures are **declared per fixture in the manifest** (`critical_failures`); this file
does not restate them. Policy:

- Any single observed critical failure **fails that fixture**, regardless of the averaged
  dimension score.
- Critical failures are **never averaged into a percentage**.
- A high average must **never compensate** for a critical failure.
- A task that is **refused when the evidence supports it is also a quality failure**: refusal is
  scored under `unknown-handling`, and where the fixture declares it critical, it fails the
  fixture.

## Authority table

What each evidence type proves, and what it does not.

| Evidence type | Proves | Does not prove |
|---|---|---|
| Deterministic check declared in the manifest, executed by a harness | That the bounded string or index assertion held over the recorded output. | That the answer is correct, useful, or complete — a passing assertion establishes only the asserted property (manifest `authority` block). |
| Reviewed outcome recorded under `evidence/` | That the explicitly attributed human or assistant reviewer inspected the recorded output and recorded an assessment. | Qualified field validation or real-world task success; the reviewer can be wrong and the scope is bounded. |
| Executed harness output | That the harness ran and what it recorded. | Answer correctness — a harness PASS does not establish that an answer is correct (project rule; [../docs/evaluation.md](https://github.com/nicoarias81/outpost/blob/b6aac40022a2d01298782312ddc6cf44a498bbac/docs/evaluation.md)). |
| Citation index in range | That the emitted index resolves within the sources provided to the model. | That the cited passage supports the claim — citation checks validate indices only (ADR-011). |
| Kev reviewer score | That a bounded reviewer model judged a first sentence against up to three passages. | Correctness — Kev has recorded false positives and is never evaluation ground truth (ADR-011). |

## Language rule

The manifest declares the expected language per fixture (`language`). Scoring is over the answer
content in that declared language. An answer that is correct but in the wrong language is a
**`task-completion` failure**, not a stylistic note.

## Bounty-relative protocol (R-BAR-50PCT) — proposed, not measured

This protocol targets bounty requirement `R-BAR-50PCT` (see
[../docs/bounty-31.md](https://github.com/nicoarias81/outpost/blob/b6aac40022a2d01298782312ddc6cf44a498bbac/docs/bounty-31.md)). **Nothing in this repository measures this bar
today.** The protocol is proposed only.

1. **Freeze** the question set from the manifest: fixture ids, initial contexts, user turns, and
   declared expectations are fixed before any run.
2. **Offline run**: answer the frozen set with Outpost on the emulator, offline, under the
   identity constraints recorded in the manifest (app version, model profile, sampler, prompt).
3. **Baseline run**: answer the same frozen set with network access, using a **named baseline
   model plus search**. This run executes **host-side and is labelled as a separate reference**:
   the offline constraint cannot be satisfied on the baseline side, and the emulator cannot be
   given network access during the offline run.
4. **Blind review**: review both answer sets blind, in randomised order, so the reviewer cannot
   tell which system produced which answer.
5. **Score**: per-question relative usefulness on a 0–3 scale.
6. **Compare**: the aggregate ratio is compared against the bounty's 0.5 bar.

Constraints on any claim drawn from this protocol:

- The 0.5 figure is **the bounty creator's wording restated**, not a derived threshold.
- A paired comparison of this kind **cannot establish the bounty's judgement**, which is
  ultimately a human confirmation described in the bounty text.
- **No result may be published** from this protocol without the frozen identities recorded (the
  minimum set is defined in the [scoring record shape](#scoring-record-shape) below).

## Explicit non-claims

- Emulator wall time does not establish phone latency.
- A post-run PSS sample does not establish peak memory.
- A favourable Kev score does not establish correctness.
- A citation index in range does not establish that the passage supports the claim.
- Object compilation does not establish ARM or GrapheneOS support.
- A passing harness does not establish field usefulness.

## Scoring record shape

A recorded score must carry, at minimum, so it stays auditable:

- Fixture id.
- Manifest version (the `manifest_version` of the manifest the fixture came from).
- App version and versionCode.
- Model profile and its sha256.
- Prompt identity (prompt source and version marker, per the manifest `prompts` block).
- Fixture language.
- The reviewer kind (`human` or `assistant`), name, and whether qualified domain review was involved.
- Rubric version 2; assistant development review must not be represented as a blinded human study.
- The raw output text, or a path to it.
- Per-dimension scores (all eight, or the fixture's declared `review_dimensions`, with any
  unlisted dimension explicitly marked not scored).
- Every critical failure observed (empty list if none).
- The time-to-useful-information measurement, with its measurement scope.

## Version boundary

Version 1 is retained unchanged. Version 2 separates missing timing measurements from observed zero usefulness and explicitly attributes assistant reviews. Fixture-specific expected outcomes and critical-failure lists remain frozen in each run's manifest. This does not retroactively turn observed failures into successes or establish bounty compliance.
