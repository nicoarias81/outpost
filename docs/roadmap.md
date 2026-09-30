# Roadmap and actionable backlog

Baseline: Outpost 0.14.0; [current validation](validation-0.14.md). Chat/Settings, seed removal, bounded PDF extraction and ARM64 packaging are implemented; quality and physical-device acceptance remain separate. R-01 through R-04 are implemented and validated in the [0.8.1 report](validation-0.8.1.md). Field-answer quality remains separate open work. All other tasks below are **open** unless explicitly labeled otherwise. Priorities express sequence and dependencies, not dates or committed effort estimates. Owner is unassigned. The [current-state inventory](current-state.md) lists already implemented features.

## Milestones

| Milestone | Outcome | Exit condition / current status |
|---|---|---|
| M0 — Independent English project | Named, reproducible local repository under `E:\projects` | Implemented local migration and English baseline; reproduce with documented external prerequisites |
| M1 — Evidence and reliable task baseline | Versioned evidence plus a task-oriented evaluation harness | Partial: migration and executable/reviewed controls exist; reliable task outcomes and broader provenance coverage remain |
| M2 — Useful contextual knowledge workflows | Several question families across world, regional and personal evidence | Partial: chat and source inspection exist; reviewed useful outcomes across varied domains and missing/ambiguous data remain |
| M3 — Broader knowledge | Encyclopedia and geographic package prototypes | Partial: bounded OSM feature import exists; ZIM and real-region storage/latency/quality remain open |
| M4 — Portable runtime | Complete ARM build and defensible dispatch/fallback | Build complete; ARM runtime/fallback evidence pending. Physical-device execution requires scope expansion |

Performance work can continue alongside M1/M2, but no speed result substitutes for their quality gates.

## P0 — Project foundation and quality baseline

E-01/W-01 definitions and an executable evidence-only runner exist; the current manifest is v7 (schema 2, app 0.14). V1 baseline/candidate and v2 baseline/diagnostic runs have attributed assistant reviews; see [0.9 validation](validation-0.9.md). V7 has 12 runnable and 3 blocked fixtures. Product chat uses a different prompt; no new broad v7/chat quality score is claimed. A validator PASS establishes internal consistency; reviewed development outcomes are not general task accuracy.

| ID | Task | Depends on | Acceptance criteria |
|---|---|---|---|
| R-01 — done | Select English product name, repo slug, namespace plan | Outpost selected | Outpost selected; identity recorded in `project.json` |
| R-02 — done | Copy source into standalone local repo | R-01 | Inventory and hashes verified; own `.git`; no unrelated workspace files, model weights, AVD, or private imports staged; original remains recoverable |
| R-03 — done | Remove sibling toolchain assumptions | R-02 | SDK/JDK/cache can be configured explicitly; missing prerequisites have actionable errors; build from new location |
| R-04 — done | Establish English application baseline | R-01 | Resource-backed UI strings, English prompts and demo content, locale-aware formatting, reviewed screenshots and mission results; no stale JNI/test/appId references |
| R-05 | Choose code license and distribution policy | Before public distribution | Root license and contribution/release terms chosen; dependency notices retained; no accidental relicensing of upstream assets |
| E-01 — done | Create a versioned mission fixture manifest and rubric — recorded in [current fixtures v7](../eval/fixtures-v7.json) and [rubric v2](../eval/rubric-v2.md), with the original v1 definitions preserved, checked by `eval/validate.py` | None | Five contexts, expected evidence, successful outcome, critical failures, package/model/prompt identities; executable checks distinct from reviewed quality (runner and development review are implemented; broader end-to-end acceptance remains E-02, E-04 and E-05) |
| E-02 — partial | Reproduce traveler and mountaineer failures under controlled prompts | E-01 | Controlled prompt/budget diagnostics executed; longer candidate rejected and source/citation/date failures recorded. Continue causal isolation and resolve quality failures without weakening the rubric |
| E-03 — partial | Preserve run identity and evidence snapshots | None | Current chat/folder/OSM/knowledge/evaluation runs have unique directories and identity checks; evaluation captures frozen inputs/source snapshots. Extend this convention to legacy fixed-path scripts and retain failed setup/execution outputs |
| E-08 — researched, proposed | Add bounded local lifecycle traces to emulator evaluations | E-03 | Correlate actual route/retrieval/prompt/native/persistence/UI events across queues; distinguish outcomes from execution status. Validate cancellation, missing ends, event drops and trace-on/off overhead; payload-minimal diagnostics, no automatic upload. [NeMo Relay review](nemo-relay-review.md) supplies the design; SDK integration and ATOF conformance remain unproven |

