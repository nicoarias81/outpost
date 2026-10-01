# Context speculation and MTP

Status in Outpost 0.17: context speculation exists only as native research functionality; product chat forces depth 0 and has no toggle. Native MTP and a dual-model drafter are not implemented. Measurements below are historical Bonsai 4B emulator experiments, not current phone/product benchmarks.

## Executed Pixel follow-up

The [2026-10-01 study](speculation-pixel-2026-10-01.md) completes speculative target-sample tracing and consistent whole-round costs, including proposal, sampling, rollback and callback work. Hash/trace overhead is reported separately; interrupted/terminal rounds are excluded from controller learning. That initial study used research artifacts. The current [0.17 normal decoder](validation-0.17.md) is separately validated, while the phone remains restored to frozen 0.16 after tests. Product depth stays 0.

The initial trace/curve/edge suites reproduced larger-prefix logit drift and changed oracle-driven text. The subsequent [TND-02b attention repair](attention-parity-2026-10-01.md) preserves all 640 candidate logit vectors and the bounded sampled/lifecycle controls by retaining original serial attention arithmetic per query. No trained drafter or relaxed numerical gate is introduced. Product adoption still requires measured real-proposal benefit; historical timings below remain unchanged.

## Implemented algorithm

After at least eight emitted tokens, search the current request's prompt and already confirmed output for a matching suffix of 8–16 tokens. Propose at most three following tokens. No prior request's answer is a proposal source.

Evaluate the pending confirmed token and proposals as a causal batch. At each position, sample directly from Bonsai's target logits with the same sampler settings as ordinary decoding. Accept the contiguous proposal prefix that exactly matches those sampled tokens. At the first disagreement, keep the target's replacement token, remove rejected speculative KV state, and continue. If all proposals match, the extra logits may supply a bonus target token.

