# Glossary

| Term | Meaning in this project |
|---|---|
| ABI | Native binary interface/architecture, x86_64 and arm64-v8a are packaged; runtime validation is x86_64 emulator-only |
| Adapter | Source-specific parser/storage/retrieval boundary; text/CSV/PDF/OSM exist within one app module, with broader adapters proposed |
| AVD | Android Virtual Device; its disk contains installed app data and is not source code |
| Cold request | A request without the relevant loaded model or reusable prompt context; specify which state is cold |
| Decode | Autoregressive output generation after the prompt has been evaluated |
| Evidence | Inspectable source with implemented document revision/hash and passage/row/page/element locator; not a correctness score |
| FTS4 | SQLite full-text search used for the current normalized lexical index |
| GGML | Tensor computation implementation underlying the pinned llama.cpp backend |
| GGUF | Model file container; a shared extension does not imply compatible tensor layout, template, or architecture |
| JNI | Java Native Interface connecting Android Java code to C/C++ in the same process |
| Kernel | Low-level numerical operation implementation; a faster kernel may not dominate whole-answer latency |
| KV cache | Attention keys/values retained for processed tokens; not a database of source facts |
| Locator | Exact local document/revision/fragment/ordinal/content-hash reference; hash semantics depend on format, especially PDF versus OSM |
| SAF | Android Storage Access Framework; grants access to a selected file/tree without broad storage permission |
| Snapshot | Private imported file version; changed bytes create another searchable document, without sync or newest-version selection |
| Knowledge pack v1 | Developer JSON text/CSV package with atomic active-version selection and retained old locators; distinct from a folder import |
| Chat context | Two bounded recent completed/limited turns plus current sources; distinct from persisted full conversation and KV cache |
| MTP | Multi-token prediction using appropriate trained model components; neither prompt batching nor an unrelated smaller model creates MTP |
| OSM | OpenStreetMap data; entities, rendered tiles, elevation, and route graphs are distinct resources |
| Prefill | Evaluation of prompt/source tokens before output decoding |
| PSS | Proportional Set Size, an Android process-memory measurement; a post-run sample is not peak memory |
| Q2_0 g64 | The locked Bonsai matrix representation: 64 two-bit values plus an FP16 scale per block |
| RAG | Retrieval-augmented generation: supply retrieved evidence to a model; retrieval does not guarantee support or truth |
| Speculation | Propose tokens cheaply, then let the target model verify them; accepted text can still be factually wrong |
| Target | The model whose sampled output determines accepted tokens, Bonsai 4B in the speculation experiment |
| VNNI | A family of x86 vector neural-network integer instructions; AVX-VNNI and AVX-512 VNNI have distinct requirements |
| Warm request | A request benefiting from loaded weights and/or compatible prompt computation; report actual reused tokens |
| ZIM | Compressed offline-content archive format; reading/search capability depends on the archive and integration |

Storage numbers labeled GB/MB are decimal; GiB/MiB/KiB are binary. Raw byte counts and raw milliseconds/microseconds in evidence take precedence over rounded summaries.
