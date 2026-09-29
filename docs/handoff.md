# Engineering handoff

Snapshot: 2026-09-29, Outpost 0.8.0 / version code 9. Start with the [documentation index](index.md) and [current state](current-state.md).

## Resume with these constraints

- Runtime work stays inside the emulator. Do not install on a physical Pixel or execute model inference on the host.
- The selected name is Outpost. The independent local repo is `E:\projects\outpost`, branch `codex/outpost`, app ID `dev.outpost.app`. There is no configured remote.
- The maintained project, UI, main prompts and seed data are English. Historical evidence and explicit bilingual reviewer probes preserve original language.
- Preserve vendor sources, locked model identities, and unfavorable experimental results.
- Do not treat a test harness PASS, a citation marker, or a Kev score as mission success.

## Key entry points

| Work | Start here |
|---|---|
| UI / local source workflow | `MainActivity.java`, `Library.java`, `ResearchPrompt.java` |
| Native lifecycle / cache / generation | `NativeEngine.java`, `engine.cpp` |
| Ternary kernels | `q2_kernel.c`, `q2_batch.c`, `cpu_caps.c`, `q2_dispatch.c` |
| Speculation | `speculation.cpp`, `SpeculationChecks.java`, `test-speculation.ps1` |
| Reproduction | [Developer guide](development.md), model locks, `llama-revision.txt` |
| Product work | [Knowledge contract](knowledge-base.md), [roadmap](roadmap.md), [evaluation](evaluation.md) |

Java production files are under `app/src/main/java/dev/outpost/app`, tests under `app/src/androidTest/java/dev/outpost/app`, and native files under `app/src/main/cpp`.

## Current delivery and continuation

Use the [0.8 validation report](validation-0.8.md) for the current APK, checks, emulator state, and English quality observations. The old emulator and working copy remain intact. Native/model identities remain pinned. Do not present 0.7 Spanish performance numbers as 0.8 English measurements.

Continue with mission fixture identity and rubric E-01/E-02, then evidence/package/document work K-01 through K-04. Kernel work remains independent from product task completion. Project code license, release signing, and public hosting remain open.

The [migration record](repository-migration.md) explains tool configuration, storage boundaries, and the renamed app identity. Check live state before resuming; no document guarantees the emulator is still running.
