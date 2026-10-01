from pathlib import Path
import json,sys

base=Path(__file__).resolve().parent/'files'
manifest=json.loads(Path(sys.argv[1]).read_text(encoding='utf-8-sig'))
assert manifest['passed'] and manifest['version']=='0.15.0'
def edit(name,changes):
    path=base/name;text=path.read_text(encoding='utf-8')
    for old,new in changes:
        assert old in text,(name,old)
        text=text.replace(old,new)
    path.write_text(text,encoding='utf-8')

edit('README.md',[
('Physical-device update (2026-10-01): [Pixel 10 Pro results](docs/pixel10-results-2026-10-01.md) add native admission, 32 practical model answers and real chat/import/PDF checks. ARM Q2 optimization and answer-quality work remain pending.','Performance update (2026-10-01): [Outpost 0.15 validation](docs/validation-0.15.md) enables bit-preserving ARM DotProd kernels and persistent workers. On the tested Pixel/Bonsai workloads, identical complete outputs took about 5.8–6.2× less native time; first-token latency improved about 3.2–3.3×. Other-device and answer-quality work remain open.'),
('The published local 0.14 APK and its three selectable models remain the product baseline; the same-version research build has separate hashes.','Spark remains research-only; the three selectable product models and pinned weights remain unchanged. Historical same-version 0.14 research builds retain separate hashes.'),
('Current version: 0.14.0','Current version: 0.15.0'),
('The measured Bonsai 4B emulator profile now uses guarded two-row processing for multi-column matrices and retains the prior single-token decoder. [Paired measurements and limits](docs/kernel-rows-0.14.md) describe the result; other devices/models keep conservative defaults until measured.','The tested Pixel 10 Pro/Bonsai 4B preset uses 4 decode workers, 6 prompt workers, batch 128, width 8 and decode row groups of 4. Persistent workers pause between requests. The exact device/OS/model key and [measurements](docs/validation-0.15.md) bound this preset; other keys retain conservative matrix settings and unsupported CPUs keep the backend fallback. The [x86 row experiment](docs/kernel-rows-0.14.md) remains historical evidence.'),
('docs/validation-0.14.md','docs/validation-0.15.md'),
('Project license, release signing, public distribution and phone trials remain separate work','Project license, release signing, public distribution and broader field trials remain separate work'),
('The commands below retain emulator guards; the candidate harness also accepts the explicitly registered Pixel through its separate protocol.','The commands above retain emulator guards. Candidate/ARM and isolated chat wrappers also accept the specifically registered Pixel through its separate protocol.')])

(base/'docs/handoff.md').write_text(f'''# Engineering handoff — start here

Updated 2026-10-01. Current product: **Outpost 0.15.0 / code 17**, canonical repo `E:/projects/outpost`, branch `codex/outpost`, app `dev.outpost.app`. Read [current validation](validation-0.15.md), [capability inventory](current-state.md), [runtime](inference-runtime.md) and [the documentation index](index.md).

## Persistent scope

- English app UI, maintained docs/comments and primary fixtures; preserve imported/historical language and deliberate multilingual tests.
- Model/numerical execution is allowed only in Outpost35 / emulator-5582 or the specifically registered Pixel 10 Pro authorized on2026-10-01. Host build/static/header/hash work is allowed; host inference and other phones are outside scope. Preserve Brújula/emulator-5580.
- The product is a practical general offline assistant for travel, farming, field engineering, hiking and driving. The restaurant example defines a contextual question type, not a vegan-specialized app.
- OSM supplies place knowledge for chat; maps/navigation/routing are outside the implemented scope. Action queues/equipment control/reflection remain proposals, not authority to operate external systems.
- Preserve model/backend pins, reference fallback, original/failed evidence and private data. Hardware support, compiled implementation and selected policy are distinct. No remote publication/push or bounty acceptance is implied.

## Current delivery and performance

The local artifact is `dist/{manifest['artifactName']}`, app SHA256 `{manifest['appSha256']}`. Test APK SHA256 `{manifest['testApkSha256']}`. The frozen0.14 artifact remains intact. Verify bytes/receipt before `-SkipInstall`; version labels alone do not identify historical research builds.

0.15 enables guarded ARM DotProd with baseline-compatible lane/FMA ordering, prepared activations, column reuse and optional decode-row reuse. ARM requests use session-owned persistent pools, paused after requests/cache release and freed on close. The verified Pixel/Bonsai preset is **decode4/prompt6, batch128, width8, prefillRows1/decodeRows4, persistent workers, no affinity**. It requires the exact tested Pixel fingerprint, CPU capabilities and model. Other keys use conservative matrix settings. ARM calibration key is `q2-arm-dot-pool-v1`; x86 retains `q2-row-v3-phase`. No I8MM or VNNI implementation was added.

The six-decode-worker candidate was rejected: attention's split-KV floating-point reduction changes with worker count, causing different distributions from step1 and different sampled text after token90 in one case. Initial-logit and short-output equality were insufficient. Corrected confirmation compared **every** sampling-logit hash, token, stop reason and output across three rotated pairs per case. Median native totals were205.05→35.52s for late arrival and262.63→42.32s for manual applicability. These use300-second research deadlines on both arms so the old path can finish; the product remains120s/192 output tokens. Latency does not establish answer correctness or battery life.

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
''',encoding='utf-8')

