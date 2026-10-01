# Pixel 10 Pro testing

The owner explicitly authorized the connected physical Pixel 10 Pro on 2026-10-01. This supersedes the earlier emulator-only constraint for that phone (ADR-033). Outpost35 remains available, emulator-5580 is preserved, and host inference/other phones are outside scope.

## Target and preservation

Observed device: Google Pixel 10 Pro / `blazer`, ARM64, Tensor G5, Android17/API37, security patch2026-09-05, fingerprint `google/blazer/blazer:17/CP3A.260905.009/16091614:user/release-keys`. Initial memory:15,949,000KiB total; available memory fluctuates. The phone had no Outpost app/test package, about121.8GiB free data storage, USB charging at20% and battery temperature28.0°C. These are an initial snapshot, not steady-state measurements or hardware specifications inferred from marketing.

The specifically authorized serial is in ignored `.local/pixel10-target.json` with `model: "Pixel 10 Pro"` and `authorization: "owner-request-2026-10-01"`. Do not substitute another discovered serial. `scripts/test-target.ps1` requires this registration plus live manufacturer/model/ABI/non-emulator/boot checks. Reports redact the full USB serial. ADB must show `device`; an `unauthorized` entry requires the owner to accept the phone's debugging prompt.

Preserve documents, conversations and system settings. Do not clear app storage, uninstall to resolve signature conflicts, reset devices, force storage-cache eviction, change power/thermal controls or touch unrelated apps. This trial leaves airplane mode/Wi-Fi/mobile-data settings unchanged (initial values0/1/1). The test verifies the product declares no INTERNET permission. It demonstrates local app execution while the OS may be online, not a fully disconnected phone mission. Network-dependent external document providers are outside these synthetic checks.

## Preparation

Resolve tooling through `.local/developer-settings.json`. Keep one install/instrumentation workflow active per target. Build first, verify the registered identity, and install both debug APKs with `adb -s <registered serial> install -r`; a signature failure is a blocker, not permission to uninstall. The frozen `dist/outpost-0.14.0-user-test.apk` remains untouched. Research builds use the same version label and must be identified by their hashes/receipt.

```powershell
./scripts/build.ps1 -Offline
. ./scripts/environment.ps1
. ./scripts/test-target.ps1
$OutpostAdb = Join-Path (Resolve-OutpostSdk '') 'platform-tools/adb.exe'
$OutpostPhone = Resolve-OutpostTestTarget -Adb $OutpostAdb -Target Pixel10Pro
& $OutpostAdb -s $OutpostPhone.Serial install -r app/build/outputs/apk/debug/app-debug.apk
if($LASTEXITCODE -ne 0){throw 'Preserve installed app; inspect installation failure'}
& $OutpostAdb -s $OutpostPhone.Serial install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
if($LASTEXITCODE -ne 0){throw 'Preserve installed test package; inspect installation failure'}
& (Get-OutpostSettings).pythonExecutable scripts/prepare-pixel-models.py
if($LASTEXITCODE -ne 0){throw 'Inspect preserved provisioning failure'}
./scripts/test-candidate.ps1 -Target Pixel10Pro -Phase admission -SkipInstall
```

Provisioning uses existing host model caches and the Spark/Bonsai/Qwen locks, verifies all host bytes before transfer, checks free space, copies through unique ADB staging paths and verifies app-private bytes. It preserves differing existing models/markers and partial files on failure. Successful staging files are removed. Only Bonsai4/Qwen receive product readiness markers; Spark remains outside Settings. No model download, host inference or selection change occurs. Total model bytes are3,362,585,248 (about3.13GiB); staging temporarily needs another model copy.

## Protocol

Start with candidate admission: template/control-token/Unicode checks, sliding-context reuse versus cold controls, cancellation/recovery, Spark/Bonsai switching and Qwen smoke. Full model hashes are verified inside Android; this warms file pages and prevents a cold-storage claim. Pixel uses at most4 generation/prompt threads, batch128, width1, rowTile1/decodeRows1, no speculation and Spark full SWA cache. This is a conservative ARM baseline, not emulator x86 tuning or a custom ARM kernel.

After admission passes, use `-Phase pilot`, `-Phase timing` and optionally `-Phase heldout`, always with `-Target Pixel10Pro -SkipInstall`. [Candidate testing](candidate-testing.md) owns fixtures, exact prompt/sampling policies and phase counts. Keep fixture outcomes separate from execution PASS. The held-out set was already inspected during emulator development; another-device replay is not newly unseen quality evidence.

The harness records before/after battery/charging/memory/storage/radio snapshots, per-call battery temperature and Android thermal status, native timings and sampled process PSS/RSS/faults. It refuses to start another recorded model request when thermal status is severe or higher. Temperature values are battery sensor readings, not CPU temperatures. Thermal boundary samples can miss in-call peaks; the memory sampler also adds overhead. USB charging and uncontrolled background activity preclude battery-life or controlled sustained-thermal claims. Compare complete answers and lengths before describing a speed advantage.

## Current status

ADB identity verified; physical execution is being prepared. No phone performance result is claimed until the run evidence is captured. Existing emulator results remain in [the Spark study](spark-candidate-results-2026-10-01.md). Build/run hashes, outcomes and remaining limitations will be recorded here after execution.
