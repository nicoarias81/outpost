# Outpost

**Offline research and information on your Android phone.**

Outpost uses a language model on your phone. The model uses your documents and stored place data to give answers. Outpost is for travel, field work, farming and other tasks without a network connection.

The product goal includes questions about differences, causes and information from more than one source. Tests of this wider research quality are not complete.

## Functions

| Function | Description |
|---|---|
| Local chat | The model gives answers on the phone. The app keeps the conversation on the phone. |
| Document search | The app reads TXT, Markdown, CSV and PDF files with text. References identify passages, CSV records or PDF pages. |
| Folder import | The app reads files in a selected folder and its subfolders. The import report shows unchanged files and errors. |
| Place search | The app uses imported OpenStreetMap XML or Overpass JSON. It can find named places and places near a stated location. |
| Offline preparation | The user installs a model and imports the necessary data before network access stops. The initial document library is empty. |

These are example questions:

- Which spare filter does this pump use?
- Which restaurants are near this museum?
- Why can GPS operate without mobile data?
- What is different between the inspection instructions in these two manuals?
- What arrival plan can I make from my saved travel instructions and transport notes?

Chat is the first screen. Settings contains model selection, document import, privacy information and third-party notices.

After preparation, Google Play Services, an account and an API key are not necessary for the app. It has no network permission, telemetry, web search or remote inference. Network access can be necessary for an external file provider during preparation. The app must import the necessary files before offline use.

Outpost operates with or without a network connection. Offline tests enable airplane mode after installation, model preparation and data preparation.

## Software and models

| Part | Software |
|---|---|
| Android app | Java, Android SDK, Storage Access Framework and Android user interface |
| Local data | SQLite, FTS4 search and references to saved sources |
| PDF text | PdfBox-Android |
| PDF page display | Android PdfRenderer |
| Model inference | JNI and a fixed version of llama.cpp |
| Native calculations | C/C++ kernels, CPU detection and an original reference path |
| System types | ARM64 and x86_64 Android; minimum Android 9 / API 28 |

The app accepts the three model files below. The APK does not contain the model weights. Model setup offers **Download from Hugging Face** and **Load local file**. Downloads open in the browser. During import, the app compares the file size and SHA256 with the specified values.

