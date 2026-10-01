# Emulator operating guide

Use this with the [agent handoff](handoff.md) and [developer guide](development.md). This is the operational entry point for a successor on the existing host or a newly prepared checkout. Runtime inference remains inside the emulator; no phone execution is authorized.

## Identity and last observed state

Configured target and latest recorded checkpoint. This documentation pass did not inspect or mutate the running emulator; repeat preflight before use:

| Item | Outpost value |
|---|---|
| Repository | `E:/projects/outpost` |
| AVD / serial | `Outpost35` / `emulator-5582` |
| AVD storage | `.local/avd/Outpost35.avd` with companion `.local/avd/Outpost35.ini` |
| System image | AOSP Android 15 / API 35, `default;x86_64`; no Google APIs |
| CPU / memory / display | Four virtual CPUs, 4 GiB configured RAM, 1080×2400 at density 420 |
| Data partition | 10 GB configured; check current free space before staging models/imports |
| App / test package | `dev.outpost.app` / `dev.outpost.app.test` |
| Instrumentation | `dev.outpost.app.test/dev.outpost.app.GenerationInstrumentation` |
| Last recorded app/model | Outpost 0.14.0/code 16; Bonsai 4B selected after test cleanup; recheck live state |
| Runtime profile | Keyed by device/OS/app version/model; unmeasured fallback ≤4 threads, batch 128, width 1; old width 4 measurements are historical |
| Product speculation / required test connectivity | Forced depth 0; airplane mode 1, Wi-Fi 0, mobile data 0 |
| Separate preserved emulator | `emulator-5580` belongs to the old Brújula setup; leave it alone |

Check live state before doing work; old observations do not establish current readiness. Serial alone is insufficient: confirm the AVD name before mutations. Only one test/benchmark workflow should own Outpost's emulator at a time; a second instrumentation run or APK install can interrupt the first.

## 1. Resolve tools on this host

Open PowerShell in the Outpost root and run:

```powershell
$ErrorActionPreference = 'Stop'
Set-Location 'E:/projects/outpost'
. ./scripts/environment.ps1
$OutpostSettings = Get-OutpostSettings
$OutpostSdk = Resolve-OutpostSdk $env:ANDROID_HOME
$OutpostAdb = Join-Path $OutpostSdk 'platform-tools/adb.exe'
$OutpostSerial = 'emulator-5582'
function Invoke-OutpostAdb {
    if ($OutpostSerial -notmatch '^emulator-[0-9]+$') { throw 'Emulator serial required.' }
    & $OutpostAdb -s $OutpostSerial @args
    if ($LASTEXITCODE -ne 0) { throw "adb failed for $OutpostSerial" }
}
```

The ignored `.local/developer-settings.json` contains actual SDK, JDK, Gradle-cache/executable, and Python paths for this machine. Python is not guaranteed to be on PATH; the host inspected here has a configured `pythonExecutable`. For preparation/static Python tools:

```powershell
$OutpostPython = $OutpostSettings.pythonExecutable
if (-not $OutpostPython) {
    $OutpostPython = (Get-Command python -ErrorAction Stop).Source
}
& $OutpostPython --version
if ($LASTEXITCODE -ne 0) { throw 'A working Python 3.9+ interpreter is required.' }
```

SDK/JDK/Gradle are external prerequisites. Native NDK/CMake, the pinned backend, and cached weights are under `.local/`. Do not delete the old shared toolchain just because the app repository moved. On a different host, populate local settings from [the example](../developer-settings.example.json) and follow the [preparation guide](development.md); the ignored resources and installed emulator models are not included in a Git clone.

## 2. Reuse or start the intended AVD

Read the inventory first:

```powershell
& $OutpostAdb devices -l
```

If Outpost is absent and no other agent is starting/using it:

```powershell
pwsh -File scripts/start-emulator.ps1 -Sdk $OutpostSdk -Port 5582
```

The default launch is headless and logs to `.local/emulator.stdout.log` and `.local/emulator.stderr.log`. `-Visible` applies when starting a new instance. It does not make an already running headless instance visible. Reuse an existing correct instance for ordinary tests; the launch script checks the serial's presence but does not by itself establish that its AVD is Outpost.

Before installation, generation, or any state change:

```powershell
if ((Invoke-OutpostAdb get-state).Trim() -ne 'device') { throw 'Emulator is not ready.' }
if ((Invoke-OutpostAdb shell getprop sys.boot_completed).Trim() -ne '1') {
    throw 'Boot is incomplete; wait briefly and repeat these checks.'
}
if ((Invoke-OutpostAdb shell getprop ro.kernel.qemu).Trim() -ne '1') { throw 'Not an emulator.' }
if ((Invoke-OutpostAdb shell getprop ro.boot.qemu.avd_name).Trim() -ne 'Outpost35') {
    throw 'Wrong or unverifiable AVD; do not mutate this target.'
}
if ((Invoke-OutpostAdb shell getprop ro.product.cpu.abi).Trim() -ne 'x86_64') {
    throw 'This runbook validates the x86_64 Outpost35 test target.'
}
```

