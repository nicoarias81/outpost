# Engineering handoff — start here

Updated 2026-10-01. Current product: **Outpost 0.15.0 / code 17**, canonical repo `E:/projects/outpost`, branch `codex/outpost`, app `dev.outpost.app`. Read [current validation](validation-0.15.md), [capability inventory](current-state.md), [runtime](inference-runtime.md) and [the documentation index](index.md).

## Persistent scope

- English app UI, maintained docs/comments and primary fixtures; preserve imported/historical language and deliberate multilingual tests.
- Model/numerical execution is allowed only in Outpost35 / emulator-5582 or the specifically registered Pixel 10 Pro authorized on2026-10-01. Host build/static/header/hash work is allowed; host inference and other phones are outside scope. Preserve Brújula/emulator-5580.
- The product is a practical general offline assistant for travel, farming, field engineering, hiking and driving. The restaurant example defines a contextual question type, not a vegan-specialized app.
- OSM supplies place knowledge for chat; maps/navigation/routing are outside the implemented scope. Action queues/equipment control/reflection remain proposals, not authority to operate external systems.
- Preserve model/backend pins, reference fallback, original/failed evidence and private data. Hardware support, compiled implementation and selected policy are distinct. No remote publication/push or bounty acceptance is implied.

## Current delivery and performance

The local artifact is `dist/outpost-0.15.0-user-test.apk`, app SHA256 `dc8af7cb9b7d92fdee9b93c27a7879482be77338dc6a62c592e86249517f9719`. Test APK SHA256 `608614371ce9acdb340928215e7b6352b578da6a87098a343a0117dac405f726`. The frozen 0.14 artifact remains intact. Verify bytes/receipt before `-SkipInstall`; version labels alone do not identify historical research builds.

0.15 enables guarded ARM DotProd with baseline-compatible lane/FMA ordering, prepared activations, column reuse and optional decode-row reuse. ARM requests use session-owned persistent pools, paused after requests/cache release and freed on close. The verified Pixel/Bonsai preset is **decode4/prompt6, batch 128, width 8, prefillRows1/decodeRows4, persistent workers, no affinity**. It requires the exact tested Pixel fingerprint, CPU capabilities and model. Other keys use conservative matrix settings. ARM calibration key is `q2-arm-dot-pool-v1`; x86 retains `q2-row-v3-phase`. No I8MM or VNNI implementation was added.

The six-decode-worker candidate was rejected: attention's split-KV floating-point reduction changes with worker count, causing different distributions from step1 and different sampled text after token 90 in one case. Initial-logit and short-output equality were insufficient. Corrected confirmation compared **every** sampling-logit hash, token, stop reason and output across three rotated pairs per case. Median native totals were205.05→35.52s for late arrival and262.63→42.32s for manual applicability. These use300-second research deadlines on both arms so the old path can finish; the product remains120s/192 output tokens. Latency does not establish answer correctness or battery life.

[The ARM record](arm-optimization-2026-10-01.md) owns rejected configurations, numeric/graph/controller/tuning/lifecycle/trace evidence. The release validation owns final regression and UI identities. The original emulator APKs are restored by the x86 regression wrapper; recheck live hashes before changing it. Model selection and original phone app data are preserved after isolated tests.

## Product and knowledge contracts

Chat is home. One send retrieves and streams; one local conversation persists and two bounded completed/limited turns supply context. `ChatPrompt` v1.1 permits general knowledge without invented private/live sources. `ResearchPrompt` is a separate evidence-only evaluation protocol. Product speculation remains0.

Settings owns Add file/Add folder, document inspection/removal, locked model import/selection and confirmed New chat. Files: strict UTF-8 TXT/Markdown/CSV, text-bearing PDF, OSM XML/Overpass JSON. Folders recurse through readable subdirectories with cancellation and per-item outcomes. Unchanged bytes/source identity skip duplicates; changed snapshots remain separately searchable. No automatic latest-version selection or sync. Developer packs have a separate atomic activation contract and no product import button.

Library schema5 preserves metadata, packs, source identities and imported snapshots. PDF originals/page text and OSM original extracts/features remain inspectable. PDF citation hashes bind extracted page serialization; import bindings separately hash raw PDF bytes. OSM queries resolve recorded names/aliases, categories/localities and proximity to a named place with deterministic distances and inspectable citations. The bounded scan is not a general spatial/navigation engine. Source presence does not guarantee current conditions or truth.

## Safe resumption

1. Inspect Git status and active processes. Read [Pixel protocol](pixel10-testing.md) or [emulator runbook](emulator-runbook.md). Use one runtime/install workflow per target; do not interrupt another owner's run.
2. Resolve tools from ignored `.local/developer-settings.json`. Host Python is `C:/Users/nicoa/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe`; set `PYTHONUTF8=1`. SDK/JDK/Gradle paths are local settings, not repo dependencies. Do not re-copy verified GB models to resume.
3. Build with `scripts/build.ps1 -Offline`; it builds both ABIs/test APK plus lint and checks unchanged source/dependency identities. `eval/check-build.ps1` verifies the receipt. Publish only a new version with `scripts/publish-artifact.ps1` and verify it; never overwrite a frozen version with different bytes.
4. Physical native work uses `test-pixel-chat-isolated.ps1 -ArmPhase ...`; product UI uses the same wrapper with `-Generate`. It temporarily renames app-private stores, preserves original files on-device and restores their hashes in `finally`. Inspect `.local/pixel-ui-active.json`; if not restored, reconnect the same phone and use `-RestoreOnly` before any further app work.
5. The test-only status Activity can remain visible over keyguard through standard Android APIs; it does not disable authentication or open personal app state. Isolated UI visibility additionally requires an active marker and empty conversation/library/drafts. Do not manually use Outpost during the suite. No global radio/governor/screen-security settings are changed.
6. Use [candidate testing](candidate-testing.md) for Spark research and [development](development.md) for other suites. Full logit traces are opt-in research overhead and off in product sessions. Generalized device/field acceptance remains open.

## Preserved limitations and next work

Model facts, range/row filtering and citation binding still fail on some known tasks; kernel equivalence intentionally preserves those outputs. Spark remains outside Settings, and LFM2.5/Qwen3.5/Engram remain separate studies. Native MTP/drafter integration is not implemented. NeMo Relay is a design reference, not an installed runtime/exporter.

Prioritize structured operations/source binding, realistic personal/regional collections, full phone picker/provider/folder/OSM coverage, process-death/storage-pressure recovery, other devices/OS builds and controlled energy/thermal measurement. Current Pixel page size is4096;16KiB and GrapheneOS are unvalidated. OCR/Office/ZIM, mission memory and wider geographic language remain open. Follow stable task IDs in [roadmap](roadmap.md).

The fixture provider stays registered with MANAGE_DOCUMENTS. Cleanup revokes grants and hides roots; do not disable/re-enable it, which previously caused readiness timeouts. All old validation/failed runs and the original Brújula baseline are preserved. The [changelog](../CHANGELOG.md) and versioned validations own historical release provenance.
