# eval/ — evaluation definition artifacts for Outpost

This directory holds the machine-readable definition of the Outpost offline
question-answering benchmark: a versioned fixture manifest, a host-only
validator for that manifest, and the scoring rubric. It defines the
benchmark; it does not measure it. Nothing in this directory runs the emulator,
runs model inference, or touches the network.

## Files

- `fixtures-v1.json` — the versioned fixture manifest. It declares the pinned
  application and model identity (cross-checked against `model-lock.json`,
  `bonsai-lock.json`, `judge-lock.json` and `llama-revision.txt`), the question
  families and evidence conditions from `docs/offline-world-knowledge.md`, the
  bounty requirements traced from poidh #31, and fifteen concrete fixtures:
  five field cases already exercised by the instrumentation tests, six new
  runnable fixtures (including two deliberate holdouts), and four fixtures
  blocked on roadmap capabilities (K-01, K-04, K-06, K-07, plus one seed
  fixture blocked on the missing typed arithmetic tool T-01).
- `validate.py` — a host-only validator (Python 3.9+, standard library only)
  that cross-checks the manifest against the pinned lock files, the seed
  library, `app/build.gradle` and `app/src/main/AndroidManifest.xml`. See
  "Running the validator" below.
- `rubric-v1.md` — the scoring rubric. It defines the eight review dimensions
  with 0-3 anchors, the critical-failure policy, the authority table, the
  language rule, and the bounty-relative protocol for `R-BAR-50PCT` (marked
  *proposed, not measured*). The fixture definitions live in `fixtures-v1.json`
  and are deliberately not duplicated here.
- A score sheet (per-run reviewed outcomes) — to be added when fixtures are
  first executed under the rubric.

## Running the validator

From the repository root:

```
python eval/validate.py
```

Exit code 0 means the manifest is internally consistent and matches the pinned
identity sources. Exit code 1 prints a numbered problem list. Warnings
(declared question families or evidence conditions that no fixture uses) are
reported but never change the exit code. The validator reads only; it never
writes, mutates, or contacts the network, so running it twice produces
byte-identical output.

## What the validator does and does not prove

A passing run proves only:

- that the manifest parses and satisfies its declared schema (ids, required
  keys, enum references, tier/blocker consistency, runnable fixtures carrying
  no tool requirements);
- that the declared application identity matches `app/build.gradle`;
- that the declared model hashes, byte counts, repositories and revisions
  match the root lock files exactly for **every** model profile — repositories
  and revisions are compared against the owning lock file's own top-level
  values where the per-file entry declares none, which includes the Kev
  repository declared at the top level of `judge-lock.json`;
- that the declared llama.cpp revision matches `llama-revision.txt`;
- that cited evidence documents exist in `app/src/main/assets/library.json`;
- that cited review assessments point at existing files under `evidence/`;
- that every string value anywhere in the manifest is literal text (no
  `TODO`/`FIXME` markers, no template or substitution syntax, no
  angle-bracket placeholders);
- that every `identity.prompts` entry whose source constant can be extracted
  reliably matches that source, and that the remaining entries are reported
  as named warnings rather than passing silently;
- that `AndroidManifest.xml` still declares no `android.permission.INTERNET`,
  which is the factual basis for the `R-NONETWORK` requirement being marked
  verifiable in the current scope.

It does **not** prove:

- that any answer produced on the emulator is correct, useful, or safe;
- that any fixture has been executed or scored — `status` is `design`;
- that any bounty requirement is satisfied;
- anything about phone latency, real-device behaviour, or peak memory.

## Authority limits

The manifest's `authority` object and `authority_levels` array are binding:
a deterministic check establishes only the asserted bounded property, a
reviewed outcome is one human's statement over recorded output, and
`unmeasured` means declared but never executed. See `ADR-011` and
`docs/evaluation.md` for the full authority model. No bounty requirement —
including `R-BAR-50PCT` — is satisfied by anything in this directory.

## Fixture keys: also_run_by, review_only, notes

Every fixture declares three keys that keep the review surface honest:

| Key | Meaning |
|---|---|
| `also_run_by` | Array of `{script, phase}` objects naming every additional harness script and phase that runs the same fixture. The five field cases are also run by `scripts/test-speculation.ps1` phase `missions`, because `SpeculationChecks.missions()` iterates the same `RuntimeChecks.fieldCases()` rows; all other fixtures declare `[]`. |
| `review_only` | `true` when the fixture carries no deterministic executable check and its outcome can only be established by human review. `review_only` exists because some fixtures are inherently judgement-based (an honest abstention, a clarifying question, an absence converted — or not — into a claim) and must be visible as such rather than appearing to have a deterministic check. |
| `notes` | Array of per-fixture clarifications, possibly empty. Every fixture without a deterministic check carries a `judgement-only: ...` note plus a one-line reason specific to that fixture; other notes record blocker scoping or vacuous-check history. |

## harness_prompt_wrapping

The `harness_prompt_wrapping` top-level key records exactly how the
instrumentation wraps a fixture before it reaches the model: the prefix
`SOURCE [1] — FICTITIOUS TEST DATA: ` prepended to `initial_context`, the
`\nQUESTION: ` and `\nANSWER:` delimiters, and the missions-phase cap of 96
output tokens per generation call. It also states the boundary that matters
when reading this manifest: a fixture's `initial_context` reproduces the
`fieldCases()` column and is **not** the live prompt. Each fact names its
source file and symbol in `RuntimeChecks.java` and `SpeculationChecks.java`.

## Status

The manifest is **not yet consumed** by the instrumentation harness; wiring it
into `RuntimeChecks`, `RetrievalChecks` and the harness scripts is E-02/E-03
roadmap scope. Until then the fixtures are definitions, not executable cases.

## Related documents

- `docs/evaluation.md` — evaluation protocol and authority model
- `docs/bounty-31.md` — poidh #31 bounty text, provenance and requirement trace
- `docs/offline-world-knowledge.md` — question families and corpus layers
