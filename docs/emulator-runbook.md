# Emulator operating guide

Use this with the [agent handoff](handoff.md) and [developer guide](development.md). This is the operational entry point for a successor on the existing host or a newly prepared checkout. Runtime inference remains inside the emulator; no phone execution is authorized.

## Identity and last observed state

Read-only inspection on 2026-09-29 confirmed:

| Item | Outpost value |
|---|---|
| Repository | `E:/projects/outpost` |
| AVD / serial | `Outpost35` / `emulator-5582` |
| AVD storage | `.local/avd/Outpost35.avd` with companion `.local/avd/Outpost35.ini` |
| System image | AOSP Android 15 / API 35, `default;x86_64`; no Google APIs |
| CPU / memory / display | Four virtual CPUs, 4 GiB configured RAM, 1080×2400 at density 420 |
| Data partition | 10 GB configured; about 4.1 GB available when inspected, not a permanent guarantee |
| App / test package | `dev.outpost.app` / `dev.outpost.app.test` |
| Instrumentation | `dev.outpost.app.test/dev.outpost.app.GenerationInstrumentation` |
| Selected model / profile | Bonsai 4B; 4 decode + 4 prompt threads, batch 128, width 4 |
| Speculation / connectivity | Default off; airplane mode 1, Wi-Fi 0, mobile data 0 |
| Separate preserved emulator | `emulator-5580` belongs to the old Brújula setup; leave it alone |

Both emulators were running when checked. Check live state before doing work. Serial alone is insufficient: confirm the AVD name before mutations. Only one test/benchmark workflow should own Outpost's emulator at a time; a second instrumentation run or APK install can interrupt the first.

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
    throw 'This APK is x86_64 only.'
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

The existing test scripts perform these network changes on their selected emulator. Offline Gradle means using the host dependency cache; it is a separate condition from the emulator's connectivity. Initial dependency/model downloads may require host connectivity, while no model inference runs on the host.

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

Both matched when this guide was prepared. The current app hash is recorded in the [0.8.1 validation record](validation-0.8.1.md). These checks read files only and do not prove model quality.

## 5. Understand model installation before copying GB of data

All three generators and Kev were installed in the existing Outpost AVD. Host caches and installed app-private copies are distinct:

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

An absent `speculative-context.xml` means the current code uses its default false setting; do not create preference files by hand. The developer selector restores Bonsai 4B:

```powershell
pwsh -File scripts/test-bonsai.ps1 -SelectOnly -UiProfile bonsai4 -SkipInstall -Serial $OutpostSerial -Sdk $OutpostSdk
```

That command still installs APKs and captures `models.png`; it is not a read-only inspection. Use the English Status UI to change the speculation setting when a test explicitly needs it.

On a fresh AVD, run the [initial functional sequence](development.md) to import Qwen, Kev, and Bonsai through application verification. Model files go through staging and app-private storage, requiring temporary extra capacity. Do not hand-copy directly into private model locations, bypass hashes, uninstall, or clear app data as routine setup. Those operations can destroy imports and evidence.

## 6. Choose the smallest relevant test

| Intent | Entry point / prerequisite |
|---|---|
| Library, import and UI | `test-emulator.ps1`; no LLM run required |
| Qwen generation/cancellation | `test-generation.ps1`; verifies host file, stages Qwen and performs generation |
| Kev integration/UI | `test-review.ps1`; Qwen must already be installed; stages Kev and runs probes |
| Bonsai compare | `test-bonsai.ps1`; default compares all three, so Qwen must be installed first |
| Bonsai 4B UI/cache/trim | `test-bonsai.ps1 -UiOnly -UiProfile bonsai4 -SkipInstall -KeepSelected` |
| Dispatch / dot / grouped kernel | `test-kernel.ps1 -Phase dispatch`, `numeric`, or `batch` |
| Whole-model kernel checks | `test-kernel.ps1 -Phase decoder4` (installed 4B), `decoder` (1.7B), or `sampling` |
| Calibration / cache / missions | `test-runtime.ps1 -Phase calibrate`, `batch`, `cache`, or `missions`; Bonsai 4B installed |
| Speculation units/runtime | `test-speculation.ps1 -Phase unit` or the relevant documented phase; its runner requires installed 4B even for unit mode |
| Drafter-cost diagnostic | `test-speculation.ps1 -Phase draft-cost`; both Bonsai models installed |

Scripts should receive `-Serial $OutpostSerial -Sdk $OutpostSdk` when resuming. Do not run phases concurrently. Check `$LASTEXITCODE` after each script rather than letting a later successful command mask a failure. A phase can take minutes; inspect ongoing session output without launching a competing run.

**`-SkipInstall` has different meanings:**

- In `test-runtime.ps1` and `test-speculation.ps1`, it skips APK installation. Use it only after the installed/local APK comparison above and model-readiness checks.
- In `test-bonsai.ps1`, it skips Bonsai GGUF staging/import inside the model loop. The script always reinstalls both APKs, including with `-UiOnly` or `-SelectOnly`.
- Functional/generation/review/kernel scripts have their own installation steps and do not accept this switch.

Qwen tests change the selected profile. Restore the desired model at the end of a session. A fresh app version/device/model key does not inherit the old measured runtime profile; read Status or run the appropriate calibration rather than manually copying a preference key.

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

Before rerunning a phase, preserve the evidence files it overwrites. Use a separate run/archive directory; record app and test APK hashes, model/profile, prompt/fixture and sampling settings, timestamps, and output. Existing historical `evidence/0.7-before-outpost` must remain intact. A harness PASS is not a field-quality score.

## 8. Recovery and visibility

- **Device absent or booting:** check inventory and `.local/emulator.stdout.log` / `.local/emulator.stderr.log`; wait briefly and repeat preflight. Do not start duplicate instances against the same AVD files.
- **Wrong AVD at port 5582:** stop and resolve the collision; do not treat `ro.kernel.qemu=1` alone as permission to overwrite another emulator's app/data.
- **Need a visible window:** first ensure no agent/test needs the instance; intentionally stop only Outpost with `adb -s emulator-5582 emu kill`, confirm it actually disappeared, then launch `start-emulator.ps1 -Visible`. A zero console exit code with an unchanged device list is not proof of shutdown. Host-account/console permission issues need appropriate host permissions, not killing unrelated processes.
- **Different host or changed SDK path:** review `local.properties`, local settings, the AVD `.ini` absolute path and `config.ini` system-image path. The launcher does not rewrite an existing `config.ini` automatically. Do not move/copy a running AVD disk or repair paths while it is active.
- **Out of storage:** distinguish host cache, external staging and installed private models; preserve data and inspect known temporary files before cleanup. Avoid blanket deletion, `pm clear`, uninstall, `-wipe-data`, and `adb kill-server` as routine fixes.
- **Permission or port problem:** use the required host/filesystem permission path and report the actual limitation. Do not bypass emulator guards or substitute a physical phone.

The guide's read-only bootstrap/preflight and APK comparisons were checked against the existing emulator. This documentation task did not restart, install, generate, change connectivity, or rerun performance suites. Startup/install/recovery procedures reflect the checked project scripts and remain actions to perform only when needed.
