# Product overview

Status: product direction adopted; complete field workflows remain proposed.

## Purpose

Help a person retrieve, understand, and apply information already on their phone when connectivity is unavailable. The primary result is a useful next step supported by inspectable information, rather than an unconstrained conversation or a benchmark score.

The user identified five equally relevant contexts:

| Context | Task | Material to prepare | Expected useful result |
|---|---|---|---|
| Traveler | Interpret a reservation, reach a destination, compare local options | Personal documents, regional map, transit reference, travel guide | Correct destination or condition, with saved-data limitations visible |
| Farmer | Investigate an irrigation or equipment anomaly | Exact equipment manual, installation records, observations with units | Applicable section, plausible explanations, and the next discriminating observation |
| Field engineer | Inspect an electrical infrastructure asset | Asset ID, applicable drawing revision, authorized procedure, inspection history | Traceable findings and missing information; no invented authorization or equipment state |
| Mountaineer | Relate position and itinerary to saved terrain information | Map, elevation data, route, battery and equipment context | Documented alternatives and explicit areas of missing coverage |
| Driver | Interpret a vehicle warning or find a saved service location | Correct vehicle manual, route, service records, stated remaining range | Relevant manual section and bounded calculations, without invented live availability |

Restaurant recommendations are one travel subcase. Generic trivia is useful for integration checks but is not the primary product evaluation.

## User journey

**Prepare:** choose a region or activity, add documents, inspect coverage and dates, verify storage requirements, and test access in airplane mode. A package download date must not be presented as the date its content was last verified.

**Use:** describe the situation; confirm important context such as equipment model, location, and units; retrieve the applicable information; show the source before waiting for long generation; calculate deterministically where appropriate; explain the result and any material uncertainty.

**Continue:** correct context without retaining invalid assumptions; reopen the original passage; optionally save a local observation or report. Saving is separate from sending. A field notebook and multi-turn mission context are not implemented yet.

## Product principles

1. Reading and searching remain useful without a loaded model.
2. Inference and knowledge can evolve independently.
3. A source stays inspectable through a stable locator, version, and provenance record.
4. Unknown current conditions stay unknown: a saved road, business, or asset record is not a live status service.
5. Deterministic arithmetic, units, and geography belong in bounded local tools.
6. Device capability is detected and measured; a phone's marketing name does not select a kernel.
7. Output speed and answer quality are separate evaluation axes.

## Scope and constraints

Current implementation targets Android and CPU inference in an x86_64 AOSP emulator. Execution must remain in the emulator. Pixel 3 XL and Pixel 10 Pro are future devices of interest, not validated targets. AOSP and the absence of Google dependencies support the intended deployment direction, but GrapheneOS operation has not been tested.

Earlier planning used a 12 GB RAM environment and a 50 GB total resource budget as bounty constraints. Those are planning references, not measured compliance or a substitute for checking the active submission rules before a submission. Current emulator configuration is 4 GiB; real peak RAM, battery use, and thermal behavior remain unmeasured.

Voice, image interpretation, automatic location, navigation, and remote synchronization are future possibilities. No cloud inference fallback is part of the current design.

## Definition of a useful milestone

A field milestone needs a complete prepared evidence package, a reproducible user workflow, source navigation, missing-data behavior, and a reviewed mission outcome. A faster answer that solves the wrong question does not qualify. The next milestones and their concrete acceptance criteria are in the [roadmap](roadmap.md).
