# Outpost 0.8 — Repository and English baseline

> Historical record: observations, proposals and commands below describe the recorded release/session. For Outpost 0.12 use [current state](current-state.md), [handoff](handoff.md) and [current validation](validation-0.12.md). Original results and language are preserved.

Status: repository migration, application build, and English validation completed. Remaining answer-quality failures are documented below. Execution is restricted to the dedicated AOSP Android 15 x86_64 emulator.

## Identity and preservation

The user selected **Outpost**, with the slogan **Knowledge beyond coverage.** The standalone local repository is `E:\projects\outpost`, on branch `codex/outpost`. Android/Java identity is `dev.outpost.app`; the native library is `outpost_engine`; the version is 0.8.0 / code 9. No remote or public publication was created.

The original Brújula source and emulator remain intact. Model/toolchain caches were copied into ignored local storage; the running old AVD was neither moved nor copied. Outpost has its own AVD on `emulator-5582` with four logical CPUs, 4 GiB RAM and a 10 GB data partition. SDK/JDK/Gradle paths are explicit local configuration rather than sibling-folder assumptions.

Prior evidence is preserved under `evidence/0.7-before-outpost`. Existing documentation links to those archived records so new English runs do not silently replace historical measurements. Earlier APKs remain separate in ignored `dist`.

## English baseline

UI labels are English resources, including dialogs, accessibility text and status screens. Six demonstration notes, primary retrieval/generation fixtures, and the application prompt were translated. Java compilation explicitly uses UTF-8. Existing imported content is not automatically translated. Explicit bilingual Kev probes and historical output retain original languages.

App ID, Java/test packages, JNI symbols, own native prefixes, CMake targets, instrumentation components, model-import paths and scripts now use Outpost consistently. Native numerical code is identical to 0.7 after normalizing the identity substitutions; no kernel algorithm or model weights changed.

## Verification completed

| Check | Result | Evidence |
|---|---|---|
| App/test APK build and Android Lint | Passed; no lint issues | Local Gradle reports |
| Functional retrieval/import/UI | 48 checks passed on the final app | [Report](../evidence/archive/0.8.0/checks.json) |
| Qwen generation, cancellation and recovery | 17 checks passed | [Report](../evidence/archive/0.8.0/generation-checks.json) |
| Kev integration, cancellation, model switching and UI | 25 functional checks passed | [Report](../evidence/archive/0.8.0/review-checks.json) |
| Dispatch | 64 checks passed | [Report](../evidence/archive/0.8.0/optimization/kernel-dispatch.json) |
| Q2 dot product | 16,241 vectors; zero bit differences | [Report](../evidence/archive/0.8.0/optimization/kernel-numeric.json) |
| Grouped Q2 kernel | 7,932 values; identical bits and graph dispatch | [Report](../evidence/archive/0.8.0/optimization/kernel-batch.json) |
| Speculation controller and sampling | 561 checks, zero failures | [Report](../evidence/archive/0.8.0/speculation/speculation-unit.json) |
| Compile portability | Ten x86_64/ARM64 objects compiled | [Report](../evidence/archive/0.8.0/optimization/portability.json) |
| Bonsai 4B UI, repeat and memory callback | Completed with identical response text | [Cold](../evidence/archive/0.8.0/bonsai-ui-bonsai4.json), [warm](../evidence/archive/0.8.0/bonsai-warm-bonsai4.json), [trim](../evidence/archive/0.8.0/bonsai-trim-bonsai4.json) |

The first speculation-unit invocation stopped at its missing-Bonsai prerequisite; after verified model import, the actual checks passed. This was setup failure, not a numerical test result. A symbol-encoding defect found in the first screenshot was corrected and the app rebuilt. The initial screenshot is [preserved](../evidence/migration-initial/home-before-encoding-fix.png).

The first mission run retained an old Spanish `RESPUESTA` delimiter. That fixture was corrected to `ANSWER` and rerun; the mixed-label run is [archived separately](../evidence/migration-initial/runtime-missions-mixed-label.json). Primary English source prompts are not changed further to chase favorable quality scores during this migration.

## Measured runtime observations

A small English prefill control selected width four: median total 14,627 ms with width one versus 12,183 ms with width four. This uses a 142-token prompt and a deliberately four-token output cap, not a successful field mission. [Profile](../evidence/archive/0.8.0/speculation/speculation-profile.json).

The Bonsai 4B UI example generated 114 tokens in 31,420 ms with its first token at 13,514 ms. An exact repeat reused all 211 prompt tokens, emitted the first token at 11 ms, and completed in 22,325 ms. A simulated memory callback then forced zero reused tokens while preserving the answer. These are individual emulator observations, not matched phone benchmarks or battery estimates.

The English answer explains kW/kWh and the 100 W × 3 h = 300 Wh example correctly, but cites `[1]` for material that also came from passage `[2]`. Numeric citation validity does not establish precise claim-level attribution.

## English mission content review

The final control uses English throughout, including the `ANSWER` delimiter. All five baseline/grouped pairs preserved text, but the harness PASS certifies execution and parity rather than five successful tasks.

| Context | Observed content |
|---|---|
| Traveler | Correct storage option and time are present, but source repetition consumes the budget and the response truncates |
| Farmer | Fails to identify F-28; copies the source and truncates at 96 tokens |
| Field engineer | Correctly refuses to infer authorization, but overstates missing records as proof that no authorization exists |
| Mountaineer | Correctly calculates the 250 m elevation difference in this fixture |
| Driver | Returns page 42, with unnecessary source repetition |

[Raw paired results](../evidence/archive/0.8.0/strata/runtime-missions.json) · [content review](../evidence/archive/0.8.0/strata/mission-review.json). The 0.7 Spanish mountaineer failure remains historical evidence; the different English result is not proof that a general reasoning defect has been fixed.

This migration session included host builds and two separate emulators. Timing observations are diagnostic, not controlled measurements of a new optimization or physical-phone performance.

## Reviewer result and final emulator state

Kev passed 25 functional checks and all 12 simple bilingual probes. It still produced **two false-support decisions** on the harder error diagnostics. This confirms that reviewer agreement must not be used as a truth label or evaluation ground truth. The fresh [review report](../evidence/archive/0.8.0/review-checks.json) records the English library and retained bilingual fixture version.

The final emulator has all three generator profiles and Kev imported, Bonsai 4B selected, context speculation off, airplane mode on, and Wi-Fi off. The installed APK SHA-256 matches the release artifact. The original emulator still has Brújula 0.7.0. [Migration QA](../evidence/archive/0.8.0/migration-check.json) records preservation, identity and local-document checks.

## Remaining scope

Complete field workflows, larger knowledge packages, OSM/ZIM/PDF adapters, ARM APK execution, real memory pressure, physical phone performance, thermal behavior, and battery use remain unvalidated or unimplemented as described in the [roadmap](roadmap.md). Code license, release signing, and public hosting are still open decisions.

The current `dist/outpost-0.8.0-emulator-debug.apk` has a `dist/outpost-0.8.0-emulator-debug.apk.sha256`. Build artifacts, models, AVDs, caches, private files, and signing material are excluded from Git.

Final APK SHA-256: `bf19c00f4e043435c8c4f262d90c5c9b021665810b57dd5caaf97aa751f3a622`.
