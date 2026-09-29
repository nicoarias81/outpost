# Roadmap and actionable backlog

Baseline: Outpost 0.8.0. R-01 through R-04 are implemented and validated in the [0.8 report](validation-0.8.md). Field-answer quality remains separate open work. All other tasks below are **open** unless explicitly labeled otherwise. Priorities express sequence and dependencies, not dates or committed effort estimates. Owner is unassigned. The [current-state inventory](current-state.md) lists already implemented features.

## Milestones

| Milestone | Outcome | Exit condition |
|---|---|---|
| M0 — Independent English project | Named, reproducible local repository under `E:\projects` | Clean source checkout builds using documented external prerequisites; English app baseline and migration evidence |
| M1 — Evidence and reliable task baseline | Versioned evidence plus a task-oriented evaluation harness | Existing imports survive migration; all five mission fixtures have explicit outcomes, missing-data cases, and source locators |
| M2 — First useful field slice | One complete document-based workflow | Prepare, search, calculate if needed, answer, inspect original, recover after cancellation, all offline |
| M3 — Broader knowledge | Encyclopedia and geographic package prototypes | Independent adapters demonstrate stated capabilities and storage/latency costs |
| M4 — Portable runtime | Complete ARM build and defensible dispatch/fallback | ARM emulator execution evidence where available; physical-device validation remains outside current scope |

Performance work can continue alongside M1/M2, but no speed result substitutes for their quality gates.

## P0 — Project foundation and quality baseline

| ID | Task | Depends on | Acceptance criteria |
|---|---|---|---|
| R-01 — done | Select English product name, repo slug, namespace plan | Outpost selected | Outpost selected; identity recorded in `project.json` |
| R-02 — done | Copy source into standalone local repo | R-01 | Inventory and hashes verified; own `.git`; no unrelated workspace files, model weights, AVD, or private imports staged; original remains recoverable |
| R-03 — done | Remove sibling toolchain assumptions | R-02 | SDK/JDK/cache can be configured explicitly; missing prerequisites have actionable errors; build from new location |
| R-04 — done | Establish English application baseline | R-01 | Resource-backed UI strings, English prompts and demo content, locale-aware formatting, reviewed screenshots and mission results; no stale JNI/test/appId references |
| R-05 | Choose code license and distribution policy | Before public distribution | Root license and contribution/release terms chosen; dependency notices retained; no accidental relicensing of upstream assets |
| E-01 | Create a versioned mission fixture manifest and rubric | None | Five contexts, expected evidence, successful outcome, critical failures, package/model/prompt identities; executable checks distinct from reviewed quality |
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

## Definition of done

A task closes when its acceptance criteria have evidence, relevant documentation/decisions are updated, failure modes are covered, and limitations are explicit. Do not close a task because a harness printed PASS if the required product outcome failed. A runtime optimization also needs baseline comparison and an assessed quality/resource tradeoff. A docs-only task needs link, source-consistency, and language checks; it does not require rerunning multi-GB model tests.
