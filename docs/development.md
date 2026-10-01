# Developer guide

Current baseline: **Outpost 0.15.0**, Windows/PowerShell host. Work from `E:/projects/outpost`. The APK packages x86_64/ARM64; execution is allowed on Outpost35/emulator-5582 or the specifically registered Pixel10Pro. Read [the emulator guide](emulator-runbook.md) or [the Pixel protocol](pixel10-testing.md) before runtime work. Host inference remains outside scope.

## Environment and preparation

| Component | Pinned / required value |
|---|---|
| JDK | 17–23 toolchain range; tested with 21; app Java compatibility 17 |
| Python | 3.9+; optional explicit `pythonExecutable` setting |
| Gradle / AGP | 8.11.1 / 8.9.2 |
| SDK / Build Tools | Platform 35 / 35.0.0; minSdk28 |
| NDK / CMake | r28b (`28.1.13356709`) / 3.22.1 |
| Emulator image | `system-images;android-35;default;x86_64`, AOSP without Google APIs |
| Host tools | Git, PowerShell, Android platform-tools and emulator |

Copy [developer-settings.example.json](../developer-settings.example.json) into ignored `.local/developer-settings.json`, using actual SDK/JDK/cache paths. Set `JAVA_HOME` and `ANDROID_HOME` or configure the corresponding settings. `gradleExecutable` can select installed Gradle; otherwise the pinned wrapper is used. `pythonExecutable` is optional, but useful when Python is absent from PATH. There is no sibling-folder fallback. An ignored `local.properties` supplies `sdk.dir`; preparation supplies `cmake.dir` and the configured NDK lives under `.local`.

```powershell
. ./scripts/environment.ps1
$OutpostPython = (Get-OutpostSettings).pythonExecutable
if (-not $OutpostPython) { $OutpostPython = (Get-Command python -ErrorAction Stop).Source }
$env:PYTHONUTF8 = '1'
& $OutpostPython scripts/prepare-native.py
if ($LASTEXITCODE -ne 0) { throw 'Native preparation failed.' }
& $OutpostPython scripts/prepare-judge.py
if ($LASTEXITCODE -ne 0) { throw 'Judge asset preparation failed.' }
& $OutpostPython scripts/prepare-bonsai.py
if ($LASTEXITCODE -ne 0) { throw 'Bonsai preparation failed.' }
pwsh -File scripts/build.ps1
if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
```

Initial preparation downloads several GB: pinned backend/native tools, Qwen, Kev/head/config and Bonsai files. These scripts do not install the base SDK/JDK. Keep sufficient space for host caches, compilation, model staging and private emulator copies. Existing verified caches should be reused. `build.ps1 -Offline` uses cached Gradle dependencies; it does not make initial Python preparation offline. [Third-party provenance](../THIRD_PARTY.md) and lock files own dependency identities.

## Build and local artifact

`scripts/build.ps1` builds app/test APKs and runs lint, checks pinned tracked-clean backend source and resolved PDF dependency bytes, then verifies source fingerprints did not change during the build. Only a successful unchanged-input build writes `.local/build-receipt.json`. Direct Gradle does not create that receipt. A documentation-only change needs no rebuild.

```powershell
pwsh -File scripts/build.ps1 -Offline
if ($LASTEXITCODE -ne 0) { throw 'Build failed.' }
pwsh -File eval/check-build.ps1
if ($LASTEXITCODE -ne 0) { throw 'Receipt verification failed.' }
pwsh -File scripts/publish-artifact.ps1
if ($LASTEXITCODE -ne 0) { throw 'Local artifact publication failed.' }
pwsh -File scripts/publish-artifact.ps1 -Verify
if ($LASTEXITCODE -ne 0) { throw 'Artifact verification failed.' }
```

Publication copies the debug APK into ignored `dist/` using the version in `app/build.gradle`, writes an LF checksum sidecar and refuses a different artifact with the same version. `-Verify` checks the existing artifact without requiring a build APK. This is local integrity checking, not production signing, public distribution or an independent reproducible-build attestation. The current artifact is `outpost-0.15.0-user-test.apk`; exact hashes/receipt are in [validation](validation-0.15.md).

