# Build and release

The maintained build workflow uses Windows and PowerShell. Run commands from the repository root. [Testing](testing.md) covers Android targets; compilation alone does not establish runtime support.

## Toolchain

| Component | Configuration |
|---|---|
| Java | JDK 21 tested; application language level 17 |
| Gradle / Android Gradle Plugin | 8.11.1 / 8.9.2 |
| Android SDK | Platform 35, Build Tools 35.0.0, platform-tools |
| NDK / CMake | r28b (`28.1.13356709`) / 3.22.1 |
| Python / shell | Python 3.9+, Git and PowerShell |
| Optional emulator | `system-images;android-35;default;x86_64` |

[toolchain-lock.json](../toolchain-lock.json), the Gradle wrapper and [app/build.gradle](../app/build.gradle) own the versions. NDK/CMake downloads use the archive hashes in the lock; model pins use SHA256.

## Configure and prepare

Create `.local/developer-settings.json` from [developer-settings.example.json](../developer-settings.example.json), using your actual paths. Set `sdk`, `javaHome`, `gradleHome` and `pythonExecutable`. `gradleExecutable` optionally selects an installed Gradle; the debug build falls back to the wrapper, while the current release-build script requires this setting. No machine-specific settings belong in Git.

Create ignored `local.properties` with your SDK path, for example `sdk.dir=C:/Android/Sdk`. Then prepare the pinned backend, NDK and CMake:

```powershell
. ./scripts/environment.ps1
$OutpostPython = (Get-OutpostSettings).pythonExecutable
if (-not $OutpostPython) { $OutpostPython = (Get-Command python -ErrorAction Stop).Source }
$env:PYTHONUTF8 = '1'
& $OutpostPython scripts/prepare-native.py
if ($LASTEXITCODE -ne 0) { throw 'Native preparation failed.' }
```

This also downloads the pinned Qwen test model and writes `cmake.dir` into `local.properties`. It does not install the base SDK or JDK. Initial preparation needs connectivity and several GB of free space; verified caches are reused. Run `scripts/prepare-bonsai.py` for Bonsai weights. `scripts/prepare-judge.py` is optional Kev research setup, not required for product use.

## Compile

```powershell
./scripts/build.ps1 -Offline
if ($LASTEXITCODE -ne 0) { throw 'Debug build failed.' }
./eval/check-build.ps1
```

Omit `-Offline` when populating Gradle dependencies initially. The build compiles both ABIs and the test APK, runs lint, checks the pinned backend/PDF dependencies and records unchanged input hashes. Outputs are under `app/build/outputs`; `.local/build-receipt.json` binds the debug app/test bytes to their inputs. Direct Gradle invocation does not issue that receipt.

For the production candidate and isolated QA variant:

```powershell
./scripts/build-release.ps1 -Offline
if ($LASTEXITCODE -ne 0) { throw 'Release build or audit failed.' }
```

| Variant | Application ID | Purpose |
|---|---|---|
| `debug` | `dev.outpost.app` | Debug-signed development and research hooks |
| `release` | `dev.outpost.app` | Non-debuggable unsigned APK/AAB awaiting production signing |
| `releaseQa` | `dev.outpost.app.releaseqa` | Non-debuggable test copy with a debug QA certificate |

Release excludes the Kev head/configuration, restricts JNI exports and rejects experimental generation policies. It currently keeps minification disabled. The release build produces all candidate/QA artifacts, runs lint and [audit-release.py](../eval/audit-release.py), then archives bytes and reports in a unique `.local/release-<timestamp>-<id>/` directory. `.local/release-build-receipt.json` identifies the latest successful build.

The audit checks manifest boundaries, ABIs, notices, research-asset exclusion, native exports, release/QA native and asset identity, and 16 KiB ELF/ZIP alignment. Static alignment does not prove 16 KiB runtime behavior. Receipts identify exact local bytes; they are not independent reproducible-build attestations.

## Sign and distribute

The distribution channel is the existing private [GitHub repository and Releases](https://github.com/nicoarias81/outpost/releases). Source is `0.19.0-rc1`, version code 21. A `v0.19.0` release draft exists; production identity/signing and exact signed-device acceptance remain pending. The proposed separate production ID `dev.outpost.mobile` and a new local signing key have not been approved or applied.

Once the owner provides or approves a signing identity, configure `OUTPOST_STORE_PASSWORD` and `OUTPOST_KEY_PASSWORD` in the invoking process and run:

```powershell
./scripts/sign-release.ps1 -KeyStore '<private-keystore-path>' `
    -KeyAlias '<alias>' -ExpectedCertificateSha256 '<verified-64-hex-fingerprint>'
```

Keep passwords out of command arguments, logs and Git; keep signing material outside tracked files. The script checks the source/audit receipt, signs a local APK, rejects debug/unexpected certificates, verifies signature/alignment and writes hash/certificate metadata. It does not upload, install or sign an AAB. Signing success and key recovery still need validation with the selected production identity.

Existing debug installations cannot be replaced by an unrelated certificate. Do not clear storage or uninstall the owner's app to work around a signature conflict. Test the final signed artifact and its data-preserving installation/update path before attaching it to the GitHub release with checksums, certificate fingerprint and validation results. Publish only that reviewed artifact. Never overwrite frozen bytes under an existing version.

`scripts/publish-artifact.ps1` is a separate **local debug artifact** workflow: it copies into ignored `dist/`, writes checksums and supports `-Verify`. It does not produce a production release.

## Common failures

| Failure | Action |
|---|---|
| SDK/JDK/native headers missing | Check local settings, `local.properties` and preparation output |
| Offline dependency resolution fails | Populate the host Gradle cache with connectivity |
| Receipt or installed APK mismatch | Rebuild/install matching bytes; a version label is insufficient |
| Model rejected | Select the matching profile and exact pinned GGUF; do not bypass verification |
| Detected CPU feature remains unused | Check compiled availability, policy and tensor eligibility separately |

Models, SDKs, AVDs, build caches, private imports and keys stay ignored. Preserve [dependency notices](../THIRD_PARTY.md). The current lint baseline is zero errors and three upstream BouncyCastle warnings; do not report it as warning-free.
