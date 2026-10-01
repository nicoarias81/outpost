# Outpost

Current runtime update: [0.16 validation](docs/validation-0.16.md) admits 32-row dynamic prefill scheduling for the registered Pixel/Bonsai4 profile after two complete paired campaigns. Attention/decode workers and model outputs remain unchanged.

Performance update (2026-10-01): [Outpost 0.15 validation](docs/validation-0.15.md) enables bit-preserving ARM DotProd kernels and persistent workers. On the tested Pixel/Bonsai workloads, identical complete outputs took about 5.8–6.2× less native time; first-token latency improved about 3.2–3.3×. Other-device and answer-quality work remain open.

Research update (2026-10-01): [Spark admission and measured comparison](docs/spark-candidate-results-2026-10-01.md) records the original emulator study. Spark remains research-only; the three selectable product models and pinned weights remain unchanged. Historical same-version 0.14 research builds retain separate hashes.

An offline Android assistant. Open the app to chat; add your own documents through **Settings → Add file / Add folder**. The interface and maintained documentation are in English.

**Current version: 0.16.0, user-test candidate.** The app starts with an empty document library. There are no sample notes, Explore/Library/Status tabs, benchmark buttons, reviewer controls or runtime metrics in the product interface.

## Current experience

- Send a message once to retrieve relevant local material and generate a streaming reply. With no matching document, chat is instructed to use model knowledge without claiming access to personal files or live information; answer correctness still requires review.
- Recent completed messages provide bounded follow-up context. The conversation is saved locally; **New chat** deletes it without deleting documents.
- **Settings → Add folder** recursively imports supported files from a chosen directory and all readable subdirectories, with progress, cancellation and a summary. Unchanged repeated imports are skipped; sources are never edited. See [folder import](docs/folder-import.md).
- Import UTF-8 TXT, Markdown or CSV, or a text-bearing PDF individually with **Add file**. Open original PDF pages or inspect exact text/CSV source records from replies.
- Import **OpenStreetMap XML or Overpass JSON** and consult stored feature names, tags and coordinates through chat or the source browser. Chat now resolves named places and category/proximity questions around a stated landmark, returning computed distances and exact sources without needing a generator. Ambiguity and missing location are explicit. [Place-query scope](docs/osm-place-queries.md) and [formats/preparation](docs/osm-import.md).
- Manage local documents and the installed offline model from Settings. Models are separate verified files; the APK does not download or bundle generator weights.

PDFs are limited to 10 MiB and 100 pages, with bounded extracted text. Text/CSV files are limited to 1 MiB. Scans without readable text, encrypted PDFs and invalid files receive explicit errors; no OCR is implemented. Source references enable inspection, not automatic verification of claims.

The tested Pixel 10 Pro/Bonsai 4B preset uses 4 decode workers, 6 prompt workers, batch 128, width 8 and decode row groups of 4, with 32-row queued prefill and static single-column work. Persistent workers pause between requests. The exact device/OS/model key and [measurements](docs/validation-0.16.md) bound this preset; other keys retain conservative matrix settings and unsupported CPUs keep the backend fallback. The [x86 row experiment](docs/kernel-rows-0.14.md) remains historical evidence.

## Engineering and validation

Start with [AGENTS.md](AGENTS.md), the [handoff](docs/handoff.md), [current state](docs/current-state.md), [chat and document design](docs/chat-beta.md), and [latest validation](docs/validation-0.16.md). The [roadmap](docs/roadmap.md) separates implementation from remaining quality/device work.

The APK now packages **arm64-v8a and x86_64**. Historical runtime validation used Outpost35, the x86_64 Android emulator. The owner has now authorized the connected Pixel 10 Pro; [its protocol and status](docs/pixel10-testing.md) distinguish physical results from emulator evidence. No physical-device latency, compatibility, peak memory, battery, thermal or GrapheneOS claim follows from the build.

Configure the external SDK/JDK/Gradle paths using [development](docs/development.md), then:

```powershell
pwsh -File scripts/build.ps1 -Offline
pwsh -File scripts/test-places.ps1
if ($LASTEXITCODE -ne 0) { throw 'Place checks failed.' }
pwsh -File scripts/test-osm.ps1 -SkipInstall
pwsh -File scripts/test-folders.ps1 -SkipInstall
pwsh -File scripts/test-chat.ps1 -SkipInstall
pwsh -File scripts/test-chat.ps1 -SkipInstall -Generate
pwsh -File scripts/test-knowledge.ps1 -SkipInstall
```

The offline flag requires cached dependencies. Read the [emulator runbook](docs/emulator-runbook.md) before any runtime work. The commands above retain emulator guards. Candidate/ARM and isolated chat wrappers also accept the specifically registered Pixel through its separate protocol. Model execution never runs on the host. Tests install synthetic content only for isolated checks and clean up their own inputs. Run the listed checks sequentially and stop on any nonzero exit code; `-Generate` is optional and requires an installed verified Bonsai 4B model.

Publish the local debug artifact with `scripts/publish-artifact.ps1`, then verify it with `-Verify`. See [validation](docs/validation-0.15.md) for the exact filename, hash, checked build and limits. A debug candidate is not a signed production release. Project license, release signing, public distribution and broader field trials remain separate work; the existing GitHub remote is private.

Use the [documentation index](docs/index.md) to find architecture, decisions, source contracts, test procedures and remaining work.

[Dependency notices](THIRD_PARTY.md) and lock files identify the pinned runtime, models, PDF library and toolchain. Historical failed results and the original Brújula implementation remain preserved. [Bounty #31](docs/bounty-31.md) is background motivation; no bounty acceptance or public submission is claimed.
