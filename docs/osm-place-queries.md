# Offline place questions from OSM data

**User clarification, 2026-09-30; adopted product scope, implementation partial.** OSM supplies a local database of places for chat questions: where a named park is, which restaurants are around another place, and where a museum can be found. The goal is answering from imported geographic facts. A map interface, rendered tiles, turn-by-turn navigation and a routing engine are outside this feature's scope and are not prerequisites.

This is a shared knowledge capability within Outpost's general assistant, not a restaurant-only product. Chat stays home and Settings owns preparation. The product UI and maintained examples remain English.

## Intended answers

| Question | Required knowledge operation | Useful answer |
|---|---|---|
| Where is Riverside Park? | Resolve the named entity in the relevant imported area; distinguish same-name matches | Name, recorded address/locality or coordinates, useful recorded details and exact source; clarify an ambiguous match |
| Which restaurants are around Central Station? | Resolve the named station, find restaurant entities near its recorded position and order the result deterministically | A bounded list of names, available addresses, approximate straight-line distances and source references; state the search radius |
| Where can I find a museum? | Filter by category within the user's named/confirmed area; ask for an area only if it is missing or ambiguous | Matching museums and their recorded locations; no invented position or assumed current city |

Examples use illustrative names, not real-world recommendations or a bundled dataset. A named reference place is enough for an initial proximity workflow; it does not require the phone's GPS. If a future query says "near me", location must come from explicit user context or a separately implemented permission-aware location feature.

## Planned knowledge operations

Implement entity retrieval before relying on the generator to describe results:

1. Interpret the query as a named-place lookup or a category search, carrying the user's stated area/reference and constraints.
2. Resolve names and available source aliases. Return disambiguation candidates when multiple parks, stations or districts match; do not silently select a different city.
3. Filter structured categories/tags. For proximity, obtain the reference position, select candidate features and compute distances with local deterministic code.
4. Use an explicit bounded radius, stable ordering and result limit. Label the radius and approximate straight-line distance. A neighborhood name/center alone does not prove polygon containment; distinguish "near the center of X" from "inside X".
5. Give the generator a compact set of actual entities and stable evidence locators. It explains the results and available details; it must not invent entities, coordinates or calculate distances from prose. Keep each returned place inspectable.

Conceptual `lookup_place` and `find_places` operations describe a proposed knowledge API, not implemented tool names. Choose indexes/columns from the actual dataset size and query workload; a particular spatial library is not mandated. There is no reason to feed the complete OSM extract into the model or build a map renderer for these questions.

An absent address stays absent; coordinates or supplied locality can still be useful. Approximate way centers must remain labeled and may give poor distances near the edge of a large park. Features without usable coordinates can appear in name/category results but must not receive fabricated distance ranks. A center is not an entrance. Missing results mean no match in the selected imported data, not that no such place exists anywhere. Current hours/availability and subjective quality are not implied by proximity.

## Data preparation

Import both potential answers and reference places. A restaurant-only extract may omit the park/station needed to anchor a query. Parks, museums, places and other chosen categories must be part of the prepared area; preserve names/aliases, relevant tags, coordinates or labeled centers, typed OSM identity, source date and attribution.

Common tag examples are [urban parks: leisure=park](https://wiki.openstreetmap.org/wiki/Tag:leisure%3Dpark), [restaurants: amenity=restaurant](https://wiki.openstreetmap.org/wiki/Tag:amenity%3Drestaurant), [museums: tourism=museum](https://wiki.openstreetmap.org/wiki/Tag:tourism%3Dmuseum), and [named places/localities: place](https://wiki.openstreetmap.org/wiki/Key:place). These are examples, not an exhaustive category ontology; national parks and other park types may use different tags. The import guide's sample query now includes leisure/place records, but no sample query guarantees complete coverage or every landmark type.

See [OSM import](osm-import.md) for supported XML/Overpass JSON formats, bounds, provenance and offline preparation. Snapshot selection also matters: changed extracts currently remain searchable beside older imports. Future place-result deduplication/version selection must preserve exact source references and not silently resolve conflicting observations.

## Implemented versus pending

Outpost 0.12 already imports bounded OSM files, retains typed objects/tags/coordinates/dates, performs lexical retrieval and opens exact cited features. That supports inspecting stored location facts when the relevant feature is retrieved. It does **not** yet implement reliable named-place resolution, structured category/proximity queries, distance ranking, or complete evaluation of the three workflows above. A successful synthetic street-address answer is not acceptance of nearby-place search.

The next K-06 slice is a text-only chat answer backed by structured place queries. K-07's map/routing experiment is deferred outside the current scope. PBF support, full topology and GPS are also not prerequisites for the bounded named-reference workflow. Broader source formats can be considered later if actual preparation/scale needs justify them.

## Acceptance for the next slice

Use isolated test data with same-name parks in different areas, a reference landmark, restaurants inside/outside an explicit radius, museums, missing coordinates/addresses, approximate centers and overlapping old/new extracts. Verify the reference entity, category, candidate set, distance calculation, deterministic order and exact locators without invoking a model. Include a boundary-distance case and a query with absent coverage.

Then run production chat in the authorized emulator and review the three question types plus ambiguity, missing area and unavailable source cases. Assert that the response lists actual retrieved entities, distinguishes approximate distance from walking distance, and opens the correct source for each place. Use a frozen real-area extract for a separate utility review. All user workflows must work without a map screen or routing graph.

The existing `regional-poi-recommendation-v1` definition in [fixture v5](../eval/fixtures-v5.json) still lists K-07 as a blocker. That dependency is superseded by this clarification. Preserve old manifests/runs as evidence; the next manifest revision must remove map/routing as a requirement and separately define name lookup, anchored category/proximity search and source-backed answers. The missing query implementation/corpus/executor still blocks acceptance; this scope correction does not turn an unexecuted fixture into a pass.

See [ADR-028](decisions.md#adr-028--use-osm-as-a-place-knowledge-source-for-chat), [K-06/K-07](roadmap.md) and [handoff](handoff.md). This clarification changes documentation and priorities, not the current APK or its measured behavior.
