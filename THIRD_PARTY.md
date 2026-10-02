# Third-party dependencies and model provenance

Outpost's original code uses [AGPL-3.0-only](LICENSE). This inventory preserves third-party attribution and points to the authoritative pins/notices. Third-party terms remain in force. Models are obtained separately from their publishers and are not bundled in the product APK.

| Component | Version / identity | License and notice |
|---|---|---|
| [llama.cpp](https://github.com/ggml-org/llama.cpp) | Unmodified revision in [llama-revision.txt](llama-revision.txt) | MIT; [bundled notice](app/src/main/assets/licenses/llama-MIT.txt) |
| [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android) | 2.0.27.0; [resolved pins](pdfbox-lock.json) | Apache-2.0; bundled [LICENSE](app/src/main/assets/licenses/pdfbox-android-LICENSE.txt) and [NOTICE](app/src/main/assets/licenses/pdfbox-android-NOTICE.txt) |
| BouncyCastle `bcprov`, `bcpkix`, `bcutil` | `jdk15to18` 1.72; transitive PDF dependencies | [Bundled license](app/src/main/assets/licenses/bouncycastle-LICENSE.html) |
| Android NDK / libc++ and build tools | NDK r28b, CMake 3.22.1; [toolchain pins](toolchain-lock.json) | Bundled [NDK notices](app/src/main/assets/licenses/android-NDK-NOTICE.txt) and [toolchain notices](app/src/main/assets/licenses/android-toolchain-NOTICE.txt) |
| [Qwen2.5 1.5B Instruct GGUF](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF) | Q4_K_M; [model-lock.json](model-lock.json) | Publisher-declared Apache-2.0; separate download |
| PrismML [Bonsai 1.7B](https://huggingface.co/prism-ml/Ternary-Bonsai-1.7B-gguf) / [4B](https://huggingface.co/prism-ml/Ternary-Bonsai-4B-gguf) | Q2_0 g64; [bonsai-lock.json](bonsai-lock.json) | Publisher-declared Apache-2.0; separate downloads |

Outpost's JNI integration, dispatch, Q2 kernels and attention wrapper are project extensions around the pinned backend. Strata and DeepGEMM-Ascend were conceptual references; their kernels or runtime dependencies were not imported. No external VNNI kernel was imported. Java compatibility also uses `com.android.tools:desugar_jdk_libs:2.1.5`. Current build configuration is in [app/build.gradle](app/build.gradle).

## Research-only material

- [Spark-X2.5-1.7B](https://huggingface.co/XHToken/Spark-X2.5-1.7B-GGUF): publisher-declared Apache-2.0. The [test lock](app/src/androidTest/assets/candidates/spark17-lock.json) identifies exact weights; they remain outside Git/APKs and outside the product model selector.
- [Kev](https://huggingface.co/jaredpalmer/kev-0.8b), converted by [DreamBlooms](https://huggingface.co/DreamBlooms/kev-0.8b-GGUF): model/head pins are in [judge-lock.json](judge-lock.json). Model/head license is Apache-2.0. It is an experimental classifier, not TypeSafe AI's Jev or an answer-correctness authority.
- Kev token layout/readout adapts `src/side/kev.cpp` and `src/side/runner.cpp` from [dohnuts.cpp](https://github.com/DreamBlooms/dohnuts.cpp) revision `63374ff55a66c50b266adfef422e1fc4b0ee5717`. Preserve its [Apache-2.0 notice](app/src/main/assets/licenses/dohnuts-Apache-2.0.txt). The unchanged head/config live in debug-only assets; release excludes them. GGUF weights remain a separate download.

The video comparison also uses [TinyLlama 1.1B Chat v1.0](https://huggingface.co/TinyLlama/TinyLlama-1.1B-Chat-v1.0) with a [TheBloke GGUF conversion](https://huggingface.co/TheBloke/TinyLlama-1.1B-Chat-v1.0-GGUF). The model card declares Apache-2.0. The [demo fixture](app/src/releaseTest/assets/demo/cases.json) pins its revision, size and SHA256. It is a test-only baseline, not a product model choice; weights remain outside Git and the APK.

## Imported and test content

Imported documents remain subject to their source's rights. User-supplied source/license metadata is not verified provenance or permission to redistribute it. Synthetic examples and test fixtures are separate from the empty product library.

OSM sources retain **© OpenStreetMap contributors** and the [ODbL/copyright link](https://www.openstreetmap.org/copyright). The APK contains no real geographic dataset and uses no map SDK. A bounded Madrid extract exists only in the test APK; its [provenance](evidence/research/osm-places-20260930/source.json) records preparation and attribution. Keep that provenance and the source's rights when redistributing actual data.

Packaged notices are available offline through Settings → About and privacy → Third-party notices. Preserve them when changing build variants or preparing a release.
