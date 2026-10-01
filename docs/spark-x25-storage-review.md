# Spark-X2.5 and storage-sensitive inference — 2026-10-01

**Research checkpoint against Outpost `a6742d5` / 0.14.0.** Add Spark-X2.5-1.7B to the first small-model comparison, with 4B as a larger follow-up. Also expand memory profiling to include page residency and storage stalls before adopting flash-backed lookup tables. No new model, device test, storage command, service change or benchmark was executed in this review.

The two inputs are separate: **DGX Spark** is NVIDIA hardware discussed in the pasted storage report; **Spark-X2.5** is XHToken's model family. SparkNest and the report's unspecified DeepSeek/Qwen checkpoints were not inspected. Shared wording does not establish shared architecture or a connection between those projects.

## NVMe report: corroborated policy, unverified measurements

NVIDIA's [DGX OS 7 guide](https://docs.nvidia.com/dgx/dgx-os-7-user-guide/additional_software.html#configuring-nvme-interrupt-coalescing) documents `nvidia-nvme-options` enabling interrupt coalescing on Samsung and Kioxia drives at boot, including the named `nvidia-nvme-interrupt-coalescing.service`. The newer [BaseOS 9 guide](https://docs.nvidia.com/dgx/baseos-9-user-guide/latest/upgrading-baseos.html#configuring-nvme-interrupt-coalescing) also names Micron. This corroborates the policy's existence, not the effective values or package version on every Spark. The [Spark release page](https://docs.nvidia.com/dgx/dgx-spark/release-notes.html) inspected here lists DGX OS 7.5.0; do not equate it with the newer guide's software state.

The user-provided report describes two Samsung PM9E1 drives, 20,000 random 4 KiB direct reads at queue depth 1, and an ON/OFF/ON sequence of approximately 200 / 55–57 / 200 microseconds. Keep those as **attributed, unverified observations**: no raw results, command/configuration capture, latency distribution or original post URL was supplied. We did not reproduce them. In particular, neither the claimed factory default nor the exact eight-completion threshold has been independently established here.

Interrupt coalescing combines completion notifications to reduce interrupt work. The controller can delay notification until an aggregation threshold or timer is reached. That tradeoff can hurt an interrupt-driven workload waiting on a single outstanding read, even when sequential bandwidth is high. This is about completion notification, not accumulating eight interrupts or making NAND intrinsically slower. The [NVMe specification](https://www.nvmexpress.org/wp-content/uploads/NVM-Express-1_4a-2020.03.09-Ratified.pdf) describes the feature, and pinned [libnvme definitions](https://github.com/linux-nvme/libnvme/blob/01ede320a30ef2094ab4d3fb072cea64f6bf6ecc/src/nvme/types.h) identify separate threshold/time and per-vector disable fields. Polling, controller behavior and I/O submission mode matter too.

The supplied `nvme get-feature ... -f 8 -H` is a read query of the interrupt-coalescing feature on the addressed controller; it does not reproduce the benchmark or identify every effective per-vector behavior. No such command was run here. Changing persistent services, firmware or host storage policy is outside this inspection. A universal “disable it everywhere” policy is not supported by one latency-oriented workload; other loads may value interrupt/CPU efficiency.

## Why this matters for Engram/PLE

An uncached lookup can put storage latency on the token's critical path. Cache hits avoid that storage wait; batching and early prefetch can overlap some misses with computation. Direct-I/O QD1 random reads are a diagnostic, **not the same workload as page faults through `mmap`**, which involve page cache, filesystem, readahead and possibly concurrent faults.

For illustration only, suppose a fraction `f` of baseline token time is exposed storage wait, and only that part becomes 3.5 times faster. Then whole-token speedup is `1 / ((1-f) + f/3.5)`:

| Exposed storage fraction | Illustrative token speedup |
|---:|---:|
| 10% | 1.077x |
| 50% | 1.556x |
| 90% | 2.800x |

These are arithmetic scenarios, not measured predictions. The exposed fraction already excludes overlapped work; it cannot be inferred just by counting bytes or total lookups. A smaller resident model can beat a nominally stronger model that repeatedly faults its tables. Conversely, a well-cached large table may cause few physical reads. Low free RAM increases the need to measure residency, not merely the model file's total size.

This strengthens the [Engram review](engram-review.md): address calculation can be cheap while fetching the addressed data is expensive. It does not establish that a phone has this NVIDIA service or this NVMe behavior. The Android emulator's guest filesystem, virtual disk, host caches and physical storage form another path; guest results cannot establish Pixel flash latency or reproduce a DGX controller policy.

## Spark-X2.5: available artifacts and architecture

The [official repository](https://github.com/XHToken/Spark-X2.5/tree/a24ca4e6366f60b8e14211af50dfbd75d089be5c) was pinned at `a24ca4e6366f60b8e14211af50dfbd75d089be5c`. It is primarily a release/model guide, not a new kernel implementation. The captured tree has README/license/policy files and images. Its Apache-2.0 model cards link trained 1.7B and 4B checkpoints plus author-published GGUFs. [Model metadata](../evidence/research/spark-x25-20261001/model-metadata.json) contains exact revisions/configurations; [candidate files](../evidence/research/spark-x25-20261001/candidates.json) contains expected sizes and publisher LFS hashes. Full weight bytes have not been downloaded or verified locally.

| Property | Spark-X2.5-1.7B | Spark-X2.5-4B |
|---|---:|---:|
| Official Q4_K_M file | 1,107,457,856 bytes (1.107 GB) | 2,600,224,352 bytes (2.600 GB) |
| Layers | 28: 21 sliding + 7 full | 36: 27 sliding + 9 full |
| Hidden dimension | 2,048 | 2,560 |
| KV heads / head dimension | 2 / 256 | 4 / 256 |
| Sliding window | 512 tokens | 512 tokens |
| Vocabulary | 131,072 | 131,072 |
| Declared architecture | `Spark2_5ForCausalLM` | `Spark2_5ForCausalLM` |

Primary weights/cards: [1.7B](https://huggingface.co/XHToken/Spark-X2.5-1.7B), [4B](https://huggingface.co/XHToken/Spark-X2.5-4B), [1.7B GGUF](https://huggingface.co/XHToken/Spark-X2.5-1.7B-GGUF), [4B GGUF](https://huggingface.co/XHToken/Spark-X2.5-4B-GGUF).

The configs use three sliding-attention layers per full-attention layer, head-wise sigmoid attention gates and GELU feed-forward activation. This is a sliding/full-attention hybrid, **not Qwen3.5's recurrent linear-attention hybrid**. The inspected config and backend graph contain ordinary token embeddings and attention/MLP tensors; they do not establish an Engram or per-layer token-embedding table. Do not apply the NVMe report as a Spark-X2.5-specific diagnosis.

The authors advertise a 1,048,576-token context. At the inspected revisions, tokenizer metadata also carries `model_max_length=131072`, so runtime/config/tokenizer limits need reconciliation before any long-context claim. Outpost still uses 2,048 tokens. As a logical F16 KV payload estimate at that budget, retaining 512 positions per sliding layer and 2,048 per full layer gives approximately **49 MiB** for 1.7B and **126 MiB** for 4B. These are config arithmetic, not measured allocation or peak RAM; implementation padding, batch workspace and retained cache capacity matter. The 1M-token headline is not a mobile memory budget.

The published benchmark table is explicitly **thinking-mode** evaluation, with temperature 1.0, top-p 0.95 and unrestricted top-k. It combines author measurements with some cited results for competitors. It supplies a reason to test the models, not a comparable Outpost ranking at 192 output tokens. The default template enables thinking; a separate no-thinking mode exists. Keep reasoning-token cost, complete-answer quality and truncation visible rather than transplanting the leaderboard score.

## Concrete Outpost integration findings

The existing backend `86ea01d05ec237f89b78b41c8c1ee0f908141ac7` already includes the `spark2_5` registry, model factory, converter and attention graph. The converter checks the expected layer pattern, gates and GELU. This is static compatibility evidence only. No backend upgrade is justified solely by the model's name, and the full candidate GGUF still needs header/load/numerical checks.

There is a more immediate issue: the official template uses case-sensitive `<|System|>`, `<|User|>` and `<|Bot|>` markers with its own sentence delimiters. Outpost calls the backend's limited built-in `llama_chat_apply_template`, whose inspected registry/detection code has no Spark handler. The captured HF template does not match the expected existing formats. Therefore the integration must validate the exact GGUF template and add an explicit faithful adapter or a correctly scoped general template implementation; model-graph support alone does not make current chat ready. No template execution or runtime rejection was measured in this review.

Also check BOS/EOS handling, no-thinking prefix, sampler semantics (`top_k=-1` must not become an invalid Outpost setting), sliding-cache reuse/rollback, cancellation and model switching. An advertised tool-call format does not add tools or authorize actions. The current hash allowlist remains unchanged, as do Bonsai-specific Q2 kernels and profiles. Q4_K_M candidates use their corresponding backend kernels; compare complete outcomes before further kernel work.

## Updated experiment order

**E-07/E-05:** include Spark-X2.5-1.7B alongside LFM2.5-1.2B QAD and Qwen3.5-2B for first-stage admission and practical-task comparison. Its 1.107 GB official Q4 file is close to existing baseline storage, and its architecture supplies another small-model option. Use the same fixed-evidence and real-retrieval arms, English product prompt, output budget and held-out cases in the [model study](model-alternatives.md). Test 4B only after 1.7B passes formatting/lifecycle checks or reveals a quality gap. This priority is a research judgment, not a model-win claim.

**P-07, proposed storage/residency study:** after bounded memory instrumentation exists, record model/table mapping size separately from resident pages, available-memory state, major/minor faults where available, I/O bytes and full-answer latency tails. Counters need attribution: a page fault is not necessarily a new physical read, and a logical lookup miss is not synonymous with a major fault. Native compute metrics do not independently identify storage wait.

Use emulator-owned synthetic files and bounded memory pressure for any first probe. Separate warm, attempted-cold and pressured states; restarting the app does not clear guest or host page caches. Do not use global cache dropping, raw block-device writes, host `fio`, service changes or NVMe tuning to stand in for an Android experiment. Unsupported counters stay unavailable rather than zero. The prior 64 MiB kernel pressure control is not proof of cold model/table pages.

If a real trained lookup model is later admitted, prioritize reducing exposed reads: pack related rows where checkpoint semantics allow, retain measured hot entries with a bounded cache, deduplicate known prefill requests and prefetch only known addresses early enough to hide latency. Compare costs of extra reads, RAM and cancellation against benefit. Future decode addresses are not all known before their tokens exist. No lookup cache, packing transformation or prefetch policy is implemented here.

Tie those metrics to the proposed [E-08 lifecycle traces](nemo-relay-review.md), with explicit profiling overhead and incomplete-run status. Phone storage, thermal and energy validation remains X-06; neural Engram remains X-07. A source-backed investigation of this PSA changes what we should measure, not current device settings or the shipped model.
