# Pixel 10 Pro testing

The owner explicitly authorized the connected physical Pixel 10 Pro on 2026-10-01. This supersedes the earlier emulator-only constraint for that phone (ADR-033). Outpost35 remains available, emulator-5580 is preserved, and host inference/other phones are outside scope.

## 0.16 row scheduling

Current 0.16 adds row scheduling phases to the same isolation protocol. Use `-ArmPhase row-numeric`, `row-tune`, `row-confirm`, `row-confirm-reverse` or `row-lifecycle`; confirmation/lifecycle take `-PrefillChunk 32 -DecodeChunk 0`. Keep 4/6 workers, width 8, decodeRows 4, persistent workers and no affinity. [Current validation](validation-0.16.md).

## Target and preservation

Observed device: Google Pixel 10 Pro / `blazer`, ARM64, Tensor G5, Android 17/API 37, security patch 2026-09-05, fingerprint `google/blazer/blazer:17/CP3A.260905.009/16091614:user/release-keys`. Initial memory: 15,949,000KiB total; available memory fluctuates. The phone had no Outpost app/test package, about 121.8GiB free data storage, USB charging at 20% and battery temperature 28.0°C. These are an initial snapshot, not steady-state measurements or hardware specifications inferred from marketing.

The specifically authorized serial is in ignored `.local/pixel10-target.json` with `model: "Pixel 10 Pro"` and `authorization: "owner-request-2026-10-01"`. Do not substitute another discovered serial. `scripts/test-target.ps1` requires this registration plus live manufacturer/model/ABI/non-emulator/boot checks. Reports redact the full USB serial. ADB must show `device`; an `unauthorized` entry requires the owner to accept the phone's debugging prompt.

Preserve documents, conversations and system settings. Do not clear app storage, uninstall to resolve signature conflicts, reset devices, force storage-cache eviction, change power/thermal controls or touch unrelated apps. This trial leaves airplane mode/Wi-Fi/mobile-data settings unchanged (initial values 0/1/1). The test verifies the product declares no INTERNET permission. It demonstrates local app execution while the OS may be online, not a fully disconnected phone mission. Network-dependent external document providers are outside these synthetic checks.

The owner subsequently requested keeping the screen awake. Android reports an enforced administrative maximum of 900,000ms and ignores permanent USB stay-on under that policy. The attempted USB stay-on setting was restored; the screen-off timeout was temporarily raised from 30,000 ms to 600,000 ms, within the existing maximum. Restore the original timeout after the trial. Do not change the administrative policy. [Screen-control record](../evidence/research/pixel10-20261001/screen-control.json) preserves the values. The owner unlocked/opened Outpost during the pilot, which started with the keyguard showing; those exploratory timings have mixed screen/foreground conditions.

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

Provisioning uses existing host model caches and the Spark/Bonsai/Qwen locks, verifies all host bytes before transfer, checks free space, copies through unique ADB staging paths and verifies app-private bytes. It preserves differing existing models/markers and partial files on failure. Successful staging files are removed. Only Bonsai 4/Qwen receive product readiness markers; Spark remains outside Settings. No model download, host inference or selection change occurs. Total model bytes are 3,362,585,248 (about 3.13GiB); staging temporarily needs another model copy.

## Protocol

Start with candidate admission: template/control-token/Unicode checks, sliding-context reuse versus cold controls, cancellation/recovery, Spark/Bonsai switching and Qwen smoke. Full model hashes are verified inside Android; this warms file pages and prevents a cold-storage claim. Pixel uses at most 4 generation/prompt threads, batch 128, width 1, rowTile 1/decodeRows 1, no speculation and Spark full SWA cache. This is a conservative ARM baseline, not emulator x86 tuning or a custom ARM kernel.

After admission passes, use `-Phase pilot`, `-Phase timing` and optionally `-Phase heldout`, always with `-Target Pixel10Pro -SkipInstall`. [Candidate testing](candidate-testing.md) owns fixtures, exact prompt/sampling policies and phase counts. Keep fixture outcomes separate from execution PASS. The held-out set was already inspected during emulator development; another-device replay is not newly unseen quality evidence.

For the separate product UI/import check, first ensure both current APKs are installed. Then use `./scripts/test-pixel-chat-isolated.ps1 -Generate` after model runs have stopped. The wrapper isolates existing databases, preferences (including drafts) and document files through opaque app-private renames, restores them in `finally`, and verifies their original file hashes. It does not read personal content onto the host. `.local/pixel-ui-active.json` is a recovery journal; if a run is interrupted, reconnect the same phone and use `./scripts/test-pixel-chat-isolated.ps1 -RestoreOnly` before any further app work. Do not overwrite or delete pending backup directories. Recovery after an abrupt USB/process failure was not fault-injected in this trial. Direct `test-chat.ps1 -Target Pixel10Pro -Generate` is limited to an empty chat/library and does not isolate saved drafts; prefer the isolation wrapper on a personal phone. For current isolated runs, the test-only visibility policy above can keep synthetic UI visible over keyguard; ordinary/direct visual runs require an unlocked phone. It imports synthetic TXT/CSV/PDF records, exercises actual Bonsai chat/history/cancellation and source display, then removes its documents/turns and restores the prior model selection. Do not use the app manually during that UI suite because it owns the temporary conversation and captures screenshots; temporary state is archived separately and is not merged into the original data. Other wrappers remain emulator-only.

