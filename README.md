# Outpost

**Offline research and everyday knowledge on your Android phone.** Outpost combines an on-device language model with your saved documents and imported place data. Its aim is useful lookup, explanation, comparison and synthesis when internet access is unavailable: during travel, field work, farming, hiking or a drive through areas without coverage.

## What it does

- **Research locally.** Ask for an explanation, compare information from saved sources or reason through a practical question. Keep the conversation on your device and inspect the supporting material.
- **Find information in your files.** Import TXT, Markdown, CSV and text-bearing PDFs. Answers can reference the original passage, CSV record or PDF page.
- **Import a whole folder.** Outpost traverses readable subfolders, skips unchanged files and reports unsupported or failed imports.
- **Answer questions about places.** Import OpenStreetMap XML or Overpass JSON to ask where a named park is, which restaurants are near a museum, or what places match a category around a landmark.
- **Prepare before going offline.** Load a model and the documents or regional extracts you expect to need. The initial library is empty; there is no bundled demo knowledge base.

Example questions range from “Which spare filter does this pump use?” and “What restaurants are near this museum?” to “Explain why GPS can work without mobile data”, “Compare the inspection requirements in these two manuals” and “Combine my arrival instructions and saved transport notes into a plan”. These illustrate the intended use; broad reasoning and synthesis quality still need evaluation.

Chat is the home screen. Settings contains model setup, file/folder import, document management, privacy information and dependency notices. Once the model and required data are prepared, core use needs no connection, Google Play Services, account or API key. The app has no network permission, telemetry, web-search calls or remote inference. External file providers may require connectivity during preparation; their bytes must be imported before going offline.

## What it uses

| Layer | Implementation |
|---|---|
| Android application | Java, Android SDK, Storage Access Framework and native UI |
| Local knowledge | SQLite with FTS4, immutable source references and structured OSM queries |
| PDF handling | PdfBox-Android for text extraction; Android PdfRenderer for original pages |
| Inference | JNI and a pinned, unmodified llama.cpp CPU backend |
| Native acceleration | Project-owned C/C++ kernels with runtime CPU detection and reference fallback |
| Supported architectures | ARM64 phones and x86_64 Android; minimum Android 9 / API 28 |

Three exact model files are supported. Weights are downloaded separately and verified by size and SHA256 during import.

