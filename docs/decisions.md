# Architecture decision register

Originated on 2026-09-29; status reconciled with Outpost 0.12.0 on 2026-09-30. These entries distinguish existing choices from proposals; they do not backdate formal approvals. Outpost is the selected name; public distribution decisions remain open.

## ADR-001 — Separate inference from knowledge

**Status: adopted direction, partially implemented.** A generator can change without reimporting documents or maps. Knowledge retains provenance and uses source-appropriate indexes. The application coordinates confirmed context and bounded evidence. A single model-specific knowledge store would couple unrelated changes. Cost: explicit adapters/contracts and migrations require ongoing maintenance. Text/CSV/PDF/OSM adapters, exact locators and schema 1→5 migrations now implement this boundary. Formal separate modules, broader adapters and tools remain pending. Revisit interfaces using observed cross-adapter needs.

## ADR-002 — Execute locally in the Android process

**Status: implemented.** JNI calls pinned llama.cpp; there is no host inference server or runtime cloud fallback. The manifest requests no network permission and has no Google Play Services dependency. Cost: native packaging, RAM, and lifecycle management are application responsibilities. Package preparation may use host connectivity before field use. Any future downloader is a separate reviewed product change.

## ADR-003 — Keep execution inside the emulator

**Status: active user constraint.** AOSP x86_64 is the current execution target. ARM compilation is permitted preparation, not evidence of phone support. Pixel ownership does not override the user's instruction to stay in the emulator. Revisit only when the user explicitly expands device execution scope.

## ADR-004 — Pin models, toolchains, and backend

**Status: implemented.** Fixed revisions, sizes, and checksums make failures attributable and avoid silently loading incompatible layouts. Model imports accept locked files only. Cost: updates require deliberate compatibility work. Revisit a generic model catalog only after metadata/architecture/template validation is implemented.

## ADR-005 — Keep llama.cpp unmodified and wrap selected operations

**Status: implemented.** Own dot and matrix functions use linker wrappers; vendor code stays unchanged. This keeps the reference available and the patch surface visible. It still depends on internal symbols, formats, and synchronization contracts, so backend upgrades require revalidation. This is not a stable upstream plugin ABI.

## ADR-006 — Separate hardware capability from kernel availability

**Status: implemented.** CPU/OS detection, compiled implementation, enabled policy, and chosen path are different fields. AVX-VNNI, AVX-512 VNNI, and ARM features do not imply interchangeable kernels. Cost: capability reporting includes pending implementations. Revisit enabled candidates only after numerical and end-to-end evidence.

## ADR-007 — Store calibration per device, build, and model

**Status: implemented.** Use a measured profile only when its environment key matches. Do not prescribe four threads or width four to every device. Current calibration is script-driven; automatic calibration would add startup cost and should be a separate choice. Retain candidates only when repeated improvement clears noise and the working 5% threshold.

## ADR-008 — Cache exact prompt computation with bounded lifetime

**Status: implemented after a corrected experiment.** Exact inputs reuse saved final-prompt logits; partial inputs reuse complete aligned batches. Generated tails are removed, and cancellation/error/configuration changes invalidate context. Cost: retained KV and buffers use more memory. The earlier final-token re-evaluation failed a UI comparison and remains archived. Revisit persistence only with privacy, invalidation, and memory-pressure evidence.

## ADR-009 — Retain conservative context speculation as research

**Status: native experiment retained; former product opt-in superseded by ADR-023.** Chat forces speculation depth 0 and has no toggle. Same-request suffix proposals need no extra weights. The final policy delays activation, proposes up to three tokens, verifies with the target, and disables itself after poor measured windows. Benefits vary and batching can change logits. Default-on behavior requires representative task benefit and numerical/quality review.

## ADR-010 — Defer MTP and a 1.7B live drafter

**Status: deferred under current weights and measurements.** The locked 4B has no MTP layers. A separate 1.7B drafter had an unfavorable optimistic cost estimate. This is not a rejection of all speculative architectures. Revisit on compatible trained heads, significantly cheaper proposals, or new device evidence.

## ADR-011 — Treat citations and Kev scores as aids, not proof

