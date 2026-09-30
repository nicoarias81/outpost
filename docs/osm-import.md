# OpenStreetMap data import — 0.12

## Prepare and import

In **Settings → Add file**, choose an uncompressed UTF-8 OSM XML `.osm`/`.xml` file or an Overpass JSON `.json` export. **Add folder** imports the same formats recursively. The app copies and indexes the file locally; it performs no network download. Existing unchanged-file deduplication and new-snapshot behavior also apply to OSM.

OpenStreetMap's [XML format](https://wiki.openstreetmap.org/wiki/OSM_XML) describes nodes, ways and relations with their tags. Overpass offers [XML and JSON exports](https://dev.overpass-api.de/overpass-doc/en/targets/formats.html), including optional geometry centers. Outpost accepts that bounded subset as searchable features. It does not accept PBF, GeoJSON, compressed archives, OSM change/history files or Overpass count/area output in this version.

Before going offline, prepare a small area in an OSM export tool. For Overpass Turbo, a sample query over the visible map bounding box is:

```overpass
[out:json][timeout:25];
(
  nwr["amenity"]({{bbox}});
  nwr["shop"]({{bbox}});
  nwr["tourism"]({{bbox}});
  nwr["emergency"]({{bbox}});
  nwr["power"]({{bbox}});
);
out meta center;
```

Export the raw Overpass JSON result, not GeoJSON. `{{bbox}}` is an Overpass Turbo macro. Adjust the area/categories to the actual trip or field task and respect the chosen service's limits. The query is preparation guidance; Outpost does not run it or automatically fetch a location. Queries with no relevant data, incomplete responses and unsupported formats receive import errors rather than fabricated results.

## What is retained

- Tagged nodes, ways and relations, including their original typed IDs. Untagged geometry nodes can assist way-center calculation but are not separate knowledge entries. Deleted objects are not indexed.
- Original tags, object version and last-edited timestamp when supplied. Missing tags remain absent; they are not negative facts.
- Node coordinates, exported geometry centers, and explicitly labeled bounding-box centers for ways whose referenced nodes are all available and do not span the dateline. Missing or unsupported geometry remains unknown. Centers are not entrances, routing points or the device's position.
- A declared XML bounding box and extract timestamp when available. These do not establish complete coverage, current opening status or current availability. Object edit time, extract time and app import time remain different concepts.
- The exact original file, source/format/raw-byte identity, attribution and a feature-level locator. Opening a chat source resolves the exact local snapshot and OSM object; positive object IDs also expose the corresponding OSM URL as text.

The source browser lists 50 features per page and displays recorded tags/coordinates without requiring a model. Lexical retrieval searches names and tags, with a few category aliases. Chat can use those excerpts with citations. This is not a geographic nearest-neighbor query engine, a ranking/reviews database, live open-hours evaluation, a rendered map, GPS integration or a routing graph. Those are separate capabilities.

## Attribution and source trust

The reader and evidence retain **© OpenStreetMap contributors · ODbL 1.0** and the [OSM copyright/license link](https://www.openstreetmap.org/copyright). Preserve the source's attribution and license when preparing or redistributing data. The app does not authenticate an imported file's publisher; compatible XML/JSON can be user-created or edited. Test fixtures are synthetic and are not real-place data or a bundled product knowledge base.

## Storage and bounds

Schema5 adds `osm_features` alongside existing documents/FTS/origin bindings. Each extract is one document with a short summary; individual feature payloads and searchable text occupy separate rows, avoiding a giant document body. A locator uses the dataset ID, revision, `element` kind, ordinal and original-file hash. The OSM type/ID/version is retained in the feature itself. Earlier document/CSV/PDF locators continue to resolve.

Per extract: at most 32 MiB, 100,000 objects, 5,000 tagged features, 200,000 way references, 128 tags per object, 256-character keys, 2,048-character values, 16,384 indexed characters per feature and 6 million aggregate indexed characters. JSON nesting and XML token sizes are bounded before parsing. DTD/entity declarations, malformed coordinates/IDs/timestamps, duplicate object identities and error/partial Overpass responses are rejected. The importer checks cancellation while parsing/indexing and commits the new dataset atomically.

Changed bytes produce another snapshot; older files/citations are preserved and remain searchable under the existing snapshot policy. There is no automatic newest-version selection, global cross-extract entity deduplication or sync. Removing an extract removes its feature/FTS rows and private original file; saved citations become unavailable. Process-death orphan-file cleanup, broad real-region evaluation and corpus-scale memory/latency work remain pending.

## Verify or resume

Use `scripts/test-osm.ps1` on offline Outpost35. `-Generate` adds one genuine Bonsai 4B source-answer check; the default executes no model. Continue with folder, no-generation chat/import, and knowledge regressions. The fixture provider is in the test APK only. Its ordered owner-grant receiver acknowledges permission changes, and the harness checks readiness. Between suites its roots are hidden and grants revoked while the provider stays registered, avoiding the component re-registration failure observed during development. See [validation](validation-0.12.md) for exact final build/run identities and preserved failures.
