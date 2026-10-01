# TandemLLM review — 2026-10-01

Static review of [0xBakeer/TandemLLM](https://github.com/0xBakeer/TandemLLM/blob/c11aecaa7ba767642409ff51d90d48c79c572d3a/README.md), pinned at `c11aecaa7ba767642409ff51d90d48c79c572d3a`; its VERSION file reads 0.3.0. Outpost baseline is 0.16.0 (`64c7472`). Public source was fetched into ignored `.local/research/tandemllm-c11aecaa/`; [the manifest](../evidence/research/tandemllm-20261001/source-manifest.json) binds 36 inspected text files to hashes. No upstream installer/code/tests, model downloads, host inference or device experiments were run. No production behavior changes follow from this review.

## Assessment

The strongest transferable idea is a measured-cost speculation controller, preceded by a numerical audit of Outpost's current verifier. TandemLLM's CUDA backend and trained drafters target Qwen3.8-27B on DGX Spark. Its reported rates are not Pixel predictions. Its documentation/source are engineering references; runtime replacement is not proposed.

The author reports 49.89 tok/s with StairCut, 47.13 with fixed block 8, 44.80 with fixed block 16 and 13.69 with plain greedy decoding. Thus StairCut's incremental gain is about 5.9% over fixed 8 or 11.4% over fixed 16; the larger 3.64x ratio includes the whole speculative setup. These are reported single-request results, not reproduced here. See [README](https://github.com/0xBakeer/TandemLLM/blob/c11aecaa7ba767642409ff51d90d48c79c572d3a/README.md) and [extracted facts](../evidence/research/tandemllm-20261001/reported-facts.json).

## Ideas and applicability

| Mechanism | What the pinned source implements | Outpost decision |
|---|---|---|
| StairCut | Select a pruned proposal by estimated committed tokens per complete-round cost, using context-dependent chain/tree price tables | High priority as a design, after measuring our verifier. Include ordinary decoding as an explicit alternative |
| Lookup proposals | Token suffix indexes over request text, a corpus and optional persistent history; votes can form a tree | Start with bounded current-request/retrieved text. Broader/persistent stores require independent value, memory and lifecycle evidence |
| Fixed per-row reduction | Projection reductions depend on weight shape rather than query-row count | Already central to our Q2 kernels. Audit the whole verifier graph, especially attention, because projection invariance is insufficient |
| Position-keyed sampling | Seeded Gumbel noise is keyed to token position, separating target draws from drafting order | Useful for future trees; not a drop-in replacement for our sampler or a reason to change current seeded outputs |
| Exact-state caches | Separate prefix-grid snapshots from conversational session states and response memoization | Retain our existing exact token/grid/cache identity. Do not import multi-GiB cache budgets |
| DFlash2/tree verification | Trained target-state interface, tree masks, accepted-path commit and recurrent rollback | Conditional research; no compatible Bonsai head or tree verifier is established |

### StairCut and the real cost curve

`MergedRouter._stair_cut` keeps ancestors with selected nodes, estimates their path probabilities and evaluates their yield against verification plus drafting and commit/rollback cost. `LengthRouter._class_price_raw` interpolates context-specific tables; chain and tree prices are separate. This is an estimated-value policy, not a guarantee of acceptance. The inspected tree-selection function compares proposal types; Outpost should also price the zero-proposal route explicitly. [Router](https://github.com/0xBakeer/TandemLLM/blob/c11aecaa7ba767642409ff51d90d48c79c572d3a/engine/router.py#L654), [context prices](https://github.com/0xBakeer/TandemLLM/blob/c11aecaa7ba767642409ff51d90d48c79c572d3a/engine/lenrouter.py#L1311).

At 1024 context tokens the shipped table lists tree verification/commit around 75.5, 74.4, 83.1 and 87.8ms for 8, 16, 24 and 32 rows. The nearly flat 8-to-16 region motivates trying more proposals on that machine; small non-monotonic differences are measurement noise, not free compute guaranteed everywhere. Neither these values nor the tile boundaries transfer to Pixel. [Price table](https://github.com/0xBakeer/TandemLLM/blob/c11aecaa7ba767642409ff51d90d48c79c572d3a/ops/stair-tables-nvfp4.json), [algorithm description](https://github.com/0xBakeer/TandemLLM/blob/c11aecaa7ba767642409ff51d90d48c79c572d3a/docs/speculative-decoding.md).

### NVFP4 does not mean native FP4 arithmetic here

The actual skinny kernel is W4A16. Source decodes packed weights into FP16 operand registers and issues FP16 tensor-core multiplication with FP32 accumulation. It builds for `sm_121a`; it does not use native FP4 multiplication. This supports choosing encoding, layout, arithmetic and dispatch together. These instructions are not ARM DotProd/I8MM implementations. [Kernel source](https://github.com/0xBakeer/TandemLLM/blob/c11aecaa7ba767642409ff51d90d48c79c572d3a/tools/nvfp4_skinny.py#L136), [kernel design](https://github.com/0xBakeer/TandemLLM/blob/c11aecaa7ba767642409ff51d90d48c79c572d3a/docs/kernels.md).

Outpost's corresponding contract remains Q2_0 g64 storage, unpacked signed INT8 dot operands, INT32 products/FP32 scaled accumulation and F16 KV. [Our stack audit](inference-stack-audit-2026-10-01.md) owns those facts. Replacing the file with NVFP4 would require a compatible loader/backend/kernel and independent quality/performance checks; a smaller bit label is not sufficient.

### Lookup memory is not trained Engram or document retrieval

`EngramDrafter` is an n-gram continuation dictionary. Its successor `NgramDrafter` adds counted occurrences and a memory-mapped suffix-array corpus. Neither inserts trained Engram embeddings into the target. Its own source notes that increasing lookup firing can lower accepted tokens and that selective copying is preferable on some workloads. [Simple drafter](https://github.com/0xBakeer/TandemLLM/blob/c11aecaa7ba767642409ff51d90d48c79c572d3a/engine/drafters/engram.py), [current lookup implementation](https://github.com/0xBakeer/TandemLLM/blob/c11aecaa7ba767642409ff51d90d48c79c572d3a/engine/drafters/ngram.py).

For Outpost, retrieved manual passages, exact identifiers and requested quotations are plausible proposal sources. This is a hypothesis, not a measured speedup. Current context is only 2048 tokens and already has a linear suffix lookup, so an index may cost more than it saves. Imported knowledge still needs retrieval, version/source binding and citations. Target acceptance verifies a token choice, not a factual claim. Token IDs can encode private text; persistent proposal memory would need explicit scope, invalidation and deletion behavior.

### A trained drafter cannot be transplanted by name

The inspected DFlash2 reads target residual states entering layers 5, 19, 33, 47 and 61, uses hidden size 5120, and borrows the target embedding and output head. Bonsai 4 has 36 layers, hidden size 2560 and no pinned MTP/draft tensors. The interfaces and weights do not match. [DFlash2 interface](https://github.com/0xBakeer/TandemLLM/blob/c11aecaa7ba767642409ff51d90d48c79c572d3a/engine/drafters/dflash2.py#L1), [our model/speculation audit](speculation.md). Its recurrent-state commit mechanisms also address Qwen's Gated DeltaNet layers, which are not the inspected Bonsai architecture.

## Exactness and measurement boundaries

The upstream greedy contract allows a one-ulp near-tie exception. Of 30 checked prompts, 17 matched through the answer and 13 diverged within it; one divergence ended an answer early. The real-model warm/cold 64k cache difference is documented as unexplained. These do not meet Outpost's current unchanged-output adoption contract. Equal projection rows or restored bytes alone do not prove equal end-to-end outputs. [Exactness](https://github.com/0xBakeer/TandemLLM/blob/c11aecaa7ba767642409ff51d90d48c79c572d3a/docs/exactness.md).

The position-keyed sampler removes one source of random-stream dependence if logits and filtering match. It cannot repair changed logits. Our linear verifier currently samples only the accepted prefix and first mismatch/bonus, so importing a vocabulary-wide Gumbel sampler is not the first problem to solve and would change the existing seeded baseline. [Sampler source](https://github.com/0xBakeer/TandemLLM/blob/c11aecaa7ba767642409ff51d90d48c79c572d3a/engine/sample.py#L173).

Useful measurement practices include clean/disabled lookup stores, frozen adaptive state for bit-preserving A/B comparisons, complete answer lengths, per-round time/yield and fresh-versus-copy workload separation. Upstream documents a contaminated store inflating a benchmark by about 21%; a teacher-forced benchmark controls output text but does not prove free-generation correctness. Retain both views, clearly labeled. [Measurement](https://github.com/0xBakeer/TandemLLM/blob/c11aecaa7ba767642409ff51d90d48c79c572d3a/docs/measurement.md), [cache contracts](https://github.com/0xBakeer/TandemLLM/blob/c11aecaa7ba767642409ff51d90d48c79c572d3a/docs/caches.md).

The code notice identifies AGPL-3.0-only plus a separate commercial offering; documentation is CC BY 4.0. This review attributes the upstream design to Khaled Bakeer/TandemLLM and vendors no executable code. Direct code incorporation would be a separate dependency/distribution decision under R-05. [NOTICE](https://github.com/0xBakeer/TandemLLM/blob/c11aecaa7ba767642409ff51d90d48c79c572d3a/NOTICE), [documentation license](https://github.com/0xBakeer/TandemLLM/blob/c11aecaa7ba767642409ff51d90d48c79c572d3a/docs/LICENSE).

## Gaps found in the reviewed Outpost baseline

The current [controller](../app/src/main/cpp/speculation.cpp) can disable a fixed-depth proposal policy after three costly windows; it does not choose a depth from a calibrated cost curve. In [the generation loop](../app/src/main/cpp/engine.cpp), proposal time is tracked separately and excluded from that decision; ordinary-step and verification-window timings include different sampling/rollback work. Normalize cost scope before trusting a new policy.

Current per-step logit tracing covers initial/ordinary decoding, but the speculative `spec::verify` callback samples rows without recording their hashes. Extend trace coverage before using it as a speculative parity gate. Also audit the 4-decode/6-prompt worker split and shape-dependent attention path: the 0.16 per-row scheduling proof does not imply serial-versus-batch graph equality. Historical speculative audits already recorded logit differences.

## Prioritized experiments — proposed, not implemented

| ID | Work | Exit condition |
|---|---|---|
| TND-01 | Complete speculative target-sample tracing and consistent round-cost instrumentation | Every accepted/mismatch/bonus sample is attributable; no hidden RNG draws; EOS, rejected KV removal, cancellation/recovery and static fallback remain correct |
| TND-02 | Measure serial and causal verify batches of 1, 2, 4, 8 rows on the registered Pixel at bounded context lengths | Same pinned 0.16 inputs/profile; phases reported separately; full numerical traces and an explicitly labeled perfect-proposal upper bound. Do not call an oracle a real drafter |
| TND-03 | Implement an independent cost-aware chain controller using request-local proposals | Choose among no draft and supported depths based on measured whole-round costs; memory bounded; paired real field-question/copy/no-match cases justify admission |
| TND-04 | Consider votes, corpus lookup or trees only after the chain result | Compatible state/mask/rollback/sampling contracts and net latency/memory benefit; clean-store controls and source removal/version invalidation verified |

Keep product speculation at depth 0 during this work. Use the existing supported 1..8-row research range first; a 32-row tree is a new implementation, not a parameter change. Do not import Spark price tables, relax the numerical gate to one ulp, change the sampler, or load a trained drafter as a side effect of this review. [Pixel protocol](pixel10-testing.md), [speculation/MTP](speculation.md), [roadmap](roadmap.md).

## Executed follow-up

[TND-01/TND-02 have now been exercised on the Pixel](speculation-pixel-2026-10-01.md). Tracing/cost accounting is implemented, but serial-versus-batch numerical parity fails at larger contexts. TND-02b now precedes TND-03: isolate and repair the attention-path boundary. The proposals above remain historical design context; product speculation is still off.
