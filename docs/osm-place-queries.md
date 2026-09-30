# Offline place questions from OSM data

**User clarification, 2026-09-30; implemented bounded slice in Outpost 0.13.0; broader place understanding remains partial.** OSM supplies a local database of places for chat questions: where a named park is, which restaurants are around another place, and where a museum can be found. The goal is answering from imported geographic facts. A map interface, rendered tiles, turn-by-turn navigation and a routing engine are outside this feature's scope and are not prerequisites.

This is a shared knowledge capability within Outpost's general assistant, not a restaurant-only product. Chat stays home and Settings owns preparation. The product UI and maintained examples remain English.

## Intended answers

| Question | Required knowledge operation | Useful answer |
|---|---|---|
| Where is Riverside Park? | Resolve the named entity in the relevant imported area; distinguish same-name matches | Name, recorded address/locality or coordinates, useful recorded details and exact source; clarify an ambiguous match |
| Which restaurants are around Central Station? | Resolve the named station, find restaurant entities near its recorded position and order the result deterministically | A bounded list of names, available addresses, approximate straight-line distances and source references; state the search radius |
| Where can I find a museum? | Filter by category within the user's named/confirmed area; ask for an area only if it is missing or ambiguous | Matching museums and their recorded locations; no invented position or assumed current city |

Examples use illustrative names, not real-world recommendations or a bundled dataset. A named reference place is enough for an initial proximity workflow; it does not require the phone's GPS. If a future query says "near me", location must come from explicit user context or a separately implemented permission-aware location feature.

## Implemented knowledge operations

`PlaceQueries` recognizes bounded location/category requests and answers directly from stored records:

1. Interpret the query as a named-place lookup or a category search, carrying the user's stated area/reference and constraints.
2. Resolve names and available source aliases. Return disambiguation candidates when multiple parks, stations or districts match; do not silently select a different city.
3. Filter structured categories/tags. For proximity, obtain the reference position, select candidate features and compute distances with local deterministic code.
4. Use an explicit bounded radius, stable ordering and result limit. Label the radius and approximate straight-line distance. A neighborhood name/center alone does not prove polygon containment; distinguish "near the center of X" from "inside X".
5. Render a concise answer with actual names, available addresses/coordinates, computed distances and stable source references. Direct place questions do not need a generator. General document/explanation chat still uses the LLM; arbitrary extra filters/time/ratings are not silently applied.

The implementation uses `Library.answerPlaces` / `PlaceQueries` with no new database schema. A bounded streaming pass reads at most 20,000 stored feature rows and 32 million payload characters, grouping identical positive OSM identities and detecting conflicting snapshots. Proximity/category selection uses a second bounded pass and keeps the best five results. It never silently truncates an over-limit collection. Negative local IDs remain distinct across extracts. This initial bounded implementation has no persistent spatial index.

Exact names and recorded aliases are normalized for case/accents; same-name records ask for locality. `in X` uses declared locality tags, not inferred polygons. `near X` uses recorded coordinates and a stated 2 km default radius; explicit 1 m–50 km radii accept m/km/miles. The anchor is excluded from nearby category results. Results show up to five places, deterministic distance/name ordering and matching counts. Follow-ups such as `In Test North` or `Within 300 m` reuse only prior user query context. Supported categories include park, museum, restaurant, cafe, pharmacy, supermarket, hotel, hospital, fuel, drinking water and charging station. Unsupported filters and near-me without a reference receive an explicit response.

An absent address stays absent; coordinates or supplied locality can still be useful. Approximate way centers must remain labeled and may give poor distances near the edge of a large park. Features without usable coordinates can appear in name/category results but must not receive fabricated distance ranks. A center is not an entrance. Missing results mean no match in the selected imported data, not that no such place exists anywhere. Current hours/availability and subjective quality are not implied by proximity.

## Data preparation

Import both potential answers and reference places. A restaurant-only extract may omit the park/station needed to anchor a query. Parks, museums, places and other chosen categories must be part of the prepared area; preserve names/aliases, relevant tags, coordinates or labeled centers, typed OSM identity, source date and attribution.

Common tag examples are [urban parks: leisure=park](https://wiki.openstreetmap.org/wiki/Tag:leisure%3Dpark), [restaurants: amenity=restaurant](https://wiki.openstreetmap.org/wiki/Tag:amenity%3Drestaurant), [museums: tourism=museum](https://wiki.openstreetmap.org/wiki/Tag:tourism%3Dmuseum), and [named places/localities: place](https://wiki.openstreetmap.org/wiki/Key:place). These are examples, not an exhaustive category ontology; national parks and other park types may use different tags. The import guide's sample query now includes leisure/place records, but no sample query guarantees complete coverage or every landmark type.

See [OSM import](osm-import.md) for supported XML/Overpass JSON formats, bounds, provenance and offline preparation. Snapshot selection also matters: changed extracts currently remain searchable beside older imports. Future place-result deduplication/version selection must preserve exact source references and not silently resolve conflicting observations.

## Implemented versus pending

Outpost 0.13 implements the bounded operations above and preserves the existing import/source adapter. Synthetic controls cover ambiguity, unknown coordinates, radius boundaries, antimeridian arithmetic, units, stable top-five ordering, duplicate/conflicting snapshots, negative local IDs, cancellation and collection bounds. Three workflows over a frozen prepared public Madrid subset are checked against independently calculated identities/order, then through the actual chat UI. This is bounded integration evidence, not full-region completeness or live venue accuracy.

The delivered K-06 slice is a text-only chat answer backed by deterministic place queries. General language/constraint coverage, typo tolerance, automatic area containment and corpus-scale indexing remain follow-up work. K-07's map/routing experiment is deferred outside the current scope. PBF support, full topology and GPS are also not prerequisites for the bounded named-reference workflow. Broader source formats can be considered later if actual preparation/scale needs justify them.

## Validation and remaining acceptance

Use isolated test data with same-name parks in different areas, a reference landmark, restaurants inside/outside an explicit radius, museums, missing coordinates/addresses, approximate centers and overlapping old/new extracts. Verify the reference entity, category, candidate set, distance calculation, deterministic order and exact locators without invoking a model. Include a boundary-distance case and a query with absent coverage.

Then run production chat in the authorized emulator and review the three question types plus ambiguity, missing area and unavailable source cases. Assert that the response lists actual retrieved entities, distinguishes approximate distance from walking distance, and opens the correct source for each place. Use a frozen real-area extract for a separate utility review. All user workflows must work without a map screen or routing graph.

[Fixture v6](../eval/fixtures-v6.json) removes K-07 as a blocker while retaining the broader recommendation task as unexecuted. Deterministic place checks are a separate suite, not retroactive scores for the research prompt. Run `scripts/test-places.ps1` on offline Outpost35; it imports and cleans only its own data and does not execute a generator. The public sample is test-APK-only, with [source/preparation provenance](../evidence/research/osm-places-20260930/source.json).

See [ADR-028/029](decisions.md), [roadmap](roadmap.md), [handoff](handoff.md) and [0.13 validation](validation-0.13.md). All runtime tests remain emulator-only.
