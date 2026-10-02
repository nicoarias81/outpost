# Build and release

This build procedure uses Windows and PowerShell. Commands use paths relative to the repository root. The [test guide](testing.md) gives the Android procedures. A successful build does not show correct operation on a device.

## Necessary tools

| Tool | Version or setting |
|---|---|
| Java | JDK 21 in the recorded tests; application language level 17 |
| Gradle / Android Gradle Plugin | 8.11.1 / 8.9.2 |
| Android SDK | Platform 35, Build Tools 35.0.0 and platform-tools |
| NDK / CMake | r28b (`28.1.13356709`) / 3.22.1 |
| Other host tools | Python 3.9+, Git and PowerShell |
| Optional emulator image | `system-images;android-35;default;x86_64` |

[toolchain-lock.json](../toolchain-lock.json), the Gradle wrapper and [app/build.gradle](../app/build.gradle) specify the versions. NDK and CMake archive checks use the hashes in the lock file. Model checks use SHA256.

## Preparation procedure

1. Open PowerShell in the repository root.
2. Make `.local/developer-settings.json` from [developer-settings.example.json](../developer-settings.example.json).
3. Set `sdk`, `javaHome`, `gradleHome` and `pythonExecutable` to the correct paths on your computer.
4. For release builds, set `gradleExecutable` to the installed Gradle executable.
5. Make the ignored `local.properties` file.
6. Set `sdk.dir` to the SDK path, for example `sdk.dir=C:/Android/Sdk`.
7. Run the commands below in sequence.

```powershell
. ./scripts/environment.ps1
$OutpostPython = (Get-OutpostSettings).pythonExecutable
if (-not $OutpostPython) { $OutpostPython = (Get-Command python -ErrorAction Stop).Source }
$env:PYTHONUTF8 = '1'
& $OutpostPython scripts/prepare-native.py
if ($LASTEXITCODE -ne 0) { throw 'Native preparation failed.' }
```

The preparation script gets the specified backend, NDK, CMake and Qwen test model. It adds `cmake.dir` to `local.properties`. It does not install the base SDK or JDK. For initial preparation, network access and several GB of free storage are necessary. The script reuses files that pass its cache checks.

The debug build can use the Gradle wrapper if `gradleExecutable` is absent. That setting is necessary for the current release-build script. Machine-specific settings must stay outside Git.

For Bonsai weights, run `scripts/prepare-bonsai.py` with the configured Python interpreter. For optional Kev research, run `scripts/prepare-judge.py`. Product use does not need Kev preparation.

## Debug build procedure

1. If the Gradle cache is empty, omit `-Offline` from the first build.
2. Run the commands below in sequence.

```powershell
./scripts/build.ps1 -Offline
if ($LASTEXITCODE -ne 0) { throw 'Debug build failed.' }
./eval/check-build.ps1
```

The script builds both ABIs and the test APK. It runs lint and compares backend and PDF dependency identities with their specified values. It also makes sure that source inputs do not change during the build.

Build outputs are in `app/build/outputs`. `.local/build-receipt.json` identifies the debug app, test APK and source inputs. A direct Gradle command does not make this receipt.

## Release build procedure

1. Make sure that all dependencies are in the local cache.
2. Run these commands:

```powershell
./scripts/build-release.ps1 -Offline
if ($LASTEXITCODE -ne 0) { throw 'Release build or audit failed.' }
```

| Variant | Application ID | Function |
|---|---|---|
| `debug` | `dev.outpost.app` | Development build with a debug certificate and research functions |
| `release` | `dev.outpost.app` | Non-debuggable unsigned APK and AAB |
| `releaseQa` | `dev.outpost.app.releaseqa` | Non-debuggable test copy with a debug QA certificate |

Release builds exclude the Kev head and configuration. They limit JNI exports and reject experimental generation policies. Minification is disabled.

The release procedure builds candidate and QA files. It runs lint and [audit-release.py](../eval/audit-release.py). It then saves the files and reports in a new `.local/release-<timestamp>-<id>/` directory. `.local/release-build-receipt.json` identifies the last successful release build.

