# Engineering handoff — start here

Consolidated: 2026-09-29. Application baseline: **Outpost 0.8.1 / version code 10**. This handoff includes the latest product corrections and the anonymized discovery discussion; a successor should not need the original chat or the earlier external draft to interpret the project.

## First five minutes

1. Read [AGENTS.md](../AGENTS.md), this handoff, and [current state](current-state.md).
2. Check `git status --short` and the latest log before editing. Do not overwrite another contributor's uncommitted work. The implementation baseline is commit `f2811ac`; subsequent handoff/discovery commits are documentation changes unless their diffs say otherwise.
3. Read [contextual question families](offline-world-knowledge.md) before changing product priorities. Read [architecture](architecture.md) and the relevant domain guide before implementation.
4. Select a bounded task from the [roadmap](roadmap.md), retaining its acceptance criteria. Read [evaluation](evaluation.md) to distinguish runtime correctness from task quality. Before proposing evaluation work, read [eval/README.md](../eval/README.md), the fixture manifest [eval/fixtures-v1.json](../eval/fixtures-v1.json) and the rubric [eval/rubric-v1.md](../eval/rubric-v1.md).
5. Before any runtime test, follow the [emulator operating guide](emulator-runbook.md) and [development](development.md), verify the AVD name as well as serial, matching installed APKs/models, and offline state. Archive evidence that a script would overwrite.

Canonical repository: `E:\projects\outpost`; documented working branch: `codex/outpost`; Android/Java identity: `dev.outpost.app`; native library: `outpost_engine`. A private GitHub remote exists at `nicoarias81/outpost`; no public release is configured and public visibility is deferred. The original `brujula-android` source/AVD is preserved; continue work in Outpost.

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

Database upgrades currently throw; schema changes need a migration preserving user imports. Peak memory, real Android memory pressure, phone battery/thermal behavior and real-device speed are not measured. The approximately 305 MiB cache retention observation is a historical post-run PSS sample, not a peak. The bounty's device, GrapheneOS, RAM, storage, phone-speed and public-publication requirements cannot be verified in the current emulator-only, private-repository scope and must never be reported as met; the trace and scope boundary live in [bounty-31.md](bounty-31.md).

## Last validated delivery, not a live-state guarantee

[Outpost 0.8.1 validation](validation-0.8.1.md) records a successful `scripts/build.ps1 -Offline` build with `:app:lintDebug` clean, 53 of 53 functional checks, `python eval/validate.py` exiting 0, 16,241 Q2 dot vectors with zero bit mismatches and a 5.79x microbenchmark speedup, 10,023 grouped prefill bitwise comparisons with zero mismatches, a teacher-forced audit bit-identical 24/24 at widths 2, 4 and 8, and completed real Bonsai 4B UI generation. The [0.8 validation record](validation-0.8.md) remains the historical 0.8.0 baseline and was not rewritten.

**Native review state.** The 0.8.1 work was reviewed as four candidate ranges and every one closed approved with its authority burned: the reduced scope `97dc1dc..cfb02f2`, then the slices `097d7ca`, `5f25fee` and `e84c3a0` against their own parents. Eight of the nine findings were closed in `a52f2b3`, which is itself **not reviewed**: two capture attempts on its range returned `reviewer-empty-output` with `stopReason: length` after 369 s and 352 s and mutated nothing, so no reviewed authority exists for that commit. Transaction detail and the single remaining finding are in [the 0.8.1 record](validation-0.8.1.md). A commit range is a review candidate; regenerated evidence committed alongside behaviour is what pushed an earlier candidate past the reviewer context budget, so keep them in separate commits.

The last recorded Outpost AVD is `Outpost35` / `emulator-5582`: four logical CPUs, 4 GiB RAM, 10 GB data partition; all three generators and Kev imported; Bonsai 4B selected; speculation off; airplane mode on and Wi-Fi off. Original Brújula used `emulator-5580`. Verify live state before testing; do not assume either emulator is running.