## P1 — Evidence contracts and first complete workflow

| ID | Task | Depends on | Acceptance criteria |
|---|---|---|---|
| K-01 — done for passage/row/page/element identity | Implement typed evidence and source locator contract | E-01 | Text/CSV/PDF/OSM results map to exact locators; source resolution and hash semantics are documented/tested in 0.12 |
| K-02 — partial | Implement versioned package schema and lifecycle | K-01 | Implemented bounded JSON text/CSV packs, atomic activation, corruption/cancel rollback, retained locators and removal. Coverage/storage display, resumable import/download and broader lifecycle guarantees remain pending |
| K-03 — done for schema 1 → 5 | Add database migrations | Before first schema change | Transactional upgrades preserve imports/IDs; exact legacy seed cleanup, origin bindings and OSM migration tested; future schema changes require new fixtures |
| K-04 — partial | Build page-aware document adapter | K-01, K-02, K-03 | Bounded PDF text extraction and original page viewing implemented/tested; scanned-only/encrypted inputs rejected. Expand to complex layouts/tables and qualified manual tasks; no OCR claim |
| T-01 | Add typed arithmetic and unit conversion | K-01, E-02 | Exact elevation difference and unit fixtures pass; inputs/units/results inspectable; no generated arithmetic result masquerades as a tool result |
| U-01 | Implement explicit, editable mission context | K-01 | Changing equipment/revision/location invalidates inapplicable evidence; source facts and user observations remain distinct |
| U-02 — partial | Show useful source information before generation | K-01 | Source reader works without a model; chat retrieves in one send. No separate search UI remains. Add time-to-useful-source measurements and test busy/no-model task usefulness |
| E-04 | Complete one document-based field mission | K-04, T-01, U-01, U-02 | Full offline preparation-to-source workflow; positive, missing-data, wrong-revision, and cancellation cases; reviewed outcome |

Next page-aware slice: a fictitious equipment manual with an exact model/revision, a numeric observation, and a source page to inspect. This exercises common infrastructure without waiting for a map renderer. Choosing the specific mission is an implementation decision, not a promise that the current prototype provides field guidance.

## Contextual-question evaluation — primary requirement