`ro.boot.qemu.avd_name` was confirmed on this AVD. Do not silently pass the identity check if it is empty on another image; inspect the actual instance. Console commands such as `adb emu avd name` can return no useful output in some host sessions, so exit code alone is not identity evidence. A running port or a process ID is not a substitute for these checks.

## 3. Inspect or establish offline state

After the identity checks, inspect:

```powershell
Invoke-OutpostAdb shell settings get global airplane_mode_on
Invoke-OutpostAdb shell settings get global wifi_on
Invoke-OutpostAdb shell settings get global mobile_data
```

Expected values are 1, 0, and 0. If preparing an offline test, set and then re-read them:

```powershell
Invoke-OutpostAdb shell cmd connectivity airplane-mode enable
Invoke-OutpostAdb shell svc wifi disable
Invoke-OutpostAdb shell svc data disable
```

The current chat/folder/OSM/knowledge/evaluation wrappers require this offline state and reject online targets. Legacy generation/runtime scripts set connectivity themselves; never assume every wrapper does so. Offline Gradle means using the host dependency cache; it is a separate condition from the emulator's connectivity. Initial dependency/model downloads may require host connectivity, while no model inference runs on the host.

## 4. Build and verify the installed artifacts

Use the existing cache when available:

```powershell
pwsh -File scripts/build.ps1 -Offline
```

This builds app/test APKs and runs Android Lint. It does not install them. If the code changed, install both, only after the identity checks and after other tests have stopped:

```powershell
Invoke-OutpostAdb install -r app/build/outputs/apk/debug/app-debug.apk
Invoke-OutpostAdb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
```

Do not use package presence or matching `versionName` alone to justify skipping installation: a new build may reuse the same version. Compare installed bytes to the current local build:

```powershell
$OutpostApks = @(
    @{ Package='dev.outpost.app'; File='app/build/outputs/apk/debug/app-debug.apk' },
    @{ Package='dev.outpost.app.test'; File='app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk' }
)
foreach ($OutpostApk in $OutpostApks) {
    $OutpostPaths = @(Invoke-OutpostAdb shell pm path $OutpostApk.Package)
    if ($OutpostPaths.Count -ne 1 -or $OutpostPaths[0] -notlike 'package:*') {
        throw "Missing or unexpected APK layout: $($OutpostApk.Package)"
    }
    $OutpostInstalledPath = $OutpostPaths[0].Trim().Substring(8)
    $OutpostInstalledHash = ((Invoke-OutpostAdb shell sha256sum $OutpostInstalledPath) -split ' +')[0]
    $OutpostLocalHash = (Get-FileHash -LiteralPath $OutpostApk.File -Algorithm SHA256).Hash.ToLowerInvariant()
    if ($OutpostInstalledHash -ne $OutpostLocalHash) { throw "APK mismatch: $($OutpostApk.Package)" }
}
```

Exact 0.14 app/test identities are in [validation](validation-0.14.md). Run `pwsh -File eval/check-build.ps1` to check the current source/build receipt as well. Byte identity is stronger than a version label, but does not prove model quality.

## 5. Understand model installation before copying GB of data

All three generators and Kev were installed in the existing AVD at earlier checkpoints; verify availability before using them. Host caches and installed app-private copies are distinct:

| Profile | Host cache under project | Installed path relative to app-private root |
|---|---|---|
| Qwen 1.5B | `.local/models/qwen2.5-1.5b-instruct-q4_k_m.gguf` | `files/models/qwen-test.gguf` |
| Bonsai 1.7B | `.local/models/Ternary-Bonsai-1.7B-Q2_0_g64.gguf` | `files/models/Ternary-Bonsai-1.7B-Q2_0_g64.gguf` |
| Bonsai 4B | `.local/models/Ternary-Bonsai-4B-Q2_0_g64.gguf` | `files/models/Ternary-Bonsai-4B-Q2_0_g64.gguf` |
| Kev | `.local/models/kev/kev-0.8b-q8_0.gguf` | `files/kev/kev-0.8b-q8_0.gguf` |

`model-lock.json`, `bonsai-lock.json`, and `judge-lock.json` are the authoritative names/sizes/hashes. Kev's head/config are APK assets. Installed verification markers accompany model files, but listing a filename does not freshly rehash its contents; installation performs the actual checks.

