# Changelog

This history is reconstructed from saved validation reports. Version dates are omitted where no release timestamp was established. Historical artifacts retain the Brújula name. No public release or repository publication is implied.

## 0.18.0 — 2026-10-02

- Added guarded matrix-only I8MM prefill using existing activation workspace and exact original FP32 accumulation order; ordinary DotProd decoding and fixed-attention semantics remain.
- Added explicit matrix policy, cache identity and profile persistence; actual hardware/compiler/path checks prevent format/ISA labels being mistaken for execution.
- Preserved all logits/answers in numeric, model and direct combined-stack comparisons; validated cancellation/cache/UI/model/x86 behavior and retained all earlier artifacts and unfavorable measurements.

## 0.17.0 — 2026-10-02

- Added six-worker normal decoding with original four-way attention arithmetic on the exact Pixel/Bonsai4 preset.
- Preserved all target logits, tokens and complete answers; counterbalanced measurements reduced decode about 20% and total time 13–15% in three known synthetic cases. Retained mixed/outlier research results and CPU-time limits.
- Added explicit persisted attention policy/cache identity, single-query prompt-tail and lifecycle checks, final real UI/model/x86 validation and fixtures v10. All prior artifacts and original phone data/APKs remain preserved.

## Post-0.16 attention parity repair — 2026-10-01

- Isolated single-query split-KV versus batched attention reduction and 256-cell padding boundaries on the registered Pixel.
- Added an opt-in owned attention wrapper that preserves original serial arithmetic per query, with layout/mask/scratch guards and synchronized scratch reuse.
- Matched 640 whole-model logit vectors and bounded sampled/EOS/rejection/cancellation/cache controls; preserved original normal-generation and x86 behavior. Restored device data/APKs; product speculation remains disabled pending real end-to-end benefit.

## Post-0.16 speculation research - 2026-10-01

- Added complete target-sample traces and consistent whole-round cost accounting, including un-emitted EOS/cancelled decisions.
- Measured causal batches on the registered Pixel and preserved failed numerical admission: larger contexts change logits and supplied-reference sampled answers.
- Validated cache, cancellation, EOS, cost fallback and normal x86 decoding; restored the frozen 0.16 phone APKs and private data. No speculative product speedup is enabled.

## 0.16.0 — 2026-10-01

- Added guarded dynamic matrix-row scheduling using existing threadpool storage; the measured Pixel/Bonsai4 profile uses 32-row prefill chunks and unchanged static decode.
- Preserved exact numerical/full-output behavior, cache/cancellation recovery and fallback; added persisted chunk identity and actual-path diagnostics.
- Recorded two complete paired campaigns, final mobile/UI/model and x86 checks; preserved previous artifacts and original device data.
- Updated current fixtures to v9 and corrected stale project metadata to the current version and authorized execution scope.

## Post-0.15 research — 2026-10-01

- Added test-only app CPU sampling with matched output controls and build-ID-checked native symbol attribution; the released product APK is unchanged.
- Documented the actual Q2 storage/INT8 arithmetic/F16 cache contract and measured prefill/decode hotspots.
- Rejected half-block prefill scheduling after exact numeric/full-output checks did not establish the required latency gain. Preserved trial source, raw measurements and verified phone restoration.

## 0.15.0 — Measured ARM execution — 2026-10-01

- Adds guarded ARM DotProd Q2 kernels with exact reference lane/FMA ordering, prepared activations, specialized column groups and decode-row reuse.
- Reuses and pauses session-owned CPU workers; validates cache/cancellation/recovery and independent pool resizing. Global power/radio policies and model selection are preserved.
- Adds the exact Pixel10Pro/Bonsai4 preset:4 decode/6 prompt threads,batch128,width8,rows1/4,persistent workers,no affinity. Other keys retain conservative matrix settings.
- Rejects six decode workers after long-output divergence; confirms the adopted path against every sampling-logit hash and output token. Separates research300-second deadlines from product120 seconds/192 tokens.
- Preserves failed probes, isolated private-data restoration, final model/UI/x86 regression and unchanged model/backend pins. Updates fixture identity to v8 without changing questions or transferring old quality scores.
- [Validation](docs/validation-0.15.md) records artifact hashes, measured latency and limits. No universal speedup, battery-life, field-quality or public-distribution claim.

