# Roadmap and actionable backlog

Baseline: Outpost 0.8.0. R-01 through R-04 are implemented and validated in the [0.8 report](validation-0.8.md). Field-answer quality remains separate open work. All other tasks below are **open** unless explicitly labeled otherwise. Priorities express sequence and dependencies, not dates or committed effort estimates. Owner is unassigned. The [current-state inventory](current-state.md) lists already implemented features.

## Milestones

| Milestone | Outcome | Exit condition |
|---|---|---|
| M0 — Independent English project | Named, reproducible local repository under `E:\projects` | Clean source checkout builds using documented external prerequisites; English app baseline and migration evidence |
| M1 — Evidence and reliable task baseline | Versioned evidence plus a task-oriented evaluation harness | Existing imports survive migration; all five mission fixtures have explicit outcomes, missing-data cases, and source locators |
| M2 — Useful contextual knowledge workflows | Several question families across world, regional and personal evidence | Correct interpretation, useful answers, inspectable sources, and missing/ambiguous-data handling across varied subject matter; implementation may be sequential |
| M3 — Broader knowledge | Encyclopedia and geographic package prototypes | Independent adapters demonstrate stated capabilities and storage/latency costs |
| M4 — Portable runtime | Complete ARM build and defensible dispatch/fallback | ARM emulator execution evidence where available; physical-device validation remains outside current scope |

Performance work can continue alongside M1/M2, but no speed result substitutes for their quality gates.

## P0 — Project foundation and quality baseline

E-01 and W-01 define the benchmark; defining it is not measuring it: no fixture in `eval/fixtures-v1.json` has been executed as a scored run, and a green `eval/validate.py` proves internal consistency only.

| ID | Task | Depends on | Acceptance criteria |
|---|---|---|---|
| R-01 — done | Select English product name, repo slug, namespace plan | Outpost selected | Outpost selected; identity recorded in `project.json` |
| R-02 — done | Copy source into standalone local repo | R-01 | Inventory and hashes verified; own `.git`; no unrelated workspace files, model weights, AVD, or private imports staged; original remains recoverable |
| R-03 — done | Remove sibling toolchain assumptions | R-02 | SDK/JDK/cache can be configured explicitly; missing prerequisites have actionable errors; build from new location |
| R-04 — done | Establish English application baseline | R-01 | Resource-backed UI strings, English prompts and demo content, locale-aware formatting, reviewed screenshots and mission results; no stale JNI/test/appId references |
| R-05 | Choose code license and distribution policy | Before public distribution | Root license and contribution/release terms chosen; dependency notices retained; no accidental relicensing of upstream assets |
| E-01 — done | Create a versioned mission fixture manifest and rubric — recorded in [eval/fixtures-v1.json](../eval/fixtures-v1.json) and [eval/rubric-v1.md](../eval/rubric-v1.md), checked by `eval/validate.py` | None | Five contexts, expected evidence, successful outcome, critical failures, package/model/prompt identities; executable checks distinct from reviewed quality (consuming the manifest in the harness and actually measuring it remains E-02, E-04 and E-05) |
| E-02 | Reproduce traveler and mountaineer failures under controlled prompts | E-01 | Freeze source/query/template/sampler; compare app and fixture prompts; record whether error comes from retrieval, truncation, interpretation, or calculation |
| E-03 | Preserve run identity and evidence snapshots | None | New runs do not overwrite the only record of a release; JSON links include app/build/config identity and failure results |

## P1 — Evidence contracts and first complete workflow

| ID | Task | Depends on | Acceptance criteria |
|---|---|---|---|
| K-01 | Implement typed evidence and source locator contract | E-01 | Existing text results map to stable evidence IDs; UI citation resolves to the exact local source/version |
| K-02 | Implement versioned package schema and lifecycle | K-01 | Stage/validate/index/activate flow; corrupt update preserves old package; canceled install is recoverable; sizes and coverage displayed |
| K-03 | Add database migrations | Before first schema change | Existing user imports and IDs survive; failed upgrade does not silently erase data; fixture covers previous schema |
| K-04 | Build page-aware document adapter | K-01, K-02, K-03 | One text PDF/manual fixture opens cited pages offline; extraction mistakes and unsupported scans are visible; no OCR claim unless implemented |
| T-01 | Add typed arithmetic and unit conversion | K-01, E-02 | Exact elevation difference and unit fixtures pass; inputs/units/results inspectable; no generated arithmetic result masquerades as a tool result |
| U-01 | Implement explicit, editable mission context | K-01 | Changing equipment/revision/location invalidates inapplicable evidence; source facts and user observations remain distinct |
| U-02 | Show useful source information before generation | K-01 | Search/reader usable with missing model or busy inference; timestamped time-to-source measure available |
| E-04 | Complete one document-based field mission | K-04, T-01, U-01, U-02 | Full offline preparation-to-source workflow; positive, missing-data, wrong-revision, and cancellation cases; reviewed outcome |

Suggested first slice: a fictitious equipment manual with an exact model/revision, a numeric observation, and a source page to inspect. This exercises common infrastructure without waiting for a map renderer. Choosing the specific mission is an implementation decision, not a promise that the current prototype provides field guidance.

## Contextual-question evaluation — primary requirement

