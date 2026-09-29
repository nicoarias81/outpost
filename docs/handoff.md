# Engineering handoff — start here

Consolidated: 2026-09-29. Application baseline: **Outpost 0.8.0 / version code 9**. This handoff includes the latest product corrections and the anonymized discovery discussion; a successor should not need the original chat or the earlier external draft to interpret the project.

## First five minutes

1. Read [AGENTS.md](../AGENTS.md), this handoff, and [current state](current-state.md).
2. Check `git status --short` and the latest log before editing. Do not overwrite another contributor's uncommitted work. The implementation baseline is commit `f2811ac`; subsequent handoff/discovery commits are documentation changes unless their diffs say otherwise.
3. Read [contextual question families](offline-world-knowledge.md) before changing product priorities. Read [architecture](architecture.md) and the relevant domain guide before implementation.
4. Select a bounded task from the [roadmap](roadmap.md), retaining its acceptance criteria. Read [evaluation](evaluation.md) to distinguish runtime correctness from task quality.
5. Before any runtime test, follow the [emulator operating guide](emulator-runbook.md) and [development](development.md), verify the AVD name as well as serial, matching installed APKs/models, and offline state. Archive evidence that a script would overwrite.

Canonical repository: `E:\projects\outpost`; documented working branch: `codex/outpost`; Android/Java identity: `dev.outpost.app`; native library: `outpost_engine`. No remote or public release is configured. The original `brujula-android` source/AVD is preserved; continue work in Outpost.

## Product intent and latest corrections

Build a general offline knowledge assistant for practical questions about the world and the user's situation. The original contexts remain traveler, farmer, field engineer, mountaineer, and driver without coverage.

The user explicitly clarified that Vitalik's restaurant example is about **the kind of question**: specific facts in context, discovery, recommendations with constraints, comparison/compatibility, explanation, cross-source synthesis, and bounded troubleshooting. It does not define a vegan/restaurant application or a mandatory restaurant benchmark. Do not replace that general aim with a personal-file finder either.

Public/reference material, regional/activity packages, personal documents and user observations are complementary **content sources**. The two **technical domains** are inference and knowledge. Do not confuse content categories with separate products or technical domains.

The [group-feedback note](discovery-2026-09-29.md) suggests personal document recall, conversation/reflection, work queued for later connectivity, and manual-assisted equipment diagnosis. It is anonymized qualitative input, not validated demand or blanket implementation approval. The issue-list and restaurant examples are illustrative fixtures, not two fixed demos that define product acceptance. Action/reflection/equipment ideas remain proposed discovery tracks.

## What exists

| Area | Implemented baseline |
|---|---|
| Android app | Native Java/JNI/C++; API 28 minimum, 35 target; x86_64 APK; English UI/resources and seed content |
| Knowledge | Six attributed demo notes, SQLite FTS4, full-source reader, TXT/Markdown UTF-8 imports up to 1 MiB |
| Inference | In-process pinned llama.cpp with Qwen 1.5B and Ternary Bonsai 1.7B/4B; optional Kev first-sentence review |
| Runtime | AVX2/F16C ternary dot path with fallback, grouped prefill, per-device/model/build profiles, bounded prompt caching |
| Speculation | Same-request context proposals verified by Bonsai 4B; experimental and off by default |
| Reproducibility | Dependency/model lock files, scripts, raw JSON/screenshots, English guides, and preserved adverse results |

Missing capabilities include PDF/DOCX/XLSX/ZIM/OSM adapters, a versioned knowledge-package manager, structured tools, persistent multi-turn mission context, GPS/routing, voice/vision, queued external actions, USB/network equipment adapters, and a complete ARM APK/runtime. The exact capability inventory belongs to [current-state.md](current-state.md).

## Runtime facts and limits to preserve

- All model/numerical execution stays inside the emulator. Host compilation, checksums and static/header inspection are allowed. No physical Pixel or host inference is part of the current scope.
- Backend and weights are pinned. Native numerical implementation after the Outpost rename matches the old implementation after normalizing identity changes. Vendor llama.cpp remains unmodified.
- The Q2 files use g64 blocks; g128 and PQ2_0 are not interchangeable. Capability descriptors for VNNI/ARM are not implemented custom kernels.
- The locked Bonsai 4B has zero MTP layers. Context speculation is not MTP. The 1.7B drafter result is an optimistic cost estimate, not a dual-model implementation.
- Context is 2,048 tokens; UI generation allows 192 output tokens and a 120-second deadline. Bonsai uses top-k 20/top-p 0.8/temperature 0.7/seed 42; Qwen uses greedy sampling.
- Exact prompt reuse retains final-prompt logits; partial reuse uses complete aligned batches. Previous output tails are removed. Model/config changes, cancellation/error, backgrounding and memory callbacks control invalidation/release. Prompt cache is not conversation history.
- Batched logits can differ from serial logits. Do not promise universal speculative text equality for the same seed. The historical `energy-audit` filename denotes a content/numerical fixture, not power consumption.

See [runtime](inference-runtime.md), [optimization evidence](optimizations.md), [speculation/MTP](speculation.md), and [decisions](decisions.md) before revisiting these choices.

## Known quality and resource gaps