## 0.14.0 — Measured Q2 row reuse — 2026-09-30

- Adds guarded two-row Q2 reuse across multi-column work, with independent single-column policy, reference fallback and unchanged pinned weights/backend.
- Keeps the old decoder after grouped decode underperformed; adopts only a device/model-specific profile confirmed on complete answers, with cache/cancellation/model-switch validation.
- Adds unique numerical/actual-shape/model experiment runs, preserves rejected and deliberately interrupted controls, and extends grouped-width/tail coverage.
- Keys calibration by `q2-row-v3-phase`, persists both row settings and invalidates incompatible cached computation. General chat and OSM functionality retain their protocols.
- [Kernel experiment](docs/kernel-rows-0.14.md) and [validation](docs/validation-0.14.md) state measurements, identities and limits. No phone or ARM runtime claim.

## 0.13.0 — Offline place answers — 2026-09-30

- Adds exact name/alias lookup, recorded-locality category search and computed proximity around named references, with radius units, stable top-five ordering and per-place sources.
- Answers bounded place questions directly in chat without a generator; retains clarification/follow-up, missing-coordinate, conflicting-snapshot, cancellation and scan-limit behavior.
- Adds 63 emulator checks with synthetic edge cases and a frozen prepared public OSM subset; preserves existing import/chat/data regressions. No maps, navigation or location permission added.
- Fixes full-ABI manifest validation, records six fault-injection controls and introduces v6 without changing frozen manifests/runs. Native kernels and general chat prompt unchanged in this feature release.
- [Validation](docs/validation-0.13.md) records exact artifacts and measured scope.

## OSM place-query scope clarification — 2026-09-30

- Clarifies OSM as imported place knowledge for chat: named parks, restaurants around a reference place, museums and other categories.
- Prioritizes entity resolution/category/proximity queries in K-06; defers map/navigation/routing K-07 outside this feature and removes it as a product prerequisite.
- Adds ADR-028, acceptance examples and preparation guidance including leisure/place records. Records the obsolete v5 evaluation blocker for correction in a new manifest, preserving existing runs.
- Documentation/scope only; current APK capabilities and runtime results are unchanged.

## DeepGEMM-Ascend research — 2026-09-30

- Pins and reviews the initial upstream Ascend source against the current Q2 runtime, separating hardware-specific implementation from transferable ideas.
- Records static Bonsai4 tensor shapes and P-05/P-06 experiment gates for shape/phase measurement and output-row activation reuse.
- No app/model/backend/dependency change, benchmark run or new speedup claim. See [review](docs/deepgemm-ascend-review.md).

## Documentation consolidation — 2026-09-30

- Reconciles maintained English documentation with 0.12: chat/Settings, recursive snapshots, PDF/OSM sources, schema 5, packaging and current test-provider lifecycle.
- Separates historical measurements/reviews from current product behavior and marks old operational reports as historical.
- Records the evaluation validator's incomplete ABI comparison as E-06 and clarifies recovery/user-test readiness tasks.
- Documentation only: no app version, source, model, fixture, raw evidence or runtime change; see [audit](docs/documentation-audit-2026-09-30.md).

## 0.12.0 — OpenStreetMap knowledge import — 2026-09-30

- Adds bounded OSM XML and Overpass JSON import through Add file/Add folder, retaining tags, IDs, dates, coordinates and attribution.
- Adds schema 5 feature rows and exact element source locators; stores the original file and preserves prior snapshots on rejected updates.
- Adds a paginated feature/source browser and integrates feature identities into chat retrieval/citations.
- Distinguishes node positions, exported/derived centers and unknown geometry; retains missing tags as unknown and opening hours as recorded data.
- Adds OSM parsing/storage/SAF/UI and optional generation checks; stabilizes sequential fixture tests with ordered grants and hidden inactive roots.
- No new map renderer, routing/GPS, PBF/GeoJSON support, native kernel or network permission. See [validation](docs/validation-0.12.md).

## 0.11.0 — Recursive folder import — 2026-09-30

