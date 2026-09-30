# Standalone repository and English migration

> Historical record: observations, proposals and commands below describe the recorded release/session. For Outpost 0.12 use [current state](current-state.md), [handoff](handoff.md) and [current validation](validation-0.12.md). Original results and language are preserved.

Status: Outpost selected and the independent local repository created at `E:\projects\outpost`. This document retains the migration checklist and records the adopted identity. See [0.8 validation](validation-0.8.md) for execution results.

## Adopted identity and preservation

| Item | Value |
|---|---|
| Product / slogan | Outpost / Knowledge beyond coverage. |
| Repository / branch | `E:\projects\outpost` / `codex/outpost` |
| Android namespace and applicationId | `dev.outpost.app` |
| Native library and own symbol prefix | `outpost_engine` / `outpost_` |
| Default product language | English |
| App version | 0.8.0 / version code 9 as established by this migration; subsequent releases are listed in [current state](current-state.md) |
| Dedicated AVD | Outpost35, `emulator-5582`, 4 GiB RAM, 10 GB data partition |

The original `C:\Users\nicoa\Documents\ChatGPT\muna 7\brujula-android` working copy and `emulator-5580` are preserved. Source and verified local caches were copied; the running old AVD was not moved or copied. The new AVD has an independent application installation. SDK/JDK/Gradle remain explicit external prerequisites, configured in ignored local settings. Historical evidence is preserved under `evidence/0.7-before-outpost`; old APKs remain in ignored `dist`.

## Migration inventory

| Material | Treatment |
|---|---|
| `app/src`, Gradle build files/wrapper, scripts | Copy as maintained project source; preserve vendor notices and auxiliary Kev assets |
| Locks, backend revision, English and historical docs | Copy and track; retain exact model/dependency identity |
| Reviewed synthetic JSON/screenshots under `evidence` | Preserve; choose a size/privacy-aware Git policy before initial staging |
| `dist` APKs/checksums | Preserve outside normal source history or in local release storage; add publication links only after a release destination exists |
| `.local/llama.cpp` | Recreate at pinned revision or use a verified local copy; never stage its nested Git repository |
| `.local` models/toolchains/downloads | Keep ignored; reuse verified caches where practical or reprepare them |
| `.local/avd` and Android app data | Preserve separately; do not copy or move a running disk image as if it were an ordinary file |
| `.gradle`, `build`, `.cxx`, machine paths | Regenerate or configure locally; exclude from Git |
| Private imports, device logs, credentials/signing material | Exclude from repository and public evidence |
| Shared workspace `.git` and unrelated projects | Leave in place; do not bring them into the standalone repository |

The current ignore file already excludes the main build/cache/model directories and `dist`. It does not classify every evidence file or future private input. Review the initial staging list explicitly.

## Migration checklist

1. Inspect `E:\projects` and the exact target. If it already exists, inventory it before writing. Resolve source/destination paths and verify that all operations remain inside the intended project directories.
2. Capture a file/hash inventory and preserve local release artifacts and evidence. Copy maintained source first; do not delete the original as part of migration.
3. Initialize an independent Git repository at the target, using a `codex/` working branch where appropriate. Do not invent a remote or publish it. Select the project code license separately before public distribution.
4. Configure SDK/JDK/Gradle cache through explicit settings. Replace the `../work/bug-hunter-toolchain` assumptions with documented configuration. Regenerate `local.properties` and native build output for the new root.
5. Prepare pinned native dependencies and verify model cache hashes. Preserve the old AVD while establishing a separate or deliberately transferred test environment. If the existing AVD must move, stop that emulator first, copy it consistently, update paths, and verify boot before retiring the old location.
6. Apply the naming map across Gradle namespace/applicationId, Java/test packages, JNI symbols, `System.loadLibrary`, CMake target, scripts, resources, and artifact labels. Source file paths and test instrumentation component names must agree.
7. Treat a changed applicationId as a new Android app identity. Existing private model files, databases, and preferences will not automatically migrate. Plan explicit reimport/export or a controlled migration; do not uninstall the old app before preservation is verified.
8. Build, lint, run functional/model/JNI smoke tests in the emulator, and recheck offline state. Run focused kernel/cache checks if integration symbols or settings changed. Save migration evidence and a new artifact identity.
9. Stage only the reviewed source/docs/evidence set. Inspect diff, large files, private content, and ignore behavior before the initial commit. Keep the original working copy until the new one is demonstrably usable.

Renaming does not justify deleting historical evidence or altering its app/model identities. Cross-link old and new release names in a migration note.

## English implementation checklist

- Extract UI strings, accessibility labels, errors, and plurals into resources; default resources use English. The manifest label should reference a resource rather than embed the old name.
- Replace the explicitly Spanish system prompt with a deliberate English default. Define response-language behavior separately from UI language if multilingual support is added.
- Translate maintained demo notes and examples with source attribution preserved. Do not silently translate private user documents or raw historical model output.
- Review language-specific stopwords, normalization, token boundaries, number/date/unit formatting, and English retrieval fixtures.
- Update scripts' user-facing text, inline developer documentation, package paths, and screenshots.
- Rerun task-quality baselines. Translation changes tokenization, prefill cost, output behavior, and cache/speculation match rates; old Spanish timings are historical comparisons, not an English baseline.
- Preserve historical Spanish reports under an explicit archival policy; produce labeled translations if needed.

## Open decisions

Project code license, release signing, large-evidence distribution, package catalog format, and whether to add Spanish as a supported UI locale remain open. A private GitHub remote exists; public visibility is deferred to a final usable version. Outpost naming is settled; the remaining choices are not implied by a local repository. Acceptance tasks are R-01 through R-05 in the [roadmap](roadmap.md).
