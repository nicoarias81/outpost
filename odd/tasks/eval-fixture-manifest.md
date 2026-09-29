# Feature: eval-fixture-manifest

Status: implemented, verified and committed as three work-unit commits; native review to be redone per
work unit
Created: 2026-09-29
Roadmap scope: E-01, W-01 (`docs/roadmap.md`)
Owning docs: `docs/evaluation.md` (protocol), `docs/offline-world-knowledge.md` (families), `docs/bounty-31.md` (new, external bar)

## Problem

E-01 asks for a versioned mission fixture manifest and rubric; W-01 asks for a varied question-family
benchmark. Today the only machine-readable expectations are hardcoded inside instrumentation tests
(`RuntimeChecks.fieldCases()` marker column, `RetrievalChecks.CASES`, `JudgeChecks.CASES`). The rubric
exists only as prose in `docs/evaluation.md`. There is no fixture manifest, no rubric artifact, no
score sheet, and no machine check that a declared identity still matches the pinned lock files.

Separately, the bounty that originated the project (poidh #31) supplies the external acceptance bar
(`>50% as good as internet search + frontier AI models`) and a set of hard requirements that are not
recorded anywhere in the repository.

## Scope boundary — user decisions, 2026-09-29

- Physical device execution stays out of scope. The emulator remains the only execution target
  (ADR-003 stays active). No `test-*.ps1` guard may be bypassed.
- A **private** GitHub repository is authorized by the user (2026-09-29). Public visibility happens
  only at the final usable version. Creating the remote is a separate operational step, listed as
  task 5, and it is not part of tasks 1-4. No release, no visibility change, and no license
  selection in this feature.
- Bounty requirements about real device, GrapheneOS, RAM, storage, real-device latency, public
  repository, and public proof are **recorded and traced but out of current verification scope**.
  They must never be reported as met.
- Bounty question-quality requirements are in scope: explanation, comparison, synthesis, reasoning
  beyond simple factual recall.
- This feature authors evaluation definition artifacts plus one host-only validator. It does not run
  the emulator, does not run model inference, does not touch `app/`, and does not change any runtime
  behaviour.

## Deliverables

| # | Path | Kind |
|---|---|---|
| 1 | `eval/fixtures-v1.json` | new — machine-readable fixture manifest |
| 2 | `eval/validate.py` | new — host-only validator (no network, no emulator) |
| 3 | `eval/README.md` | new — directory purpose, how to validate, authority limits |
| 4 | `eval/rubric-v1.md` | new — rubric, critical-failure policy, bounty-relative protocol |
| 5 | `docs/bounty-31.md` | new — bounty text, provenance, requirement trace, scope boundary |
| 6 | doc wiring | edits in `docs/index.md`, `docs/roadmap.md`, `docs/evaluation.md`, `docs/handoff.md`, `docs/decisions.md`, `docs/current-state.md` |

## Fixed design (workers implement; do not redesign)

### Manifest schema — `eval/fixtures-v1.json`

Top-level keys, all required:

- `manifest_schema_version` — string, `"1"`.
- `manifest_id` — `"outpost-eval-fixtures"`.
- `manifest_version` — semantic version string.
- `created` — ISO date.
- `status` — `"design"` (no fixture in this file has been executed as a scored benchmark yet).
- `authority` — object with `proves` (string array) and `does_not_prove` (string array). Must state that
  a passing deterministic check, a citation index, or a Kev score does not establish answer
  correctness, and that this file is not measurement evidence.
- `identity` — object:
  - `app`: `name` Outpost, `applicationId` `dev.outpost.app`, `versionName` `0.8.0`,
    `versionCode` 9, `nativeLibrary` `outpost_engine`, `abiFilters` `["x86_64"]`,
    `minSdk` 28, `targetSdk` 35, `compileSdk` 35.
  - `backend`: `llamaCppRevision` from `llama-revision.txt`,
    `ndk` `28.1.13356709`, `cmake` `3.22.1`, `gradle` `8.11.1`, `agp` `8.9.2`.
  - `models`: one entry per locked profile with `repo`, `revision`, `file`, `bytes`, `sha256`,
    `quantisation`. Sources: `model-lock.json` (qwen), `bonsai-lock.json` (bonsai17, bonsai4),
    `judge-lock.json` (kev). Copy values from the lock files; do not retype from memory.
  - `prompts`: `research_prompt` (`app/src/main/java/dev/outpost/app/ResearchPrompt.java`,
    symbol `SYSTEM`, `version` `"unversioned"` + `version_note`), `field_fixture_system`
    (`RuntimeChecks.SYSTEM`), `speculation_system` (`SpeculationChecks.SYSTEM`),
    `bonsai_policy_string` (`TernaryChecks` `policy`, notes `"compact prompt v2"`).
  - `runtime_budget`: `contextTokens` 2048, `maxOutputTokens` 192, `deadlineSeconds` 120,
    `bonsaiSampler` `top_k 20 / top_p 0.8 / temp 0.7 / seed 42`, `qwenSampler` `greedy`.
  - `execution_scope`: `"emulator-only"`, with `emulator` object `avd` `Outpost35`,
    `serial` `emulator-5582`, `ramMiB` 4096, `dataPartitionGiB` 10, `cpuCores` 4, and
    `preserved_other_instance` `emulator-5580`.
- `harness_prompt_wrapping` — object describing how the instrumentation harness wraps a fixture
  before it reaches the model: the exact prefix prepended to `initial_context`, the `QUESTION:`
  delimiter, the output-token cap, and a statement that a fixture's `initial_context` is the
  `fieldCases()` column and NOT the live prompt. Source: `RuntimeChecks.java` and
  `SpeculationChecks.java`.
- `question_families` — array of the seven families from `docs/offline-world-knowledge.md`, each
  `{id, label, example, bounty_requirement_ids}`:
  `specific-fact-in-context`, `contextual-discovery`, `recommendation-with-constraints`,
  `comparison-and-compatibility`, `explanation-and-application`, `cross-source-synthesis`,
  `bounded-troubleshooting`.
- `evidence_conditions` — array of `{id, definition}`:
  `sufficient-coverage`, `multi-source`, `absent-coverage`, `ambiguous-reference`,
  `insufficient-context`, `conflicting-revisions`, `obsolete-record`, `misleading-nearby-text`,
  `held-out-entity`.
- `evidence_layers` — array of `{id, status}`: `world-reference` (implemented, 6 seed notes),
  `regional-activity` (not implemented), `personal-document` (implemented, UTF-8 text/Markdown
  import), `user-observation` (not implemented), `mixed` (composition marker).
- `authority_levels` — array of `{id, means, does_not_mean}`:
  `deterministic-check` (a bounded string/index assertion over recorded output),
  `reviewed-outcome` (a human statement recorded in an evidence JSON),
  `unmeasured` (declared, never executed).
- `bounty_requirements` — array of `{id, text, verifiable_in_current_scope, verification_route,
  scope_note}`. `verifiable_in_current_scope` is one of `yes`, `partial`, `no`, `informational`.
  Required ids and text, quoting the bounty:

  | id | text (short form) | verifiable now |
  |---|---|---|
  | `R-ANDROID` | run on Android | partial — emulator x86_64 only |
  | `R-GRAPHENEOS` | compatible GrapheneOS hardware | no — out of scope by user decision |
  | `R-RAM-12GB` | max 12 GB RAM environment | partial — no peak measurement exists (P-04) |
  | `R-STORAGE-50GB` | max 50 GB total for app, weights, indexes, databases | partial — host sum is measurable |
  | `R-OFFLINE` | work completely offline once installed | partial — scripts force airplane mode on the emulator |
  | `R-NONETWORK` | no API calls, remote inference, web searches or other network requests during use | yes — manifest declares no INTERNET permission |
  | `R-NOGMS` | no Google Play Services for core offline functionality | yes — AOSP system image, no GMS dependency |
  | `R-BEYOND-RECALL` | handle explanation, comparison, synthesis, reasoning beyond factual recall | partial — fixtures defined, not measured |
  | `R-SPEED` | usable lookup speed on a phone | no — emulator timing is not phone performance |
  | `R-PUBLIC-REPO` | published public GitHub repo, reproducible, documented | partial — a private GitHub remote is authorized; public visibility is deferred to the final version |
  | `R-REAL-DEVICE` | must work on real Android hardware at submission | no — user deferred |
  | `R-PROOF` | public demo on X or Farcaster plus poidh screenshot | no — out of scope |
  | `R-BAR-50PCT` | >50% as good as internet search + frontier models | no — protocol defined, not measured |
  | `R-ARCH-OPEN` | architecture open, no parameter-count limit | informational |

- `fixtures` — array; every entry uses this shape (all keys present; use `null` or `[]` explicitly
  rather than omitting):

```
{
  "id": "<kebab-case>-v1",
  "tier": "runnable" | "blocked",
  "domain": "<string>",
  "language": "en",
  "question_families": ["<family id>", ...],
  "evidence_conditions": ["<condition id>", ...],
  "evidence_layers": ["<layer id>", ...],
  "clarification_required": true | false,
  "holdout": true | false,
  "requires_tools": ["T-01", ...],
  "defined_in": { "path": "<repo-relative>", "symbol": "<symbol>" } | null,
  "harness": { "script": "scripts/test-runtime.ps1", "phase": "missions" } | null,
  "also_run_by": [ { "script": "<path>", "phase": "<string>" } ],
  "import_payloads": [ { "filename": "<string>", "content": "<string>" } ],
  "review_only": true | false,
  "notes": [ "<string>", ... ],
  "initial_context": "<string>",
  "turns": [ { "role": "user", "content": "<string>" } ],
  "expected_evidence": {
    "locator_kind": "passage" | "page" | "row" | "entity" | "none",
    "document_ids": ["<id from app/src/main/assets/library.json>"],
    "resolvable_now": true | false,
    "note": "<string>"
  },
  "successful_outcome": "<string>",
  "critical_failures": ["<string>", ...],
  "executable_checks": [
    { "id": "<kebab-case>", "kind": "citation-index-range" | "cited-document-id" |
      "substring-present" | "substring-absent", "authority": "deterministic-check",
      "detail": { } }
  ],
  "review_dimensions": ["<rubric dimension id>", ...],
  "observed_0_8": { "assessment": "<string>", "evidence": "<path or null>",
                    "marker_present": true | false | null,
                    "authority": "reviewed-outcome" | "unmeasured" },
  "bounty_requirement_ids": ["R-...", ...],
  "blocked_by": ["<roadmap id>", ...]
}
```

Rubric dimension ids (fixed, from `docs/evaluation.md`): `task-completion`,
`evidence-applicability`, `supported-claims`, `citation-resolution`, `context-handling`,
`deterministic-result-correctness`, `unknown-handling`, `time-to-useful-information`.

Revised after independent verification: `seed-kwh-power-v1` moved from `runnable` to `blocked` on
`T-01`, and the runnable explanation-only sibling `seed-kwh-distinction-v1` was added, because a
runnable fixture must not require an unimplemented capability. `personal-record-row-lookup-v1`
narrows to `blocked_by ["K-01"]`. `also_run_by`, `review_only` and `notes` were added to the fixture
shape to record where a fixture also runs, to mark judgement-only fixtures honestly, and to carry
per-fixture clarifications instead of overloading existing fields.

### Fixtures to encode (15)

Existing five field cases — `defined_in` `app/src/androidTest/java/dev/outpost/app/RuntimeChecks.java`,
symbol `fieldCases`, harness `scripts/test-runtime.ps1` phase `missions` and
`scripts/test-speculation.ps1` phase `missions`. **Copy `context` and `query` verbatim from the
source file**; do not paraphrase. Expected marker: traveler `10:00`, farmer `F-28`, field-engineer
`""`, mountaineer `250`, driver `42`. `observed_0_8` comes from
`evidence/strata/mission-review.json` (`assessment` text) with authority `reviewed-outcome`;
traveler, farmer, field-engineer are failures/critical, mountaineer and driver are supported for
that fixture only. Ids: `traveler-luggage-v1`, `farmer-filter-replacement-v1`,
`field-engineer-authorization-v1`, `mountaineer-elevation-v1`, `driver-manual-page-v1`.

New runnable fixtures, tier `runnable`, `defined_in` `null` until E-02 materialises them, with the
`initial_context` and user turn written out in full:

1. `seed-kwh-power-v1` — **tier `blocked`, `blocked_by ["T-01"]`**, `requires_tools ["T-01"]`,
   `executable_checks []`, `review_only true`, `observed_0_8` `"not implemented"` / `unmeasured`.
   Domain `energy-units`; families `specific-fact-in-context`, `explanation-and-application`;
   conditions `sufficient-coverage`; layers `world-reference`. Context: the six seed notes are
   available. Turn: "Using only these notes: a device used 24 kWh over 6 hours. What average power in
   kW does that imply, and which note explains the difference between kW and kWh?" Successful
   outcome: cites the `energy` note ("Power and energy: kW versus kWh"), states 4 kW, distinguishes
   power from energy. Critical failures: asserts the computed value came from a note or a tool;
   states 24 kW or 6 kW; claims the note supplies a precision it does not. `notes` must record why it
   is blocked: the declared outcome includes an exact numeric result whose correctness depends on the
   typed arithmetic tool T-01, which does not exist, and scoring it today would grade generated
   arithmetic as if it were a deterministic tool result, which `docs/roadmap.md` T-01 forbids.
   Bounty ids: `R-BEYOND-RECALL`, `R-OFFLINE`.
1b. `seed-kwh-distinction-v1` — **tier `runnable`**, `requires_tools []`, `holdout false`,
   `clarification_required false`, domain `energy-units`; families
   `explanation-and-application`; conditions `sufficient-coverage`; layers `world-reference`. Turn:
   "Which of these notes explains the difference between kW and kWh, and in one sentence what is the
   difference?" Successful outcome: cites the `energy` note ("Power and energy: kW versus kWh") and
   states that power is a rate while energy is power multiplied by time. Critical failures: presents
   any computed numeric result as if it came from the note or a tool; cites `solar` or
   `solar-efficiency`; states that kW and kWh are the same thing. Executable check:
   `cited-document-id` on `energy`. Review dimensions: `evidence-applicability`, `supported-claims`,
   `citation-resolution`. Bounty ids: `R-BEYOND-RECALL`, `R-OFFLINE`.
2. `seed-gps-accuracy-synthesis-v1` — domain `satellite-positioning`; families
   `explanation-and-application`, `cross-source-synthesis`; conditions `sufficient-coverage`,
   `multi-source`; layers `world-reference`. Turn: "I am 30 m off from where the map says I am.
   Which note explains why that can happen, and what should I not conclude about my exact position?"
   Successful outcome: cites `gps-accuracy` ("Why GPS accuracy changes") and does not treat the notes
   as a precision guarantee. Critical failures: claims a guaranteed precision; explains accuracy
   only from `gps`; invents an error budget. Executable check: `cited-document-id` on `gps-accuracy`.
   Review dimensions: `evidence-applicability`, `supported-claims`, `citation-resolution`.
3. `seed-absent-pois-v1` — domain `local-discovery`; families `contextual-discovery`; conditions
   `absent-coverage`, `held-out-entity`; layers `regional-activity`; `holdout true`;
   `clarification_required false`; `executable_checks []` and `review_dimensions`
   `unknown-handling`, `evidence-applicability`. Turn: "I am at this station with no signal. Which
   pharmacies are within walking distance?" Successful outcome: states that this installation has no
   local place information and that it cannot answer; does not name any place. Critical failures:
   names a specific pharmacy or address; cites a demonstration note as a place record; implies
   current opening hours. Bounty ids `R-BEYOND-RECALL`, `R-OFFLINE`.
4. `seed-ambiguous-reference-v1` — domain `personal-recall`; families `specific-fact-in-context`;
   conditions `ambiguous-reference`, `insufficient-context`; layers `personal-document`;
   `clarification_required true`; `executable_checks []`. Turn: "What revision was the problem in
   the record I imported last week?" Successful outcome: asks which document, or states that it
   cannot identify the record; offers no invented revision. Critical failures: invents a revision
   number or a filename; claims to have located a document it did not retrieve.
   Review dimensions `context-handling`, `unknown-handling`.
5. `seed-conflicting-revisions-v1` — domain `personal-recall`; families
   `comparison-and-compatibility`; conditions `conflicting-revisions`, `obsolete-record`,
   `sufficient-coverage`; layers `personal-document`. `import_payloads` holds two synthetic UTF-8
   Markdown notes with the same subject, declared revision numbers, distinct dates, and a
   contradicting torque value; write both bodies out in full in the manifest. Turn: "These two
   imported notes disagree about the torque value. Which do I use, and why?" Successful outcome:
   keeps both evidence items separate, reports the conflict with both declared revisions, states what
   would resolve it, and does not silently pick one. Critical failures: silently picks one value;
   claims the records agree; invents a supersession rule; drops the older revision without saying so.
   Executable check: `citation-index-range`. Review dimensions `evidence-applicability`,
   `context-handling`, `unknown-handling`.
6. `seed-holdout-plug-type-v1` — domain `travel-reference`; families `specific-fact-in-context`;
   conditions `absent-coverage`, `held-out-entity`; layers `world-reference`; `holdout true`;
   `executable_checks []`. Turn: "What power plug type does this country use?" Successful outcome:
   states the installation has no coverage; does not answer from model weights. Critical failures:
   answers with a plug type as if sourced; cites a note; hedges without stating the absence.
   This fixture exists specifically to catch parametric knowledge presented as retrieval.
   Review dimensions `unknown-handling`, `supported-claims`.

Blocked fixtures, tier `blocked`, non-empty `blocked_by`, `executable_checks []`,
`observed_0_8` `{"assessment": "not implemented", "evidence": null, "marker_present": null,
"authority": "unmeasured"}`:

7. `doc-manual-page-locator-v1` — families `bounded-troubleshooting`; conditions
   `sufficient-coverage`; layers `personal-document`; `blocked_by ["K-01", "K-04"]`;
   `expected_evidence.locator_kind` `"page"`, `resolvable_now false`. Bounded troubleshooting over a
   page-aware manual with an exact model/revision and a numeric observation.
8. `regional-poi-recommendation-v1` — families `recommendation-with-constraints`, conditions
   `sufficient-coverage`, layers `regional-activity`, `blocked_by ["K-06", "K-07"]`,
   `expected_evidence.locator_kind` `"entity"`, `resolvable_now false`. Must declare at least three
   different subject matters and must state that a named restaurant is one illustrative subject, not
   a product vertical, per ADR-017 and `docs/offline-world-knowledge.md`.
9. `personal-record-row-lookup-v1` — families `specific-fact-in-context`,
   `cross-source-synthesis`; conditions `conflicting-revisions`, `ambiguous-reference`; layers
   `personal-document`, `mixed`; `blocked_by ["K-01"]`;
   `expected_evidence.locator_kind` `"row"`, `resolvable_now false`. Row-aware lookup in an imported
   structured record with two distinct date meanings. `notes` must record that the row-aware
   synthetic-file work is owned by roadmap D-02 and that K-02 becomes a blocker only once this case
   is promoted from an imported file to a versioned package; K-03 gates a schema change, not this
   retrieval case.

- `capability_blockers` — array of `{roadmap_id, capability, source_of_truth}` covering `K-01`,
  `K-02`, `K-03`, `K-04`, `K-06`, `K-07`, `T-01`, `P-04`.
- `holdout_policy` — object: what is held out, that held-out entities must not be added to the seed
  corpus, and that a held-out fixture passes by abstaining or clarifying, not by answering.

### Validator — `eval/validate.py`

Python 3.9+, standard library only, no network, no emulator, no mutation. Reads the manifest plus
`app/src/main/assets/library.json`, `model-lock.json`, `bonsai-lock.json`, `judge-lock.json` and
`llama-revision.txt`. Exits 0 when clean, 1 with a numbered problem list otherwise; prints a short
OK summary with fixture counts by tier.

Checks:

1. Manifest parses; `manifest_schema_version` is a known value.
2. Every required top-level key is present and non-empty.
3. Fixture ids are unique and match `^[a-z0-9]+(-[a-z0-9]+)*-v[0-9]+$`.
4. Every fixture carries the full key set with explicit `null`/`[]`, never a missing key.
5. `question_families`, `evidence_conditions`, `evidence_layers`, `review_dimensions`,
   `bounty_requirement_ids`, `requires_tools` all reference declared ids. `requires_tools` ids must
   exist as keys in `capability_blockers`. `question_families[].bounty_requirement_ids` must also
   resolve against `bounty_requirements`. A fixture with `tier == "runnable"` must have an empty
   `requires_tools`, so a rumnable fixture can never require an unimplemented capability.
6. `tier == "blocked"` implies non-empty `blocked_by` and every blocker declared in
   `capability_blockers`; `tier == "runnable"` implies empty `blocked_by`.
7. Every runnable fixture has at least one `executable_checks` entry or is `holdout true` or
   `clarification_required true` or `review_only true`. A fixture with an empty `executable_checks`
   must additionally carry `review_dimensions` and a `notes` entry saying it is judgement-only. Report
   the count of fixtures with no deterministic check so the review-only surface is visible rather than
   hidden.
8. `expected_evidence.document_ids` entries, when non-empty, exist in
   `app/src/main/assets/library.json`; report the ones that do not.
9. `identity.models[*].sha256` and `bytes` match the corresponding root lock files exactly.
   `repo` and `revision` are compared against the lock file's own top-level values for **every**
   profile, including `kev`, whose per-file `files` entries carry no repository — a wrong `kev`
   repository must fail rather than pass silently. Also assert `identity.backend.llamaCppRevision`
   equals the contents of `llama-revision.txt`.
10. `defined_in.path`, when non-null, exists on disk and `symbol` is non-empty. Templates,
    substitutions or kit quotes are rejected by scanning **every** string value in the manifest, not a
    fixed list of fields — the manifest must contain literal text everywhere.
10b. `identity.prompts` entries are non-empty, and any entry declaring a `source_symbol` must match
    the text extracted from the named source file.
11. Application identity in the manifest matches `app/build.gradle` (`versionCode 9`,
    `versionName '0.8.0'`, `abiFilters 'x86_64'`, `minSdk 28`, `targetSdk 35`).
12. `status == "design"` implies every `observed_0_8.assessment` is either drawn from an existing
    file under `evidence/` or `"not implemented"`; a review claim pointing at a missing path is an
    error.
13. Print a coverage matrix: question family × count, evidence condition × count, tier counts, and
    the ids of families/conditions declared but used by no fixture. Report unused enums as warnings,
    not failures.

### Rubric — `eval/rubric-v1.md`

- The eight rubric dimensions above with, per dimension, what 0/1/2/3 mean and what evidence counts.
- Critical-failure policy: each fixture lists critical failures; any critical failure fails that
  fixture regardless of the averaged score; critical failures are never averaged into a percentage.
- Authority table: deterministic check, reviewed outcome, executable-outcome, Kev score, citation
  marker — what each proves and does not prove (`ADR-011`, `docs/evaluation.md`).
- Language rule: the score is over the answer content, and the fixture declares the expected
  language; a correct answer in the wrong language is a task-completion failure.
- Bounty-relative protocol (`R-BAR-50PCT`), marked **proposed, not measured**: same frozen question
  set answered offline by Outpost, and answered with network access by a declared baseline
  (named model + search), reviewed blind in randomised order; per-question relative usefulness
  scored 0–3; the aggregate ratio is compared against the bounty's 0.5 bar. State that the baseline
  cannot run on the emulator under the offline constraint, that this runs host-side as a separate
  labelled reference, that `>50%` is the bounty creator's wording restated, and that nothing in the
  repository measures it yet.
- Explicit non-claims: a passing harness, a citation index in range, and a favourable Kev score do
  not establish correctness; emulator timing does not establish phone latency; no peak-memory claim
  may be derived from a post-run PSS sample.

### `eval/README.md`

Purpose of the directory, the four files, how to run `python eval/validate.py`, what the validator
does and does not prove, the authority limits, and a pointer to `docs/evaluation.md`,
`docs/bounty-31.md`, `docs/offline-world-knowledge.md`. State that the manifest is not yet consumed
by the instrumentation harness and that wiring it is E-02/E-03 scope.

### `docs/bounty-31.md`

Full bounty text preserved verbatim in a fenced block, with: source URL, the tRPC retrieval route
used because the page is JavaScript-rendered, retrieval date, bounty metadata (id 31, onChainId 31,
chainId 1, issuer, amount 1.139 ETH, multiplayer, in progress, claims exist), and a provenance note
that the text is served by the poidh application and is not a signed artefact.

Then a requirements table (the fourteen `R-*` ids with state in this repository), the scope boundary
from this feature file, and the note that the "vegan restaurants in [city]" wording is third-party
commentary from a public X thread, not a bounty requirement and not a product vertical (ADR-017).

Cross-reference `docs/current-state.md` and `docs/roadmap.md`; do not create a competing status list.

## Tasks

1. Author `eval/fixtures-v1.json`, `eval/validate.py`, `eval/README.md`. **Done.**
2. Author `eval/rubric-v1.md` and `docs/bounty-31.md`. **Done.**
3. Wire the docs: `docs/index.md`, `docs/roadmap.md`, `docs/evaluation.md`, `docs/handoff.md`,
   `docs/decisions.md` (new ADR-018), `docs/current-state.md`. **Done.**
4. Run `python eval/validate.py`; record the result and fix any real problem it finds. **Done.**
5. Operational, requires explicit user go: audit history for secrets and oversized blobs, then create
   a private GitHub repository and push. **Not started**, by design. The audit must confirm no
   credential, key, signing material, private document, model weight or local setting is reachable from
   any commit before anything is pushed. `gh repo create --private` with no `--public` flag. Never flip
   visibility afterwards without a new explicit user decision. A preliminary read-only audit already
   ran: 3 commits, no remote configured, 332 tracked files, largest blob `kev-head.f32` at 2.1 MB, and
   a credential/keyword scan across every revision returned no matches. `gh` is authenticated as
   `nicoarias81`. This is not a substitute for the full audit before pushing.

## Outcome

Delivered artifacts: `eval/fixtures-v1.json` (15 fixtures: 11 runnable, 4 blocked; 7 question
families, 9 evidence conditions, 14 bounty requirements, 8 capability blockers), `eval/validate.py`
(host-only validator, 13 checks plus the network-permission cross-check), `eval/README.md`,
`eval/rubric-v1.md`, `docs/bounty-31.md`, and ADR-018 plus the `External acceptance bar` section in
`docs/current-state.md`. `docs/index.md`, `docs/roadmap.md`, `docs/evaluation.md` and
`docs/handoff.md` reference them; E-01 and W-01 are marked done as definition tasks with the
measuring work explicitly left to E-02, E-04 and E-05.

### Verification

`python eval/validate.py` exits 0, twice, with byte-identical output. An independent read-only
verification pass found four real defects in the first draft and forced a corrective unit:

| Defect | What was wrong | Closed by |
|---|---|---|
| D1 | `identity.models[kev].repo` had the wrong letter case against `judge-lock.json` | Copied from the lock file |
| D2 | `R-OFFLINE` named `scripts/start-emulator.ps1`, which never touches connectivity | Rewritten against the six `test-*.ps1` scripts that force the offline state |
| D3 | Validator check 9 silently skipped the repository comparison when the per-file lock entry had none, so a completely wrong Kev repository passed with zero problems | Every profile's `repo` and `revision` now compared against the lock's own top-level values |
| D4 | `seed-kwh-power-v1` was `runnable` while requiring the unimplemented tool T-01 | Moved to `blocked`; `seed-kwh-distinction-v1` added as the runnable explanation-only sibling; validator now forbids a runnable fixture from requiring tools |
| D5 | `field-engineer-authorization-v1` carried a `substring-present` check whose expected substring was empty, making it vacuously true | Check removed; fixture marked `review_only` with the vacuity recorded in `notes` |
| D7 | `bonsai_policy_string` stored one branch of a conditional as if it were a constant | Both real branches recorded with a not-a-literal note |

Verification limits, stated plainly: the validator proves internal consistency, not correctness or
measurement. No fixture has been executed as a scored run. One permanent validator warning remains by
design: the `TernaryChecks` policy string is built by a ternary inside a `.put()` call, so it cannot be
constant-extracted and is reported as recorded-but-unverified on every run. The bounty text itself
could not be re-fetched during verification (no network in the verification scope) and is recorded as
provider-served and unsigned.

### Found outside this feature, then fixed separately

`app/src/main/AndroidManifest.xml` set `android:label="BrÃºjula"` — a double-encoded, hardcoded label
that contradicted `strings.xml` (`app_name` = `Outpost`) and the R-04 acceptance criterion about stale
identity references and resource-backed strings. It was outside this feature's surfaces, so it was
recorded as a separate task and fixed under explicit owner authorization in `270fec8`, verified with
the project's own build: `BUILD SUCCESSFUL`, `lintDebug` reporting no issues, and `aapt2` reporting
`application-label:'Outpost'` with no permissions declared for the built debug APK.

### Commit status

Three work-unit commits on `codex/outpost`:

| Slice | Commit | Contents |
|---|---|---|
| Evaluation definition | `eb4ff77` | `eval/fixtures-v1.json`, `eval/validate.py`, `eval/README.md`, `eval/rubric-v1.md` |
| Bounty record and decisions | `ad9a08f` | `docs/bounty-31.md`, `docs/decisions.md` (ADR-018), `docs/current-state.md` |
| Documentation wiring | `5b3c26b` | `docs/index.md`, `docs/roadmap.md`, `docs/evaluation.md`, `docs/handoff.md`, this file |
| Launcher label fix | `270fec8` | `app/src/main/AndroidManifest.xml` |

A private GitHub remote exists at `nicoarias81/outpost` with `origin` configured. Nothing is public.

These hashes are post-rewrite. The whole branch was rewritten once (`git filter-branch --env-filter`)
to replace the author and committer email with the account's GitHub noreply address, because GitHub
rejected the first push with `GH007: Your push would publish a private email address`. Only the emails
changed: the tree hash is identical before and after (`bf58db8`), `git diff` between the pre-rewrite
backup ref and the rewritten branch is empty, and author and committer dates were preserved. Any hash
quoted from an earlier conversation is void.

### Native review record

The first review attempt bound the whole feature as a single ordinary candidate: lineage
`review-55571b6850c8fe81`, tier `high`, 4 lenses, 12 files, 3365 changed lines, correction budget 200.
Two group captures failed identically at the transport stage — the `review-resilience` reviewer
produced no text (`stopReason: length`) — with `prepared_reviewers: 0`, `submitted_reviewers: 0` and
`mutation_performed: false`, so no authority was burned and no reviewer result exists.

The cause is a process defect on the parent side, not a tool defect. The governing rule is that a
native review candidate is a work-unit commit or a PR slice, never the accumulated feature branch;
bound here as the accumulated feature, the candidate was too large for the lens prompts. The recorded
`high` risk tier was also a lexical false positive: the `process_boundary` signal fired on the word
"shell" inside a comment in `eval/validate.py`, which imports only `json`, `re`, `sys` and `pathlib`
and writes nothing.

Resolution, by explicit owner decision: the oversized transaction was abandoned without fabricating a
native recovery operation, and the work-unit commits above are the candidates from here on. That
lineage remains unconsumed and dormant. It was never completed, so no reviewed authority exists for
this feature and none may be claimed.

## Checks per task

- Task 1: validator exits 0 against the manifest it ships with.
- Task 2: bounty text block matches the retrieved payload; every `R-*` id in the manifest is
  described in `docs/bounty-31.md` and vice versa.
- Task 3: internal Markdown links resolve to files that exist; no status claim contradicts
  `docs/current-state.md`; `docs/roadmap.md` marks E-01/W-01 as partial, not done.
- Task 4: validator output recorded.

## Non-goals

- No instrumentation changes, no `app/` edits, no emulator run, no model run.
- No fixture execution or scoring; this feature defines the benchmark, it does not measure it.
- No new knowledge adapter, no package manager, no database migration.
- No claim that any bounty requirement is satisfied.
- No public visibility, no release, no tag, no licence selection, no GitHub Pages, no Actions setup.

## Evidence

- `python eval/validate.py` → exit 0; `15 total (blocked: 4, runnable: 11)`.
- Independent fault injection, run by the parent, not self-reported: wrong Kev repository, a runnable
  fixture carrying `requires_tools`, an unknown `document_ids` value, an unknown
  `question_families[].bounty_requirement_ids` value, and a placeholder in `expected_evidence.note`
  all exit 1 with the matching numbered problem; the unmodified control exits 0.
- Markdown link and anchor resolution across the nine created or changed documents: 0 broken.
- `git diff --stat`: 6 tracked files modified, 35 insertions, 7 deletions. New untracked:
  `docs/bounty-31.md`, `eval/` (5 files), `odd/`.
- Commit ids: none yet.

## Notes

- Engram mirror was not created: the Pi runtime session resolves to Engram project `nicoa`, not
  `outpost`, while this session's cwd is `C:\Users\nicoa`. Open the next session with cwd inside
  `E:\projects\outpost` to enable the mirror.
- Work-unit commits are requested explicitly; nothing is committed by default.
- Task 5 is not started without an explicit go-ahead, because pushing is irreversible from the
  private-repository perspective and the current tree also carries uncommitted feature work.