Read-only inspection of the debug app:

```powershell
Invoke-OutpostAdb shell run-as dev.outpost.app ls -l files/models
Invoke-OutpostAdb shell run-as dev.outpost.app ls -l files/kev
Invoke-OutpostAdb shell run-as dev.outpost.app cat shared_prefs/generator.xml
Invoke-OutpostAdb shell run-as dev.outpost.app ls shared_prefs
Invoke-OutpostAdb shell df -h /data
```

Product chat ignores research speculation preferences and always sets depth 0. Do not create preference files by hand. The developer selector restores Bonsai 4B:

```powershell
pwsh -File scripts/test-bonsai.ps1 -SelectOnly -UiProfile bonsai4 -SkipInstall -Serial $OutpostSerial -Sdk $OutpostSdk
```

That command still installs APKs and captures `models.png`; it is not a read-only inspection. Product model selection/import is under Settings → Offline model; no Status screen or speculation switch exists. Native research phases select speculation explicitly.

On a fresh AVD, use the [model provisioning instructions](development.md) to import the needed locked model through application verification; normal chat does not require Kev or all generator profiles. Model files go through staging and app-private storage, requiring temporary extra capacity. Do not hand-copy directly into private model locations, bypass hashes, uninstall, or clear app data as routine setup. Those operations can destroy imports and evidence.

## 6. Choose the smallest relevant test

| Intent | Current entry point |
|---|---|
| Chat/files/PDF/persistence | `test-chat.ps1`, no model by default; `-Generate` explicitly adds real Bonsai 4B conversation checks |
| Recursive selected-folder import | `test-folders.ps1`, no model |
| OSM parser/storage/SAF/source reader | `test-osm.ps1`, no model by default; `-Generate` adds one synthetic source answer |
| Migrations/CSV/packs/locators/policy | `test-knowledge.ps1`, no model |
| Evidence-only matrix | `test-evaluation.ps1 -Phase baseline -Model bonsai4`; see [runner guide](../eval/README.md) |
| Calibration/cache/kernels/speculation/Kev research | Explicit native phases in [development](development.md); archive fixed-output evidence first |

Pass `-Serial $OutpostSerial -Sdk $OutpostSdk` when resuming. Run one phase at a time and check `$LASTEXITCODE` after each. Modern product/data suites require the successful build receipt and compare installed app/test hashes. `-SkipInstall` only skips their APK installation. Model generation is never implied by the default chat/folder/OSM/knowledge checks.

Flag differences in legacy wrappers matter:

- Runtime/speculation `-SkipInstall` skips APK installation, but does not independently enforce the modern receipt/hash guard. Perform those checks first.
- Non-UI Bonsai `-SkipInstall` skips weight staging/import, while both APKs are still installed. `-SelectOnly` also installs/captures a screenshot.
- Bonsai `-UiOnly` delegates immediately to `test-chat.ps1 -Generate`; outer `-SkipInstall` and `-KeepSelected` are not forwarded. Prefer calling the current chat wrapper directly.
- The old speculation UI phase and direct Bonsai search/reviewer UI paths are retired. There is no product experimental toggle.

Qwen tests change the selected profile. Restore the intended model after tests. Runtime profile keys include app version, not arbitrary same-version source changes; changing the version invalidates an older measured profile. Inspect developer reports or calibrate when needed rather than copying preferences. Do not run a new calibration merely for a docs edit.

### Fixture provider lifecycle

The synthetic DocumentsProvider and `FixtureGrantReceiver` exist only in the test APK. Android instrumentation startup clears earlier transient grants, so `FixtureGrants` requests owner-issued grants afterward and checks root readability. The wrapper uses an ordered explicit broadcast and checks the acknowledgement:

```text
am broadcast --include-stopped-packages -n dev.outpost.app.test/dev.outpost.app.FixtureGrantReceiver --es run_id RUN_ID --es operation grant
am broadcast --include-stopped-packages -n dev.outpost.app.test/dev.outpost.app.FixtureGrantReceiver --es run_id RUN_ID --es operation revoke
```

These are the test protocol, not product setup commands. Use the wrappers rather than issuing ad hoc grants. Successful owner broadcasts return result code -1 with granted/revoked data. Cleanup revokes grants and sets the fixture-provider active flag false, making `queryRoots` empty while leaving the component registered. Do not disable/re-enable it between suites or use the old grant Activity. Those obsolete patterns caused provider-unavailable stalls. See [preserved failures](validation-0.12.md). No product permission change is needed.

## 7. Inspect UI and retrieve evidence

After the identity check, open the installed app:

```powershell
Invoke-OutpostAdb shell am start -W -n dev.outpost.app/.MainActivity
```