The harness records before/after battery/charging/memory/storage/radio snapshots, per-call battery temperature and Android thermal status, native timings and sampled process PSS/RSS/faults. It refuses to start another recorded model request when thermal status is severe or higher. Temperature values are battery sensor readings, not CPU temperatures. Thermal boundary samples can miss in-call peaks; the memory sampler also adds overhead. USB charging and uncontrolled background activity preclude battery-life or controlled sustained-thermal claims. Compare complete answers and lengths before describing a speed advantage.

## Current0.15 operations

[Current validation](validation-0.15.md) supersedes the reference-path timings below. Product ARM calls enable DotProd only when supported and use persistent workers; the exact tested Pixel/Bonsai4 key selects4 decode/6 prompt,batch 128,width 8,rows1/4,no affinity. Normal calls retain120 seconds/192 output tokens. `test-arm.ps1` supports numeric/controller/tune/lifecycle/trace/confirm research; execute through the isolation wrapper and inspect its journal before resuming.

The status-only ARM test Activity may display over keyguard using standard Android show-when-locked/turn-screen-on APIs and requires resumed/focused/interactive state. Device authentication stays enabled. The isolated product UI suite can do the same only with an active app-private isolation marker and empty chat/library/drafts. It clears the temporary Activity task state and never displays the original private UI. Direct non-isolated visual tests still require an unlocked device. The wrapper removes its marker after original file hashes are restored. No global screen/security/radio setting is changed by this policy.

Use `test-x86-regression.ps1` for bounded ABI regression; it archives prior fixed-name reports and restores the original Outpost35 APKs after checking the new build. Do not use legacy scripts that overwrite historical host outputs without archiving them.

## Initial phone-trial checkpoint (historical)

Physical admission passed 23 execution controls in [candidate-admission-20261001T095919Z-c0129c18](../evidence/runs/candidate-admission-20261001T095919Z-c0129c18/candidate-checks.json). These include the prior 21 lifecycle controls plus product INTERNET-permission absence and full Qwen smoke-file hash verification. Spark, Bonsai 4 and Qwen generated on ARM64; Spark cache/text/logit parity and cancellation/recovery passed. Device capability mask 7168 reports NEON, DotProd and I8MM, but the custom Q2 candidates are not compiled: Bonsai selects the linked reference path. This is compatibility evidence, not an ARM kernel optimization.

The first wrapper attempt stopped before instrumentation because PowerShell's case-insensitive `$target` path variable collided with the new validated `-Target` parameter. Fix 97f83e2 renames the path variable; [the failed run and analyst note](../evidence/runs/candidate-admission-20261001T095850Z-7f0c40ef/analyst-note.md) remain preserved.

Admission/pilot app SHA256 is `93b91e7fe5e9ac0363488fb07b000f2ea4355199337946dc3f6cdeb9c3ff5d35`; test APK is `7584f34c8d1e73558e1ba65d786872805477db204fb506acfe17144c41163634`. [Their receipt](../evidence/research/pixel10-20261001/build-receipt-final.json) owns source identities. The final visual-test keep-awake/keyguard/privacy guard changes only the test APK to `6097b92f3e8da208ca23f2962d4022176f8e6b35ad63011daf19cab78f4b4015`, with [its own receipt](../evidence/research/pixel10-20261001/build-receipt-chat-final.json). The preceding `cbbb200b…9911bb4` test artifact was built but not used for the recorded visual runs. No product/native input changed during the trial, and no different same-version APK overwrote the published artifact.

The [completed Pixel validation](pixel10-results-2026-10-01.md) records 23 admission controls, 37 pilot execution controls/32 answers and 45 real-chat/import/PDF controls. Both models have quality failures; Bonsai hit four pilot deadlines. The guarded initial UI attempt preserved existing data; the isolated run passed and restored original directory hashes. Screen settings were restored and network settings preserved. Phone repeated sampled timing, reserved replay, full picker/folder/OSM and sustained battery/thermal testing remain pending. Existing emulator results remain in [the Spark study](spark-candidate-results-2026-10-01.md).

## App-only CPU sampling

The current ARM wrapper also accepts `-ArmPhase profile` through the Pixel isolation script. Use 4 decode/6 prompt workers, width 8, decodeRows 4 and persistent workers as shown in [the stack audit](inference-stack-audit-2026-10-01.md). It records own-process user CPU samples with Android simpleperf and observed first-text boundaries, then restores personal app data. Run the offline symbol analysis against the exact native build ID. The rejected half-block experiment is retained only in its evidence snapshots.

The retained `profile` phase reproduces the earlier static-row CPU audit. It does not automatically select the 0.16 queued product profile. Current queue execution is verified by `row-confirm`/`row-lifecycle` and the real-chat native node counters.