The user clarified that [Vitalik's example](offline-world-knowledge.md) identifies a question type, not a diet/travel specialization or fixed mandatory test. Evaluate contextual retrieval, recommendations, comparisons, synthesis and bounded troubleshooting across different subjects and evidence conditions.

| ID | Task | Dependencies / acceptance |
|---|---|---|
| W-01 — done | Define a varied question-family benchmark — declared in [current fixtures v7](../eval/fixtures-v7.json) and judged per [rubric v2](../eval/rubric-v2.md), with historical runs retained | E-01; varied domains, contextual constraints, held-out questions/entities, public/personal/mixed evidence, missing/ambiguous/conflicting data and inspectable locators (definition, execution and attributed development scores exist; failed outcomes and broader quality work remain E-02, E-04 and E-05) |
| W-02 | Demonstrate contextual retrieval and recommendations | K-01 and source-specific adapters; K-02 only when a versioned pack is used. OSM place questions use K-06 without K-07; preserve applicable entities/constraints, explain ranking criteria, show useful results before long generation, and avoid unsupported current-state claims |
| W-03 | Demonstrate comparison, synthesis and supported explanations | K-01, W-01 and suitable reference material; combine relevant sources, preserve specifications/units, distinguish evidence from inference, and clarify material gaps |

The restaurant and issue-list questions are illustrative fixtures, not product-defining gates. Evaluate transfer across topics; neither a prepared POI list nor a successful file lookup establishes the broader capability.

## P2 — Knowledge breadth and runtime portability

| ID | Task | Depends on | Acceptance criteria |
|---|---|---|---|
| K-05 | ZIM reader/search spike | K-02 | Read an explicitly licensed bounded archive; preserve entry locators; measure binary/index/storage cost; record archives without search indexes |
| K-06 — bounded slice delivered; broader coverage partial | Answer offline place questions from imported OSM | K-01, K-03 | Implemented exact names/aliases, recorded-locality categories, deterministic proximity/radii/order, source-backed no-model chat and clarification/conflict handling. 63 checks include synthetic edge cases and a frozen public OSM subset. Broader language/constraints, geographic applicability and persistent indexes remain pending. No map/routing/GPS dependency; [scope](osm-place-queries.md) |
| K-07 — deferred, outside current scope | Map display and routing feasibility | Separate future user request | Retained as a historical candidate. Not a prerequisite for K-06/W-02 or named/category/proximity place answers. Reopen only if the user requests a map/navigation capability; no map SDK or route graph is required by the current OSM feature |
| K-08 | Investigate OCR | K-04, mission need | Page image remains inspectable; uncertain numbers/units flagged; extraction evaluation and size/cost recorded |
| P-01 — build complete; runtime pending | Build complete ARM64 app/backend | R-03 | JNI/llama ARM64 link and APK packaging now complete. ARM emulator/phone startup, model execution and fallback remain unverified; physical-device execution still requires expanded scope |
| P-02 | Implement ARM Q2 kernels | P-01 | Reference equivalence/guard/tail tests, supported feature dispatch, whole-model timing and memory; no unsupported-instruction execution |
| P-03 | Prototype VNNI candidates separately | Existing dispatch, compatible emulator | Signedness/overflow/packing tests; CPU/OS gates; paired dot, prefill and generation measurements; retain fallback |
| P-04 | Measure peak memory and pressure recovery | E-03 | Defined sampling cadence, peak vs post-run distinction, cancellation/model-switch/process-recovery behavior; no battery inference from PSS |
| P-05 — partial, measured in 0.14 | Profile operation shapes and establish current cache/phase baselines | E-03 | [DeepGEMM-Ascend review](deepgemm-ascend-review.md), DG-01/DG-03: Implemented opt-in shape/path counts, actual-shape cache/pressure controls and paired generation timing. Detailed quantization/barrier attribution, profiler-overhead study, peak memory and wider devices remain open; [results](kernel-rows-0.14.md) |
| P-06 — bounded prefill slice adopted in 0.14 | Reuse Q8 activations/correction across output rows | P-05 | Two-row reuse adopted for eligible multi-column matrices in the measured Bonsai 4B emulator profile, with unchanged weights/scratch contract and exact numerical/output checks. Single-column two/four-row alternatives remain research-only after regressions; finer shape selection and ARM require new evidence; [record](kernel-rows-0.14.md) |
| E-05 — partial | Compare generators and optional reviewer on missions | E-01, E-04, E-07 for new profiles | Four problem-case Qwen diagnostics and Bonsai baselines recorded; complete representative paired generator/reviewer comparison with fixed evidence/budgets and qualified review; Kev not ground truth. [Model study](model-alternatives.md) defines the new comparison; no new candidate runs yet |
| E-07 — researched, admission pending | Admit alternative models to a research-only comparison | Pinned backend, E-03 | Pin/verify artifact and conversion provenance, template/stops/no-thinking/sampler; validate load, cancellation, prefix/recurrent-state behavior and model switching on Outpost35. Start LFM2.5-1.2B QAD and Qwen3.5-2B; preserve production allowlist and model-specific profiles. [Candidate evidence](../evidence/research/model-survey-20260930/candidates.json) is metadata-only, not runtime support |

## P3 — Conditional research

| ID | Task | Trigger / exit evidence |
|---|---|---|
| X-01 — candidate weights identified, integration pending | Native MTP or cheaper trained drafter | [Survey](model-alternatives.md) pins LFM DSpark and Gemma E2B assistants for their own targets; none is a Bonsai head. Baseline target first; validate state/quantized-target compatibility and acceptance, then full-answer cost and memory. No Android gain established |
| X-02 | GPU/NPU backend | Specific supported backend and mission workload justify integration; account for transfers and fallbacks |
| X-03 | Semantic retrieval | Lexical/entity retrieval misses documented mission evidence; evaluate encoder and index cost separately from generator |
| X-04 | Voice and vision | A mission requires them; local models and consent/permissions designed; text workflow still works |
| X-05 | Field notebook and controlled export | User workflow requires persistent observations; explicit save/export behavior, provenance, deletion, and private-data review |
| X-06 | Physical Pixel and GrapheneOS validation | User explicitly lifts emulator-only execution restriction; define device-specific latency, peak RAM, thermal and battery protocol first |
| X-07 — researched, conditional | Trained Engram-style conditional memory | Suitable licensed small trained checkpoint and emulator implementation, or separately scoped training research. Control storage/compute/quality; cold/warm lookup and full-answer memory/latency. Official demo is not ready weights; no host/GPU training implied. [Review](engram-review.md) |

## Discovery candidates — not yet scheduled for implementation

These tasks follow the [2026-09-29 group feedback](discovery-2026-09-29.md). They are proposed experiments, not authorization to connect to equipment/accounts. D-02 is one personal-recall experiment within W-01 through W-03; D-03 through D-05 require further discovery and must not displace the broader knowledge objective.

| ID | Candidate | Dependencies / acceptance |
|---|---|---|
| D-01 | Validate recurring jobs and existing workarounds | Recent concrete incidents, frequency/cost, preparation behavior, relevant file/device types, phone-versus-laptop comparison; anonymized artifacts |
| D-02 — partial | Find the downloaded issue list | Text/CSV version and row identity implemented; real-budget diagnostic preserves row/value/date facts but cites the wrong source. Complete ambiguity/missing-file/relative-date retrieval and source support; the complete PDF recall mission and DOCX/XLSX adapters remain separate |
| D-03 | Manual-assisted equipment diagnosis | K-01, U-01; emulator fake transport only; exact device/firmware/manual revision; read-only observations; known-procedure registry; timeout/disconnect/wrong-device cases; no physical writes |
| D-04 | Draft and retry a reconnect action | Define the actual job first; simulated destination/network; exact payload/destination, expiry, scoped authorization, cancel/restart persistence, duplicate prevention and unknown-result reconciliation; no real sending or permission change |
| D-05 | Reflection and brainstorming interaction | Define optional retention and mode boundary separately from source answers; user correction, multi-turn continuity, no fabricated personal history; no clinical positioning or efficacy claim |

D-02 and the earlier manual example share evidence identity, provenance, import and retrieval foundations. The broader contextual-question evaluation remains required across varied topics and evidence sources. Do not implement every file format or all five discovery candidates to demonstrate initial utility.

## Definition of done

A task closes when its acceptance criteria have evidence, relevant documentation/decisions are updated, failure modes are covered, and limitations are explicit. Do not close a task because a harness printed PASS if the required product outcome failed. A runtime optimization also needs baseline comparison and an assessed quality/resource tradeoff. A docs-only task needs link, source-consistency, and language checks; it does not require rerunning multi-GB model tests.


## Delivered preparation slices and immediate follow-up

Recursive selected-folder import and bounded OSM XML/Overpass JSON are implemented, including path provenance, per-item results, stop/retry, unchanged-file deduplication and exact feature sources. Changed snapshots remain searchable alongside old ones; no freshest-version or cross-extract entity policy is implemented. This advances document preparation; it does not close K-02's broader package lifecycle/coverage/storage requirements, add Office/OCR support, or implement continuous sync. Full restart/resume scheduling and PDF orphan-file recovery remain pending.


| ID | Task / current status | Dependencies / acceptance |
|---|---|---|
| E-06 — done in 0.13 | Correct complete ABI identity validation | Full literal ABI-set comparison; six valid/reordered/missing/extra/duplicate/malformed controls pass. V6 declares both ABIs and removes the obsolete K-07 regional blocker while preserving old manifests/runs. |
| U-03 — partial | Prepare chat for representative mobile user tests | Chat/Settings/ingestion exist. Validate provisioning instructions, follow-up retrieval, source/conflict handling, interruption and no-model reading with realistic tasks; expose useful limitations. Signing/license/distribution depend on R-05; physical trials depend on X-06. |
| K-09 — open | Harden file recovery and snapshot management | K-03/K-04/K-06; test process death during copy/index, storage exhaustion, orphan cleanup and coherent retry without deleting valid imports; define user-visible old/new snapshot management before automatic freshness selection. |

Recommended sequence: improve U-03/E-02 using observed source/conversation failures, harden K-09, then expand real-region K-06/W-02 and document E-04/W-03 workflows. Choose runtime experiments from measured bottlenecks. Do not close blocked PDF/OSM evaluation fixtures merely because their low-level importers exist. None of this sequence authorizes phone execution or public release.