Current lint: **0 errors, 3 existing upstream BouncyCastle TrustAllX509TrustManager warnings**. The app has no INTERNET permission and does not use those networking helpers. Report actual counts; do not describe lint as having no issues.

## Focused checks

Before testing, follow the runbook for AVD identity, boot/offline state, APK hashes and model readiness. Modern product/data wrappers require the emulator already offline; they do not all set connectivity for you. Run sequentially, checking each exit code.

| Change | Entry point | Model needed? |
|---|---|---|
| OSM named/category/proximity answers and exact sources | `test-places.ps1` | No; includes an absent-model Activity and a frozen real-data subset |
| Chat, persistence, TXT/CSV/PDF and source UI | `test-chat.ps1` | No; `-Generate` adds real Bonsai 4B conversation/source/cancel/restart checks |
| Recursive SAF import | `test-folders.ps1` | No |
| OSM parser/storage/SAF/source UI | `test-osm.ps1` | No; `-Generate` adds one real Bonsai 4B source answer |
| Migrations/locators/CSV/packs/runtime policy | `test-knowledge.ps1` | No |
| Pinned candidate admission/comparison | `test-candidate.ps1 -Phase admission`, `pilot`, `heldout`, `timing` | Yes, Spark 1.7B and Bonsai 4B; [preparation, rejected compact experiment and scope](candidate-testing.md) |
| Evidence-only question matrix | `test-evaluation.ps1 -Phase baseline -Model bonsai4` | Yes; [runner/review guide](../eval/README.md) |
| Dot / dispatch / grouped numeric operations | `test-kernel.ps1 -Phase numeric`, `dispatch`, `batch` | Numerical execution in emulator; whole-model phases separately use `decoder`, `decoder4`, `sampling` |
| Calibration / grouping / cache / research missions | `test-runtime.ps1 -Phase calibrate`, `batch`, `cache`, `missions` | Installed Bonsai 4B |
| Speculation research | `test-speculation.ps1` with explicit `unit`, `pilot`, `benchmark`, `lifecycle`, `guard`, `profile`, `missions`, `audit`, `energy-audit`, `draft-cost` | Runner requires Bonsai 4B, including unit; draft-cost also needs Bonsai 1.7B |
| ARM compile-only portability | `check-native-portability.ps1` | No runtime; full APK build remains a separate check |
| Locked GGUF capability inspection | `audit-spec-models.py` | Host static/header reads only |

Speculation's old `ui` phase and direct Bonsai search/reviewer UI phases are retired. Product chat has no experimental toggle. Use native cache/speculation controls for research, and chat checks for the product. Some legacy script parameters remain accepted even when their old UI path is no longer usable.

For an ingestion change, a typical no-model regression sequence is OSM, folders, chat, then knowledge. `-SkipInstall` on these four wrappers skips APK installation while still requiring matching app/test hashes and a successful current receipt. It never means skip verification. These suites and the evaluation runner write unique `evidence/runs` directories.

## Row-kernel experiments

`test-rows.ps1 -Phase graphs` runs numerical/reference/guard/stride/tail/worker controls, actual-shape cache-resident/64 MiB-pressure measurements and grouped-width coverage. It executes no model. `-Phase model` retains the exploratory combined multi-/single-column variants. `-Phase confirm` tests the prefill-only policy with the product 192-token budget and normal EOS, then checks cache invalidation, cancellation/recovery, non-Q2 fallback and model switching. All runs are unique and enforce build/APK/AVD/offline identity. These remain developer tests; there are no runtime-tuning product controls.

Use `-ApplyProfile` only with confirmation. It persists the full measured 4/4/128/width 4/prefill2/decode1 tuple for the specific build/kernel/device/model if the gate passes. It does not change global defaults or enable unmeasured ARM/VNNI paths. Shape-based selection treats one-column prompt tails as single-column work too. [Experiment record](kernel-rows-0.14.md).

