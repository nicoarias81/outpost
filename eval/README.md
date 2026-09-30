# Executable evaluation and attributed review

The current default is [fixtures-v7.json](fixtures-v7.json): manifest schema 2, revision 7, app **0.14.0**, 15 defined fixtures (**12 runnable, 3 blocked**). This evaluates the evidence-only `ResearchPrompt` protocol. Product chat uses `ChatPrompt` v1.1 and has separate integration checks. No historical score automatically transfers to current chat.

## Validate, execute and review

```powershell
. ./scripts/environment.ps1
$OutpostPython = (Get-OutpostSettings).pythonExecutable
if (-not $OutpostPython) { $OutpostPython = (Get-Command python -ErrorAction Stop).Source }
$env:PYTHONUTF8 = '1'
& $OutpostPython eval/validate.py
if ($LASTEXITCODE -ne 0) { throw 'Fixture validation failed.' }
pwsh -File scripts/test-evaluation.ps1 -Phase baseline -Model bonsai4
if ($LASTEXITCODE -ne 0) { throw 'Evaluation execution failed.' }
```

Run from the repository root after [build/preparation](../docs/development.md) and [emulator preflight](../docs/emulator-runbook.md). The dedicated Outpost35 emulator must already be booted/offline, the current app/test build must match its successful receipt, and the requested model must already be verified/imported. The runner rejects physical serials, other AVDs, online state, incompatible ABI and stale/mismatched artifacts. Host Python coordinates; inference executes only in Android.

`-SkipInstall` skips APK reinstallation with hash verification. `-Manifest` chooses an explicit manifest; `-Fixtures` accepts comma-separated IDs; `-Model` accepts `qwen15`, `bonsai17`, `bonsai4`; `-Width` defaults to 4 (a fixed diagnostic setting, not proof of a current calibrated product profile); `-MaxTokens` defaults to 96. `-Phase both` alternates baseline/candidate order by fixture. The longer candidate system is a rejected experiment, not the current product prompt.

After inspecting every output, write an attributed review in that run directory and validate it:

```powershell
& $OutpostPython eval/review.py evidence/runs/RUN_ID/review.json
if ($LASTEXITCODE -ne 0) { throw 'Review validation failed.' }
```

Replace `RUN_ID` with the actual directory. The review validator reads/checks records; it does not generate scores or infer answer correctness.

## Execution contract

Each fixture declares `execution.evidenceMode`: `fixed-evidence` supplies explicitly synthetic initial context; `retrieval` searches an isolated library seeded by `TestLibrary` from test-APK assets plus declared import payloads. Product databases remain empty until user imports. Expected answers, critical failures and expected evidence IDs are never injected as hidden context.

`ResearchPrompt.prepare` selects the actual bounded sources. No hit follows the evidence-only no-evidence path, unlike product chat's permitted general-knowledge response. Reports retain full system/user prompts, selected sources, hashes/locators, output, stop reason, tokens and timings. Blocked fixtures remain blocked; completed execution can still contain truncated or incorrect answers.

The three blocked definitions require complete PDF-manual, regional-recommendation and deterministic-tool workflows. Basic PDF pages and OSM elements now exist, but that alone does not supply each fixture's concrete corpus/executor, context constraints or reviewed outcome. [Roadmap](../docs/roadmap.md) tracks the remaining work.

## Immutable provenance and review

Each run creates a unique timestamp/random-ID directory on host and debug app; reuse is rejected. The host freezes manifest/envelope bytes and hashes, source snapshots, successful build receipt, APK identities, device/API/offline state, configuration and Git HEAD when readable. It keeps logs and original failure/incomplete results. The Android report is checked against the frozen input identity. Early runs may lack later provenance fields; never backfill guessed metadata or attribute new harness capabilities to old runs.

Substring, citation-range and cited-document assertions prove only those bounded properties. Citation-range requires a marker and rejects out-of-range numbers; even a valid reference can support the wrong claim. [Rubric v2](rubric-v2.md) requires result SHA-256, reviewer identity/kind, fixture/variant outcomes, dimension scores, critical-failure indices and rationale. Use null plus a reason for unmeasured first-useful-information latency. Engine first-token time is not that metric.

Assistant review is not blinded human/domain-expert validation. Critical failures cannot be averaged away. Development pass counts are not general accuracy, safe field performance or [bounty](../docs/bounty-31.md) acceptance.

## Manifest and validator history

| Manifest | Release context | Meaning |
|---|---|---|
| v1 | 0.8.1 | Frozen original definition, 11 runnable / 4 blocked; rubric v1 |
| v2 | 0.9 | Executable schema 2, explicit evidence modes and runnable CSV; attributed development runs/reviews |
| v3 | 0.10 | Corpus moved to test APK; evidence-only protocol retained after chat became home |
| v4 | 0.11 | App identity revision for recursive ingestion |
| v5 | 0.12 | Frozen identity after bounded OSM import |
| v6 | 0.13 | Full ABI identity and scoped regional blockers; deterministic places tested separately |
| v7 | 0.14 | Runtime-release identity; same evidence-only protocol, separate row-kernel controls |

`eval/validate.py` defaults to v7 and accepts `--manifest PATH`. It checks schema/references, declared model/tool/prompt identities and source paths. Historical manifests can intentionally differ from current source; evaluate their frozen run snapshots in their own context. A validator PASS does not prove execution or quality.

The validator now compares all literal Gradle ABI filters, rejects missing/extra/duplicate/malformed sets and accepts a reordered equal set. V6 records both packaged ABIs. Six host-only controls are preserved in [ABI validation](../evidence/research/osm-places-20260930/abi-validation.json). This is identity consistency, not proof of ARM runtime support.

The regional fixture no longer depends on map/routing K-07. Its broader time/preference recommendation task remains blocked on its actual corpus/executor. `test-places.ps1` separately checks the implemented deterministic name/category/proximity path; neither that suite nor a manifest revision retroactively changes historical scores.

## Current integration suites

`test-chat.ps1` checks chat/files/PDF/persistence, with optional `-Generate` for real conversational controls. `test-folders.ps1` checks recursive SAF ingestion; `test-osm.ps1` checks parser/storage/source UI, with optional `-Generate` for one synthetic source answer; `test-knowledge.ps1` checks migrations/CSV/packs/locators. These are integration/regression suites, not the broad question-family study. [0.14 validation](../docs/validation-0.14.md) owns current run identities, including `test-places.ps1` and its no-model synthetic/real-subset checks. The older generated OSM answer remains recorded with its 0.12 identities.
