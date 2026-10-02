# Install and prepare Outpost

Outpost is an offline Android chat app. Prepare its model and local documents while you have a connection, then use the saved material without mobile data.

## Install the release APK

1. Open the project's [GitHub releases](https://github.com/nicoarias81/outpost/releases) and download the signed `outpost-<version>-release.apk` and its `.sha256` file. A private repository requires GitHub access. Draft releases and unsigned candidates are not ready for installation.
2. Check the checksum against the release notes or sidecar. Open the APK on Android and permit installation from that file source when Android asks.
3. Open Outpost. Chat is the home screen; use the Settings icon to prepare a model and documents.

Do not uninstall an existing debug build to resolve a signing conflict. Its data is private to that installation. The final production identity and any migration procedure must be confirmed in the release notes.

## Bring a supported model

The APK does not include model weights and does not download them. Download one exact file from its publisher before going offline:

| Model | File size | Pinned publisher download |
|---|---:|---|
| Bonsai 4B | 1,137,806,656 bytes | [Ternary-Bonsai-4B-Q2_0_g64.gguf](https://huggingface.co/prism-ml/Ternary-Bonsai-4B-gguf/resolve/a3eb42bafe873f9686bc97486c43b72ef7d75ec8/Ternary-Bonsai-4B-Q2_0_g64.gguf) |
| Bonsai 1.7B | 490,163,968 bytes | [Ternary-Bonsai-1.7B-Q2_0_g64.gguf](https://huggingface.co/prism-ml/Ternary-Bonsai-1.7B-gguf/resolve/983b5dec2ff16aab79990711ba0f828a499a7e6a/Ternary-Bonsai-1.7B-Q2_0_g64.gguf) |
| Qwen 1.5B | 1,117,320,736 bytes | [qwen2.5-1.5b-instruct-q4_k_m.gguf](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/91cad51170dc346986eccefdc2dd33a9da36ead9/qwen2.5-1.5b-instruct-q4_k_m.gguf) |

Open Settings, choose the corresponding model under model management, then import its GGUF file. Outpost verifies its size and SHA256. A different quantization or newer publisher file will be rejected even if its name looks similar. Exact hashes are recorded in [Bonsai pins](../bonsai-lock.json) and [Qwen pins](../model-lock.json).

Import makes an app-private copy, so leave space for the download and the imported model. Start with a short question after setup. The measured optimization applies to the recorded Pixel 10 Pro/Bonsai 4B configuration; other phones use conservative settings and do not inherit its performance measurements.

## Bring documents and places

- **Add file:** UTF-8 TXT, Markdown, CSV, text-bearing PDF, OSM XML or Overpass JSON.
- **Add folder:** choose a directory in Android's picker. Supported files in readable subfolders are included. The summary lists imported, unchanged, skipped and failed items.
- **Documents:** inspect saved sources and remove individual documents. Chat source buttons open the corresponding text, CSV record, PDF page or OSM feature.

Text/CSV imports are limited to 1 MiB per file; PDF to 10 MiB and 100 pages; OSM extracts to 32 MiB. Scanned or encrypted PDFs, Office files, PBF and map-app offline downloads are not supported. See [folder behavior](folder-import.md) and [OSM preparation](osm-import.md).

OSM supplies place knowledge, such as "Where is Ridge Park?" or "What restaurants are near Cedar Museum?" It does not turn Outpost into a navigation app. The named place and relevant nearby features must be present in the imported extract. There is no automatic current-location or live availability claim.

Imports are snapshots. Reimporting unchanged bytes skips them; changed versions remain separately searchable. Source dates and references help you inspect an answer, but do not guarantee its correctness or that an older manual applies to your equipment.

## Before leaving coverage

Open the imported model and a few representative sources, then try the questions you expect to need. Check both a question with an answer in your files and one whose information is missing. Verify exact identifiers, dates and units against the displayed sources. Make provider-hosted files available locally before import.

Your conversation persists locally. **New chat** removes the conversation; it does not remove documents. Removing a document can make old references unavailable. Uninstalling the app deletes its private models, documents and conversation; application backup is disabled. Privacy information and dependency notices are available offline in Settings → About and privacy.
