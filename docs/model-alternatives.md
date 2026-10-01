# Mobile model alternatives — reviewed 2026-09-30, updated 2026-10-01

**Research status, not a new runtime release.** Outpost remains 0.14.0 at source checkpoint `511d6af`, with its three existing generator pins. Bonsai is a measured baseline, not a permanent architectural requirement. This review inspected public model cards/configurations, publisher artifact metadata and the pinned local backend. The September 30 survey downloaded metadata only. The subsequent [October 1 Spark admission/comparison](spark-candidate-results-2026-10-01.md) verified and executed Spark 1.7B in the emulator; other new candidates and phone execution remain untested. The [metadata snapshot](../evidence/research/model-survey-20260930/survey-metadata.json) records revisions, source hashes, artifact sizes and failed discovery requests; the [candidate manifest](../evidence/research/model-survey-20260930/candidates.json) is research input, not an import allowlist.

## Recommended comparison order

Start with **LFM2.5-1.2B-Instruct QAD Q4_0**, **Qwen3.5-2B Q4_K_M** and **Spark-X2.5-1.7B Q4_K_M**, against the current Bonsai 4B and Qwen2.5 1.5B. They test compact evidence-driven generation, general synthesis and an additional sliding/full-attention architecture at comparable storage cost. The [October 1 Spark review](spark-x25-storage-review.md) adds the third candidate and identifies its formatting gate; the September metadata snapshot remains unchanged. Add **LFM2.5-2.6B QAD Q4_0** if the smaller LFM loses too much quality; add **Gemma 4 E2B QAT** as a separate higher-memory candidate. Select by completed practical tasks, source fidelity, latency and peak memory together. There is no measured winner yet.

All sizes below are **decimal GB of the named GGUF file**, not RAM. New-model sizes/SHA-256 values come from the publisher's HF LFS metadata and have not been verified by downloading full files. Existing Bonsai/Qwen2.5 values come from Outpost's verified locks. Unsloth publishes the listed Qwen conversions; Qwen publishes the underlying checkpoints. Conversion provenance still needs checking before admission.

| Candidate | File size | Why evaluate it | Main integration/resource issue | Priority |
|---|---:|---|---|---|
| Bonsai 4B Q2_0 g64 | 1.138 GB | Current optimized baseline; keep its measured profile | Its Q2 kernel advantage is specific to this format/workload | Control |
| Bonsai 1.7B Q2_0 g64 | 0.490 GB | Existing low-storage control | Practical answer quality still needs comparison | Control |
| Qwen2.5 1.5B Q4_K_M | 1.117 GB | Existing conventional-quantization control | Older model, different sampler | Control |
| LFM2.5-1.2B-Instruct QAD Q4_0 | 0.696 GB | Text-only, compact, evidence-driven assistant candidate | Hybrid convolution/attention state, template and sampler; LFM license | First |
| Qwen3.5-2B Q4_K_M | 1.281 GB | General knowledge and supplied-document synthesis candidate | Recurrent/attention hybrid; template/no-thinking and cache validation | First |
| Spark-X2.5-1.7B Q4_K_M | 1.107 GB | Official compact model with sliding/full attention | Explicit Spark formatting/no-thinking adapter and sliding-cache checks | Research-admitted October 1; mixed measured quality, no product promotion |
| Spark-X2.5-4B Q4_K_M | 2.600 GB | Larger sibling for a quality follow-up | Same formatting gate, greater weight bandwidth; no measured advantage | Conditional |
| LFM2.5-2.6B QAD Q4_0 | 1.594 GB | More capacity while retaining the hybrid design | Larger than 1.2B; not identical to older LFM2-2.6B | Second |
| Gemma 4 E2B QAT Q4_0 | 3.350 GB | Mobile-oriented architecture; trained assistant available | Effective size excludes large lookup embeddings; new formatting and cache behavior | Second, memory lane |
| Qwen3-4B-Instruct-2507 Q4_K_M | 2.497 GB | Same broad dense Qwen3 family as Bonsai; non-thinking control | More storage/bandwidth; this is not a quantization-only ablation of Bonsai | Diagnostic |
| Qwen3.5-4B Q4_K_M | 2.741 GB | Larger sibling if 2B quality is inadequate | More memory; no reason to assume faster than Bonsai | Conditional |
| Gemma 4 E4B QAT Q4_0 | 5.155 GB | Higher-capacity follow-up for a roomy device | Weight footprint alone makes it a separate device tier | Defer initial comparison |
| BitNet b1.58 2B 4T, official I2_S GGUF | 1.188 GB | Native low-bit alternative worth tracking | I2_S is not Outpost's Q2_0; dedicated backend/format work | Separate research |
| LFM2.5-350M QAD Q4_0 | 0.219 GB | Bounded intent/entity extraction or query reformulation | Not a demonstrated replacement for general offline knowledge | Separate task-model lane |