- Adds Settings/Documents → Add folder using Android's selected-tree picker; recursively imports supported PDF/TXT/Markdown/CSV.
- Shows progress, stop control and persistent summary, with per-item skips/errors; keeps successful files when a sibling fails.
- Preserves relative paths and adds schema 4 source/format/raw-byte identity for unchanged-file deduplication and safe retry. Changed files remain new snapshots.
- Shares the single-file parser/limits with folder ingestion and adds dedicated real-provider tests without shipping fixtures in the user APK.
- No model/prompt/kernel change, source write, automatic sync or physical-device execution.

## 0.10.0 — Chat and personal documents — 2026-09-30

- Opens directly on a persistent local conversation with streaming send/stop and bounded follow-up context.
- Moves document/model setup to Settings; removes prototype tabs, metrics, reviewer/speculation UI and unused demo resources.
- Starts with an empty library and removes only exact unchanged historical seeds on schema-3 upgrade; imports and edited records survive.
- Adds bounded PDF page extraction, exact original copies and original-page viewing alongside TXT/Markdown/CSV.
- Moves synthetic corpora into the test APK; adds current chat/import/migration checks and preserves failed outcomes.
- Packages linked ARM64 and x86_64 libraries; runtime validation remains emulator-only. No phone-performance or production-release claim.
- See [validation](docs/validation-0.10.md) for final run/build identities and remaining limits.

## 0.9.0 — Outpost — 2026-09-30

- Added schema-2 migration preserving imports and typed exact source/revision/hash locators.
- Added bounded UTF-8 CSV records and local JSON knowledge packs with atomic version activation, cancellation, retention and removal; English import/source UI.
- Added executable contextual evaluation v2, immutable runs, source/input snapshots and attributed review validation; preserved failed answers and rejected the longer prompt candidate.
- Clarified runtime selection/speedup/time-reduction reporting and tested policy boundaries; retained existing numerical kernels and width 4 profile on this emulator.
- Hardened Windows argument quoting and build/publication provenance with source fingerprints and build receipts.
- Final build/lint, 61 knowledge checks and 53 functional checks passed; detailed run/build scopes and quality gaps are in [validation](docs/validation-0.9.md).
- Implementation committed as `c51c73f`. Final docs/evidence integration initially paused on an automatic permission-review quota failure; it was resolved before 0.10, with evidence in `449bca6` and line-ending protection in `c4deeb9`. No public release is implied.

## 0.8.1 — Outpost

- The launcher label is resource-backed again (`@string/app_name`); the double-encoded hardcoded literal is gone, and the app drawer shows `Outpost`.
- The Explore screen opens directly on the question surface; the slogan, hero text, and the hardcoded preset questions were removed.
- Grouped prefill can now group over eight activation columns, bit-exact by construction across widths 1, 2, 4 and 8. Calibration keeps a wider grouping only when it beats the narrower one by more than the working threshold; three sweeps put the 4 -> 8 step at 1.5% to 4.6% of total time, so this target selects width 4.
- The seven `test-*.ps1` scripts retrieve evidence portably, fixing the PowerShell 5.1 `InvokeMethodOnNull` failure that lost run results.
- Calibration phases no longer overwrite runtime dimensions they did not measure.
- Context speculation was re-measured on the field missions and stays opt-in, off by default.
- The seven test scripts build their evidence-retrieval arguments through a shared quoting helper, so a value containing a space can no longer split into two arguments. `publish-artifact.ps1` refuses to publish an APK older than any source or build file, and its `-Verify` mode reports a missing checksum sidecar instead of failing with a raw read error.
- The functional suite asserts that the question input opens empty and that none of the removed preset questions reappears, and reports 53 checks.
- Calibration re-confirms the stored matrix width at the threads and batch it just chose, so a saved profile is a combination measured as a whole rather than a merge of separately measured dimensions.
- Validation is recorded in [the 0.8.1 report](docs/validation-0.8.1.md); the artifact is published reproducibly with [publish-artifact.ps1](scripts/publish-artifact.ps1).

## Emulator operating handoff — 2026-09-29

