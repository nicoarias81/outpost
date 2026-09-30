# Developer guide

Baseline: Windows/PowerShell development and x86_64 emulator execution. Commands are run from the project root. The repository is `E:\projects\outpost` and uses `dev.outpost.app`. The original project and emulator are preserved separately.

For the actual operating sequence, identity checks, installed APK hashes, models and recovery, use the [emulator runbook](emulator-runbook.md). It distinguishes an already prepared host from a fresh clone.

## Prerequisites and preparation

| Component | Pinned / required value |
|---|---|
| JDK | 17–23; tested with 21 |
| Python | 3.9+ |
| Gradle / AGP | 8.11.1 / 8.9.2 |
| SDK platform / Build Tools | 35 / 35.0.0 |
| NDK / CMake | r28b (`28.1.13356709`) / 3.22.1 |
| Android system image | `system-images;android-35;default;x86_64` (AOSP, no Google APIs) |
| Other tools | Git, Android platform-tools and emulator, PowerShell for scripts |

Configure `JAVA_HOME` and `ANDROID_HOME` to installed prerequisites. Create an untracked `local.properties` with `sdk.dir=C:/your/android-sdk` using the actual SDK path. Preparation sets `cmake.dir`; the app Gradle file points to the prepared NDK under `.local`.

```powershell
python scripts/prepare-native.py
python scripts/prepare-judge.py
python scripts/prepare-bonsai.py
pwsh -File scripts/build.ps1
```

`prepare-native.py` obtains the pinned backend, NDK/CMake, and Qwen file. `prepare-judge.py` obtains Kev and copies its small head/config assets into the app. `prepare-bonsai.py` obtains both Bonsai profiles. Initial downloads total several GB; extraction, build caches, staged imports, and emulator disks require additional space. These scripts do not install the base SDK/JDK. Inspect [lock files and provenance](../THIRD_PARTY.md) before changing any dependency.

For a populated local cache, append `--offline` to Gradle or use `pwsh -File scripts/build.ps1 -Offline`. The helper reads explicit parameters/environment settings or ignored `.local/developer-settings.json`. Copy [developer-settings.example.json](../developer-settings.example.json) and fill in actual `sdk`, `javaHome`, and optional `gradleHome` paths. An optional `gradleExecutable` can select an already installed Gradle; otherwise the pinned wrapper is used. No sibling-folder fallback exists. The optional `pythonExecutable` records an interpreter when Python is not on PATH; invoke it explicitly for preparation scripts. Python preparation may still access the network; an offline Gradle flag does not make first-time preparation offline.

`scripts/build.ps1` verifies the pinned, tracked-clean backend and unchanged production/test inputs across build/lint, then writes ignored `.local/build-receipt.json` with source/APK hashes. Direct Gradle builds do not create that receipt. Publication requires the receipt, matching production inputs and app bytes, and refuses a differing artifact with the same version. After a successful helper build, `pwsh -File scripts/publish-artifact.ps1` copies the built APK into `dist/` under the versioned name read from `app/build.gradle` and writes an LF-terminated `.sha256` sidecar; `pwsh -File scripts/publish-artifact.ps1 -Verify` re-checks an existing artifact against its sidecar. `-Verify` does not require the build APK. `dist/` is ignored by Git. The earlier quota block has been resolved; current publication targets the 0.10 candidate. See [validation](validation-0.10.md).

## Emulator setup

```powershell
pwsh -File scripts/start-emulator.ps1 -Sdk $env:ANDROID_HOME
```

The script creates `.local/avd/Outpost35.avd`, uses port 5582, four virtual CPUs, 4 GiB RAM, 1080×2400 display, and a 10 GB data partition. By default it starts without a visible window. Use `-Visible` when starting a visible instance; an already running instance is reused rather than relaunched.

Wait for boot completion before tests. If importing all model profiles, account for both installed files and temporary staging copies within the data partition. Do not delete app data or resize the AVD casually to recover space; preserve installed evidence and imports first.

Always address the emulator explicitly. Test scripts reject physical-style serials and verify `ro.kernel.qemu=1`, including the functional suite. Do not bypass these guards to install on a phone.

