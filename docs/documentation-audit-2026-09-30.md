# Documentation audit — 2026-09-30

Documentation-only reconciliation of **Outpost 0.12.0 / code 14**, starting from repository HEAD `23231b3`, implementation `1d311c7` and evidence `dbdd0b6`. Source/build/lock files, fixture definitions, raw evidence and APKs were not edited. No build, emulator install, model generation or new runtime measurement was performed.

## Scope and corrections

The maintained documentation now describes chat as home and Settings as preparation, supported recursive/file inputs, precise snapshot versus pack activation behavior, schema 1→5 migrations, format-specific hashes, PDF originals/pages and bounded OSM element evidence. It separates package contents from runtime validation and product chat from research prompts/controls.

Developer/runbook instructions describe current offline prerequisites, test flags and test-only provider cleanup. Historical provider Activity/disable instructions and prototype screen names remain only within explicitly historical records. Optimization numbers retain their original workloads and evidence, with corrected percentage interpretation. No old width 4 profile or score is described as a fresh 0.12 measurement.

ADR and roadmap IDs remain stable. Superseded choices are labeled, completed slices remain distinct from quality acceptance, and old raw observations/languages are preserved. The dated bounty text/metadata was not re-fetched or changed; its repository capability trace was refreshed.

## Known gap found during source review

`eval/validate.py` reads only the first string after Gradle `abiFilters`; fixture v5 declares x86_64 only although the current APK also packages arm64-v8a. A validator PASS therefore does not verify complete ABI identity. E-06 records a full-set check and new manifest revision, preserving old runs. No code/fixture repair is included in this documentation task.

## Verification

- Reviewed 55 project Markdown files, including preserved historical reports and the synthetic example; 52 documentation files updated/added. The OSM guide and 0.12 validation already matched current source and required no content change.
- All 593 local Markdown file/heading links resolve in the prepared checkout; code fences are excluded so the Overpass example is not misread as a link. Historical ignored APK links are local artifacts, not files supplied by a clean clone.
- `eval/validate.py`: PASS, 15 defined / 12 runnable / 3 blocked, no warnings. The independent ABI finding above limits what that PASS establishes.
- `eval/check-build.ps1`: current main/test source fingerprints and both APK byte identities match the successful build receipt.
- `scripts/publish-artifact.ps1 -Verify`: delivered APK and sidecar match SHA-256 `d73f5cc489b74cc1825aeb9ca8daf2b60900cb8fbc1f585a5c3768026f4b6df7`.
- `git diff --check`: PASS; the change set contains only Markdown documentation.
- The quoted bounty block is unchanged; ADR/backlog identifiers are retained. Existing raw evidence, fixture/model/dependency identities and application sources remain untouched.

Exact release/runtime results remain owned by [0.12 validation](validation-0.12.md), not this audit. No external bounty-status or upstream integration-term refresh is claimed.
