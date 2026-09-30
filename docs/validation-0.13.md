# Outpost 0.13.0 validation — 2026-09-30

Version code 15 delivers bounded offline place questions through chat. `PlaceQueries` resolves recorded names/aliases, locality/category filters and proximity to named references. Answers, distance calculations and citations are deterministic and do not require a generator. Existing native numerical kernels, model pins and the general chat prompt are unchanged.

## Artifact and exact identity

- Local debug candidate: `dist/outpost-0.13.0-user-test.apk`, **28,963,230 bytes**.
- App SHA-256: `ddf5ac901becaa0e6426e277df73ba2801057b6ebbd7536a97736000f457a5ea`.
- Test APK SHA-256: `ca00a0221b79aeaeeffc87c008956979bb9993937d0bb44bda19c9ff49380720`.
- Build UTC: `2026-09-30T19:02:05.6133589Z`; [receipt](../evidence/releases/0.13.0/build-receipt.json).
- Main source fingerprint: `600c1d250be5b7fdceb41cc2fc242e22e58763e1668d2131df6a0e69767a2424`.
- Test source fingerprint: `4dfafeebe1d970d49f06fe3e42e7fce4567ab24c1fbb6c23352bbf1d5d156f33`.
- [Package manifest](../evidence/releases/0.13.0/manifest.json): ARM64/x86_64 libraries; no place fixture, mock library or fixture-provider receiver in the product APK. Generator weights remain separate.

Publication and checksum verification succeeded locally. No public release, phone execution or signed production release is claimed. Lint: 0 errors,3 existing upstream BouncyCastle warnings; [report](../evidence/releases/0.13.0/lint-results-debug.txt).

## Final emulator verification

All final runs below use the app/test identities above on offline Outpost35, emulator-5582. No model executes in these suites.

| Scope | Result | Evidence |
|---|---|---|
| Place queries, import, no-model chat, real subset and source UI | 63 passed| [Report](../evidence/runs/places-20260930T190208Z-73ef278c/place-checks.json) |
| OSM parser/storage/SAF/source reader | 39 passed| [Report](../evidence/runs/osm-20260930T190651Z-7c1a71c1/osm-checks.json) |
| Folder traversal/retry/identity | 30 passed| [Report](../evidence/runs/folders-20260930T190657Z-fc6dabb5/folder-checks.json) |
| Chat/files/PDF/persistence | 35 passed| [Report](../evidence/runs/chat-20260930T190712Z-167e06ae/chat-checks.json) |
| Knowledge/migration/CSV/packs/locators/policy | 60 passed| [Report](../evidence/runs/knowledge-20260930T190721Z-432ab319/knowledge-checks.json) |
| Complete ABI manifest identity | 6 controls passed| [Host-only fault injection](../evidence/research/osm-places-20260930/abi-validation.json) |

Place checks cover same-name ambiguity and locality clarification, aliases/accents, category filtering, explicit/default radii and units, boundary distances, antimeridian/coincident/antipodal math, top-five ordering, missing coordinates/coverage, reference exclusion, unsupported filters, cancellation, duplicate/conflicting snapshots, distinct negative IDs, scan limits, source removal and conversation persistence. An isolated empty model-directory Context verifies the actual Activity no-model branch without moving/deleting installed weights.

## Real-data scope and observed latency

A public OSM API extract was reduced to 100 named restaurant/museum/urban-park objects plus available way geometry. The prepared 74,492-byte test-only subset retains source IDs/tags/dates and excludes contributor account attributes. [Provenance](../evidence/research/osm-places-20260930/source.json) records URL, retrieval time, raw/prepared hashes, preparation and attribution. Retrieval time is not an invented dataset update date; relation coordinates missing from this format remain unknown.

Host-side independent arithmetic froze expected place identities/order. Emulator queries located Plaza de la Lealtad, selected the five nearest of 47 recorded restaurants within 500 m of Museo de Colecciones ICO, and selected five of 6 positioned museums within 1 km of the plaza. This is a bounded imported-data result, not complete regional coverage, live availability or a recommendation study.

In the final run, direct real-subset library queries took 71/86/90 ms. The three corresponding actual chat checks took 674/765/780 ms including test polling/UI waits. These are single-run integration timings over 100 prepared features, not a statistical performance or physical-phone benchmark. Native LLM latency is not part of this direct-answer path.

Screenshots: [named park](../evidence/runs/places-20260930T190208Z-73ef278c/places-park.png), [synthetic proximity](../evidence/runs/places-20260930T190208Z-73ef278c/places-nearby.png), [museum follow-up](../evidence/runs/places-20260930T190208Z-73ef278c/places-museum.png), [real-data chat](../evidence/runs/places-20260930T190208Z-73ef278c/places-real.png), [exact source](../evidence/runs/places-20260930T190208Z-73ef278c/places-source.png). Test imports/turns are removed afterward; no corpus is seeded into product storage.

## Earlier controls and limits

Earlier 47- and 50-check place runs and intermediate ingestion regressions remain under unique run IDs. They belong to earlier app/test bytes and are not relabeled as final checks. No failed runtime run occurred in this feature slice; the initial v6 validator rejected an uppercase judgement-only note, which was corrected before the final PASS.

The parser covers bounded named/category/radius forms and short user-context follow-ups; arbitrary natural language, preference/rating/time filters, typo tolerance and automatic area containment are not implemented. `in` means a recorded locality tag. `near` uses a stated 2 km default, with explicit 1 m–50 km radii, approximate straight-line distance and center limitations. A bounded streaming scan permits 20,000 feature rows /32 million payload characters, without a persistent spatial index. Exceeding it is explicit. Conflicting snapshots prompt source review rather than silent latest-version selection.

E-06 is fixed and v6 records both ABIs while removing the obsolete map/routing blocker. The broader regional time/preference recommendation fixture remains blocked and unscored; the existing general evidence-only matrix is not replaced by these deterministic checks. No map renderer, GPS, routing, model-weight change or kernel improvement is part of 0.13.

## Reproduce

Read [runbook](emulator-runbook.md), verify the dedicated offline emulator and current build receipt, then run `scripts/test-places.ps1`. Follow with `test-osm.ps1 -SkipInstall`, `test-folders.ps1 -SkipInstall`, `test-chat.ps1 -SkipInstall` and `test-knowledge.ps1 -SkipInstall`. Use the original release checkout/artifacts to reproduce this identity; newer source needs its own build/evidence.