The debug APK is `dist/outpost-0.8.1-emulator-debug.apk`, 8,973,271 bytes, SHA-256 `5551f8047b1746b74e8e04ab0f7778d6c3e08457cc9134000b1c783bd8511676`; the retained `dist/outpost-0.8.0-emulator-debug.apk` is the previous historical build. `dist/` is ignored and is not guaranteed to exist in a fresh clone. Native/model/tool caches live under ignored `.local`; SDK/JDK/Gradle configuration is in ignored `.local/developer-settings.json` or explicit environment/parameters. See [developer settings example](../developer-settings.example.json) and [development](development.md).

Historical 0.7 evidence is preserved in `evidence/0.7-before-outpost`. Do not relabel it as current English performance. Some scripts overwrite fixed output paths; run identity and immutable snapshots remain backlog work E-03. Build activity and two emulators were present during parts of migration, so its timing values are diagnostic.

## Recommended next bounded work

These are engineering recommendations within the documented direction, not claims that every proposal is approved for implementation:

1. **E-01 + W-01 (definition done):** the question-family matrix now exists as the versioned manifest [eval/fixtures-v1.json](../eval/fixtures-v1.json) — 15 fixtures, 11 runnable and 4 blocked — with the machine validator `eval/validate.py` and the scoring rubric [eval/rubric-v1.md](../eval/rubric-v1.md). The remaining work is executing and scoring it rather than defining it; no fixture has been executed as a scored run. Keep retrieval, fixed-evidence inference, tools and integration scores separate.
2. **E-02:** reproduce known failures under controlled prompts and budgets, then identify retrieval, context selection, generation, truncation or arithmetic causes. Do not hide a failure by weakening its rubric.
3. **K-01 + K-03:** introduce the evidence/locator contract and a safe database migration path around the existing library. Keep search/reading useful with no model loaded.
4. **K-02 + one justified adapter:** implement a small versioned package lifecycle and one next source type based on the evaluation needs. PDF/manual, structured personal records and regional/reference data are candidates, not a mandate to implement all formats at once. W-02/W-03 cover contextual recommendations and cross-source reasoning.
5. **Runtime work as a separate measured task:** retain numerical/fallback/lifecycle checks for any kernel change; pursue ARM/VNNI only within supported execution scope. Revisit MTP only with a compatible trained component and a measured cost/memory case.
6. **Review `a52f2b3`:** the commit that closes the review findings carries no reviewed authority. Two capture attempts on its range failed with `reviewer-empty-output` and `stopReason: length` without mutating anything, so retry it on a reoffered one-slot binding or review it under a different reviewer configuration. Do not describe it as reviewed until an acknowledgement burns authority for it.
7. **`R3-width-report-coupling` is open:** it names `RuntimeChecks.java:122-123` but the controller exposes only an identifier, a lens, a location, a severity and a disposition per finding, never the reviewer's reasoning. Resolving it needs either the reviewer's text or a fresh reading of the batch phase's reporting against the acceptance rule it claims to follow.

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
| Evaluation definition | [eval/fixtures-v1.json](../eval/fixtures-v1.json), [eval/rubric-v1.md](../eval/rubric-v1.md), `eval/validate.py` |
| External bar | [bounty-31.md](bounty-31.md) |

Production Java lives in `app/src/main/java/dev/outpost/app`, test Java in `app/src/androidTest/java/dev/outpost/app`, and native code in `app/src/main/cpp`. The maintained [index](index.md) is the documentation map. `current-state.md` owns observed capabilities/failures; `roadmap.md` owns task status/acceptance; `decisions.md` owns rationale and proposal/adoption status. IDs are scoped to their owning document. Historical Spanish reports retain their experimental context and do not override the latest scope clarification.

## Close a work session

Record what changed, relevant source/config/model identities, the focused checks run, observed failures, and limitations. Update the owning docs and the decision/backlog status; preserve raw evidence. Leave a clean, reviewable Git diff or an explicit explanation of outstanding work. Do not declare complete field usefulness because execution tests passed. No new runtime validation is implied by a documentation-only handoff update.
