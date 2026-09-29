# Outpost

*Knowledge beyond coverage.*

An Android prototype for consulting local knowledge and generating answers without connectivity. It is intended for travelers, farmers, field engineers, mountaineers, and people traveling through areas without signal.

**Current build: 0.8.0, emulator only.** Outpost is an independent local Git repository at `E:\projects\outpost`, with Android application ID `dev.outpost.app` and native library `outpost_engine`. The original Brújula 0.7 working copy remains preserved.

The interface, seed library, main prompts, and maintained documentation are in English. Historical reports and raw experimental outputs preserve their original language; explicitly labeled bilingual reviewer probes remain in the tests.

Migration and fresh English validation: [0.8 report](docs/validation-0.8.md). Earlier performance measurements remain historical controls and have not been relabeled as English results.

## Start here

- [Documentation index](docs/index.md): reading paths and document ownership.
- [Product scope](docs/project-overview.md): users, field workflows, and success criteria.
- [Current status](docs/current-state.md): implemented capabilities, gaps, and known failures.
- [Architecture](docs/architecture.md): current code and intended separation of inference from knowledge.
- [Roadmap](docs/roadmap.md): prioritized tasks, dependencies, and acceptance criteria.
- [Decision register](docs/decisions.md): adopted choices, proposals, and deferred options.
- [Developer guide](docs/development.md): preparation, builds, emulator tests, and troubleshooting.

## What works today

The app searches six attributed demonstration notes and imported UTF-8 text/Markdown documents using SQLite FTS4. Users can open the full source and inspect a retrieved passage. Local generation runs inside the Android process through JNI and pinned llama.cpp, using Qwen2.5 1.5B or Ternary Bonsai 1.7B/4B. An optional Kev classifier reviews the first sentence of a draft.

Bonsai 4B has a custom AVX2/F16C dot kernel, grouped prefill, device-specific runtime profiles, prefix caching, and optional context speculation. Speculation is experimental and off by default. See [measured optimizations](docs/optimizations.md) and [speculation and MTP](docs/speculation.md).

The APK has no network permission and no Google Play Services dependency. Model and dependency preparation happens on the development computer; model execution stays inside the emulator.

## What is not ready

There are no OSM, ZIM, PDF/OCR, routing, GPS, voice, or vision adapters. Complete field missions are not implemented. The recorded traveler and mountaineer fixtures include incorrect answers. A valid citation number or a favorable Kev score does not establish factual correctness.

The APK contains only x86_64 code. ARM object compilation and CPU capability detection do not establish ARM app support, Pixel performance, battery life, or GrapheneOS compatibility. Physical phone execution remains outside the authorized test scope.

## Build and verify

Requirements: JDK 17–23 (tested with 21), Python 3.9+, Git, Android SDK platform 35 and Build Tools 35.0.0. The Windows preparation scripts obtain pinned NDK r28b, CMake 3.22.1, llama.cpp, and model files. The Gradle wrapper pins 8.11.1; AGP is 8.9.2.

After configuring `JAVA_HOME`, `ANDROID_HOME`, and a local `sdk.dir`, run:

```powershell
python scripts/prepare-native.py
python scripts/prepare-judge.py
python scripts/prepare-bonsai.py
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
pwsh -File scripts/start-emulator.ps1
```

Wait for the emulator to finish booting. Then run the applicable tests in the [developer guide](docs/development.md). First-time preparation needs internet access on the host and several GB of storage. `--offline` builds require an already populated dependency cache. Models are imported separately from the APK.

## Evidence and release

The local [Outpost 0.8.0 debug APK](dist/outpost-0.8.0-emulator-debug.apk) has a [SHA-256 sidecar](dist/outpost-0.8.0-emulator-debug.apk.sha256). The [0.7.0 artifact](dist/brujula-0.7.0-emulator-debug.apk) remains a separate historical build.

`dist/` is ignored by Git; these local artifact links will need a release destination when the repository is published. Historical measurements are linked from the [evaluation guide](docs/evaluation.md). The migration report distinguishes fresh English functional checks from historical performance claims.

Dependency provenance is recorded in [THIRD_PARTY.md](THIRD_PARTY.md) and the model/toolchain lock files. The project code license remains to be selected before public distribution. The [historical Spanish README](README.legacy-es.md) is retained for traceability.

The original motivation includes [poidh bounty #31](https://poidh.xyz/mainnet/bounty/31). No bounty submission or public repository has been created, and the prototype does not demonstrate that the bounty requirements are met.