**Status: implemented limitation and evaluation rule.** Citation checks validate indices only. Kev reviews a bounded first sentence and has recorded false positives. Neither establishes a fact or serves as evaluation ground truth. Inspectable sources and reviewed mission outcomes remain necessary. Claim-level support checking is a separate research task.

## ADR-012 — Prioritize field missions over generic question scores

**Status: adopted product direction.** Evaluate traveler, farmer, field engineer, mountaineer, and driver workflows. Separate coverage, retrieval, deterministic tools, and model interpretation. Existing fictitious fixtures are development controls, not validated domain procedures. Cost: preparing useful fixtures and qualified review takes more work than keyword-based PASS checks.

## ADR-013 — Use source-specific packages and deterministic tools

**Status: partially implemented through 0.12.** Text/CSV packs and individual text/CSV/PDF/OSM snapshots preserve exact source identity and locators under distinct activation/retention policies. Coverage metadata, broader adapters and deterministic tools remain proposed. Text search, geographic lookup, routing, and numerical operations remain distinct capabilities. No commitment has been made to a universal vector store, map SDK, OCR library, or routing engine. Decide each dependency after a measured vertical slice.

## ADR-014 — Establish English as the maintained project language

**Status: implemented in Outpost 0.8.0.** Maintained documentation, UI resources, main prompts and primary fixtures are English. The former English seed corpus moved to the test APK in 0.10; product libraries start empty. Explicit bilingual reviewer probes and historical experimental outputs preserve their original language. The app ID, Java packages, JNI symbols, native library, and repository slug use Outpost consistently. Fresh English validation is recorded separately from historical Spanish performance.

## ADR-015 — Formalize a standalone repository under E:\projects

**Status: implemented locally as Outpost.** The user selected Outpost. Repository: `E:\projects\outpost`; app ID: `dev.outpost.app`; native library: `outpost_engine`; branch: `codex/outpost`. The original copy, emulator, and unmodified 0.7 evidence are preserved. Models, tools, builds, AVD data, and machine settings remain ignored. Code license, release signing, and publication policy are still open. See the [migration record](repository-migration.md).

## ADR-016 — Keep action and context proposals under the coordinator

**Status: proposed, no runtime change.** Qualitative [group feedback](discovery-2026-09-29.md) suggests personal recall, local-device diagnosis and reconnect actions. Preserve inference/knowledge separation; use explicit user context, bounded adapters and a persistent action ledger if experiments justify them. Internet availability, local-device connectivity and authorization are separate states. Begin with synthetic documents and emulator mock transports. A connector or permission expansion requires a subsequent concrete design decision; reconnect is not authorization. Revisit after the proposed document-recall and read-only diagnostic experiments.

## ADR-017 — Evaluate contextual question families, not a narrow example

**Status: user-clarified product direction; development evaluation implemented in 0.9, general quality still open.** Vitalik's example illustrates the kind of specific, contextual question Outpost should answer. It is not a request for a vegan/restaurant app or a mandatory domain-specific benchmark. Evaluate contextual facts, discovery, recommendations, comparisons, explanations, synthesis and bounded troubleshooting across world, regional and personal evidence. Individual examples help construct fixtures; they do not define product verticals or substitute for transfer across topics. See the [corrected specification](offline-world-knowledge.md).

## ADR-018 — Record the origin bounty and anchor evaluation to its requirements