## Model provisioning and research scripts

The product imports one of the exact locked GGUFs through **Settings → Offline model**. Select its matching profile first. There is no downloader and no generator weights in the APK. Documents can be inspected without a model. Kev is not selectable in product UI.

On a fresh test AVD, `test-generation.ps1` stages/verifies Qwen and runs generation checks. `test-bonsai.ps1 -Profiles bonsai4` stages/verifies and compares Bonsai 4B; its default comparison includes Qwen and both Bonsai models, so install Qwen first if using that default. `test-review.ps1` stages Kev for research, with Qwen already installed; it is not required for normal chat use.

`test-bonsai.ps1 -SelectOnly -UiProfile bonsai4 -SkipInstall` restores the selected profile without generating, but still installs both APKs and captures `models.png`. In non-UI Bonsai modes, `-SkipInstall` means **skip Bonsai weight staging/import**, not skip APK installation. `-UiOnly` immediately delegates to `test-chat.ps1 -Generate`; outer `-SkipInstall` and `-KeepSelected` are not forwarded. Prefer the explicit current chat wrapper.

For runtime/speculation wrappers, `-SkipInstall` skips APK installation **without the modern receipt/hash guard**. Independently verify installed bytes first. Legacy functional/generation/review/kernel wrappers have their own installs and fixed-output evidence. `test-emulator.ps1` is a compatibility functional path, not the current user-flow acceptance suite.

## Test provider and recovery

Folder/OSM suites use a synthetic DocumentsProvider in the test APK. `FixtureGrantReceiver` acknowledges ordered grant/revoke broadcasts; `FixtureGrants` obtains permission after instrumentation startup and checks readability. Keep the provider registered, revoke grants and hide inactive roots after tests. Do not disable/re-enable it between suites or revive the removed grant Activity; see the [preserved provider failure record](validation-0.12.md).

No fixture provider, receiver or mock corpus is in the user APK. Repair test lifecycle in the harness, not by broadening product permissions. Provider/parser calls can delay cancellation; do not run a competing install/instrumentation to recover a busy test. Follow the runbook's targeted recovery instructions.

## Evidence and change hygiene

Keep behavior commits separate from regenerated evidence; raw JSON/screenshots can overwhelm a review candidate. Preserve `evidence/runs`, `evidence/releases` and historical archives exactly, including failed results and line endings. Archive fixed-output legacy evidence before another run. Review all captures for private content before staging.

A native-controller review must actually complete before it grants review authority. The historical `a52f2b3` review failed with empty/length-limited output; later practical fixes do not retroactively acknowledge it. [Handoff](handoff.md) retains that distinction.

For docs-only work, inspect source contracts, resolve local links, run `eval/validate.py`, `eval/check-build.ps1`, artifact `-Verify` and `git diff --check` as appropriate. No emulator/model/build rerun is needed. The fixture validator now compares the full packaged ABI set; v7 preserves the current identity while earlier manifests remain frozen. Validate with `eval/validate.py`; host-only ABI fault-injection results are preserved with the place-query research evidence.

## Troubleshooting

| Symptom | Check |
|---|---|
| Missing SDK/JDK/native headers | Actual local settings and pinned preparation; no old sibling fallback |
| Offline Gradle resolution fails | Populate the dependency/wrapper cache using host connectivity |
| Receipt/APK mismatch | Rebuild changed code and install matching app/test APKs; versionName alone is insufficient |
| Model rejected | Exact selected profile, bytes and SHA-256; do not bypass identity checks |
| Import fails | Format-specific bounds, local provider readability and retained per-item reason |
| Provider not ready | Owner grant after instrumentation startup; keep component registered and inspect the unique failure run |
| Warm request slow / feature detected but unused | Exact cache/profile compatibility; compiled/enabled kernel is separate from CPU capability |
| Harness passes but answer is wrong | Review supported outcome, citations and truncation independently |