Only target-confirmed tokens are emitted. A token being verified means it matches target sampling at that position, not that its factual claim is true. The deterministic lookup proposals do not need a learned draft distribution. The [speculative decoding paper](https://arxiv.org/abs/2211.17192) provides background; this implementation samples the target directly rather than implementing a learned-drafter rejection-correction scheme.

The adaptive controller estimates normal-step cost and actual verification-window cost. After three windows it disables proposals for the rest of the response unless estimated benefit exceeds 5%. These initial trials still cost time, so the guard cannot guarantee that every request is faster.

Diagnostic modes permit suffixes/depths outside the production policy and an oracle continuation. Oracle mode reads a known target answer for testing; it is not a deployable predictor.

## Historical final versus initial research policy

| Workload | Final baseline / speculation | Interpretation |
|---|---|---|
| Long repeated-text control, three pairs | 21.664 / 19.818 s median decode | 8.5% less decode time; identical text in all three pairs |
| Short 16-token answer | 3.445 / 3.034 s median decode | No proposals were launched; difference is not speculative acceleration |
| Final UI case | 105 tokens in both modes; 7 of 9 proposals accepted in 3 windows | Confirmed the old 0.7 UI integration for that case; that product control has since been removed |

The initial four-token-suffix policy showed about 20% less decode time in the long control and about 6% more in a short control; it also changed one UI answer. It was replaced by the more conservative policy. Keep [initial results](../evidence/0.7-before-outpost/speculation/initial-v1/speculation-benchmark.json) distinct from [final results](../evidence/0.7-before-outpost/speculation/speculation-benchmark.json).

## Correctness evidence and numerical boundary

- [561 checks](../evidence/0.7-before-outpost/speculation/speculation-unit.json): lookup, acceptance, cost logic, and 512 synthetic 128-token sampling traces, including the production sampler chain.
- [First logits audit](../evidence/0.7-before-outpost/speculation/speculation-audit.json): 24 positions at widths 2, 4, and 8; all 72 comparisons bitwise equal.
- [Adverse-case logits audit](../evidence/0.7-before-outpost/speculation/speculation-energy-audit.json): 64 positions; maximum absolute logit difference about 0.216, mean absolute difference 0.0249, mean KL 0.000231 nats; top-1 token matched everywhere, but no position was bitwise identical.
- [Lifecycle controls](../evidence/0.7-before-outpost/speculation/initial-v1/speculation-lifecycle.json): deliberately wrong proposals accepted 0/183; output limits, EOS, cancellation, and changed-source handling preserved.
- [Cost/cancellation guard](../evidence/0.7-before-outpost/speculation/speculation-guard.json): 0/9 wrong proposals, fallback after three windows, and no unconfirmed emission during verification cancellation.

Serial and batch computation can produce different logits. Therefore, even with the same random seed, universal literal text parity and exact implementation-level distribution equality are not promised. The filename `speculation-energy-audit.json` refers to a content fixture; it is a numerical audit, not a battery or power measurement.

The [five field fixture pairs](../evidence/0.7-before-outpost/speculation/speculation-missions.json) had matching text, including the traveler and mountaineer failures. Matching an incorrect baseline is not mission success. See the [content review](../evidence/0.7-before-outpost/speculation/mission-review.json).

## Why MTP is not available in these weights

The [locked GGUF audit](../evidence/0.7-before-outpost/speculation/model-audit.json) found 398 tensors and 36 ordinary layers in Bonsai 4B, with no MTP/nextn/draft tensors; the engine reported zero MTP layers. The smaller 1.7B checkpoint shares tokenizer fields but is a separate complete model. Tokenizer compatibility does not create an MTP head.

The official [Prism speculation guide](https://github.com/PrismML-Eng/Bonsai-demo/blob/main/SPECULATIVE.md) is a reference for compatible trained drafter setups. It is not evidence that any particular head is compatible with this locked 4B. Reopen integration when exact supported weights and their runtime contract are identified.

## Smaller-drafter cost estimate

Measured median step costs in one series were about 121 ms for 1.7B and 212 ms for 4B. An oracle verification test accepted all 47 proposals. Adding optimistic 1.7B proposal cost to verification yielded about 15.25 s versus 13.34 s of equivalent ordinary 4B work, approximately 0.875x speedup (slower).

This is a [cost estimate](../evidence/0.7-before-outpost/speculation/speculation-draft-cost.json), not a dual-model implementation. It assumes perfect acceptance and excludes auxiliary prefill, synchronization, simultaneous residency, and memory traffic. A cheaper trained head, different hardware, or a different workload could change the conclusion.

## Reconsideration gate

Require compatible trained weights, a numerical/acceptance audit, EOS and KV rollback tests, cancellation/recovery tests, paired mission measurements, and peak-memory accounting. Keep product speculation disabled. Reintroducing a control or enabling it requires representative end-to-end benefit without unacceptable quality/memory regression, plus a new decision; old copy controls alone do not meet that gate.

## New trained-drafter candidates — 2026-09-30 research

The [model survey](model-alternatives.md) pins LFM2.5-1.2B-Instruct-DSpark (about 296M parameters) and Gemma 4 E2B assistant (about 78M). They depend on their corresponding targets and state/activation interfaces; neither supplies the missing Bonsai MTP heads. Baseline new targets before testing drafting, and separately verify quantized/QAD pairing, rollback, sampling correctness and draft/verify/cancellation costs. A theoretical target-distribution guarantee is not necessarily identical sampled text for an identical seed. No weights were downloaded, no drafter was integrated, and product depth remains zero. X-01 is now actionable research for those families, not a measured speedup.

## TandemLLM follow-up — 2026-10-01

[The static review](tandemllm-review.md) maps a measured-cost chain controller onto this existing research path. TND-01 first fills speculative logit-trace coverage and normalizes timing scope; TND-02 measures current Pixel verify costs and numerical equivalence. Neither earlier emulator timings nor TandemLLM's DGX Spark tables establish a 0.16 phone gain. Product speculation remains off; trained drafters/tree verification and sampler changes remain separate work.

The 0.17 target baseline is now six decode/prompt workers with four logical attention workers. Any new proposal policy must preserve that arithmetic in both serial and verification paths and measure against its lower ordinary-step cost. The public configuration currently rejects speculation combined with fixed attention; admitting that combination is future work, not an existing product feature.