| Model | Encoding | Download size | Pinned download |
|---|---|---:|---|
| Bonsai 4B | Ternary Q2_0 g64 | 1.14 GB | [GGUF](https://huggingface.co/prism-ml/Ternary-Bonsai-4B-gguf/resolve/a3eb42bafe873f9686bc97486c43b72ef7d75ec8/Ternary-Bonsai-4B-Q2_0_g64.gguf) |
| Bonsai 1.7B | Ternary Q2_0 g64 | 490 MB | [GGUF](https://huggingface.co/prism-ml/Ternary-Bonsai-1.7B-gguf/resolve/983b5dec2ff16aab79990711ba0f828a499a7e6a/Ternary-Bonsai-1.7B-Q2_0_g64.gguf) |
| Qwen2.5 1.5B Instruct | Q4_K_M | 1.12 GB | [GGUF](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/91cad51170dc346986eccefdc2dd33a9da36ead9/qwen2.5-1.5b-instruct-q4_k_m.gguf) |

Exact identities are in [bonsai-lock.json](bonsai-lock.json), [model-lock.json](model-lock.json) and [llama-revision.txt](llama-revision.txt). Models with a similar name or another quantization are not interchangeable.

## Optimizations

Outpost optimizes prompt processing (**prefill**) and token generation (**decode**) separately, while preserving the model's arithmetic and outputs in the admitted comparisons.

| Optimization | Purpose |
|---|---|
| ARM NEON DotProd Q2 kernels | Accelerate supported ternary operations, including single-token decode, without expanding stored weights |
| ARM I8MM matrix kernels | Process eligible prompt matrices with INT8 matrix instructions while preserving the original accumulation order |
| Grouped columns and output rows | Reuse weights and activation work across compatible operations |
| Persistent thread pools and row scheduling | Reduce worker setup overhead and distribute prefill work |
| Six workers with four logical attention partitions | Improve Pixel decode throughput while retaining the validated attention arithmetic |
| KV/prefix cache and saved prefix logits | Reuse compatible prompt state across requests instead of rebuilding it unnecessarily |
| Deterministic place queries | Resolve supported OSM questions directly from stored data without invoking the model |

Dispatch distinguishes CPU support, compiled kernel availability and enabled policy. The optimized Pixel preset is gated by the exact device/OS/model profile; other profiles retain conservative settings and a reference fallback. VNNI, speculative decoding/MTP and Engram are not enabled production optimizations.

On the tested Pixel 10 Pro with Bonsai 4B, median paired reductions in total response time were **about 20–22%**, with identical compared logits and generated tokens. The table shows the median times for each policy:

| Test prompt | Original median | Optimized median |
|---|---:|---:|
| Late arrival | 32.4 s | 25.7 s |
| Field manual | 40.0 s | 32.4 s |
| GPS and offline maps | 34.5 s | 27.0 s |

The [paired measurements](evidence/research/i8mm-20261002/combined-summary.json) and [numeric checks](evidence/research/i8mm-20261002/numeric-018-summary.json) retain the evidence. The kernel checks include over 65 million exact float comparisons. Process CPU time increased about 3–4%; battery consumption was not measured. These results do not establish the same speedup on other phones or on a future signed release.

## Deployment goals

The target is a useful offline research app on Android and GrapheneOS-compatible hardware, in an environment with **at most 12 GB of RAM** and **50 GB for the complete installed offline setup**: app, weights, documents, indexes, databases and other assets. These are release acceptance goals, not measured resource guarantees or enforced library limits.

Current execution evidence covers an AOSP emulator and a real Pixel 10 Pro. The physical tests used a 16 GB phone; they do not establish the 12 GB environment target. GrapheneOS runtime, full installed storage accounting and broader explanation/comparison/synthesis quality remain open. The [testing guide](docs/testing.md) defines the checks needed before a public release.

## Getting started

Distribution is through [GitHub Releases](https://github.com/nicoarias81/outpost/releases). **The current source is 0.19.0-rc1; the release remains a draft pending production identity, signing and final signed-APK validation.** The repository is currently private.

Once a signed APK is available:

1. Download the APK and check its SHA256 against the release checksum before installing it.
2. Download one of the pinned model files above. In Settings, select that model and use **Import model** to import its GGUF. Allow storage for both the downloaded file and the app-private copy.
3. Use **Add file** or **Add folder** to import your local knowledge. Search indexes are built locally; no separate database download, server setup or remote index is required. Open a source and try a representative question before leaving coverage.

Imports are snapshots: unchanged files are skipped; changed versions remain separately searchable. **New chat** removes the conversation without deleting documents. Uninstalling the app removes its private data; application backup is disabled.

TXT/CSV files are limited to 1 MiB; PDFs to 10 MiB and 100 pages; OSM extracts to 32 MiB. Scanned PDFs need OCR elsewhere. Office files, OSM PBF, Wikipedia/ZIM and map-app offline downloads are not currently supported. OSM supplies place knowledge, not maps or navigation, and does not provide the phone's current location. Answers and citations still require checking against the relevant source, especially for dates, exact values and equipment applicability.

## Technical documentation

- [Architecture](docs/architecture.md): components, data flow, storage and inference invariants.
- [Build and release](docs/build.md): toolchain, configuration, compilation, artifacts and signing.
- [Testing](docs/testing.md): emulator/Pixel operation, focused checks, evidence and remaining release gates.

[Third-party notices](THIRD_PARTY.md) cover dependencies and model provenance. A license for Outpost's own code has not yet been selected.
