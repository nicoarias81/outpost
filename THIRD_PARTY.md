# Third-party dependencies and model provenance

Current app: Outpost 0.12.0. Runtime/model identities remain pinned; PDF extraction adds the dependencies listed below. Model and dependency identities refer to the locked files used by this prototype, not automatically to newer upstream releases. This file preserves dependency notices; it does not select a license for this project's own code.

## llama.cpp

- Upstream: [ggml-org/llama.cpp](https://github.com/ggml-org/llama.cpp).
- Pinned revision: `86ea01d05ec237f89b78b41c8c1ee0f908141ac7`, also in [llama-revision.txt](llama-revision.txt).
- License: MIT. Full notice: [llama-MIT.txt](app/src/main/assets/licenses/llama-MIT.txt), included in APK assets.
- Vendor code is built without source modifications. Own JNI integration is in `app/src/main/cpp/engine.cpp`.
- Own `q2_kernel.c` wraps `ggml_vec_dot_q2_0_q8_0` through the linker. AVX2/F16C selection retains the original reference fallback. This uses the pinned revision's formats and contracts and must be revalidated on upgrade. No external VNNI PR or fork kernel was imported.
- `cpu_caps.c` separates CPUID/XCR0 and HWCAP/HWCAP2 detection from `q2_dispatch.c` policy. VNNI and ARM candidates currently have requirement descriptors, not new optimized implementations.
- `q2_batch.c` also wraps `ggml_compute_forward_mul_mat_tiled` for supported Q2 g64 operations. It uses GGML activation quantization and barriers with an own grouped-token kernel. Context reuse and saved-logit sampling use llama.cpp memory and sampler APIs.
- Since 0.7, own `speculation.cpp` proposes same-request token continuations and controls their cost. JNI verifies them using decode, logits, sampler, and memory APIs. No DSpark/EAGLE implementation or auxiliary speculative weights were imported.
- CPU/x86_64 and ARM64 are packaged; runtime validation remains x86_64 emulator-only. HTTP server, tools, OpenSSL, and web interfaces are not built.

[Strata](https://github.com/Niko1221/Strata) was a conceptual reference. Its engine, ActQ layout, and kernel code were not imported. The custom wrappers still depend on backend internals; this is not a stable upstream extension ABI.

## Qwen2.5 1.5B Instruct

- Publisher: Qwen / Alibaba Cloud; [official GGUF repository](https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF).
- Revision: `91cad51170dc346986eccefdc2dd33a9da36ead9`.
- File: `qwen2.5-1.5b-instruct-q4_k_m.gguf`.
- Size: 1,117,320,736 bytes.
- SHA-256: `6a1a2eb6d15622bf3c96857206351ba97e1af16c30d7a74ee38970e434e9407e`.
- Publisher-declared license for this model: Apache-2.0. The model is downloaded from its publisher, kept separate from the APK, and used without modification as a functional baseline.

The initial comparison also used Qwen2.5 0.5B Instruct Q4_K_M. Its identity is preserved in [the historical lock](evidence/0.7-before-outpost/model-0.5b-lock.json), with [baseline](evidence/0.7-before-outpost/generation-baseline.json) and [0.5B outputs](evidence/0.7-before-outpost/generation-0.5b.json). It is not one of the current selectable generator profiles.

## Ternary Bonsai

- Publisher: PrismML; [1.7B GGUF](https://huggingface.co/prism-ml/Ternary-Bonsai-1.7B-gguf) and [4B GGUF](https://huggingface.co/prism-ml/Ternary-Bonsai-4B-gguf).
- Publisher-declared license: Apache-2.0.
- [bonsai-lock.json](bonsai-lock.json) pins exact revisions, filenames, byte counts, and SHA-256 values.
- These files use Q2_0 g64, GGML tensor type 42, groups of 64 weights with an FP16 scale: 2.25 effective bits per matrix weight. Normalization tensors remain F32; file sizes include vocabulary and metadata.
- Matrix values are scaled ternary values. The three-state entropy of approximately 1.58 bits is not this file's physical storage cost. Older Q2_0 g128 blocks use a different layout and are not interchangeable.
- Architecture: `qwen3`. Both locked templates end the assistant prefix with a closed empty `think` block. The integration reproduces that suffix because the basic ChatML formatter does not execute that Jinja-specific part. Generated output is retained without removing generated reasoning text after the fact.
- Bonsai sampling uses top-k 20, top-p 0.8, temperature 0.7, and seed 42. The earlier parameter choice was informed by the [Qwen3 non-thinking guidance](https://huggingface.co/Qwen/Qwen3-4B); it is not a claim that these are optimal Bonsai parameters.
- Weights are downloaded from the publisher, verified, and excluded from the APK. The same pinned llama.cpp backend is used; no Prism fork replaced it.

## Kev classifier and GGUF conversion

- Original model: [jaredpalmer/kev-0.8b](https://huggingface.co/jaredpalmer/kev-0.8b); [project source](https://github.com/jaredpalmer/kev).
- Conversion: [DreamBlooms/kev-0.8b-GGUF](https://huggingface.co/DreamBlooms/kev-0.8b-GGUF), revision `4f3367e227569b4be717a0ca4aa30739efcaa575`.
- [judge-lock.json](judge-lock.json) pins the GGUF, FP32 head, and configuration sizes and SHA-256 values.
- Port used as reference/adapted: [DreamBlooms/dohnuts.cpp](https://github.com/DreamBlooms/dohnuts.cpp), revision `63374ff55a66c50b266adfef422e1fc4b0ee5717`, specifically `src/side/kev.cpp` and `src/side/runner.cpp`.
- Model/head and port license: Apache-2.0; the port notice is retained in [APK assets](app/src/main/assets/licenses/dohnuts-Apache-2.0.txt).
- The JNI adaptation handles one textual question per call, with no HTTP server, remote tools, or vision. The head/config assets are redistributed without modification; GGUF weights are obtained from the publisher and remain separate.
- Published temperature: `2.406050072164233`; pointer-head dimension 256; hidden dimension 1024. Scores have not been calibrated for this application or its Spanish tasks.
- Kev is a community project inspired by Jev. It is not TypeSafe AI's Jev and does not call that service.

## Android build dependencies

Gradle 8.11.1 is pinned through the wrapper and its SHA-256. AGP is 8.9.2; SDK platform 35; NDK r28b / 28.1.13356709; CMake 3.22.1. Official Google NDK/CMake archive identities and SHA-1 checksums are in [toolchain-lock.json](toolchain-lock.json). Model files use SHA-256; do not describe every lock as using the same hash algorithm.

The APK includes the NDK's `libc++_shared.so`. Distribution notices are retained in [android-NDK-NOTICE.txt](app/src/main/assets/licenses/android-NDK-NOTICE.txt) and [android-toolchain-NOTICE.txt](app/src/main/assets/licenses/android-toolchain-NOTICE.txt). Java compatibility uses `com.android.tools:desugar_jdk_libs:2.1.5`.

Downloaded native tooling is kept in ignored `.local/`. Preparation and compilation run on the host; runtime model and numerical validation run inside Android in the emulator.

## Demonstration content and future distribution

[The seed library](app/src/main/assets/library.json) contains original short demonstration summaries with source references and incorporation dates. It does not bundle full web pages. Imported documents are labeled unverified, and their rights remain with their respective owners.

Before public distribution, choose the project code license, review all redistributed assets and notices, and define release/evidence storage. Future ZIM, geographic, PDF, or OCR dependencies and datasets need their own recorded versions and attribution. No current dependency list implies those integrations are already installed.

## Knowledge-pack examples in 0.9

Files under `examples/knowledge` are synthetic project demonstration material. Pack parsing and CSV support add no third-party parser library. Imported `source` and `license` fields are declared metadata, not verified provenance or a redistribution grant. Keep each actual source's attribution and rights when preparing packs; no encyclopedia/map/Office adapter dependency was added.

## PDF extraction in 0.10

- [PdfBox-Android](https://github.com/TomRoush/PdfBox-Android), `com.tom-roush:pdfbox-android:2.0.27.0`, pinned from Maven Central. Apache-2.0; upstream tag `v2.0.27.0` LICENSE and NOTICE are included in assets/licenses.
- Its pinned transitive BouncyCastle artifacts are `bcprov-jdk15to18`, `bcpkix-jdk15to18` and `bcutil-jdk15to18`, version1.72. The official `r1rv72` license is included in assets/licenses.
- [pdfbox-lock.json](pdfbox-lock.json) records resolved AAR/JAR/POM sizes/hashes and notice-source hashes. These are pinned resolved inputs, not a claim of independent security certification or the latest upstream versions.
- No optional JPEG2000 library, OCR engine, network service or cloud parser is added. Android PdfRenderer displays stored original pages. Original files stay in app-private storage.


## OpenStreetMap data in 0.12

The application uses Android's XML pull parser and JSON reader; no map SDK or additional parser dependency was added. Users supply bounded OSM XML/Overpass JSON extracts. Source/evidence UI carries OpenStreetMap contributor attribution and the [OSM copyright/license link](https://www.openstreetmap.org/copyright). The product APK ships no real map dataset. Instrumented examples are synthetic and remain outside production assets. Data licensing does not select a license for Outpost's code; redistribution decisions must preserve the actual source's rights and attribution.
