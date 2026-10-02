# Testing and device operation

Build instructions are in [build.md](build.md). Use one install/instrumentation workflow per target. Host compilation, hashing, static inspection and report analysis are allowed; **model inference and numerical kernel execution run only in Android** on the targets below.

## Targets and preparation

| Target | Identity and boundary |
|---|---|
| Outpost35 | `emulator-5582`, AOSP Android 15/API 35, x86_64, 4 GiB, four virtual CPUs |
| Pixel 10 Pro | The owner-authorized physical ARM64 phone registered in ignored `.local/pixel10-target.json` |
| Preserved baseline | Never modify the old Brújula `emulator-5580` |

Tool paths are in `.local/developer-settings.json`; phone registration contains its private serial, `model: "Pixel 10 Pro"` and `authorization: "owner-request-2026-10-01"`. Do not infer a target from ADB ordering or substitute another device.

```powershell
. ./scripts/environment.ps1
. ./scripts/test-target.ps1
$OutpostAdb = Join-Path (Resolve-OutpostSdk '') 'platform-tools/adb.exe'
& $OutpostAdb devices -l
$OutpostTarget = Resolve-OutpostTestTarget -Adb $OutpostAdb -Target Outpost35
```

The target guard validates live identity, ABI and boot status. For Outpost35 it also requires airplane mode/Wi-Fi/mobile data values **1/0/0**. If the emulator is absent, `scripts/start-emulator.ps1 -Port 5582` starts the dedicated AVD headlessly using the installed AOSP image; `.local/avd` holds its data. Verify AVD name `Outpost35` and emulator identity before changing its connectivity. The launcher alone is not an identity check.

For the registered phone, use `-Target Pixel10Pro`. Preserve its radios, power/security settings and private data. The phone may remain connected: absence of app network permission proves local app operation, not that every external document provider is offline. Standard show-when-locked/keep-screen-on flags are permitted only for the test-owned synthetic UI; never disable device authentication or capture the personal app's contents.

Debug workflows require a current `eval/check-build.ps1` receipt and matching app/test APK hashes. Read each wrapper's install behavior: modern `-SkipInstall` guards verify bytes, while some legacy wrappers do not. Reuse verified model caches; fresh emulator provisioning uses `test-generation.ps1` for Qwen and `test-bonsai.ps1 -Profiles bonsai4` for Bonsai. Those setup wrappers perform installs and can run inference; use them only after preflight. Pixel model preparation is in `scripts/prepare-pixel-models.py`.

## Focused checks

Run the relevant commands sequentially, checking every exit code. Do not rerun model suites for documentation-only changes.

| Changed area | Entry point |
|---|---|
| Named places/category/proximity | `scripts/test-places.ps1` |
| OSM import/storage/source display | `scripts/test-osm.ps1` |
| Recursive folder imports | `scripts/test-folders.ps1` |
| Chat, files, PDF and persistence | `scripts/test-chat.ps1`; add `-Generate` for Bonsai 4B |
| Migrations, locators, CSV and packs | `scripts/test-knowledge.ps1` |
| x86 numerical and real-decoder regression | `scripts/test-x86-regression.ps1` |
| ARM I8MM and combined policy | Pixel isolation wrapper with `-ArmPhase i8mm-numeric`, `i8mm-lifecycle` or `stack-confirm` |
| Pinned model admission on Pixel | Pixel isolation wrapper with `-CandidateAdmission` |
| Non-debuggable emulator QA | `scripts/test-release.ps1 -Generate` after a release build |
| Expanded non-debuggable acceptance | `scripts/test-acceptance.ps1 -Target Pixel10Pro` on a new test-owned QA store |

The ordinary data wrappers default to the emulator. Do not point them directly at personal phone storage. The folder/OSM fixture provider lives only in the test APK: cleanup revokes grants and hides roots while leaving the provider registered.

For tests using the original phone package, use `scripts/test-pixel-chat-isolated.ps1`. It moves databases/preferences/document files behind opaque private names, uses a synthetic store and restores original digests in `finally`. Inspect `.local/pixel-ui-active.json` first; if restoration is pending, run `scripts/test-pixel-chat-isolated.ps1 -RestoreOnly` before anything else. Restore the original APK pair too when an experiment replaced it; do not use uninstall/clear-storage to resolve conflicts. Record exact hashes and retain failed attempts.

## Separate release QA

`releaseQa` is non-debuggable but has a test certificate and separate application ID. It is not the final signed production APK. Its tests verify the same release native code/assets without replacing the original app.

The expanded acceptance runner verifies original app/test APK hashes, database/preferences/document digests and radios before/after. Preparation requires an empty QA library/conversation and creates a synthetic-store ownership marker. Later phases require its recorded `OwnerRun`. Preparation must not be rerun over existing data.

The current Pixel QA store belongs to `accept-20261002T062037Z-8554f0ce`. A targeted recovery replay is:

```powershell
./scripts/test-acceptance.ps1 -Target Pixel10Pro -Phase recovery `
    -OwnerRun accept-20261002T062037Z-8554f0ce
