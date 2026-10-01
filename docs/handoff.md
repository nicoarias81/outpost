# Engineering handoff — start here

Updated 2026-10-01. Current product: **Outpost 0.16.0 / code 18**, canonical repo `E:/projects/outpost`, branch `codex/outpost`, app `dev.outpost.app`. Read [current validation](validation-0.16.md), [capability inventory](current-state.md), [runtime](inference-runtime.md) and [the documentation index](index.md).

[Dynamic row scheduling](validation-0.16.md) is now admitted for multi-column prefill on the exact Pixel/Bonsai4 key. The earlier half-block loop remains rejected; its historical evidence is preserved. Current defaults for all other keys remain static.

## Persistent scope

- English app UI, maintained docs/comments and primary fixtures; preserve imported/historical language and deliberate multilingual tests.
- Model/numerical execution is allowed only in Outpost35 / emulator-5582 or the specifically registered Pixel 10 Pro authorized on2026-10-01. Host build/static/header/hash work is allowed; host inference and other phones are outside scope. Preserve Brújula/emulator-5580.
- The product is a practical general offline assistant for travel, farming, field engineering, hiking and driving. The restaurant example defines a contextual question type, not a vegan-specialized app.
- OSM supplies place knowledge for chat; maps/navigation/routing are outside the implemented scope. Action queues/equipment control/reflection remain proposals, not authority to operate external systems.
- Preserve model/backend pins, reference fallback, original/failed evidence and private data. Hardware support, compiled implementation and selected policy are distinct. No remote publication/push or bounty acceptance is implied.

## Current delivery and performance

Local artifact: `dist/outpost-0.16.0-user-test.apk`, app SHA256 `095c5c56995e3de9947d0125a0757387c429191cf0602700f7d5b9b8c036239f`; test APK `92d2c533b1aaff6267c1607b46f6219351589da83bf62ea3b1b1fa4f0c0fa0d0`. [Manifest](../evidence/releases/0.16.0/manifest.json) records exact final runs. Frozen 0.14/0.15 APKs remain preserved. Verify the current source/APK receipt before `-SkipInstall`.

The admitted Pixel/Bonsai4 preset is **4 decode/6 prompt workers, batch 128, width 8, row groups 1/4, prefill chunk 32/decode chunk 0, persistent pools, no affinity**. Six pairs per question across normal/reversed campaigns preserve every token and per-step logit hash while passing the predeclared phase/total latency gate. Research and product use 120 seconds/192 output tokens. No model/backend/prompt/sampling/KV-precision changes occurred. [Validation](validation-0.16.md) owns measurements, drift limitations and final lifecycle/UI/model/x86 checks.

Configuration and cache identity now include both row chunk fields. ARM profile key is `q2-arm-dot-queue-v1`; x86 remains `q2-row-v3-phase`. Chunk names follow activation-column count, so single-column prompt tails retain the static decode policy. Zero, small matrices and a single worker use static scheduling. Ownership tests detect missed/duplicate rows, not only matching output values. MainActivity obtains the entire configuration from the profile factory; do not reconstruct it through a legacy constructor, which dropped the new fields in the preserved failed UI run. The final UI test checks actual queued execution. The static fallback and original DotProd arithmetic remain available. I8MM/VNNI kernels remain unimplemented.

The previous six-decode-worker candidate changed attention reduction and was rejected. The new policy keeps attention workers unchanged. Full-logit tracing is test-only. Use `row-numeric`, `row-tune`, `row-confirm`, `row-confirm-reverse` and `row-lifecycle` through the Pixel isolation wrapper; select 32/0 explicitly for confirmation/lifecycle. Model selection and original phone data are restored after isolated tests. Outpost35's original APKs are restored after its regression wrapper; always check live hashes before changing it.

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

## Current research checkout: TND-01/TND-02

[The Pixel study](speculation-pixel-2026-10-01.md) implements complete target-sample traces and comparable round costs, and measures 1/2/4/8-row verification at 66/466/1066/1666-token prefixes. Execution/lifecycle and normal 0.16-prefix checks pass, but larger-context logits differ and supplied-reference sampled runs change text. Product speculation remains zero. TND-03/TND-04 await an attention-path parity repair; TND-02b should isolate the padded-KV/query-shape boundary without weakening the reference gate.

The current source/build receipt is a research 0.16 app (`509384984532b696e30948fccf992c1433618425e0925caabb5dddb7e53b032b`) with test APK `424c53df056be98b7a0d9308e753bf761f412400f417c430e7e567b389147e97`. The Pixel was restored to the frozen product app/test pair listed above; Outpost35 was also restored. Install both verified research APKs before further research; do not use `-SkipInstall` against the restored phone or overwrite the frozen 0.16 distribution. The prior source-only [TandemLLM review](tandemllm-review.md) remains background, and no upstream code/model/backend patch was incorporated.

## Preserved limitations and next work

Model facts, range/row filtering and citation binding still fail on some known tasks; kernel equivalence intentionally preserves those outputs. Spark remains outside Settings, and LFM2.5/Qwen3.5/Engram remain separate studies. Native MTP/drafter integration is not implemented. NeMo Relay is a design reference, not an installed runtime/exporter.

Prioritize structured operations/source binding, realistic personal/regional collections, full phone picker/provider/folder/OSM coverage, process-death/storage-pressure recovery, other devices/OS builds and controlled energy/thermal measurement. Current Pixel page size is4096;16KiB and GrapheneOS are unvalidated. OCR/Office/ZIM, mission memory and wider geographic language remain open. Follow stable task IDs in [roadmap](roadmap.md).

The fixture provider stays registered with MANAGE_DOCUMENTS. Cleanup revokes grants and hides roots; do not disable/re-enable it, which previously caused readiness timeouts. All old validation/failed runs and the original Brújula baseline are preserved. The [changelog](../CHANGELOG.md) and versioned validations own historical release provenance.
