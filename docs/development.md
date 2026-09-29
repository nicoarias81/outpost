# Developer guide

Baseline: Windows/PowerShell development and x86_64 emulator execution. Commands are run from the project root. The repository is `E:\projects\outpost` and uses `dev.outpost.app`. The original project and emulator are preserved separately.

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
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

`prepare-native.py` obtains the pinned backend, NDK/CMake, and Qwen file. `prepare-judge.py` obtains Kev and copies its small head/config assets into the app. `prepare-bonsai.py` obtains both Bonsai profiles. Initial downloads total several GB; extraction, build caches, staged imports, and emulator disks require additional space. These scripts do not install the base SDK/JDK. Inspect [lock files and provenance](../THIRD_PARTY.md) before changing any dependency.

For a populated local cache, append `--offline` to Gradle or use `pwsh -File scripts/build.ps1 -Offline`. The helper reads explicit parameters/environment settings or ignored `.local/developer-settings.json`. Copy [developer-settings.example.json](../developer-settings.example.json) and fill in actual `sdk`, `javaHome`, and optional `gradleHome` paths. An optional `gradleExecutable` can select an already installed Gradle; otherwise the pinned wrapper is used. No sibling-folder fallback exists. Python preparation may still access the network; an offline Gradle flag does not make first-time preparation offline.

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

`-SkipInstall` is only appropriate when installed APKs and imported models match the current build. Rebuild/reinstall after code changes. Archive existing evidence before a script overwrites its output names.

## Focused verification

| Change | Commands / phases |
|---|---|
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
