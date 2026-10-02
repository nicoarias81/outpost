# Testing and device operation

[build.md](build.md) gives the compilation procedures. Only one installation or instrumentation workflow can use a target at a time. The host can compile code and examine files or reports. Model inference and numerical kernel execution must occur in Android on the targets below.

## Targets

| Target | Identity |
|---|---|
| Outpost35 | `emulator-5582`; AOSP Android 15/API 35; x86_64; 4 GiB; four virtual CPUs |
| Pixel 10 Pro | The owner-authorized ARM64 phone in `.local/pixel10-target.json` |
| Previous baseline | Brújula on `emulator-5580`; no changes permitted |

Tool paths are in `.local/developer-settings.json`. The phone registration contains its private serial, `model: "Pixel 10 Pro"` and `authorization: "owner-request-2026-10-01"`. ADB list order does not identify an approved target.

## Target preparation procedure

1. Make sure that no other workflow uses the target.
2. Run the commands below.

```powershell
. ./scripts/environment.ps1
. ./scripts/test-target.ps1
$OutpostAdb = Join-Path (Resolve-OutpostSdk '') 'platform-tools/adb.exe'
& $OutpostAdb devices -l
```

3. If the emulator is absent, run `scripts/start-emulator.ps1 -Port 5582`.
4. Wait until Android startup is complete.
5. Do the identity checks below before a connectivity change.

```powershell
$OutpostSerial = 'emulator-5582'
if ((& $OutpostAdb -s $OutpostSerial shell getprop ro.kernel.qemu).Trim() -ne '1') { throw 'Not an emulator.' }
if ((& $OutpostAdb -s $OutpostSerial shell getprop ro.boot.qemu.avd_name).Trim() -ne 'Outpost35') { throw 'Wrong AVD.' }
if ((& $OutpostAdb -s $OutpostSerial shell getprop ro.product.cpu.abi).Trim() -ne 'x86_64') { throw 'Wrong ABI.' }
if ((& $OutpostAdb -s $OutpostSerial shell getprop sys.boot_completed).Trim() -ne '1') { throw 'Boot incomplete.' }
```

6. If all identity checks pass, set only this emulator to offline mode.

```powershell
& $OutpostAdb -s $OutpostSerial shell cmd connectivity airplane-mode enable
& $OutpostAdb -s $OutpostSerial shell svc wifi disable
& $OutpostAdb -s $OutpostSerial shell svc data disable
$OutpostTarget = Resolve-OutpostTestTarget -Adb $OutpostAdb -Target Outpost35
```

The target guard examines live identity, ABI and boot status. It also makes sure that airplane mode, Wi-Fi and mobile data have values **1, 0 and 0**.

The start script uses the installed AOSP image without a visible window. `.local/avd` contains emulator data. A successful start command is not an identity check.

For the registered phone, use `-Target Pixel10Pro`. Do not change its radios, power settings or security settings. Do not change its private data. Do not use another discovered device.

The phone can have a network connection during some tests. The app has no network permission, but this does not make an external document provider offline. Test-owned synthetic UI can use Android show-when-locked and keep-screen-on flags. Device authentication must stay active. Screenshots must not contain the personal app's contents.

## Build and model preparation

Debug tests must have a current `eval/check-build.ps1` receipt and matching app/test APK hashes. Some modern `-SkipInstall` options examine these hashes. Some legacy wrappers do not.

1. Read the selected wrapper's installation behavior before use.
2. Compare installed files with the current build receipt.
3. Reuse model files that pass their identity checks.

For fresh emulator setup, `test-generation.ps1` prepares Qwen. `test-bonsai.ps1 -Profiles bonsai4` prepares Bonsai 4B. These wrappers install files and can run inference. Pixel model preparation uses `scripts/prepare-pixel-models.py`. Completed target checks are necessary before these commands.

## Test selection

1. Select tests for the changed function.
2. Run them in sequence.
3. Examine the exit code after each command.
4. Stop the sequence after an error.