edit('docs/current-state.md',[
('Outpost 0.14.0 / version code 16','Outpost 0.15.0 / version code 17'),
('[Validation](validation-0.14.md)','[Validation](validation-0.15.md)'),
('| Runtime | Guarded AVX2/F16C Q2 row reuse for multi-column work, retained one-row decode, existing grouped widths and cache | Measured Bonsai 4B/Outpost35 profile only; custom ARM/VNNI kernels remain pending; the authorized Pixel now passes bounded model admission on the reference path |','| Runtime | Guarded x86 AVX2/F16C and ARM DotProd Q2 paths, prepared activation/column/decode-row reuse, persistent ARM workers and cache | Exact Pixel/Bonsai4 preset validated; other keys remain conservative. No new I8MM/VNNI/GPU/NPU implementation or broad phone/energy claim |'),
('| Calibration | Device/OS/app-version/kernel/model key now includes separate prefill/single-column row settings | Current measured emulator profile 4/4 threads, batch 128, width 4, rows 2/1; other keys remain conservative 1/1 |','| Calibration | Device/OS/app-version/kernel/model key, separate prompt/decode workers and row settings | Pixel/Bonsai4 uses4 decode/6 prompt, batch128, width8, rows1/4, no affinity. Six decode workers were rejected after full-trace drift; unmatched keys keep conservative settings |'),
('## Current evidence','## Current evidence\n\n[0.15 validation](validation-0.15.md) records144,384,240 matrix comparisons and complete-output/per-step-logit confirmation, followed by final artifact regressions and restored private data. It supersedes the initial phone reference-path measurements below for current runtime performance; historical results remain unchanged.'),
('The delivered debug candidate is `dist/outpost-0.14.0-user-test.apk`','The previous0.14 debug candidate was `dist/outpost-0.14.0-user-test.apk`; current delivery is `dist/outpost-0.15.0-user-test.apk`'),
('defaults to v7:','defaults to v8:'),
('Kernel 0.14 adds measured row-policy optimization','Kernel0.14 added measured x86 row-policy optimization')])

edit('docs/index.md',[
('Outpost 0.14.0 / version code 16','Outpost 0.15.0 / version code 17'),
('[current validation](validation-0.14.md)','[current validation](validation-0.15.md)'),
('[0.14 row-kernel experiment](kernel-rows-0.14.md)','[ARM experiment](arm-optimization-2026-10-01.md), [historical x86 row experiment](kernel-rows-0.14.md)'),
('[v7 fixtures](../eval/fixtures-v7.json)','[v8 fixtures](../eval/fixtures-v8.json)'),
('[0.14 validation](validation-0.14.md), [frozen release manifest](../evidence/releases/0.14.0/manifest.json)','[0.15 validation](validation-0.15.md), [frozen release manifest](../evidence/releases/0.15.0/manifest.json)')])

edit('docs/development.md',[
('Current baseline: **Outpost 0.14.0**, Windows/PowerShell host, Android API 35 emulator execution. Work from `E:/projects/outpost`. The APK packages `x86_64` and `arm64-v8a`; all runtime/model/numerical testing remains in Outpost35 / `emulator-5582`. Read the [emulator runbook](emulator-runbook.md) before runtime work.','Current baseline: **Outpost0.15.0**, Windows/PowerShell host. Work from `E:/projects/outpost`. The APK packages x86_64/ARM64; execution is allowed on Outpost35/emulator-5582 or the specifically registered Pixel10Pro. Read [the emulator guide](emulator-runbook.md) or [the Pixel protocol](pixel10-testing.md) before runtime work. Host inference remains outside scope.'),
('outpost-0.14.0-user-test.apk','outpost-0.15.0-user-test.apk'),
('[validation](validation-0.14.md)','[validation](validation-0.15.md)')])

