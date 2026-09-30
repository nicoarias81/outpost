# Outpost 0.10.0 validation — 2026-09-30

Version code12. This is the chat-first user-test candidate requested by the owner. The product UI is English; source imports and conversation content retain their original text. Runtime execution stayed inside Outpost35, the x86_64 AOSP Android emulator. The APK additionally compiles/links/packages ARM64 for future phone trials.

## Artifact

- Local artifact: `dist/outpost-0.10.0-user-test.apk`, **28,777,650 bytes**.
- App SHA-256: `cef2473ca5acfc2bcc93b94507719d6f243504178b7073cbe8c3a6717da2d2fe`.
- Test APK SHA-256: `7e51cacaf53223968d7a0fcc6753ac4ff4f95db5434e6878e8ee5a40afb63abf`; the instrumented test APK is separate from the user artifact.
- Build receipt UTC: `2026-09-30T13:47:45.1797422Z`; [frozen receipt](../evidence/releases/0.10.0/build-receipt.json).
- Production input fingerprint: `cf4c94123416986137ceddda8e8b7168407e0c83487e06b4c98cf28f34e0e9e0`.
- Test input fingerprint: `0a3e2b329dc51c76b4cfb5a6a2b6ff5887dc005fa42061cc182825e0f700cd06`.
- Backend unchanged: `86ea01d05ec237f89b78b41c8c1ee0f908141ac7`.

Generator GGUFs are not bundled. This is a debug-signed candidate; it does not establish production signing, physical-phone compatibility or public release. `scripts/publish-artifact.ps1` checks the receipt and current production inputs; `-Verify` checks the versioned artifact and LF checksum sidecar.

## Implemented and checked

| Scope | Result | Frozen evidence |
|---|---|---|
| Chat, import, migration and genuine Bonsai 4B conversation | **45 checks passed** | [Run](../evidence/runs/chat-20260930T134747Z-ada02691/chat-checks.json) |
| Knowledge/CSV/schema migration/package/locator regression | **60 checks passed** | [Run](../evidence/runs/knowledge-20260930T135044Z-1051c357/knowledge-checks.json) |
| Existing functional/retrieval controls adapted to chat/Settings | **53 checks passed** | [Report](../evidence/releases/0.10.0/checks.json) |
| App/test build and lint | Successful; **0 errors, 3 dependency warnings** | [Lint](../evidence/releases/0.10.0/lint-results-debug.txt) |
| Package inspection | ARM64 and x86_64 libraries, PDF licenses present, no `assets/library.json` in product APK | [Inspection](../evidence/releases/0.10.0/apk-inspection.json) |
| Alignment | `zipalign -c -P 16 4` succeeds; ARM64 engine LOAD segments align to `0x4000` | [Manifest](../evidence/releases/0.10.0/manifest.json) |

The three lint warnings refer to trust-all TLS helper classes inside upstream BouncyCastle's PKIX JAR. Outpost does not invoke those helpers and has no INTERNET permission. The warnings were preserved rather than globally suppressed. Dependency AAR/JAR/POM byte identities and notices are pinned in `pdfbox-lock.json` and checked during the build.

Checks cover a fresh empty knowledge DB; exact unchanged-demo cleanup from schema2 while retaining a user import and edited former seed; schema1 migration/failure rollback; local conversation persistence and interrupted-turn recovery; Settings/import actions; actual TXT/CSV/PDF result handling; exact original PDF copy; page locators and original rendering; invalid/encrypted/no-text rejection; current chat send/stop/restart; and a relevant fact near the end of a long page reaching the bounded prompt.

The historical mock corpus lives only in the test APK and is inserted explicitly into isolated fixture databases. The product APK no longer bundles that corpus or presents preset/benchmark/reviewer/speculation controls. Test-created documents and conversation rows are removed by identity at the end; pre-existing user rows are retained.

## Real conversation observations

The final `chat-v1.1` run uses the actual selected Bonsai 4B GGUF, ordinary production send/retrieval/generation, and stable document display names. Full system/user prompts, selected excerpts, outputs and timings are in the run JSON.

1. First message introduces the code name Cedar; the model replies `Cedar` (2 output tokens, 21.230 s total).
2. Follow-up asks which code name was given; the model replies `Cedar` using previous-turn context (2 tokens, 21.290 s).
3. After real file import, the user asks for sample PX-65's spare filter. The model answers F-92 and cites source[1], the corresponding PDF page2 (21 tokens, 20.517 s).

These three synthetic plumbing observations are manually inspectable, not a broad quality score. The timing is emulator wall time, not phone latency or a controlled performance comparison. No native numerical kernel was changed in this release. Previous kernel and MTP/speculation results remain historical; chat disables experimental speculation.

## Preserved failures and limitations

An earlier [chat run](../evidence/runs/chat-20260930T133832Z-d8f7b238/chat-checks.json) failed the imported-file answer assertion after passing initial/follow-up chat. Its first harness asserted before saving that third native output, so that exact failed response is unavailable; the failed assertion and preceding outputs remain preserved. The recorder was fixed to save outputs before assertions and retain failed conversation rows before cleanup. A subsequent `chat-v1` run passed44 checks without a production prompt fix; therefore the prior answer-quality failure is **not** claimed resolved. The final run additionally uses stable filenames and query-centered excerpts; this is not a controlled proof of what caused the earlier failure.

The first PDF screenshot captured extraction before original rendering finished. Verification was strengthened to await the explicit rendering state; later screenshots show the original PDF page. The [final PDF screenshot](../evidence/runs/chat-20260930T134747Z-ada02691/chat-pdf.png) and [home](../evidence/runs/chat-20260930T134747Z-ada02691/chat-home.png) are actual emulator captures, not design mockups.

General model knowledge is permitted without documents; it can still be wrong or stale. Source relevance and citation numbers do not guarantee claim support. Recent conversation context is bounded, not full-history memory. PDF extraction does not implement OCR, table/layout understanding or robust coverage of every PDF producer; original textless pages are labeled. Input bounds may reject larger files. Crash/orphan-file cleanup and real storage/memory-pressure behavior remain pending.

ARM64 JNI/backend linking and 16KiB alignment checks are static/build evidence only. No ARM model execution, physical Pixel/GrapheneOS, peak RAM, thermal or battery measurement was performed. The candidate moves toward user tests; it does not complete those device or broader quality gates, nor the bounty acceptance bar.

## Reproduction

Follow [the emulator runbook](emulator-runbook.md). With matching built APKs and the dedicated emulator booted/offline:

```powershell
pwsh -File scripts/test-chat.ps1 -SkipInstall -Generate
pwsh -File scripts/test-knowledge.ps1 -SkipInstall
```

The chat runner checks the successful source/APK receipt and requires installed verified Bonsai 4B for generation. Without `-Generate`, it executes no model. Evidence-only fixtures now use v3 and the explicit test-only corpus; their protocol still uses `ResearchPrompt`, not the chat prompt. A validated manifest is not a new scored run. Historical 0.9 data is archived before fixed-path reports are overwritten.