## Initial functional run

After building and booting, run sequentially:

```powershell
pwsh -File scripts/test-emulator.ps1 -Sdk $env:ANDROID_HOME
pwsh -File scripts/test-generation.ps1 -Sdk $env:ANDROID_HOME
pwsh -File scripts/test-review.ps1 -Sdk $env:ANDROID_HOME
pwsh -File scripts/test-bonsai.ps1 -Sdk $env:ANDROID_HOME
```

Scripts install debug/test APKs, set airplane mode and disable emulator Wi-Fi/data, and save results under `evidence/`. Generation scripts push model files and import through application storage logic. Qwen setup precedes the three-profile Bonsai comparison. Initial model copies can be expensive; do not rerun them merely to validate prose edits.

`test-generation.ps1` leaves Qwen selected. To restore Bonsai 4B without another generation, use:

```powershell
pwsh -File scripts/test-bonsai.ps1 -SelectOnly -UiProfile bonsai4 -SkipInstall
```

For UI generation, cached repeat, and simulated memory callback:

```powershell
pwsh -File scripts/test-bonsai.ps1 -UiOnly -UiProfile bonsai4 -SkipInstall -KeepSelected
```

For `test-runtime.ps1` and `test-speculation.ps1`, `-SkipInstall` skips APK installation and requires installed APKs to match the build. For `test-bonsai.ps1`, it skips Bonsai weight staging/import but **still installs both APKs**, including in `-UiOnly` and `-SelectOnly` modes. Rebuild after code changes and archive evidence before a script overwrites it. The [runbook](emulator-runbook.md) provides exact readiness checks.

## Focused verification

| Change | Commands / phases |
|---|---|
| Knowledge/migration/pack/CSV | `test-knowledge.ps1`; unique evidence directory, no LLM required |
| Contextual evaluation | `test-evaluation.ps1 -Phase baseline -Model bonsai4`; see [runner guide](../eval/README.md) and review results separately |
| Dot kernel | `test-kernel.ps1 -Phase numeric`, then `-Phase decoder4` |
| Dispatch | `test-kernel.ps1 -Phase dispatch` plus portability compile |
| Grouped matrix path | `test-kernel.ps1 -Phase batch`, `test-runtime.ps1 -Phase batch` |
| Cache/lifecycle | `test-runtime.ps1 -Phase cache`, then the Bonsai UI/trim run |
| Runtime tuning | `test-runtime.ps1 -Phase calibrate`, then confirm with `-Phase batch` |
| Speculation | `test-speculation.ps1` with `unit`, `benchmark`, `lifecycle`, `guard`, `audit`, `energy-audit`, `missions`, `ui` as applicable |
| Native portability | `pwsh -File scripts/check-native-portability.ps1` |
| Model capability / drafter feasibility | `python scripts/audit-spec-models.py`; `test-speculation.ps1 -Phase draft-cost` |

The speculation script defaults to `pilot`; specify the phase explicitly for reproducibility. Additional `profile` and `pilot` diagnostics are available. These scripts are diagnostics, not a CI pipeline. Do not run benchmark phases concurrently on the same emulator.

## Manual application use

The English UI exposes model selection/import and speculation in **Status**. Select a profile, then import its exact locked GGUF. Search a topic and use **Draft from sources**; **Stop generation** cancels it. **Review first claim with Kev** runs optional review after generation. Choose local files in the Android picker; external providers may require their own connectivity. Imported text is labeled unverified and reviewer scores do not certify the full answer.

## Keep regenerated evidence out of behaviour commits

Legacy test scripts rewrite fixed tracked evidence under `evidence/`. New evaluation and knowledge scripts create unique run folders; both new and legacy evidence still require deliberate staging. Commit that regenerated
evidence separately, as its own `chore(evidence)` commit, and keep it out of the commit that changes
behaviour.

