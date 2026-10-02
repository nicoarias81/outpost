# Pixel release acceptance — 2026-10-02

**73 accepted controls** across preparation, contextual chat, seeded recovery and same-version replacement on the registered Pixel 10 Pro. This advances production validation; it is **not acceptance of a production-signed APK**. The app under test is the separate non-debuggable `dev.outpost.app.releaseqa` package with a debug QA certificate. The owner still needs to confirm the proposed production application ID/signing identity.

The [summary](../evidence/research/release-acceptance-20261002/summary.json), [current receipt](../evidence/research/release-acceptance-20261002/receipt.json), [61-control static audit](../evidence/research/release-acceptance-20261002/audit.json), [answer review](../evidence/research/release-acceptance-20261002/answer-review.json) and source snapshots bind this result. Product code, model pins and kernel policy did not change in this pass. All accepted phases used the same QA APK SHA256 `7e61e918494e9c6dc8c79f73dc71b8399880d836b426cb3be0bf7e9c3095863a`; the test APK evolved to correct harness failures, which remain recorded.

## Accepted phases

| Phase | Controls | Evidence |
|---|---:|---|
| Empty chat, five real file-result imports, PDF display, malformed/canceled import rejection | 20 | [Preparation](../evidence/runs/release-acceptance-20261002T062029Z-8be70eaa/prepare.json) |
| OSM queries, five Bonsai 4B answers, stop and subsequent generation | 31 | [Chat](../evidence/runs/release-acceptance-20261002T062029Z-8be70eaa/chat.json) |
| Seed pending state, draft and document identities | 5 | [Seed](../evidence/runs/release-acceptance-20261002T063223Z-213b5668/seed.json) |
| Relaunch after process termination and same-version APK replacement | 17 | [Recovery](../evidence/runs/release-acceptance-20261002T063223Z-213b5668/recover.json) |

The chat questions cover restaurants near a named museum, park location, a spare-part identifier from a field manual, an irrigation time from CSV, a shelter name and a roadside assistance reference. The two OSM replies use deterministic stored-place queries; the other five responses use Bonsai 4B, including one answer after cancellation. The recorded facts match the supplied synthetic records. Each generated answer finished naturally; observed total times were **12.273–14.004 seconds**, with **8.266–11.604 seconds to first token**. These single observations are not a controlled performance comparison.

The original Outpost app/test APK hashes, private database/preferences/document digests and radio settings were unchanged in every recorded run. Original data was neither replaced nor copied into the QA package. The separate QA app, verified model and synthetic stores remain on the phone. Its ownership marker is `accept-20261002T062037Z-8554f0ce`; do not run preparation again over that store. Use `scripts/test-acceptance.ps1 -Target Pixel10Pro -Phase recovery -OwnerRun accept-20261002T062037Z-8554f0ce` only while that package remains exclusively test-owned. Nothing here authorizes testing against future personal data in the QA package.

## Interpretation and limits

The recovery probe deliberately writes pending conversation state, an unsent draft and an interrupted-folder flag, then force-stops and reinstalls the same-version QA APK before relaunching. It verifies documents/content identities, completed answers, pending-state recovery, the draft and the visible folder report. It does **not** inject process death during an actual database/file write, prove a version/schema migration, test storage exhaustion, or establish all cancellation interleavings. Recursive folder/picker and other broader reliability gates retain their prior recorded scope.

The first recovery assertion used the wrong preference filename for a different application ID. Later UI assertions looked for dialog text before opening/waiting for the report window. These were test failures, not corrected production defects. Their raw failed runs remain intact. The final harness waits for the actual app accessibility window. Preparation, imported-document list, rendered PDF, grounded answer, stopped/recovered chat and folder-recovery screenshots were visually inspected.

GitHub distribution is now authorized. The source is being pushed to the existing private [repository](https://github.com/nicoarias81/outpost), and the [0.19 release draft](https://github.com/nicoarias81/outpost/releases/tag/untagged-23ded0442b06542e0b1c) exists. This does not change repository visibility or select a code license. The signed APK, exact production identity, final installation/update acceptance and publication remain pending. See [release closure](release-0.19.md) and the English [installation guide](getting-started.md).