| Changed function | Test entry point |
|---|---|
| Named places and proximity | `scripts/test-places.ps1` |
| OSM import and source display | `scripts/test-osm.ps1` |
| Folder imports | `scripts/test-folders.ps1` |
| Chat, files, PDF and persistence | `scripts/test-chat.ps1`; `-Generate` adds Bonsai 4B tests |
| Schema changes, locators, CSV and packs | `scripts/test-knowledge.ps1` |
| x86 calculations and decoder | `scripts/test-x86-regression.ps1` |
| ARM I8MM and combined policy | Pixel isolation wrapper with `-ArmPhase i8mm-numeric`, `i8mm-lifecycle` or `stack-confirm` |
| Pixel model acceptance | Pixel isolation wrapper with `-CandidateAdmission` |
| Non-debuggable emulator QA | `scripts/test-release.ps1 -Generate`, after a release build |
| Wider Pixel QA | `scripts/test-acceptance.ps1 -Target Pixel10Pro`, with a new test-owned QA store |

Normal data wrappers use the emulator by default. Do not use them with personal phone storage. Only the test APK includes the folder and OSM fixture provider. Cleanup removes URI grants and hides inactive roots. It keeps the provider registered.

## Original phone package

The wrapper `scripts/test-pixel-chat-isolated.ps1` separates test data from the original phone data. It temporarily moves databases, preferences and document files to private locations. The tests use a synthetic store. The wrapper restores the original data and compares its digests in `finally`.

1. Read `.local/pixel-ui-active.json` before a phone test.
2. If restoration is incomplete, run `scripts/test-pixel-chat-isolated.ps1 -RestoreOnly` first.
3. Use the isolation wrapper for tests of the original phone package.
4. If an experiment replaces the APK pair, restore the original pair after the experiment.
5. Record the exact file hashes.
6. Keep failed results.

Do not uninstall the app to correct an installation conflict. Do not clear its storage.

## Separate release QA

`releaseQa` is non-debuggable. It has a separate application ID and a debug QA certificate. It is not the final production-signed APK. Its tests use the release native code and assets without replacement of the original app.

The acceptance runner compares original APK hashes, private-data digests and radio settings before and after each run. An empty QA library and conversation are necessary for preparation. It makes a synthetic-store ownership marker. The recorded `OwnerRun` is necessary for later phases.

Do not run preparation again over an existing store. The recorded Pixel QA store belongs to `accept-20261002T062037Z-8554f0ce`.

If that store is still exclusively for tests, run this recovery command:

```powershell
./scripts/test-acceptance.ps1 -Target Pixel10Pro -Phase recovery `
    -OwnerRun accept-20261002T062037Z-8554f0ce