The English 0.8 field fixtures contain source repetition and truncation. The farmer case fails to identify F-28; the engineering answer incorrectly turns missing authorization records into a claim that no authorization exists. The traveler result is partial. The mountaineer arithmetic and driver page lookup work in those particular fixtures. This is not real field validation. Read the [fresh content review](../evidence/strata/mission-review.json).

Kev passed simple probes but falsely supported two harder diagnostic claims. Numeric citation checks verify reference ranges, not claim support; the English UI example cites one passage for material also supplied in another. Neither mechanism is evaluation ground truth.

Database upgrades currently throw; schema changes need a migration preserving user imports. Peak memory, real Android memory pressure, phone battery/thermal behavior and real-device speed are not measured. The approximately 305 MiB cache retention observation is a historical post-run PSS sample, not a peak.

## Last validated delivery, not a live-state guarantee

[Outpost 0.8 validation](validation-0.8.md) records build/lint, 48 functional checks, 17 Qwen generation checks, 25 Kev integration checks, 64 dispatch checks, 16,241 dot vectors, 7,932 grouped values, 561 speculation unit checks, ten native object builds, and Bonsai 4B UI/cache/memory-callback checks.

The last recorded Outpost AVD is `Outpost35` / `emulator-5582`: four logical CPUs, 4 GiB RAM, 10 GB data partition; all three generators and Kev imported; Bonsai 4B selected; speculation off; airplane mode on and Wi-Fi off. Original Brújula used `emulator-5580`. Verify live state before testing; do not assume either emulator is running.

The debug APK is `dist/outpost-0.8.0-emulator-debug.apk`, SHA-256 `bf19c00f4e043435c8c4f262d90c5c9b021665810b57dd5caaf97aa751f3a622`. `dist/` is ignored and is not guaranteed to exist in a fresh clone. Native/model/tool caches live under ignored `.local`; SDK/JDK/Gradle configuration is in ignored `.local/developer-settings.json` or explicit environment/parameters. See [developer settings example](../developer-settings.example.json) and [development](development.md).

Historical 0.7 evidence is preserved in `evidence/0.7-before-outpost`. Do not relabel it as current English performance. Some scripts overwrite fixed output paths; run identity and immutable snapshots remain backlog work E-03. Build activity and two emulators were present during parts of migration, so its timing values are diagnostic.

## Recommended next bounded work

These are engineering recommendations within the documented direction, not claims that every proposal is approved for implementation:

1. **E-01 + W-01:** define a versioned question-family evaluation matrix across varied subjects and public/regional/personal evidence. Include held-out questions, ambiguity, missing/conflicting data and exact success/critical-failure criteria. Keep retrieval, fixed-evidence inference, tools and integration scores separate.
2. **E-02:** reproduce known failures under controlled prompts and budgets, then identify retrieval, context selection, generation, truncation or arithmetic causes. Do not hide a failure by weakening its rubric.
3. **K-01 + K-03:** introduce the evidence/locator contract and a safe database migration path around the existing library. Keep search/reading useful with no model loaded.
4. **K-02 + one justified adapter:** implement a small versioned package lifecycle and one next source type based on the evaluation needs. PDF/manual, structured personal records and regional/reference data are candidates, not a mandate to implement all formats at once. W-02/W-03 cover contextual recommendations and cross-source reasoning.
5. **Runtime work as a separate measured task:** retain numerical/fallback/lifecycle checks for any kernel change; pursue ARM/VNNI only within supported execution scope. Revisit MTP only with a compatible trained component and a measured cost/memory case.

D-01 through D-05 capture discovery candidates. USB changes, networked executors, background delivery and clinical positioning are not current capabilities or permissions. Use mock transports/destinations for any initial prototype within scope. Code license, release signing, public hosting, large-evidence policy, and physical-device testing remain open decisions.

## Source entry points and document ownership

| Task | Starting files / documents |
|---|---|
| UI and retrieval | `MainActivity.java`, `Library.java`, `ResearchPrompt.java`; [knowledge design](knowledge-base.md) |
| Model files / settings | `ModelStore.java`, `JudgeStore.java`, `RuntimeSettings.java`; root lock files |
| Inference / cache | `NativeEngine.java`, `engine.cpp`; [runtime](inference-runtime.md) |
| Kernels | `q2_kernel.c`, `q2_batch.c`, `cpu_caps.c`, `q2_dispatch.c`; [optimizations](optimizations.md) |
| Speculation | `speculation.cpp`, `SpeculationChecks.java`, `test-speculation.ps1` |
| Product and evaluation | [question families](offline-world-knowledge.md), [overview](project-overview.md), [evaluation](evaluation.md) |

Production Java lives in `app/src/main/java/dev/outpost/app`, test Java in `app/src/androidTest/java/dev/outpost/app`, and native code in `app/src/main/cpp`. The maintained [index](index.md) is the documentation map. `current-state.md` owns observed capabilities/failures; `roadmap.md` owns task status/acceptance; `decisions.md` owns rationale and proposal/adoption status. IDs are scoped to their owning document. Historical Spanish reports retain their experimental context and do not override the latest scope clarification.

## Close a work session

Record what changed, relevant source/config/model identities, the focused checks run, observed failures, and limitations. Update the owning docs and the decision/backlog status; preserve raw evidence. Leave a clean, reviewable Git diff or an explicit explanation of outstanding work. Do not declare complete field usefulness because execution tests passed. No new runtime validation is implied by a documentation-only handoff update.
