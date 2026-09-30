# Executable evaluation and attributed review

Outpost 0.9 uses [fixtures-v2.json](fixtures-v2.json), manifest schema 2. The original [fixtures-v1.json](fixtures-v1.json) remains frozen as the 0.8.1 definition. Version 2 adds explicit execution modes and makes the CSV record case concrete and runnable: 15 defined fixtures, 12 runnable, 3 blocked.

## Validate, run and review

```powershell
. ./scripts/environment.ps1
$OutpostPython = (Get-OutpostSettings).pythonExecutable
& $OutpostPython eval/validate.py
pwsh -File scripts/test-evaluation.ps1 -Phase baseline -Model bonsai4
```

Run from the project root, with the dedicated `Outpost35` emulator booted and offline and the selected model already verified/imported. The runner refuses physical serials, a different AVD, online network settings, incompatible ABI, a stale build receipt or installed APK mismatches. Initial preparation and build instructions are in [development](../docs/development.md) and the [emulator runbook](../docs/emulator-runbook.md).

`-SkipInstall` skips reinstalling APKs only after their hashes have been verified against the current local build. `-Fixtures` accepts comma-separated fixture IDs, `-Model` selects an installed profile, and `-Width` fixes the matrix width. Default output budget is 96 tokens. Tests execute model inference only in Android, not in the host Python process.

`-Phase both` alternates baseline/candidate order by fixture. The longer candidate system is experimental, not the production prompt. The first controlled run did not justify adopting it. Performance comparisons must consider system-token overhead and output length as well as latency.

After inspecting every output, add an attributed `review.json` to that run directory and validate it:

```powershell
& $OutpostPython eval/review.py evidence/runs/RUN_ID/review.json
```

Replace `RUN_ID` with the actual generated directory name. The review validator reads only; it does not create scores or infer answer quality from matching tokens.

## Execution semantics

Each v2 fixture declares `execution.evidenceMode`:

- `fixed-evidence`: supply the fixture's exact initial context as a clearly synthetic source.
- `retrieval`: use an isolated seeded SQLite library plus the manifest's explicit text/CSV import payloads, then call the application's lexical retrieval.

Expected answers, critical failures and expected evidence IDs are never silently inserted into model input. The production `ResearchPrompt.prepare` builder determines the bounded evidence sent to the model. The complete rendered system/user prompts, actual selected sources, source hashes and typed locators where available are recorded.

No retrieved sources means the source-grounded no-evidence path, not an implicit request to answer from model memory. Blocked fixtures are recorded as blocked. A completed execution means outcomes were recorded; it does not mean every model answer completed without truncation or satisfied its task.

## Immutable run provenance

Every invocation creates a timestamp-plus-random-ID directory under `evidence/runs/` and a corresponding unique directory inside the debug app. Existing run IDs are rejected. The host preserves:

- The exact manifest bytes, input envelope and their hashes.
- App and test APK hashes, local successful build receipt and source-file hashes.
- A source snapshot whose members are checked against the recorded hashes, plus Git HEAD when readable, device fingerprint/API, model identity, configuration and offline state.
- Instrumentation log and original result JSON, including incomplete/failing runs.

The Android report captures actual app/model identity, prompts, selected evidence, source hashes, output text, token counts, stop reason, cache/width settings and engine timings. The host checks the returned envelope/manifest identity against the frozen inputs. Build receipts bind app/test APKs to successfully built source snapshots; they are not a signed reproducible-build attestation.

Early development runs may lack the later build receipt or source snapshot; inspect the files actually present rather than attributing final-runner capabilities retrospectively. The new runner does not overwrite older runs. Legacy performance scripts still use fixed output names; archive their existing artifacts before rerunning. Host permission to read `.git` may be required; if Git metadata cannot be read, the error is recorded rather than replaced with a guessed commit.

## Bounded checks versus quality review

The runner implements the manifest's substring, citation-range and cited-document checks. Citation-range checks require a citation and reject out-of-range indices; an absent citation cannot pass vacuously. These checks prove only their stated bounded property.

The [rubric](rubric-v2.md) governs review. A review must identify the exact results SHA-256, reviewer kind/name, per-fixture/variant outcome, declared dimensions, critical-failure indices and rationale. Use `null` with an explicit reason for an unmeasured dimension such as first-useful-information latency. Engine first-token time is not automatically that metric.

Human and assistant reviews must be distinguished. The implementation agent's recorded review is not a blinded human/domain-expert study. Critical failures cannot be averaged away. Counts of satisfied development fixtures are not general accuracy, safe field performance or bounty compliance.

## Manifest validator

`eval/validate.py` defaults to v2 and supports `--manifest PATH`. It checks structure, fixture capabilities, root model/tool identities, source document references and extractable prompt constants. Schema 2 requires an explicit evidence mode. Versioned prompt and sampler constants remove the earlier unverified `bonsai_policy_string` warning.

The frozen v1 identity intentionally targets the earlier app; validating it against newer sources may report identity differences. Its historical run copies remain independently inspectable. Passing the validator does not establish that fixtures were executed or answers are correct.

## 0.10 update

The current default is `fixtures-v3.json` (same schema2 execution shape, new manifest revision). The demo corpus now exists only in the instrumented test APK under `app/src/androidTest/assets/library.json`; `TestLibrary` seeds isolated evaluation databases explicitly. Product libraries start empty. These fixtures continue to evaluate evidence-only `ResearchPrompt`, while the chat product uses `ChatPrompt`. Use `scripts/test-chat.ps1 -Generate` for actual conversational integration; neither suite establishes general model quality. V1/v2 manifests and their runs remain historical.