A native review candidate is a commit range and it carries the frozen diff of every changed path.
Mixing thousands of lines of regenerated JSON into a behaviour commit makes the candidate exceed the
reviewer context budget, and then no review authority is created at all and the range cannot be
reviewed: the controller never truncates frozen candidate evidence. One behaviour commit plus one
evidence commit keeps each candidate reviewable. `dist/` and `.local/` are ignored, so published
artifacts are ignored, while `evidence/runs` and historical evidence archives are intentionally versioned separately.

## Troubleshooting

| Symptom | Check |
|---|---|
| Missing `llama.h` during CMake | Run pinned native preparation; inspect `.local/llama.cpp` and `llama-revision.txt` |
| SDK/JDK missing after moving | Configure actual external paths; the old sibling fallback no longer applies |
| Offline Gradle resolution failure | Dependencies/wrapper not yet cached; prepare once with host connectivity |
| Model rejected | Profile, exact byte count and SHA-256; never bypass verification to fit another quantization |
| Emulator storage failure | Installed models plus staging files and evidence; preserve data before cleanup |
| No speculation proposals | Default off, wrong profile, short output, insufficient suffix match, or cost fallback; inspect counters |
| CPU feature shown but kernel not selected | Candidate may lack a compiled/enabled implementation; inspect all registry fields |
| Warm request still slow | Changed token prefix/configuration or released context; inspect reused tokens and prefill separately |
| Test PASS but answer wrong | Review mission outcome and truncation; execution success is not answer correctness |

See [evaluation](evaluation.md) for evidence interpretation and [migration](repository-migration.md) before moving toolchains, AVDs, or app identifiers.

## Knowledge preparation and English UI

The Library exposes text/Markdown/CSV import and local knowledge-pack import, cooperative cancellation and confirmed removal. Inspect revision/hash/content date and exact record in the source reader. [Format v1](knowledge-packs-v1.md) includes the host pack-builder command, limits and supported lifecycle. Pack metadata does not authenticate its publisher.

Set `$env:PYTHONUTF8='1'` when running Python tools on Windows. The recorded argv round-trip was executed on PowerShell 7; do not attribute it to a tested PS5.1 runtime. Run focused checks sequentially against the dedicated emulator and preserve prior legacy outputs before rerunning.

## 0.10 product workflow and packaging

The current product opens on chat, with documents/models under Settings. Use `scripts/test-chat.ps1` for no-model UI/import/persistence checks and `-Generate` for actual Bonsai 4B conversation. It verifies the successful build receipt, offline Outpost35 identity and app/test APK hashes, and creates a unique run folder. `test-bonsai.ps1 -UiOnly -UiProfile bonsai4` routes to this check; retired search/regenerate UI cache comparisons are replaced by native `test-runtime.ps1 -Phase cache`. Kev and speculation controls are no longer product UI.

The build packages ARM64 and x86_64. No ARM runtime is implied. `pdfbox-lock.json` records PDFBox/BouncyCastle artifact hashes; the helper checks resolved dependency bytes and carries the lock in input fingerprints. Licenses are packaged under assets/licenses. Lint currently reports three warnings in the unused networking helper classes inside upstream BouncyCastle; the application has no INTERNET permission and does not use those helpers. Record actual lint counts rather than claiming a clean report.


## Folder regression

Use `scripts/test-folders.ps1` against Outpost35. The instrumented provider and ordered grant receiver exist only in the test APK. Android's instrumentation startup clears earlier transient grants, so the test requests fixture-owner read grants afterward. The wrapper revokes grants and hides the fixture roots on completion. Runs and setup failures are preserved under `evidence/runs/folders-*`. Follow with `test-chat.ps1 -SkipInstall` and `test-knowledge.ps1 -SkipInstall` for ingestion/schema regressions; no model generation is required for this feature.


## OSM checks and test-provider lifecycle

Use `test-osm.ps1` for data integration, optionally `-Generate` for one real Bonsai answer. Default checks are model-free. The fixture provider remains registered in the test APK; `FixtureGrantReceiver` synchronously grants/revokes test URI access and switches root visibility, while `FixtureGrants` checks readiness after instrumentation starts. Do not disable/re-enable the provider between consecutive suites: this produced an unavailable-provider timeout. The user APK contains no fixture provider/receiver or mock map data.