- Added a practical runbook for tool resolution, correct AVD identity, startup/readiness, offline state, matching APKs, installed models, evidence, test flag semantics and recovery.
- Recorded optional Python executable configuration for hosts without Python on PATH, and corrected the distinction between Bonsai and runtime/speculation `-SkipInstall` behavior.
- Read-only live checks only; no application behavior change, emulator reset, model execution or new performance claim.

## Agent handoff consolidation — 2026-09-29

- Integrated the corrected question-family direction and anonymized discovery notes into the repository documentation.
- Added AGENTS.md and a self-contained engineering handoff covering runtime constraints, implementation state, evidence limits, known failures, open decisions and bounded next tasks.
- Documentation only; application version, model/runtime behavior and validation baseline remain 0.8.0.

## Product scope clarification — 2026-09-29

- Clarified that Vitalik's example concerns contextual question types, not a diet/restaurant specialization or mandatory standalone benchmark.
- Proposed question-family evaluation across varied world, regional and personal evidence; action-oriented ideas remain complementary proposals.

## Product discovery notes — 2026-09-29

- Recorded anonymized hypotheses from owner-supplied group feedback: personal document recall, contextual visits, reflection, deferred actions, and field equipment diagnosis.
- Proposed an issue-list recall demo, a simulated read-only device workflow, and follow-up discovery tasks; clarified provenance and action boundaries.
- Documentation only: no application/model/permission change, no outbound message, and no hardware connection.

## 0.8.0 — Outpost — 2026-09-29

- Selected the Outpost identity and created an independent local repository under `E:\projects\outpost`.
- Renamed Android/Java/JNI/native identities, translated the UI into resources, and established English prompts, seed content, and primary fixtures.
- Replaced sibling toolchain assumptions with explicit configuration and created a separate emulator; retained the original installation and evidence.
- Fresh validation and quality observations are recorded in [the migration report](docs/validation-0.8.md). No historical speedup is reclassified as a new English measurement.

## Documentation consolidation — 2026-09-29

- Added maintained English product, status, architecture, knowledge-contract, runtime, optimization, speculation, decision, backlog, evaluation, development, data, migration, and handoff documentation.
- Replaced the root README and dependency overview with English versions; preserved the original Spanish README and experimental reports.
- Documented name selection and migration to `E:\projects` as pending, and kept application code, prompts, model files, and runtime behavior unchanged.

## 0.7.0

- Added optional same-request context speculation for Bonsai 4B, with target verification, KV rollback, cancellation handling, and adaptive cost fallback.
- Audited locked weights for MTP support and measured an optimistic auxiliary-drafter cost bound; no MTP or dual-model backend integrated.
- Preserved numerical/wording differences and field-content failures; left speculation off by default.
- [Validation](docs/validation-0.7.md), [current English analysis](docs/speculation.md).

## 0.6

- Added grouped Q2 g64 prefill, device/build/model-specific runtime profiles, and compatible prompt caching.
- Replaced an initial cache strategy after UI logits/text mismatch; recorded retained-memory cost.
- [Validation](docs/validation-0.6.md).

## 0.5.1

- Separated CPU/OS capability detection from compiled/enabled kernel selection, with synthetic dispatch and ARM/x86 object compilation checks.
- Custom VNNI/ARM implementations remained pending. [Runtime design](docs/device-runtime.md).

## 0.5

- Added the guarded AVX2/F16C ternary dot kernel and reference comparisons.
- Refined compact source prompting and Bonsai sampling; achieved completion of earlier timing-out 4B controls in the emulator.
- [Validation](docs/validation-0.5.md).

## 0.4

- Integrated locked Ternary Bonsai 1.7B and 4B profiles alongside Qwen, with separate verified model files and selector UI.
- Recorded slow/unusable baseline behavior before kernel optimization. [Validation](docs/validation-0.4.md).

## 0.3

- Added optional local Kev first-sentence review and preserved false-positive findings. [Validation](docs/validation-0.3.md).

## 0.2

- Integrated local llama.cpp generation, streaming, cancellation, and locked Qwen model import. [Validation](docs/validation-0.2.md).

## 0.1

- Established an offline Android text library with lexical retrieval, source reading, and emulator checks. [Validation](docs/validation-0.1.md).
