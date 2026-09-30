# Engram and local memory — 2026-09-30

**Conclusion:** DeepSeek Engram is a promising trained architecture for trading computation against lookup memory. It is not a plug-in knowledge file for Bonsai, and the inspected release does not provide a ready small mobile checkpoint. Keep it as conditional research while testing available small models and improving source retrieval. No Engram code, model or training was executed.

This review interprets “engrams” primarily as DeepSeek's conditional-memory work because the discussion concerns inference, kernels and models. Persistent agent-memory packages use the same name; the distinction below preserves that second interpretation without installing an unrelated framework.

## What was verified

The [official repository](https://github.com/deepseek-ai/Engram/tree/fb7f84a21f91223715394a33a1dc24bbfb7f788e) was inspected at `fb7f84a21f91223715394a33a1dc24bbfb7f788e`. Its captured tree contains the paper, figures, license and a demonstration, not trained model weights. The README says the demo mocks standard backbone components. This is a bounded observation of that release, not a claim that no Engram-related checkpoint exists anywhere. The [research snapshot](../evidence/research/model-survey-20260930/survey-metadata.json) pins the tree and downloaded metadata/source hashes.

In the [paper, arXiv 2601.07372v1](https://arxiv.org/html/2601.07372v1), token n-grams address learned embedding tables through normalization and multiple hash heads. Context-dependent gates/projections fuse the retrieved vectors into chosen layers. The paper compares jointly trained 27B-scale systems under matched parameter/compute budgets. Its offload demonstration uses an H800, 512 sequences and a 100B-parameter table resident in host DRAM; it reports under 3% throughput loss. That experiment is not batch-one Android latency or flash-backed lookup performance.

The Apache-licensed [demo source](https://github.com/deepseek-ai/Engram/blob/fb7f84a21f91223715394a33a1dc24bbfb7f788e/engram_demo_v1.py) exposes why adding an empty table to Bonsai cannot reproduce the result: embeddings, projections, gates and surrounding representations must be trained together or adapted with a validated training procedure. Token normalization also defines a checkpoint-specific mapping. A changed tokenizer is not interchangeable just because its vocabulary has a similar size. The host review read source only; it did not invoke PyTorch or `trust_remote_code`.

## Four different meanings of memory

| Mechanism | Stores | How it changes | Source/citation behavior | Outpost implication |
|---|---|---|---|---|
| Engram inside a model | Learned vectors keyed by token patterns | Training and checkpoint updates | Parametric information; no document/page provenance by itself | New trained model/runtime path |
| Imported knowledge | Documents, OSM records, dates, exact source identities | Explicit snapshot import/removal | Inspectable pages/rows/elements and retained versions | Current knowledge domain; keep it |
| Mission/conversation memory | User-provided constraints, observations and corrections | Explicit save/edit/delete with scope | User statement provenance, not external fact verification | Chat history exists; editable mission memory remains proposed |
| Exact inference/answer cache | Computation state or a previously completed answer | Deterministic key and invalidation | Must retain current source/model/context identity | Prefix cache exists; general answer caching is not implemented |

A traveler asking for museums near a recorded landmark needs entity resolution, coordinates and coverage/freshness handling. A field engineer needs the matching manual revision and relevant passage. A learned n-gram embedding has no direct replacement for those contracts. Better internal knowledge may help explanation, but it cannot establish that a restaurant is currently open or identify the user's missing downloaded document.

## Memory arithmetic, not a phone benchmark

The demo defaults use two inserted layers, orders 2 and 3, eight hash heads per order and 512 embedding values per order split across the heads. Each head's table starts near 646,400 entries and uses a distinct prime size. A **lower-bound approximation** is therefore:

`2 layers * 2 orders * 8 heads * 646400 entries * 64 values = 1,323,827,200 parameters`.

The lookup tables alone require about **2.65 GB at 16 bits or 5.30 GB at 32 bits**, before prime-size increments, projections, backbone and caches. This is a static calculation from the demo configuration, not a released trained model's measured footprint. The 100B-table paper experiment would require 200 GB at 16-bit storage before overhead; neither its dtype assumption here nor its hardware budget describes a phone.

For the demo shape, the useful retrieved vectors amount to about 4 KiB per token at 16 bits across both inserted layers. Random flash access could fetch many more bytes in pages: 32 independent lookups touching distinct 4 KiB pages would imply 128 KiB of page reads before cache reuse. This illustrative bound ignores co-location, resident pages and prefetch effects. Constant-time address calculation does not make page faults, storage bandwidth, energy or projection/gating compute free.

On a phone, CPU and GPU generally draw from the same limited system RAM. Host-DRAM offload is not a second free memory pool as it can be beside a server GPU. A flash-backed table is a different storage hierarchy and needs its own cold/warm measurements. Quantizing or pruning tables might help, but requires quality validation; one cannot reuse Bonsai's matrix Q2 kernel for arbitrary embedding gathers and claim the same gain.

## Work worth doing

**Near term:** test deployed lookup-heavy architectures before inventing a new trained one. [Gemma 4 E2B](https://huggingface.co/google/gemma-4-E2B-it) includes per-layer token embeddings; it is related in the broad compute/memory tradeoff, but differs from Engram's multi-token hash mechanism. Its actual 3.350 GB official QAT text GGUF is in the [model survey](model-alternatives.md). Measure end-to-end benefit and resident memory; a lower effective-parameter count is not sufficient.

**Knowledge-side experiment:** only after recording real retrieval failures, compare lexical search with aliases/structured lookup and then a small retrieval encoder. Keep source/version/radius/filter keys and original locators. A semantic retrieval result must select evidence, not manufacture a fact. This addresses paraphrases such as a maintenance note whose terms differ from the question without claiming neural Engram integration. E-07 and X-03 remain distinct.

**Optional mission memory:** if a user saves “I am working on pump P-17 with firmware 3.2,” retain that as editable, scoped user context with time/provenance. Test corrections, deletion and stale constraints before prompting it into future conversations. Automatically summarizing every chat into unquestioned facts is not the design. D-05/X-05 and existing context proposals own this area; no retention behavior changed here.

**Conditional neural experiment (X-07):** first obtain a licensed trained small Engram-compatible checkpoint, or specify a separate training scope, data and cost. Current emulator-only execution does not authorize host/GPU training. A synthetic table microbenchmark would test memory access only and cannot prove improved answers.

If that condition is met, compare matched backbone baselines with and without trained memory, controlling total stored bytes, active computation, quantization and prompt/output budgets. Measure answer quality separately for internal knowledge, source reading, paraphrase/entity variation and conflicting evidence; test collisions, token normalization, layer/state restoration and cancellation. Profile cold/warm lookup, page faults, prefetch benefit, RAM/scratch and full-answer latency. A perplexity gain or synthetic gather speed alone is not the acceptance gate.

**Decision:** no graft onto Bonsai, no enormous offloaded table, and no production memory/cache change from this review. Revisit when a suitable trained checkpoint and an emulator-compatible implementation exist, or the user explicitly chooses a training investigation. Available model comparisons can proceed independently now.