The user clarified that [Vitalik's example](offline-world-knowledge.md) identifies a question type, not a diet/travel specialization or fixed mandatory test. Evaluate contextual retrieval, recommendations, comparisons, synthesis and bounded troubleshooting across different subjects and evidence conditions.

| ID | Task | Dependencies / acceptance |
|---|---|---|
| W-01 — done | Define a varied question-family benchmark — declared in [eval/fixtures-v1.json](../eval/fixtures-v1.json) and judged per [eval/rubric-v1.md](../eval/rubric-v1.md) | E-01; varied domains, contextual constraints, held-out questions/entities, public/personal/mixed evidence, missing/ambiguous/conflicting data and inspectable locators (the definition is done; executing and scoring it remains E-02, E-04 and E-05) |
| W-02 | Demonstrate contextual retrieval and recommendations | K-01, K-02 and source-specific adapters as needed; preserve applicable entities/constraints, explain ranking criteria, show useful results before long generation, and avoid unsupported current-state claims |
| W-03 | Demonstrate comparison, synthesis and supported explanations | K-01, W-01 and suitable reference material; combine relevant sources, preserve specifications/units, distinguish evidence from inference, and clarify material gaps |

The restaurant and issue-list questions are illustrative fixtures, not product-defining gates. Evaluate transfer across topics; neither a prepared POI list nor a successful file lookup establishes the broader capability.

## P2 — Knowledge breadth and runtime portability

| ID | Task | Depends on | Acceptance criteria |
|---|---|---|---|
| K-05 | ZIM reader/search spike | K-02 | Read an explicitly licensed bounded archive; preserve entry locators; measure binary/index/storage cost; record archives without search indexes |
| K-06 | Regional OSM entity lookup spike | K-02 | Query saved POIs and geometry with attribution, coverage and content dates; distinguish missing tags from negative facts |
| K-07 | Map display and routing feasibility | K-06 | Select formats/libraries after measured spike; display and route capability flags separate; no route beyond installed graph coverage |
| K-08 | Investigate OCR | K-04, mission need | Page image remains inspectable; uncertain numbers/units flagged; extraction evaluation and size/cost recorded |
| P-01 | Build complete ARM64 app/backend | R-03 | JNI/llama link and packaging complete; emulator startup/fallback evidence where an ARM execution environment is available; object compilation alone insufficient |
| P-02 | Implement ARM Q2 kernels | P-01 | Reference equivalence/guard/tail tests, supported feature dispatch, whole-model timing and memory; no unsupported-instruction execution |
| P-03 | Prototype VNNI candidates separately | Existing dispatch, compatible emulator | Signedness/overflow/packing tests; CPU/OS gates; paired dot, prefill and generation measurements; retain fallback |
| P-04 | Measure peak memory and pressure recovery | E-03 | Defined sampling cadence, peak vs post-run distinction, cancellation/model-switch/process-recovery behavior; no battery inference from PSS |
| E-05 | Compare generators and optional reviewer on missions | E-01, E-04 | Fixed evidence and budgets, reviewed answers, resource costs, truncation/abstention/citation errors; Kev not ground truth |

## P3 — Conditional research

| ID | Task | Trigger / exit evidence |
|---|---|---|
| X-01 | Native MTP or cheaper trained drafter | Exact compatible weights become available; measured cost/acceptance and memory beat baseline |
| X-02 | GPU/NPU backend | Specific supported backend and mission workload justify integration; account for transfers and fallbacks |
| X-03 | Semantic retrieval | Lexical/entity retrieval misses documented mission evidence; evaluate encoder and index cost separately from generator |
| X-04 | Voice and vision | A mission requires them; local models and consent/permissions designed; text workflow still works |
| X-05 | Field notebook and controlled export | User workflow requires persistent observations; explicit save/export behavior, provenance, deletion, and private-data review |
| X-06 | Physical Pixel and GrapheneOS validation | User explicitly lifts emulator-only execution restriction; define device-specific latency, peak RAM, thermal and battery protocol first |

## Discovery candidates — not yet scheduled for implementation

These tasks follow the [2026-09-29 group feedback](discovery-2026-09-29.md). They are proposed experiments, not authorization to connect to equipment/accounts. D-02 is one personal-recall experiment within W-01 through W-03; D-03 through D-05 require further discovery and must not displace the broader knowledge objective.

| ID | Candidate | Dependencies / acceptance |
|---|---|---|
| D-01 | Validate recurring jobs and existing workarounds | Recent concrete incidents, frequency/cost, preparation behavior, relevant file/device types, phone-versus-laptop comparison; anonymized artifacts |
| D-02 | Find the downloaded issue list | E-01, K-01; synthetic versioned files, row-aware retrieval, distinct date meanings, ambiguity/missing-file cases; measure correct source and exact rows before prose; PDF/DOCX/XLSX adapters staged separately |
| D-03 | Manual-assisted equipment diagnosis | K-01, U-01; emulator fake transport only; exact device/firmware/manual revision; read-only observations; known-procedure registry; timeout/disconnect/wrong-device cases; no physical writes |
| D-04 | Draft and retry a reconnect action | Define the actual job first; simulated destination/network; exact payload/destination, expiry, scoped authorization, cancel/restart persistence, duplicate prevention and unknown-result reconciliation; no real sending or permission change |
| D-05 | Reflection and brainstorming interaction | Define optional retention and mode boundary separately from source answers; user correction, multi-turn continuity, no fabricated personal history; no clinical positioning or efficacy claim |

D-02 and the earlier manual example share evidence identity, provenance, import and retrieval foundations. The broader contextual-question evaluation remains required across varied topics and evidence sources. Do not implement every file format or all five discovery candidates to demonstrate initial utility.

## Definition of done

A task closes when its acceptance criteria have evidence, relevant documentation/decisions are updated, failure modes are covered, and limitations are explicit. Do not close a task because a harness printed PASS if the required product outcome failed. A runtime optimization also needs baseline comparison and an assessed quality/resource tradeoff. A docs-only task needs link, source-consistency, and language checks; it does not require rerunning multi-GB model tests.
