# Architecture decision register

Recorded on 2026-09-29 from the current implementation and project discussion. These entries distinguish existing choices from proposals; they do not backdate formal approvals. Outpost is the selected name; public distribution decisions remain open.

## ADR-001 — Separate inference from knowledge

**Status: adopted direction, partially implemented.** A generator can change without reimporting documents or maps. Knowledge retains provenance and uses source-appropriate indexes. The application coordinates confirmed context and bounded evidence. A single model-specific knowledge store would couple unrelated changes. Cost: explicit adapters/contracts and migrations still need implementation. Revisit boundaries after a second source type is working, not before.

## ADR-002 — Execute locally in the Android process

**Status: implemented.** JNI calls pinned llama.cpp; there is no host inference server or runtime cloud fallback. The manifest requests no network permission and has no Google Play Services dependency. Cost: native packaging, RAM, and lifecycle management are application responsibilities. Package preparation may use host connectivity before field use. Any future downloader is a separate reviewed product change.

## ADR-003 — Keep execution inside the emulator

**Status: active user constraint.** AOSP x86_64 is the current execution target. ARM compilation is permitted preparation, not evidence of phone support. Pixel ownership does not override the user's instruction to stay in the emulator. Revisit only when the user explicitly expands device execution scope.

## ADR-004 — Pin models, toolchains, and backend

**Status: implemented.** Fixed revisions, sizes, and checksums make failures attributable and avoid silently loading incompatible layouts. Model imports accept locked files only. Cost: updates require deliberate compatibility work. Revisit a generic model catalog only after metadata/architecture/template validation is implemented.

## ADR-005 — Keep llama.cpp unmodified and wrap selected operations

**Status: implemented.** Own dot and matrix functions use linker wrappers; vendor code stays unchanged. This keeps the reference available and the patch surface visible. It still depends on internal symbols, formats, and synchronization contracts, so backend upgrades require revalidation. This is not a stable upstream plugin ABI.

## ADR-006 — Separate hardware capability from kernel availability

**Status: implemented.** CPU/OS detection, compiled implementation, enabled policy, and chosen path are different fields. AVX-VNNI, AVX-512 VNNI, and ARM features do not imply interchangeable kernels. Cost: capability reporting includes pending implementations. Revisit enabled candidates only after numerical and end-to-end evidence.

## ADR-007 — Store calibration per device, build, and model

**Status: implemented.** Use a measured profile only when its environment key matches. Do not prescribe four threads or width four to every device. Current calibration is script-driven; automatic calibration would add startup cost and should be a separate choice. Retain candidates only when repeated improvement clears noise and the working 5% threshold.

## ADR-008 — Cache exact prompt computation with bounded lifetime

**Status: implemented after a corrected experiment.** Exact inputs reuse saved final-prompt logits; partial inputs reuse complete aligned batches. Generated tails are removed, and cancellation/error/configuration changes invalidate context. Cost: retained KV and buffers use more memory. The earlier final-token re-evaluation failed a UI comparison and remains archived. Revisit persistence only with privacy, invalidation, and memory-pressure evidence.

## ADR-009 — Offer conservative context speculation as opt-in

**Status: experimental implementation, default off.** Same-request suffix proposals need no extra weights. The final policy delays activation, proposes up to three tokens, verifies with the target, and disables itself after poor measured windows. Benefits vary and batching can change logits. Default-on behavior requires representative task benefit and numerical/quality review.

## ADR-010 — Defer MTP and a 1.7B live drafter

**Status: deferred under current weights and measurements.** The locked 4B has no MTP layers. A separate 1.7B drafter had an unfavorable optimistic cost estimate. This is not a rejection of all speculative architectures. Revisit on compatible trained heads, significantly cheaper proposals, or new device evidence.

## ADR-011 — Treat citations and Kev scores as aids, not proof

**Status: implemented limitation and evaluation rule.** Citation checks validate indices only. Kev reviews a bounded first sentence and has recorded false positives. Neither establishes a fact or serves as evaluation ground truth. Inspectable sources and reviewed mission outcomes remain necessary. Claim-level support checking is a separate research task.

## ADR-012 — Prioritize field missions over generic question scores

**Status: adopted product direction.** Evaluate traveler, farmer, field engineer, mountaineer, and driver workflows. Separate coverage, retrieval, deterministic tools, and model interpretation. Existing fictitious fixtures are development controls, not validated domain procedures. Cost: preparing useful fixtures and qualified review takes more work than keyword-based PASS checks.

## ADR-013 — Use source-specific packages and deterministic tools

**Status: proposed implementation.** Preserve originals, versions, coverage, and locators. Text search, geographic lookup, routing, and numerical operations remain distinct capabilities. No commitment has been made to a universal vector store, map SDK, OCR library, or routing engine. Decide each dependency after a measured vertical slice.

## ADR-014 — Establish English as the maintained project language

**Status: implemented in Outpost 0.8.0.** Maintained documentation, UI resources, main prompts, seed notes, and primary fixtures are English. Explicit bilingual reviewer probes and historical experimental outputs preserve their original language. The app ID, Java packages, JNI symbols, native library, and repository slug use Outpost consistently. Fresh English validation is recorded separately from historical Spanish performance.

## ADR-015 — Formalize a standalone repository under E:\projects

**Status: implemented locally as Outpost.** The user selected Outpost. Repository: `E:\projects\outpost`; app ID: `dev.outpost.app`; native library: `outpost_engine`; branch: `codex/outpost`. The original copy, emulator, and unmodified 0.7 evidence are preserved. Models, tools, builds, AVD data, and machine settings remain ignored. Code license, release signing, and publication policy are still open. See the [migration record](repository-migration.md).

## Updating this register

Add a new numbered decision when a material tradeoff changes. Include status, problem, choice, alternatives, consequences, evidence, and reconsideration condition. Mark superseded decisions rather than erasing the reason an earlier implementation existed.