```

Use that only while the package remains exclusively test-owned. Recovery seeds pending conversation, draft and interrupted-folder state, terminates the QA process, reinstalls the same-version QA APK and relaunches. It does not prove an actual mid-write crash or a version/schema upgrade.

The original Pixel package remains the restored 0.16 debug installation, distinct from current build outputs; verify live bytes before installing or skipping an install. The separate QA package now contains only the recorded synthetic imports and verified Bonsai 4B. No workflow should assume the phone or emulator already has the latest app/test pair.

## Evidence and answer quality

Record exact APK/test/model/source identity, target, prompt, output and timing scope. Keep immutable reports, screenshots and failed runs under `evidence/`; never overwrite earlier fixed-name results. Review screenshots for private content before upload. Supporting prose from older runs and frozen fixtures may refer to documents removed from the working tree; their original context remains in Git history.

For evidence-only model evaluation, the current definitions are [fixtures-v12.json](../eval/fixtures-v12.json), with 12 runnable and three blocked fixtures. They exercise `ResearchPrompt`, not the product's `ChatPrompt`. Versioned [rubric v2](../eval/rubric-v2.md) is an evaluation input; scores must cite the actual output and fixture expectations. An executable substring/citation assertion or Kev score does not establish answer correctness. Critical failures cannot be averaged away. First-token time is not first-useful-information time.

```powershell
# Set OutpostPython from the configured environment as in build.md.
& $OutpostPython eval/validate.py
if ($LASTEXITCODE -ne 0) { throw 'Fixture validation failed.' }
./scripts/test-evaluation.ps1 -Phase baseline -Model bonsai4
# After writing an attributed review for the actual run:
& $OutpostPython eval/review.py evidence/runs/RUN_ID/review.json
```

`RUN_ID` is the actual generated directory, not a literal test target. The review validator checks the review's structure and attribution; it does not generate scores.

## Release acceptance

| Area | Required evidence | Current boundary |
|---|---|---|
| Offline operation | After asset preparation, complete lookup, research, source inspection and persistence with phone connectivity disabled; no app API/web/cloud calls | No INTERNET permission or Play Services dependency; offline AOSP coverage exists. The recorded Pixel trial preserved the phone's radios rather than proving a fully disconnected phone mission |
| Android and GrapheneOS | Install and use the exact release artifact on supported physical hardware; record OS, model, ABI and build | Real Pixel Android QA runs exist; GrapheneOS runtime and the production certificate are untested |
| Memory | Run representative short/long conversations and imports on hardware with at most 12 GB installed RAM; record process peaks, system pressure and recovery | Pixel results came from a 16 GB environment; a low app-memory sample or 4 GB emulator alone does not close this physical-device gate |
| Storage | Inventory APK, weights, originals, indexes, databases, caches and import/update staging; keep the whole prepared setup within 50 GB | Pinned model sizes are known; full installed-footprint acceptance and a global cap are not established |
| Research quality | Evaluate explanation, comparison, cross-source synthesis, reasoning, absent/conflicting evidence and exact applicability; compare identical missions against a small-model baseline and an internet-assisted frontier-model reference | Current contextual checks are bounded. Target offline usefulness above half of the reference result under a declared rubric; no such comparative score has been measured |
| Usable response time | Report first token, first useful information and completed-answer latency with output length, cold/warm state and device conditions | Paired Pixel performance evidence exists; no universal speed or usefulness claim follows |
| Reproducible installation | Public source tag, signed APK/checksums, pinned assets or direct downloads, documented index creation and a clean-phone setup trial | Source/build instructions and model links exist; repository is private, release is draft and production signing remains open |
| Inspectable demonstration | Record a real phone operating without connectivity, with several complete responses including challenging research tasks, source inspection and the matching source revision | Synthetic screenshots exist; the final release demonstration is pending |

Question coverage should include a real comparison between two applicable manuals, synthesis of travel instructions with regional facts, and an explanation that cannot be judged by a single memorized identifier. Predeclare evidence, expected outcomes and critical failures. Do not relabel existing lookup checks as proof of these harder tasks, or count an unmeasured comparison as a passed gate.

## Current acceptance and remaining gates

- [Performance evidence](../evidence/research/i8mm-20261002/combined-summary.json): exact compared logits/tokens and paired Pixel latency improvements; no energy measurement.
- [Static release audit](../evidence/research/release-acceptance-20261002/audit.json): 61 controls, including manifest restrictions, native exports and 16 KiB ELF/ZIP alignment.
- [Pixel acceptance](../evidence/research/release-acceptance-20261002/summary.json): 73 accepted controls across imports, contextual questions, cancellation and seeded recovery. [Bounded answer review](../evidence/research/release-acceptance-20261002/answer-review.json) records its scope.

Production identity/key selection, exact signed-APK installation/update acceptance and release publication remain open. Broader quality coverage still needs applicability, row/range filtering and citation-binding work. Actual process death during import, storage/memory pressure, sustained energy/thermal behavior, other phones and 16 KiB runtime remain unvalidated. No passing suite should be presented as general factual accuracy or completion of these gates.
