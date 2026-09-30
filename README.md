# Outpost

An offline Android assistant. Open the app to chat; add your own documents through **Settings → Add documents**. The interface and maintained documentation are in English.

**Current version: 0.12.0, user-test candidate.** The app starts with an empty document library. There are no sample notes, Explore/Library/Status tabs, benchmark buttons, reviewer controls or runtime metrics in the product interface.

## Current experience

- Send a message once to retrieve relevant local material and generate a streaming reply. With no matching document, chat can use model knowledge without claiming access to personal files or live information.
- Recent completed messages provide bounded follow-up context. The conversation is saved locally; **New chat** deletes it without deleting documents.
- **Settings → Add folder** recursively imports supported files from a chosen directory and all readable subdirectories, with progress, cancellation and a summary. Unchanged repeated imports are skipped; sources are never edited. See [folder import](docs/folder-import.md).
- Import UTF-8 TXT, Markdown or CSV, or a text-bearing PDF individually with **Add file**. Open original PDF pages or inspect exact text/CSV source records from replies.
- Import **OpenStreetMap XML or Overpass JSON** and consult stored feature names, tags and coordinates through chat or the source browser. Attribution and snapshot dates stay visible. [Formats, preparation and limits](docs/osm-import.md).
- Manage local documents and the installed offline model from Settings. Models are separate verified files; the APK does not download or bundle generator weights.

PDFs are limited to 10 MiB and 100 pages, with bounded extracted text. Text/CSV files are limited to 1 MiB. Scans without readable text, encrypted PDFs and invalid files receive explicit errors; no OCR is implemented. Source references enable inspection, not automatic verification of claims.

## Engineering and validation

Start with [AGENTS.md](AGENTS.md), the [handoff](docs/handoff.md), [current state](docs/current-state.md), [chat and document design](docs/chat-beta.md), and [latest validation](docs/validation-0.12.md). The [roadmap](docs/roadmap.md) separates implementation from remaining quality/device work.

The APK now packages **arm64-v8a and x86_64**. ARM64 compilation/linking is preparation for phone testing; all runtime validation still occurs in Outpost35, the x86_64 Android emulator. No physical-device latency, compatibility, peak memory, battery, thermal or GrapheneOS claim follows from the build.

Configure the external SDK/JDK/Gradle paths using [development](docs/development.md), then:

```powershell
pwsh -File scripts/build.ps1 -Offline
pwsh -File scripts/test-osm.ps1
pwsh -File scripts/test-folders.ps1 -SkipInstall
pwsh -File scripts/test-chat.ps1 -SkipInstall
pwsh -File scripts/test-chat.ps1 -SkipInstall -Generate
pwsh -File scripts/test-knowledge.ps1 -SkipInstall
```

The offline flag requires cached dependencies. Read the [emulator runbook](docs/emulator-runbook.md) before any runtime work. Test commands explicitly reject phones; model execution never runs on the host. Tests install synthetic content only for isolated checks and clean up their own inputs.

Publish the local debug artifact with `scripts/publish-artifact.ps1`, then verify it with `-Verify`. See [validation](docs/validation-0.12.md) for the exact filename, hash, checked build and limits. A debug candidate is not a signed production release. Project license, release signing, public distribution and phone trials remain separate work; the existing GitHub remote is private.

[Dependency notices](THIRD_PARTY.md) and lock files identify the pinned runtime, models, PDF library and toolchain. Historical failed results and the original Brújula implementation remain preserved. [Bounty #31](docs/bounty-31.md) is background motivation; no bounty acceptance or public submission is claimed.
