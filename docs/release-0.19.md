# Production release candidate — 0.19.0-rc1

Prepared 2026-10-02, version code 21. This is a release-engineering candidate, not a published production release. The last frozen, measured user-test delivery remains [0.18](validation-0.18.md). Its performance numbers do not automatically become measurements of the signed production artifact.

## Candidate scope

- Preserve model/backend pins, prompts, sampling, context/output limits, fallback and the admitted Pixel I8MM/fixed-attention policy. No new optimization or model is admitted by this release task.
- Explicit non-debuggable `release` APK and unsigned AAB. No minification or new dependency migration in this candidate.
- Release exports exactly seven product JNI functions. Research diagnostics, kernel overrides, judge and test entry points are not exported. Generation rejects oracle tokens, speculative depth and experimental template/sampling policies. Internal shared helper code and Java declarations may remain; this is not a claim that every research-related byte was removed.
- Kev auxiliary head/configuration move to debug-only assets. Generator weights and synthetic knowledge remain outside the production APK. Existing third-party notices remain packaged.
- Settings offers **About and privacy**, version and offline dependency notices. App keeps its existing no-permission/no-network and disabled-backup boundaries.
- `releaseQa` shares release native build settings but uses `dev.outpost.app.releaseqa` and a debug certificate. It is non-debuggable, separate from both the existing app and its test APK, and never distributable as production.

## Build and acceptance

```powershell
./scripts/build-release.ps1 -Offline
./scripts/test-release.ps1 -Generate
```

The build validates pinned backend/dependencies, captures unchanged source fingerprints, builds APK/AAB/QA/test artifacts, runs release lint and audits APKs. A unique ignored `.local/release-<timestamp>-<id>/` directory keeps all four artifacts, audit and receipt. `.local/release-build-receipt.json` points to the latest successful build. Existing `dist` deliveries are never replaced.

The audit checks package/version, no debugging/test-only/network permissions or auxiliary components, backup disabled, exact ABI/library set, bundled notices, absence of the research head/models/fixture library, 16 KiB ELF LOAD alignment, APK ZIP alignment, seven exported native symbols, and byte identity of release/QA/AAB native libraries. APK asset hashes must match between release and QA. Alignment is a static property; **16 KiB runtime testing is still open**. See [Android's page-size guidance](https://developer.android.com/guide/practices/page-sizes).

The dedicated test runner accepts only the identity-checked offline Outpost35 emulator. Its synthetic tests target the separate non-debuggable QA package; the original app/test APK hashes and radios are checked before/after. A verified existing Bonsai 1.7B is copied entirely within the emulator for optional generation. This is functional release-path testing, not a new phone benchmark or broad answer-quality result. It does not provide the exact signed production package/certificate acceptance.

## Signing and distribution decisions

The owner was asked whether the first channel is a direct closed-group APK, a Google Play closed test or public APK, and whether code stays private or receives a selected license. These decisions remain pending until explicitly answered. No project license is selected by this document. No public upload or new signing identity is created.

Android updates require the appropriate signing identity; existing debug installations cannot be replaced by an unrelated production certificate. Never uninstall the owner's app or clear its data to make an install work. Decide a data-preserving migration or separate preview ID before migrating real users. Follow [Android's signing guidance](https://developer.android.com/studio/publish/app-signing).

For an owner-provided keystore and independently verified certificate fingerprint, `scripts/sign-release.ps1` prepares a local APK. Configure `OUTPOST_STORE_PASSWORD` and `OUTPOST_KEY_PASSWORD` in the invoking process without committing or sharing secrets, then pass `-KeyStore`, `-KeyAlias` and `-ExpectedCertificateSha256`. It verifies the source/audit receipt, refuses frozen filename replacement, signs in ignored scratch storage, rejects a debug or unexpected certificate, checks signature/alignment and writes SHA256/certificate/build metadata. Successful signing has not been exercised without an authorized key. It does not upload, install, sign the AAB or declare release acceptance. Play upload signing/configuration follows only if that channel is chosen.

## Remaining release gates

| Gate | State / acceptance |
|---|---|
| Release packaging and static controls | Implementation in this candidate; exact executed evidence is recorded below |
| Non-debuggable release-path behavior | Isolated QA tests; final production-signed APK still needs fresh install, update, restart, chat/import/source/cancel acceptance |
| Signing identity and recovery | Owner chooses/provides identity; verify certificate, secure backup/recovery and update path |
| Distribution and code license | Owner decision pending; preserve all dependency notices |
| User-facing setup | Test a first-time user obtaining/importing a pinned model and documents before losing connectivity |
| Reliability | Process death during import/generation, low storage/memory and long-session recovery remain open; inspect K-09 and U-03 |
| Quality | Known row/range filtering, source applicability/citation binding and model-fact failures remain; define reviewed representative release acceptance, not just harness PASS |
| Hardware | Physical release smoke, sustained heat/RAM/battery and additional phones/16KiB runtime remain unvalidated |
| Publication | Exact signed artifact, install/update acceptance, notes and explicit distribution action required |

Do not represent compilation, static 16KiB alignment or the QA certificate as completion of the remaining gates. Keep the candidate status until the relevant gates are satisfied.

## Executed evidence

[Candidate manifest](../evidence/releases/0.19.0-rc1/manifest.json), [build receipt](../evidence/releases/0.19.0-rc1/receipt.json) and [static audit](../evidence/releases/0.19.0-rc1/audit.json) bind the artifacts. Build `release-20261002T054516Z-8b4af162` passed **61 static controls**. Release and debug lint each report **0 errors and 3 pre-existing upstream BouncyCastle warnings**.

[Final emulator run](../evidence/runs/release-qa-20261002T054518Z-cd5f089d/run.json) passed **21 smoke + 14 generation controls** on the non-debuggable QA package. TXT/CSV/OSM/PDF import, unchanged-file deduplication, retrieval, JNI research exclusion, launcher chat and About are covered. Bonsai 1.7B completed a short answer and repeated the same text/first-logit hash after cache clearing. This is functional coverage, not a score for instruction adherence or broad factual quality. Original app/test APK hashes and radio settings were unchanged. The separate QA app and its synthetic stores remain on Outpost35; no Pixel change occurred.

The [About screenshot](../evidence/releases/0.19.0-rc1/about.png) was inspected visually. The debug APK still packages the unchanged research head/config and exports research JNI; v12 fixtures validate with 12 runnable and 3 blocked cases. Earlier compilation/audit/staging/test-isolation failures are retained or described in the manifest, without turning them into application-quality passes.

Frozen local unsigned files: `dist/outpost-0.19.0-rc1-unsigned.apk` (**23,432,335 bytes**, SHA256 `d0aca81fa83a40a7b9f90eccf846cb9c77b6216faa1a5e65829fc56ce5d7ee4c`) and `dist/outpost-0.19.0-rc1-unsigned.aab` (**17,361,549 bytes**, SHA256 `2c5f5fc284c98cd0c5839a64d3ee62285233f94740badd12f4d8b88f039f5f55`). These are **not installable/distributable signed releases**. Preserve these files and use another version for changed candidate bytes.
