# Changelog

This history is reconstructed from saved validation reports. Version dates are omitted where no release timestamp was established. Historical artifacts retain the Brújula name. No public release or repository publication is implied.

## 0.11.0 — Recursive folder import — 2026-09-30

- Adds Settings/Documents → Add folder using Android's selected-tree picker; recursively imports supported PDF/TXT/Markdown/CSV.
- Shows progress, stop control and persistent summary, with per-item skips/errors; keeps successful files when a sibling fails.
- Preserves relative paths and adds schema4 source/format/raw-byte identity for unchanged-file deduplication and safe retry. Changed files remain new snapshots.
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
- Clarified runtime selection/speedup/time-reduction reporting and tested policy boundaries; retained existing numerical kernels and width4 profile on this emulator.
- Hardened Windows argument quoting and build/publication provenance with source fingerprints and build receipts.
- Final build/lint, 61 knowledge checks and 53 functional checks passed; detailed run/build scopes and quality gaps are in [validation](docs/validation-0.9.md).
- Implementation committed as `c51c73f`. Final docs/evidence commits and canonical local artifact publication remain pending an automatic permission-review service quota failure. A verified delivery copy and review patch are recorded in the handoff; no public release implied.

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