Primary cards: [LFM 1.2B](https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct), [LFM 2.6B](https://huggingface.co/LiquidAI/LFM2.5-2.6B), [LFM 350M](https://huggingface.co/LiquidAI/LFM2.5-350M), [Qwen3.5 2B](https://huggingface.co/Qwen/Qwen3.5-2B), [Qwen3.5 4B](https://huggingface.co/Qwen/Qwen3.5-4B), [Qwen3 2507](https://huggingface.co/Qwen/Qwen3-4B-Instruct-2507), [Gemma 4](https://ai.google.dev/gemma/docs/core/model_card_4), [BitNet](https://huggingface.co/microsoft/bitnet-b1.58-2B-4T). Exact revision-bound file links are in the manifest.

Also inspected **SmolLM3-3B** (Apache 2.0, explicitly disable its default thinking for this experiment) and **Phi-4-mini-instruct** (3.8B, MIT, text-only). Both have architectural support in our backend and merit reserve status if the first group fails. Neither has an exact admitted quantized artifact in this study. The unsuccessful guessed official GGUF endpoints returned HTTP 401; that does not establish that every conversion is absent or access-gated. [SmolLM3 card](https://huggingface.co/HuggingFaceTB/SmolLM3-3B), [Phi card](https://huggingface.co/microsoft/Phi-4-mini-instruct).

## What changes the initial preference

Liquid's August **QAD** release is more relevant than simply quantizing another model to fewer bits. These are distinct trained/distilled checkpoints using the ordinary Q4_0 storage format. Compare 1.2B QAD with its official Q4_K_M (0.731 GB) and, if quantization attribution matters, PTQ Q4_0 (0.696 GB). Do not relabel an ordinary Q4_0 file as QAD or re-quantize QAD weights and assume the published result still applies. The 1.2B author specifically recommends evidence/extraction workloads and cautions against knowledge-intensive tasks, making library-empty questions an essential counterweight. Vendor desktop/NPU speeds are not Outpost measurements. [QAD release](https://www.liquid.ai/blog/qad), [exact format distinction](https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-GGUF).

LFM weights use **LFM Open License v1.0**, not Apache 2.0. Its commercial-use threshold and redistribution conditions need a release decision if selected; current research is not a public redistribution decision. The captured LICENSE is revision-bound in the metadata snapshot. Qwen, Gemma 4 and SmolLM3 cards declare Apache 2.0; BitNet and Phi declare MIT. These declarations do not settle licenses of separately imported documents or regional datasets.

Qwen3.5 uses linear-attention layers interleaved with full attention. This can reduce growing KV storage, but introduces recurrent state. The 2B card defaults to non-thinking; it explicitly does not support Qwen3's textual `/think` and `/nothink` switch. Respect its template API, rather than appending a magic instruction. Large advertised contexts do not expand Outpost's 2,048-token context or 192-token answer budget.

Gemma 4 E2B's published 2.3B effective / 5.1B including embeddings illustrates why model names cannot be memory estimates. E4B is 4.5B effective / 8B including embeddings. The official QAT text GGUFs above exclude separate roughly 0.99 GB multimodal projectors, unnecessary for a text-only experiment. Per-layer embeddings are an interesting deployed relative of the lookup-memory idea; they are **not DeepSeek Engram**. Specialized mobile quantizations/backend paths are a separate experiment from the cited GGUFs.

BitNet's card reports a small **non-embedding** memory figure and directs efficient inference to bitnet.cpp. The official complete GGUF is larger than Bonsai 4B's file. Neither the 1.58-bit label nor the same `.gguf` extension makes its packed layout compatible with Q2_0. A fair experiment must count embeddings, state and scratch, plus integration cost.

## Compatibility with the actual Outpost backend

The pinned backend `86ea01d05ec237f89b78b41c8c1ee0f908141ac7` already registers and constructs `lfm2`, `qwen35`, `gemma4`, `gemma4-assistant`, `qwen3`, `smollm3`, `phi3` and `bitnet`. This was checked in local source, not inferred from current upstream marketing. See the [static audit](../evidence/research/model-survey-20260930/static-analysis.json). Architectural presence is a **candidate for testing**, not proof that a particular GGUF loads or produces correct output. There is no demonstrated need to upgrade the backend merely to start the first comparison.

Current obstacles are in the integration contract:

1. `ModelStore` accepts only the three current hashes. New candidates need separate pinned research profiles; never weaken hash verification or silently replace an existing model file.
2. `engine.cpp` uses the built-in `llama_chat_apply_template`, not a general Jinja interpreter. Its special suffix handles the exact Bonsai template only. Check tokenized system/user/assistant formatting, BOS/EOS, stops and no-thinking behavior for each new profile. Reject unsupported formatting visibly.
3. Sampling is currently a boolean choice between Qwen greedy and Bonsai's sampled settings. Define explicit per-model sampler/template policy before attributing failures to weights. Record any departure from the author's recommendation.
4. Partial prefix reuse calls `llama_memory_seq_rm`; failure clears memory and starts cold. Hybrid models need cancellation, exact/partial reuse, changed-source, model-switch and restart checks. Do not assume transformer KV truncation equals recurrent-state rollback. Start new hybrids without speculative decoding.
5. The custom kernel targets Q2_0; new Q4 profiles use their backend kernels. Retain model-specific profiles and CPU/compiled-kernel gates. Bonsai's width/row tuning and x86 emulator gains cannot transfer automatically to Q4, VNNI, NEON or a Pixel.

## Resource accounting before optimization

At 2,048 tokens with F16 K and V, a conventional cache estimate is `2 * tokens * full_attention_layers * kv_heads * head_dim * 2 bytes`. Derived config-only estimates are **288 MiB** for Bonsai 4B/Qwen3 4B, **24 MiB** for LFM2.5 1.2B's six attention layers and **24 MiB** for Qwen3.5 2B's six full-attention layers. Hybrid figures exclude convolution/recurrent state, which must be added. They are neither measured allocations nor process peaks; padding, allocator policy, logits, scratch, native/Java heaps and mapped-page residency also matter.

Measure cold load, warm inference and warm-cache follow-up separately. Report APK + retained model files + library/index storage, then sampled peak PSS/RSS with cadence, sampling limitations and post-run state. Do not add weight file bytes to mapped RSS and double-count the same pages. CPU and GPU share the phone's RAM budget; moving tensors to CPU is not free capacity. Future phone tiers should follow measurements and memory headroom, not hard-coded phone names. Current scope remains emulator-only.

## Trained drafting and memory research

The inventory found a **296M LFM2.5 1.2B DSpark drafter** and a **roughly 78M Gemma 4 E2B assistant**. They require their corresponding target architecture/state interface; neither is a Bonsai MTP head. DSpark's published SGLang/Metal gains do not imply Android CPU gains. Gemma's assistant uses target activations/cache, so architecture support alone does not wire it into Outpost. Baseline each target first, then measure draft + verification + rollback cost and additional memory. QAD/quantized-target pairing needs its own acceptance check. [LFM DSpark](https://huggingface.co/LiquidAI/LFM2.5-1.2B-Instruct-DSpark), [Gemma MTP](https://ai.google.dev/gemma/docs/mtp/mtp).

[Engram review](engram-review.md) separates trained conditional memory, source retrieval, conversation memory and exact response caches. It is a conditional architecture experiment, not an immediate Bonsai optimization. LFM2.5-Embedding-350M is also pinned for a possible **retrieval** experiment; its bidirectional graph/pooling/index cost is separate from generator support and no encoder was integrated.

## Emulator experiment contract

Spark artifact sizes above are from the separate [October metadata](../evidence/research/spark-x25-20261001/candidates.json); the1.7B file has since been verified and admitted to emulator research, while4B remains metadata-only. See [executed results](spark-candidate-results-2026-10-01.md); the original metadata snapshot is unchanged. The [storage review](spark-x25-storage-review.md) also adds residency/I/O profiling, without importing DGX NVMe settings into Android.

**Protocol, partially executed for Spark/Bonsai.** The linked October 1 study implements admission, eight development cases/two evidence arms and 24 reserved fixed-evidence greedy pairs; it adds two sampled timing controls at one seed. The complete sampled multi-seed and broader retrieval study remains open. E-07 owns candidate admission and E-05 owns answer comparison. Preserve both the measured Bonsai profile and the conservative defaults as identified controls.

1. Admit one candidate at a time: verify full file size/SHA, inspect header/tensor types, confirm conversion provenance/license, template/tokenization and load. Keep test profiles outside product Settings until accepted. No arbitrary-GGUF import change.
2. Use eight development cases to fix integration, then freeze prompt/adapters/samplers before 24 held-out cases. Use six families with four cases each: travel/locality evidence; downloaded records/date conflicts; field manuals/version matching; farm reference tables/units; hiking or driving preparation with missing data; general explanations and follow-up corrections. Include complete evidence, missing evidence, conflicting evidence and plausible distractors across the set. These are a study design, not 24 existing scored fixtures.
3. Run two arms: fixed evidence to isolate generation, and actual retrieval to diagnose end-to-end usefulness. Use the same source bytes and user questions; count model-specific tokens and ensure all prompts fit. Keep `chat-v1.1`, 2,048 context, 192 output tokens, 120 seconds and the two-turn policy as the product arm. Larger context/output belongs in a separate labelled study. Do not compare `ResearchPrompt` evidence-only scores to product chat scores.
4. Compare greedy decoding first as a reproducible diagnostic, then each model's documented non-thinking sampler with three recorded seeds. Greedy is not a claim of best possible quality. Hold semantic instructions/evidence constant while allowing the required model-specific chat template. Preserve raw prompts/tokens/results; never tune on the held-out split.
5. Review source entailment, exact names/numbers/units, supported conclusion, useful clarification, uncertainty, citation identity, conflict handling and task completion. Record reviewer identity/rubric. A citation or harness PASS is insufficient. Exact arithmetic and geographic distances should be computed by tools; current deterministic OSM answers are regression controls and **do not count as generator wins**.
6. Time at least three alternating paired repetitions of representative short, longer, cold and follow-up workloads. Report first token and completed answer, timeout/truncation frequency, paired medians, variability and sampled memory peaks. Decode tok/s alone is not comparable across tokenizers and answer lengths. No energy or phone extrapolation.

Promote only a candidate with correct lifecycle behavior, acceptable complete-answer latency/headroom and evidence-backed quality gains or a clear resource-saving tradeoff. Any invented source-sensitive procedure/value is a recorded failure, not averaged away by fluent easy answers. If evidence retrieval is the limiting factor, prioritize retrieval/context work instead of declaring that more parameters solved it. No winner threshold, default replacement, new APK or benchmark result is asserted by this research pass.

## Explain model-comparison outcomes

The subsequent [NeMo Relay review](nemo-relay-review.md) proposes E-08: local correlated pipeline events to distinguish retrieval, clipping, queueing, generation and persistence failures/costs. This complements the two evaluation arms above; it does not block candidate admission, supply a quality judge or establish that tracing has zero overhead. No Relay dependency was added.
