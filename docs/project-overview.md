# Product overview

Status: product direction adopted; Outpost 0.12 implements chat, local document/folder preparation and bounded OSM lookup. Complete reviewed field workflows remain pending.

## Purpose

Help a person retrieve, understand, and apply information already on their phone when connectivity is unavailable. The intended result is a useful answer or next step, with inspectable sources when supplied and clear limits on personal or current information. Chat is the main interface.

The user identified five equally relevant contexts:

| Context | Task | Material to prepare | Expected useful result |
|---|---|---|---|
| Traveler | Interpret a reservation, reach a destination, compare local options | Personal documents, regional place data, transit reference, travel guide | Correct destination or condition, with saved-data limitations visible |
| Farmer | Investigate an irrigation or equipment anomaly | Exact equipment manual, installation records, observations with units | Applicable section, plausible explanations, and the next discriminating observation |
| Field engineer | Inspect an electrical infrastructure asset | Asset ID, applicable drawing revision, authorized procedure, inspection history | Traceable findings and missing information; no invented authorization or equipment state |
| Mountaineer | Relate position and itinerary to saved terrain information | Map, elevation data, route, battery and equipment context | Documented alternatives and explicit areas of missing coverage |
| Driver | Interpret a vehicle warning or find a saved service location | Correct vehicle manual, route, service records, stated remaining range | Relevant manual section and bounded calculations, without invented live availability |

The [contextual-question requirement](offline-world-knowledge.md) preserves the aim of useful offline knowledge about the world. Vitalik's restaurant example illustrates specific, contextual questions that require retrieval, constraints, comparison or recommendations; it does not define a restaurant or vegan product. Evaluate those question families across different subjects, using world, regional and personal knowledge as appropriate.

## Additional discovery hypotheses

A [small group discussion](discovery-2026-09-29.md) suggests personal document recall, contextual visits, reflective conversation, tasks prepared for reconnection, and local equipment diagnosis. One participant recalled carrying manuals and prepared scripts for disconnected equipment. Another questioned how often coverage loss still occurs. These are qualitative signals, not validated demand or approved feature commitments.

The issue-list demo is one personal-recall fixture within a broader question-family evaluation. Restaurant recommendations are another illustrative fixture; neither alone defines acceptance. Read-only diagnosis against a simulated device is an additional candidate. Reflection and connected execution would require distinct interaction/permission designs; neither is implemented. The five original contexts remain in scope.

## Intended user journey and delivered subset

**Prepare:** choose a region or activity, add documents, inspect coverage and dates, verify storage requirements, and test access in airplane mode. A package download date must not be presented as the date its content was last verified.

**Use:** describe the situation; confirm important context such as equipment model, location, and units; retrieve the applicable information; show the source before waiting for long generation; calculate deterministically where appropriate; explain the result and any material uncertainty.

**Continue:** correct context without retaining invalid assumptions; reopen the original passage; optionally save a local observation or report. Saving is separate from sending. Bounded recent-turn context and local chat persistence are implemented. An editable mission record, typed observations, deterministic tools and a field notebook are not.

The current UI implements chat, file/folder import, model preparation and source reading. Region/context selection, coverage/storage planning, typed tools and explicit mission context remain goals, not present controls. The current OSM goal is chat-based place lookup and category/proximity questions from imported data; map display and route guidance are outside that feature. See [place-query scope](osm-place-queries.md). See [current state](current-state.md).

## Product principles

1. Source reading remains useful without a loaded model; fast useful retrieval is an evaluation goal. Current search is integrated into chat, without a separate search screen.
2. Inference and knowledge can evolve independently.
3. A source stays inspectable through a stable locator, version, and provenance record.
4. Unknown current conditions stay unknown: a saved road, business, or asset record is not a live status service.
5. Deterministic arithmetic, units, and geography belong in bounded local tools.
6. Device capability is detected and measured; a phone's marketing name does not select a kernel.
7. Output speed and answer quality are separate evaluation axes.

## Scope and constraints

The APK packages x86_64 and ARM64 CPU backends; current runtime evidence comes from the x86_64 AOSP emulator. Execution must remain in the emulator. Pixel 3 XL and Pixel 10 Pro are future devices of interest, not validated targets. AOSP and the absence of Google dependencies support the intended deployment direction, but GrapheneOS operation has not been tested.

Earlier planning used a 12 GB RAM environment and a 50 GB total resource budget as bounty constraints. Those are planning references, not measured compliance or a substitute for checking the active submission rules before a submission. Current emulator configuration is 4 GiB; real peak RAM, battery use, and thermal behavior remain unmeasured.

Voice, image interpretation, automatic location and remote synchronization are future possibilities. Map/navigation work is deferred outside the current OSM feature and requires a separate request. No cloud inference fallback is part of the current design.

## Definition of a useful milestone

A field milestone needs a complete prepared evidence package, a reproducible user workflow, source navigation, missing-data behavior, and a reviewed mission outcome. A faster answer that solves the wrong question does not qualify. The next milestones and their concrete acceptance criteria are in the [roadmap](roadmap.md).