The audit examines manifest settings, ABIs, notices, assets, JNI exports and native-file identity. It also examines 16 KiB ELF and ZIP alignment. Static alignment does not prove operation on a 16 KiB device. A receipt identifies exact local bytes. It is not independent proof of a reproducible build.

## Release state

The source version is `0.19.0-rc2`, version code 22. The private [GitHub repository](https://github.com/nicoarias81/outpost/releases) has a `v0.19.0-rc2` candidate release draft. Its tag identifies the current source, including the license and release instructions.

The draft contains the recorded QA APK, source archive, checksums, license files and artifact metadata. `release-manifest.json` records the APK hash, certificate and source identity. The QA APK has a development certificate and application ID `dev.outpost.app.releaseqa`. Production identity, signing and final signed-device acceptance are not complete.

The proposed production ID is `dev.outpost.mobile`. Owner approval is still necessary for that ID and a new local signing key. The current app does not use that proposed ID. Public source and a signed release are delivery goals. Owner action and completed acceptance tests are also necessary for public visibility and release publication.

## Signing procedure

Owner approval and a known signing identity are necessary before this procedure. The procedure does not make a signing key.

1. Set `OUTPOST_STORE_PASSWORD` in the current process.
2. Set `OUTPOST_KEY_PASSWORD` in the current process.
3. Get the approved certificate's SHA256 fingerprint through an independent check.
4. Replace the three placeholders below with the actual values.
5. Run the command.

```powershell
./scripts/sign-release.ps1 -KeyStore '<private-keystore-path>' `
    -KeyAlias '<alias>' -ExpectedCertificateSha256 '<verified-64-hex-fingerprint>'
```

Do not put passwords in command arguments, logs or Git. Keep signing material outside tracked files.

The script examines the build and audit receipts. It signs a local APK and rejects debug or unexpected certificates. It examines the signature and alignment, then writes file-hash and certificate metadata. It does not upload files, install an APK or sign an AAB. Tests with the selected production identity are still necessary for signing and key recovery.

An unrelated certificate cannot update an existing debug installation. Do not uninstall the owner's app to correct a signature mismatch. Do not clear its storage. Do not replace a frozen artifact with different bytes under the same version.

## License files

Outpost's original code uses `AGPL-3.0-only`. Commercial use is permitted under those terms. Third-party dependencies, model weights and data keep their own licenses.

1. Include `LICENSE`, `NOTICE` and `THIRD_PARTY.md` with a distributed APK.
2. Provide the corresponding source and build instructions as the license requires.
3. Preserve all component-specific notices.

The license decision did not change the recorded RC2 APK or its functionality.

## Publication procedure

1. Do installation and update tests with the final signed APK.
2. Make sure that the tests keep existing user data.
3. Associate that APK with its exact source tag.
4. Add the APK, SHA256 and certificate fingerprint to the release.
5. Add the tested device and OS details.
6. Add the validation results and their limits.
7. Publish only the reviewed artifact.

The README gives direct model downloads. Lock files specify dependency and model versions, sizes and hashes. Users supply their documents and regional extracts. The app makes SQLite indexes locally. It does not contain a complete world corpus or a remote search index.

A new user must be able to install the app from the README without developer tools. This installation trial must use the production-signed APK.

1. Measure the setup time on a clean compatible phone.
2. Record download time separately.
3. Record offline operation with several questions, complete answers and source display.
4. Remove private content from release material.

`scripts/publish-artifact.ps1` is a separate local procedure for debug artifacts. It saves files in ignored `dist/` and writes checksums. `-Verify` examines an existing artifact. This procedure does not make a production release.

## Fault isolation

| Condition | Action |
|---|---|
| Missing SDK, JDK or native headers | Examine local settings, `local.properties` and preparation output. |
| Offline dependency resolution failure | Populate the host Gradle cache with network access. |
| Receipt or APK mismatch | Rebuild the source. Install the matching files. A version label is not sufficient. |
| Model rejection | Select the correct profile. Use its exact specified GGUF. Do not bypass the file checks. |
| Detected CPU feature stays unused | Examine compiled availability, enabled policy and tensor conditions separately. |

Keep models, SDKs, AVDs, caches, private imports and keys outside Git. Keep the [dependency notices](../THIRD_PARTY.md). The lint baseline has zero errors and three upstream BouncyCastle warnings.