**Status: adopted direction, partially implemented.** Problem: the project originates from an external bounty (poidh #31) whose acceptance bar and hard requirements were not recorded anywhere in the repository, so the external bar silently disappeared from the backlog while the evaluation work was being defined against internal documents only. Choice: the full bounty text, metadata and provenance are recorded in [bounty-31.md](bounty-31.md) as an external reference this repository does not control, and the evaluation definition is anchored to the bounty's question-quality requirements while tracing every requirement, including the ones that cannot be verified in the current scope, through [fixtures-v1.json](../eval/fixtures-v1.json), [rubric-v1.md](../eval/rubric-v1.md) and [validate.py](../eval/validate.py). Alternatives rejected: keeping the bounty as an external link only, which leaves the bar unmanaged; adopting the bounty requirements wholesale as current product acceptance criteria, which would turn user-deferred requirements into unmeetable gates; and treating the widely quoted restaurant phrasing as a product vertical, rejected by reference to ADR-017. Consequences: the repository now carries an external acceptance bar it does not control, which may change or be wound down by its creator; the bounty page is JavaScript-rendered, so the text has to be re-fetched through the recorded tRPC endpoint and the record must be refreshed before any submission decision; no requirement that cannot be verified in the current scope may ever be reported as met; a private GitHub remote is now authorized while public visibility stays deferred to a final usable version. Explicit non-supersession: ADR-003 stays active — the emulator remains the only execution target, and nothing in this entry expands device execution scope. Revisit when the user lifts the emulator-only restriction, authorizes public distribution, or the bounty closes by payment, wind-down or refund.

## ADR-019 — Deliver bounded JSON packs and CSV before larger adapters

**Status: implemented in 0.9.** Problem: source identity and existing-import preservation were prerequisites for trustworthy retrieval, while the personal-record fixture needed row/date relationships. Choice: transactional schema-2 migration, typed version/hash/fragment locators, bounded inline JSON text/CSV packs and exact CSV records. PDF-first and a universal adapter framework were deferred in favor of this tested slice. Consequences: atomic updates and inspectable retained versions are available. The pack format still supports text/CSV only; later PDF/OSM file adapters are covered by ADR-025/027. Office/ZIM, catalog/signatures, coverage/storage display and resumable downloads remain pending. Product pack-import UI was removed by ADR-023; existing packs remain readable/removable. Revisit with larger licensed source requirements and storage measurements. See [format](knowledge-packs-v1.md) and [validation](validation-0.9.md).

## ADR-020 — Separate execution assertions from attributed answer review

**Status: implemented in 0.9.** Problem: parity/substring/citation-range PASS can hide an unhelpful or unsupported answer. Choice: explicit retrieval/fixed-evidence execution, frozen inputs/results, actual source selection and attributed rubric-v2 records bound to result hashes. Unknown useful-information timing stays unscored with a reason. Keyword PASS and Kev-as-ground-truth were rejected. Consequences: implementation-agent review remains limited development evidence; held-out qualified review is still required. Preserve failures and early runs with incomplete provenance rather than backfilling invented metadata.

## ADR-021 — Retain the production prompt and generator after diagnostics

**Status: retained for evidence-only research; product prompt superseded by ADR-024 in 0.10.** A longer system prompt increased critical failures in the controlled v1 comparison and generally added latency. Four Qwen problem-case probes improved authorization wording but exposed citation loops, unsupported conflict synthesis and CSV date errors. Retain the old system text as `ResearchPrompt` / `evidence-v1` for controlled research, and do not globally switch the generator on those observations. Product chat now uses `ChatPrompt` v1.1 and requires its own quality evidence. Reconsider after representative paired evidence at the UI budget resolves citation/source alignment and supported outcomes. The [review records](validation-0.9.md) retain rejected candidates.

## ADR-022 — Bind local publication to built input and output identities

**Status: implemented and used for local delivery through 0.12.** The earlier0.9 permission-review quota interruption was resolved before 0.10. Modification times alone can miss stale or concurrently changed inputs. Build/lint records source fingerprints and app/test APK hashes only after unchanged-input verification; publication requires matching production inputs and bytes and refuses a different same-version artifact. Standalone `-Verify` remains usable without the build APK. This is a local integrity check, not signing or independent reproducible-build proof. Revisit signed release provenance when distribution is approved.

## ADR-023 — Make chat the product home and move preparation into Settings

**Status: user-directed, implemented in 0.10.** The owner requested a clean chat-first app moving toward mobile user tests. Sending now retrieves and generates in one action; documents/model setup and conversation deletion live in Settings. Prototype diagnostics, demo content, reviewer/speculation UI and the three-tab layout are removed. Existing imports remain protected by precise seed cleanup. The earlier evidence-only home was replaced, while its native/research controls remain available outside product flows. Revisit based on user observations, not internal benchmark convenience.

## ADR-024 — Allow bounded general chat with explicit document provenance

**Status: implemented in 0.10, quality still under evaluation.** An empty library must not make the main chat unusable. General replies may use model knowledge while document-specific/current facts require appropriate evidence or an explicit gap. Recent turns provide bounded follow-up context; source numbers are per-turn and stripped from prior assistant text. This is not full-history memory or a factuality guarantee. Reconsider context budgets, retrieval and prompt policy against held-out practical tasks and preserved failures.

## ADR-025 — Add bounded PDF extraction and prepare ARM64 without device execution

**Status: implemented build/import slice.** PDF is an explicitly requested user input. Pin PdfBox-Android plus its transitive dependency byte identities, preserve original PDF/page locators, reject unsupported encrypted/scanned-only input, and expose extracted text alongside original rendering. OCR and robust layout/table interpretation remain separate. ARM64 is linked and packaged using the existing backend/reference path to prepare mobile testing; custom ARM kernels and device execution/performance are not established. The prior emulator-only execution constraint stays active.


## ADR-026 — Import a selected directory as retryable snapshots

**Status: user-requested, implemented in 0.11.** The owner requested choosing a directory and loading supported files recursively. Use ACTION_OPEN_DOCUMENT_TREE and the existing offline parsers through one shared bounded importer. Process files sequentially with independent transactions, preserve paths, report skips/errors and allow cancellation. Schema 4 binds provider/document identity, format and raw-byte hash so reselecting an unchanged tree does not duplicate completed work. Changed files create new snapshots and retain older searchable documents/references. Retry means selecting the folder again; it does not mean background or automatic byte-offset resume. Automatic sync, background watchers and source writes are outside this request. Android access restrictions and traversal bounds remain explicit. Revisit for a separate refresh/version-management workflow if user tests justify it.


## ADR-027 — Start OSM integration with bounded, inspectable feature extracts

**Status: implemented in 0.12.** The owner requested OSM data alongside document imports. Accept local OSM XML and Overpass JSON, preserve typed IDs/tags/dates/coordinates/attribution, index features separately and bind citations to the exact file snapshot. Use standard Android parsers with bounded input and reject DTDs, invalid/ambiguous coordinates, duplicate identities and error/partial exports. Optional way centers are explicitly approximate and require complete referenced geometry. PBF/GeoJSON, full topology, maps, spatial ranking, GPS and routing remain separate work. This gives a reviewable knowledge slice without claiming a map/navigation engine or current place availability. Revisit with real-region workloads and geographic-query requirements.

## ADR-028 — Use OSM as a place knowledge source for chat

**Status: user-clarified product direction, 2026-09-30; query implementation partial.** The owner wants imported OSM data to answer where a named park is, what restaurants are around a stated place, or where a museum can be found. Adopt named-entity resolution, structured category/proximity retrieval and source-backed chat answers. Keep map rendering, navigation and routing outside this feature; K-07 is deferred and is not an acceptance dependency. A named reference can anchor proximity without GPS. The knowledge domain resolves records/coordinates and computes distances; the model explains the selected evidence. This clarifies ADR-027's next slice without changing its import/storage implementation. Requiring a map stack would add unrelated work; purely lexical passages are insufficient for reliable proximity. Existing 0.12 remains import/lexical/source functionality. Update future evaluation definitions to remove the old K-07 blocker while preserving frozen evidence. Revisit maps/GPS/routes only under a separate concrete user request. See [scope and acceptance](osm-place-queries.md).

## ADR-029 — Answer bounded place queries deterministically before generation

**Status: implemented in 0.13.** Named-location and category/proximity questions have exact entity/distance/source results. Render those results directly in chat, including clarification and unsupported constraints, before requiring a generator. This avoids generating coordinates/ranks and makes the capability usable without a model. General chat and document synthesis retain ChatPrompt/inference. Use a bounded two-pass feature scan initially rather than a new schema/index; preserve sources and expose collection bounds. Reconsider a spatial index and richer language interpretation after workload evidence, keeping deterministic geographic operations separate from model prose. This refines ADR-028's presentation choice and does not imply unrestricted place understanding.

## ADR-030 — Measure and select row reuse separately by matrix shape

**Status: bounded adoption in 0.14 for the measured Bonsai 4B/Outpost35 profile.** Reusing activations/correction across output rows helped multi-column prompt work, but applying row grouping to single-token decode erased much of that gain on longer answers. Keep those settings independent, retain the prior single-column decoder and preserve guarded fallback. Use `q2-row-v3-phase` profile identity and include both settings in cache compatibility. Require full-answer paired timing, exact output/logit checks and lifecycle validation; microbenchmarks or early truncation alone do not justify adoption. Unmatched environments remain conservative. Further per-shape tuning, ARM kernels and memory/energy claims require separate evidence. See [experiment record](kernel-rows-0.14.md).

## ADR-031 — Compare models by task outcomes and actual resource cost

**Status: research direction adopted, 2026-09-30; no runtime/default change.** The owner explicitly questioned selecting Bonsai by familiarity and requested Engram exploration. Keep Bonsai as the measured baseline while admitting pinned alternatives through E-07 and comparing them through E-05. Prioritize compact LFM2.5 QAD and Qwen3.5, with Gemma 4 as a distinct memory tier; include template, sampler, state and full-answer costs. Parameter labels and vendor benchmark speeds cannot choose the product default. Engram requires trained weights and an appropriate runtime; retain source-backed knowledge and treat neural memory as X-07 conditional research. Alternatives rejected are a blind model swap, changing the backend solely because a new model exists, and attaching untrained lookup tables to Bonsai. Reconsider the default only after attributed practical-task review and emulator lifecycle/resource evidence; phone execution still needs expanded scope. See [model survey](model-alternatives.md), [Engram review](engram-review.md) and their pinned metadata.

## ADR-032 — Admit pinned candidate protocols through emulator research

**Status: implemented research slice, 2026-10-01; no product model/default promotion.** The owner asked to advance beyond static model discovery. Add a hash-pinned Spark 1.7B test profile, explicit formatter/sampler/cache policy, literal-data token handling and frozen practical-task runs. Keep existing product calls and three-model selection intact. The backend's generic formatter could not represent Spark faithfully, and adding an arbitrary-GGUF import or silently changing a boolean sampler would hide that mismatch; a bounded tested adapter was chosen instead of importing a general serving framework.

Full SWA storage remains the comparison policy after compact storage changed initial logits despite the same short answer. Record the memory reduction and rejection without claiming quality degradation from hash drift alone. Greedy reserved review found 13/24 fully supported outcomes for each model and several critical errors; sampled timing controls also changed answer quality. Preserve exact artifacts and failed runs, separate model quality from speed, and continue broader sampled-policy/other-model evaluation before changing the product default. No phone, host inference, storage tuning or Relay integration follows from this decision. See [results](spark-candidate-results-2026-10-01.md) and [resumption commands](candidate-testing.md).

## ADR-033 — Extend execution to the specifically authorized Pixel 10 Pro

**Status: owner-authorized trial, 2026-10-01.** The owner explicitly requested tests on the connected Pixel 10 Pro, superseding the earlier emulator-only boundary for that device. Register its USB serial in ignored local settings and require matching Google/Pixel 10 Pro/ARM64 properties before installing or executing. Keep emulator guards/defaults; do not turn device discovery into authorization for arbitrary phones. Host inference remains outside scope.

Start with conservative ARM settings and the pinned candidate lifecycle controls, then paired practical questions. Preserve personal content, model selection, system radio settings and old emulators. The app has no INTERNET permission, which permits local-inference tests without interrupting the owner's phone connectivity; this does not demonstrate a fully disconnected-device mission. Record APK/model/source identities, memory, battery temperature, charging state and Android thermal status. Stop starting requests at severe thermal status. USB-powered timing cannot establish battery life. See [protocol and validation status](pixel10-testing.md). Reconsider tuned ARM policies only with device-specific equivalence and complete-answer evidence.

## Updating this register

Add a new numbered decision when a material tradeoff changes. Include status, problem, choice, alternatives, consequences, evidence, and reconsideration condition. Mark superseded decisions rather than erasing the reason an earlier implementation existed.