```

The recovery probe writes pending conversation, draft and interrupted-folder state. It stops the QA process and reinstalls the same-version APK. It then examines the state after restart. This probe does not cause an actual mid-write crash. It does not prove a version or schema upgrade.

The last recorded original Pixel package is the restored 0.16 debug installation. It is different from current build outputs. The separate QA package contains synthetic imports and Bonsai 4B with the specified file hash. Make sure that installed bytes agree with the intended test pair before each run.

## Evidence and answer quality

Each run must have exact APK, test, model and source identities. Its record must also include the target, prompt, output and timing conditions.

1. Keep reports, screenshots and failed runs in `evidence/`.
2. Do not replace earlier results.
3. Examine screenshots for private content before upload.

Historical records can refer to documents that are no longer in the working tree. Git history keeps their original context.

The current evaluation definitions are in [fixtures-v12.json](../eval/fixtures-v12.json). They contain 12 runnable fixtures and three blocked fixtures. They use `ResearchPrompt`, not the product's `ChatPrompt`. [Rubric v2](../eval/rubric-v2.md) specifies the scoring rules.

A string check, citation check or Kev score does not prove a correct answer. An average score must not hide critical failures. Time to first token is not time to first useful information. A review must identify its author and the actual output.

1. Set `$OutpostPython` as specified in [build.md](build.md).
2. Run the validation and evaluation commands below.

```powershell
& $OutpostPython eval/validate.py
if ($LASTEXITCODE -ne 0) { throw 'Fixture validation failed.' }
./scripts/test-evaluation.ps1 -Phase baseline -Model bonsai4
```

3. Examine all output from the generated run.
4. Write an attributed review for that run.
5. Replace `RUN_ID` below with the actual directory name.
6. Run the review check.

```powershell
& $OutpostPython eval/review.py evidence/runs/RUN_ID/review.json
```

The review validator examines structure and attribution. It does not give scores or establish that an answer is correct.

## Release acceptance conditions

| Area | Necessary evidence | Current state |
|---|---|---|
| Offline operation | Complete tasks after preparation with phone connectivity disabled. Include source display and persistence. | No app INTERNET permission or Play Services dependency. Offline AOSP test results are available. Pixel tests kept the original radio settings. |
| Android and GrapheneOS | Use the exact signed APK on compatible physical hardware. Record OS, model, ABI and build. | Physical Android QA test results are available. GrapheneOS and production-certificate tests are incomplete. |
| Memory | Run representative tasks on hardware with a maximum of 12 GB installed RAM. Record peaks and memory-pressure recovery. | Pixel measurements use a 16 GB phone. Emulator results and low app-memory samples do not prove this physical-device condition. |
| Storage | Count app, weights, originals, indexes, databases, caches and temporary copies. Keep the complete setup within 50 GB. | Model sizes are known. Complete footprint acceptance and a global cap are not available. |
| Research quality | Compare identical missions against a small model and an internet-assisted frontier model. Include absent and conflicting evidence. | The goal is more than half the reference score under a declared rubric. This measurement is not complete. |
| Response time | Record first token, first useful information and completed answer times. Include output length and cold/warm conditions. | Paired Pixel results are available. They do not establish a universal speed or usefulness claim. |
| Installation | Public source tag, signed APK, checksums, exact assets, index instructions and a clean-phone setup trial | Source and model instructions are available. The repository is private. The release is a draft. Signing is incomplete. |
| Demonstration | Record a disconnected phone, difficult questions, complete answers, source display and the matching source revision. | Synthetic screenshots are available. The final release demonstration is incomplete. |

Wider questions must include differences between applicable manuals and a travel plan from regional and personal information. They must also include causes that a single saved identifier cannot describe. Each fixture must specify evidence, expected outcomes and critical failures. Existing lookup tests do not prove performance on these wider tasks.

## Recorded results and remaining work

| Evidence | Recorded result |
|---|---|
| [Performance](../evidence/research/i8mm-20261002/combined-summary.json) | Identical compared logits and tokens; lower paired Pixel answer times; no energy measurement |
| [Static release audit](../evidence/research/release-acceptance-20261002/audit.json) | 61 checks, including permissions, JNI exports and 16 KiB ELF/ZIP alignment |
| [Pixel acceptance](../evidence/research/release-acceptance-20261002/summary.json) | 73 accepted checks across imports, contextual questions, cancellation and seeded recovery |
| [Answer review](../evidence/research/release-acceptance-20261002/answer-review.json) | Assessment of the specified synthetic cases only |

Production identity, key selection, signed-APK installation, update acceptance and publication are incomplete. Wider quality work includes applicability, row/range filters and citation binding. Other incomplete tests include actual process death during import, storage pressure, memory pressure, sustained energy use and thermal behavior. Tests on other phone families and 16 KiB runtime are also necessary. A passing suite does not prove general factual accuracy.

For documentation-only changes, do link and fixture checks. Make sure that build receipts and frozen artifacts do not change. Do not rebuild the app or run model suites for these changes.

## Demo capture

`scripts/prepare-demo-tools.py` gets the specified comparison model and local video encoder. It does not run a model on the host. `scripts/record-demo.py` controls the registered Pixel and keeps a recovery journal in `.local/demo-active.json`.

1. Get owner approval before a temporary phone disconnection.
2. Run `prepare` to isolate the QA store.
3. Run `compare` for local model evidence, if necessary.
4. Run `record --scene NUMBER --allow-offline-transition` only with that approval.
5. Always run `restore` after capture or failure.

The recording guard needs airplane mode, Wi-Fi off and no active default network. It keeps mobile-data preferences unchanged. A review must examine the complete timeline for unrelated UI or private content. Raw videos stay outside Git. Public posting and claim submission need separate owner approval.

The capture uses the native keyboard, incremental key events and touchscreen navigation. This is an automated walkthrough of the real app. The first selected scene opens the loaded library and a source. The general-knowledge scene requires zero retrieved passages through the normal app path.

The test input requests no suggestions or personalized learning. The keyboard can still show generic predictions. It supplies an English keyboard hint. The recorder restores the original touch-indicator preference. Captures keep the actual response text and generation time.

RC2 place narration passed the 66-check OSM suite on Outpost35. The [recorded Pixel review](../evidence/runs/demo-20261002T100257Z-a1235e31/quality-review.json) includes model output, source checks and timings. It also identifies the repeated small-model comparison and rejected pilot. These selected cases do not prove general factual accuracy.