edit('docs/evaluation.md',[
('Current reference: **Outpost 0.14.0**, [validation](validation-0.14.md), [fixtures v7](../eval/fixtures-v7.json)','Current reference: **Outpost0.15.0**, [validation](validation-0.15.md), [fixtures v8](../eval/fixtures-v8.json)'),
('All current run links and exact identities are in [0.14 validation](validation-0.14.md).','Current identities are in [0.15 validation](validation-0.15.md); [0.14 validation](validation-0.14.md) preserves earlier integration/OSM controls.')])
edit('eval/README.md',[
('[fixtures-v7.json](fixtures-v7.json): manifest schema 2, revision 7, app **0.14.0**','[fixtures-v8.json](fixtures-v8.json): manifest schema2, revision8, app **0.15.0**'),
('| v7 | 0.14 | Runtime-release identity; same evidence-only protocol, separate row-kernel controls |','| v7 | 0.14 | Runtime-release identity; same evidence-only protocol, separate row-kernel controls |\n| v8 | 0.15 | ARM runtime-release identity; unchanged questions/locks, separate full-logit performance controls |'),
('[0.14 validation](../docs/validation-0.14.md) owns current run identities','[0.15 validation](../docs/validation-0.15.md) owns current run identities; the0.14 record preserves previous integration coverage')])

edit('THIRD_PARTY.md',[
('Product baseline: Outpost 0.14.0, with the separately identified October 1 Spark research build.','Product baseline: Outpost0.15.0, with ARM optimization and the separately identified Spark research profile.'),
('VNNI and ARM candidates currently have requirement descriptors, not new optimized implementations.','VNNI/I8MM candidates remain descriptors. The own `q2_arm.c` implements guarded DotProd while preserving the pinned backend\'s emulated-dot lane grouping and fused accumulation.'),
('runtime validation remains x86_64 emulator-only.','bounded runtime validation now covers the x86 emulator and specifically authorized Pixel10Pro. Persistent workers use existing GGML/llama public thread-pool APIs; no vendor source was modified.')])

edit('docs/roadmap.md',[
('| P-02 | Implement ARM Q2 kernels | P-01 | Reference equivalence/guard/tail tests, supported feature dispatch, whole-model timing and memory; no unsupported-instruction execution |','| P-02 — DotProd slice adopted in0.15 | Implement ARM Q2 kernels | P-01 | Guarded DotProd, prepared activations/column reuse and decode row groups,144M exact matrix comparisons and complete per-step-logit confirmation on the registered Pixel. [Results](validation-0.15.md). I8MM, broader devices/shapes, memory pressure and energy remain open |'),
('Single-column two/four-row alternatives remain research-only after regressions; finer shape selection and ARM require new evidence;','The x86 single-column alternatives remain rejected; ARM0.15 separately validates four-row decode for the measured Pixel preset. Finer shape/device selection remains open;')])

decision='''## ADR-034 — Preserve ARM numerical semantics while optimizing execution

**Status: bounded adoption in0.15,2026-10-01.** The owner requested maximum phone optimization. Use per-function guarded DotProd, activation/column/row reuse and session-owned persistent pools without modifying pinned backend/model sources. Preserve the reference's per-lane float/FMA order and fallback. Six decode workers looked faster in short probes but changed split-KV attention reductions; full-output tracing rejected that policy. Select4 decode/6 prompt workers only for the exact tested Pixel/Bonsai key, with every sampling-logit hash and generated token matching the reference. Fixed affinity was slower and is not enabled.\n\nResearch alone may extend each engine session's deadline to300 seconds for both comparison arms; product requests retain120 seconds/192 tokens. An initial-logit or short-token match is not sufficient for adoption. Pools pause after requests/cache release, resize only after context teardown and restore caller affinity if research pinning is used. Test lock-screen visibility never disables authentication and personal UI state is isolated. See [validation](validation-0.15.md) and [experiment](arm-optimization-2026-10-01.md). Reconsider worker counts, KV layout, I8MM or other model profiles only with independent full-trace and practical-cost evidence.

'''
edit('docs/decisions.md',[('## Updating this register',decision+'## Updating this register')])
edit('CHANGELOG.md',[('## 0.14.0 —','''## 0.15.0 — Measured ARM execution — 2026-10-01

- Adds guarded ARM DotProd Q2 kernels with exact reference lane/FMA ordering, prepared activations, specialized column groups and decode-row reuse.
- Reuses and pauses session-owned CPU workers; validates cache/cancellation/recovery and independent pool resizing. Global power/radio policies and model selection are preserved.
- Adds the exact Pixel10Pro/Bonsai4 preset:4 decode/6 prompt threads,batch128,width8,rows1/4,persistent workers,no affinity. Other keys retain conservative matrix settings.
- Rejects six decode workers after long-output divergence; confirms the adopted path against every sampling-logit hash and output token. Separates research300-second deadlines from product120 seconds/192 tokens.
- Preserves failed probes, isolated private-data restoration, final model/UI/x86 regression and unchanged model/backend pins. Updates fixture identity to v8 without changing questions or transferring old quality scores.
- [Validation](docs/validation-0.15.md) records artifact hashes, measured latency and limits. No universal speedup, battery-life, field-quality or public-distribution claim.

## 0.14.0 —''')])
print('Current English documentation staged from verified0.15 manifest.')