The headless emulator still runs UI tests and captures screenshots. Existing test scripts copy app-private JSON/PNG evidence to `evidence/` using binary-safe streams. Do not pipe PNG bytes through text-mode PowerShell redirection. A manual screen capture can use a unique temporary device file and `adb pull`:

```powershell
$OutpostStamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$OutpostCaptureDir = '.local/captures'
New-Item -ItemType Directory -Force -Path $OutpostCaptureDir | Out-Null
$OutpostRemotePng = "/sdcard/Download/outpost-$OutpostStamp.png"
Invoke-OutpostAdb shell screencap -p $OutpostRemotePng
Invoke-OutpostAdb pull $OutpostRemotePng "$OutpostCaptureDir/outpost-$OutpostStamp.png"
```

For a failed test, inspect its console output, corresponding failure JSON in app-private `files/evidence`, and `adb -s emulator-5582 logcat -d -t 200`. Source-reading commands such as `run-as dev.outpost.app cat files/evidence/generation-failure.json` apply only when that file exists. Do not publish private document content accidentally captured in logs/screenshots.

Before rerunning a phase, preserve the evidence files it overwrites. Use a separate run/archive directory; record app and test APK hashes, model/profile, prompt/fixture and sampling settings, timestamps, and output. Existing historical archives, `evidence/0.7-before-outpost` and release/run snapshots must remain intact. A harness PASS is not a field-quality score.

## 8. Recovery and visibility

- **Device absent or booting:** check inventory and `.local/emulator.stdout.log` / `.local/emulator.stderr.log`; wait briefly and repeat preflight. Do not start duplicate instances against the same AVD files.
- **Wrong AVD at port 5582:** stop and resolve the collision; do not treat `ro.kernel.qemu=1` alone as permission to overwrite another emulator's app/data.
- **Need a visible window:** first ensure no agent/test needs the instance; intentionally stop only Outpost with `adb -s emulator-5582 emu kill`, confirm it actually disappeared, then launch `start-emulator.ps1 -Visible`. A zero console exit code with an unchanged device list is not proof of shutdown. Host-account/console permission issues need appropriate host permissions, not killing unrelated processes.
- **Different host or changed SDK path:** review `local.properties`, local settings, the AVD `.ini` absolute path and `config.ini` system-image path. The launcher does not rewrite an existing `config.ini` automatically. Do not move/copy a running AVD disk or repair paths while it is active.
- **Out of storage:** distinguish host cache, external staging and installed private models; preserve data and inspect known temporary files before cleanup. Avoid blanket deletion, `pm clear`, uninstall, `-wipe-data`, and `adb kill-server` as routine fixes.
- **Permission or port problem:** use the required host/filesystem permission path and report the actual limitation. Do not bypass emulator guards or substitute a physical phone.

These procedures were checked against current scripts; the earlier live observations remain release evidence, not a new live preflight. This documentation task did not restart, install, generate or change emulator connectivity. If an owned test stalls, preserve its run/logs first. A targeted `am force-stop dev.outpost.app` on the verified Outpost emulator interrupts the app without clearing its files, but must not interrupt another workflow. Recheck fixture cleanup before retrying; never use `pm clear` as a routine recovery step.

## Measured row profile in 0.14

The final post-validation checkpoint selected Bonsai 4B with 4/4 threads, batch 128, width 4, multi-column rowTile 2 and single-column decodeRows 1, under kernel identity `q2-row-v3-phase`. This is scoped to the recorded device/model/build; recheck live state and do not transplant it to other phones/models. `test-rows.ps1` has graphs/model/confirm phases; only confirmation accepts `-ApplyProfile`. All kernel/model execution stays on the emulator, and runs remain sequential. See [kernel record](kernel-rows-0.14.md) and [post-validation state](../evidence/releases/0.14.0/emulator.json).

## Spark research checkpoint — 2026-10-01

The emulator now has a same-version 0.14 research app/test pair with hashes in [Spark results](spark-candidate-results-2026-10-01.md), distinct from the frozen user-test APK. It remains offline with Bonsai 4 selected. Spark 1.7B Q4_K_M is verified in app-private `files/models/Spark-X2.5-1.7B-Q4_K_M.gguf` but is not a selectable product profile; no verification marker/product admission is implied by that file's presence.

Use [candidate preparation and tests](candidate-testing.md). `prepare-spark-research.py` requires an installed debug APK, uses binary ADB push/copy/hash and preserves existing valid files. Do not repeat the failed binary-stdin transfer method. The final candidate tests clean their per-run databases; unrelated older test databases remain preserved. Always verify installed APK bytes before `-SkipInstall`, and run only one instrumentation/model workflow at a time. No phone scope expansion occurred.