| Model | File encoding | Download size | Specified file |
|---|---|---:|---|
| Bonsai 4B | Ternary Q2_0 g64 | 1.14 GB | [GGUF](https://huggingface.co/prism-ml/Ternary-Bonsai-4B-gguf/resolve/a3eb42bafe873f9686bc97486c43b72ef7d75ec8/Ternary-Bonsai-4B-Q2_0_g64.gguf) |
| Bonsai 1.7B | Ternary Q2_0 g64 | 490 MB | [GGUF](https://huggingface.co/prism-ml/Ternary-Bonsai-1.7B-gguf/resolve/983b5dec2ff16aab79990711ba0f828a499a7e6a/Ternary-Bonsai-1.7B-Q2_0_g64.gguf) |
| Qwen2.5 1.5B Instruct | Q4_K_M | 1.12 GB | [GGUF](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/91cad51170dc346986eccefdc2dd33a9da36ead9/qwen2.5-1.5b-instruct-q4_k_m.gguf) |

[bonsai-lock.json](bonsai-lock.json), [model-lock.json](model-lock.json) and [llama-revision.txt](llama-revision.txt) identify the necessary files and versions. A file with a different quantization is not an alternative to a specified file.

## Performance changes

Prefill processes the input prompt. Decode generates the answer tokens. Outpost uses different changes for these two operations.

| Change | Function |
|---|---|
| ARM NEON DotProd kernels | Faster Q2 calculations, including single-token decode, without larger stored weights |
| ARM I8MM matrix kernels | Faster calculations for eligible prompt matrices, with the original accumulation order |
| Groups of columns and output rows | Reuse of weights and activation data |
| Persistent thread pools and row scheduling | Less worker setup and distribution of prefill work |
| Six workers and four attention partitions | Faster Pixel decode with the specified attention calculations |
| KV cache and saved prefix logits | Reuse of compatible prompt state |
| Structured place retrieval | Local filtering and distance calculations supply facts for model-generated answers |

The runtime does three separate checks: CPU support, compiled kernel availability and enabled policy. The faster Pixel profile applies only to the specified device, OS and model. Other profiles use conservative settings and a reference path. Production settings do not use VNNI, speculative decoding, MTP or Engram.

Tests on the Pixel 10 Pro used Bonsai 4B. The median paired reductions in total answer time were approximately **20–22%**. All compared logits and output tokens were identical. The table gives the median time for each policy.

| Question | Original policy | Faster policy |
|---|---:|---:|
| Late arrival | 32.4 s | 25.7 s |
| Field manual | 40.0 s | 32.4 s |
| GPS and offline maps | 34.5 s | 27.0 s |

The [paired results](evidence/research/i8mm-20261002/combined-summary.json) and [numeric results](evidence/research/i8mm-20261002/numeric-018-summary.json) identify the test conditions. The numeric checks include more than 65 million exact floating-point comparisons. Process CPU time increased approximately 3–4%. The tests did not measure battery consumption. These results do not show the same improvement on other phones or a future signed release.

## Product limits and goals

The deployment goal includes Android and GrapheneOS-compatible hardware. The device must have **no more than 12 GB of RAM**. The complete offline installation must use **no more than 50 GB of storage**. This total includes the app, models, documents, indexes, databases and other files.

These are acceptance goals. They are not measured guarantees or enforced library limits. The physical tests used a 16 GB Pixel 10 Pro. Those tests do not show operation in a 12 GB environment. GrapheneOS tests, complete storage measurements and wider research-quality tests are not complete. The [test guide](docs/testing.md) gives the necessary acceptance conditions.

## Installation and first use

The distribution channel is [GitHub Releases](https://github.com/nicoarias81/outpost/releases). The current source candidate is **0.19.0-rc3**, version code 23. Device validation is in progress. The last published candidate is `v0.19.0-rc2`. The repository and [QA prerelease](https://github.com/nicoarias81/outpost/releases/tag/v0.19.0-rc2) are public.

The candidate release contains `outpost-0.19.0-rc2-qa.apk`, its checksums, license files and matching source archive. This is the exact non-debuggable QA APK shown in the recorded Pixel demo. Its application ID is `dev.outpost.app.releaseqa`. It uses a development certificate. Production identity, signing and final signed-APK tests are not complete.

For the published RC2 QA candidate, do these steps:

1. Download [outpost-0.19.0-rc2-qa.apk](https://github.com/nicoarias81/outpost/releases/download/v0.19.0-rc2/outpost-0.19.0-rc2-qa.apk).
2. Download [SHA256SUMS.txt](https://github.com/nicoarias81/outpost/releases/download/v0.19.0-rc2/SHA256SUMS.txt) from the same release.
3. Compare the APK SHA256 with the checksum file.
4. Install the APK only if the values agree.
5. Download one model file from the table above.
6. Open Settings in Outpost.
7. Select **Manage model**.
8. Select the model that agrees with the downloaded file.
9. Select **Manage model** again.
10. Select **Import model file**.
11. Select **Choose file**.
12. Select the downloaded GGUF file.
13. Use **Add file** or **Add folder** to import the necessary documents.
14. Open an imported source.
15. Open chat.
16. Enter a representative question before offline use.
17. Select **Send message** (the upward arrow).
18. Do a source check of the answer.

RC3 model setup keeps model selection and both preparation routes in one dialog. Select **Download from Hugging Face** for the specified file. After download, select **Load local file**. You can also copy the download link. An alternative server must provide the exact specified file.

Model import makes an app-private copy. Free storage must be sufficient for the download and that copy. The app makes search indexes locally. A separate server or index download is not necessary.

Each import is a snapshot. Unchanged files do not make another copy. Changed files make new snapshots. The older snapshots are still searchable.

**New chat** removes the conversation but keeps documents. App removal also removes private data. Application backup is disabled.

| Input | Limit |
|---|---|
| TXT or CSV | 1 MiB per file |
| PDF | 10 MiB and 100 pages |
| OSM extract | 32 MiB |

OCR in another tool is necessary for scanned PDFs. The app does not accept Office files, OSM PBF, Wikipedia/ZIM or map-app downloads. OSM gives stored place information, not maps, navigation or the current phone position. Answers and references can be incorrect. A source check is necessary for exact values, dates and equipment applicability.

## Technical documents

- [Architecture](docs/architecture.md): components, data flow, storage and technical terms.
- [Build and release](docs/build.md): tools, configuration, compilation and signing.
- [Testing](docs/testing.md): devices, test procedures, evidence and acceptance conditions.

The [documentation rules](AGENTS.md#documentation-rules) use ASD-STE100 Issue 9 as the writing reference. [Third-party notices](THIRD_PARTY.md) identify dependencies and model provenance. Outpost's original code uses AGPL-3.0-only.

## License

Outpost's original code uses the [GNU Affero General Public License, version 3 only](LICENSE), identified as `AGPL-3.0-only`. Commercial use is permitted.

If you distribute covered binaries, provide the corresponding source as the license requires. Modified versions offered over a network must offer their corresponding source to remote users. Private use does not require public release of your changes.

Third-party code, model weights and datasets keep their own licenses. See [NOTICE](NOTICE) and [THIRD_PARTY.md](THIRD_PARTY.md). The project license does not change the rights in imported user documents.
