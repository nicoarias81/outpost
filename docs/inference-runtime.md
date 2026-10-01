# Inference runtime

Status: implementation reference for Outpost 0.14.0. The owned Q2 wrappers now support guarded output-row reuse and separate multi-column/single-column policies; vendor backend, model weights and sampler remain pinned and unchanged. See [architecture](architecture.md) for the application flow and [optimizations](optimizations.md) for measurements.

## Model identities

| Profile | Locked file format | Bytes | Role |
|---|---|---:|---|
| `qwen15` | Qwen2.5 1.5B Instruct Q4_K_M | 1,117,320,736 | Functional baseline; greedy sampling |
| `bonsai17` | Ternary Bonsai 1.7B Q2_0 g64 | 490,163,968 | Smaller generator; sampled |
| `bonsai4` | Ternary Bonsai 4B Q2_0 g64 | 1,137,806,656 | Main optimization target; sampled |
| Kev | Kev 0.8B Q8_0 plus auxiliary head/config | 811,843,040 GGUF bytes | Research-only first-sentence classifier; no product UI |

Exact publishers, revisions, and hashes are in [model-lock.json](../model-lock.json), [bonsai-lock.json](../bonsai-lock.json), and [judge-lock.json](../judge-lock.json). Profiles accept their exact locked files; generic arbitrary-GGUF loading is not supported. The GGUFs are separate from the APK. Kev's 2,099,200-byte head and small configuration are packaged as assets.

The Bonsai matrix blocks contain 64 two-bit entries plus an FP16 scale: 18 bytes / 64 weights = 2.25 effective bits per matrix weight. The entropy of three states, approximately 1.58 bits, is not the storage cost of these files. Q2_0 g128, g64, and PQ2_0 must not be treated as interchangeable layouts.

## Backend and sampling

The pinned llama.cpp revision is `86ea01d05ec237f89b78b41c8c1ee0f908141ac7`. Vendor sources are unchanged. CMake disables server/tools/examples, dynamic backend loading, OpenSSL, and blanket host-native CPU flags. Own functions wrap selected GGML symbols at link time.

Bonsai uses top-k 20, top-p 0.8, temperature 0.7, seed 42. Its fixed closed empty `think` prefix is reproduced from the model template. These choices are an experimental baseline, not a universal quality optimum. Changing prompts, tokenization, or sampling requires fresh quality and performance evidence. Current application prompts explicitly request English. Historical Spanish measurements remain linked as historical experiments; translation changes tokenization and requires its own baseline.

One model is loaded in the native session at a time. Selecting another generator or Kev changes model residency; the on-disk model files remain independent. There is no dual-model drafter running alongside Bonsai 4B.

## Capability detection and selection

| Candidate | Requirement category | Implementation state |
|---|---|---|
| Original reference | Baseline supported ABI | Compiled fallback |
| AVX2/F16C | CPU instruction support plus OS vector-state support | Compiled custom Q2 path |
| AVX-VNNI | Separate x86 capability; not equivalent to AVX-512 VNNI | Descriptor only |
| AVX-512 VNNI | CPU extensions plus required OS extended-state support | Descriptor only |
| ARM NEON / DotProd / I8MM | Android ARM HWCAP/HWCAP2 as applicable | Descriptors; custom optimized kernels pending |

The registry separately reports CPU compatibility, compiled implementation, enabled policy, and selected path. Detection never makes a missing implementation executable. Synthetic dispatch checks test policy; they do not exercise unsupported instructions. The current APK packages x86_64 and ARM64. The [authorized Pixel 10 Pro trial](pixel10-results-2026-10-01.md) now verifies bounded ARM model admission and product chat in addition to the historical x86_64 emulator checks. That phone detects NEON, DotProd and I8MM but selects the Q2 reference path; custom ARM kernels remain pending. Other devices, 16 KiB pages and broad stress/field validation are not established by this trial.

The measured profile key includes Android fingerprint/API, ABI, feature mask, online CPU count, app version, kernel identifier, and model SHA-256. A different key returns a conservative default: at most 4 decode/prompt threads, batch 128 and width 1, marked unmeasured. The key uses app `versionName`, not the source/APK hash; changing code without a version change does not automatically invalidate it. Calibration is a developer action, not an automatic startup benchmark. Current Settings exposes model preparation, not runtime calibration/metrics. Any future runtime UI must distinguish unsupported or unmeasured states.

## Cache invariants

1. Match token IDs exactly; semantic similarity is not sufficient.
2. For an identical prompt, retain its KV state and saved final-prompt logits. Start a fresh sampler and sample the first output from those logits.
3. For a partial match, reuse only complete batches aligned with cold execution. Recompute the remaining prompt.
4. Remove generated response tokens from attention before the next request. Caching does not create conversational history.
5. Invalidate incompatible model, thread/batch/width/kernel settings, and contexts affected by cancellation or error.
6. Release context on backgrounding or the relevant memory callback; do not retain it when Android reports low memory.

Re-evaluating only the last token of an identical prompt was tried and replaced after UI logits/text changed. The failed record remains part of the [cache evidence](../evidence/0.7-before-outpost/strata/cache-initial-ui-mismatch.json).

## Budgets and observability

The context limit is 2,048 tokens. Normal UI output is bounded to 192 tokens and 120 seconds. Product chat forces speculation depth 0 regardless of retained research preferences. Diagnostic test configurations may use smaller output budgets or fixed speculative depths; do not confuse them with UI defaults.

`NativeEngine.Result` records prompt/output tokens; load, preparation, prefill, decode, first-token and total milliseconds; reused tokens and first-logits fingerprint; stop reason; proposed/accepted tokens; verification/rejection windows; plain steps; verification/proposal microseconds; and cost-controller disablement. The JNI result array has 19 numeric slots plus text delivered through callbacks. Any schema change must update both sides and the instrumented consumers.

First token, complete answer, and first useful source are different latency metrics. Current engine timing does not implement the full product metric of time to a useful field outcome. Post-run PSS does not establish peak memory or energy consumption.

## Calibration report semantics in 0.9

`RuntimePolicy` (instrumented-test support) reports `fastestWidth`, `candidateWidth` and `selectedWidth` separately. It selects the narrowest candidate within 5% of the fastest and adopts it only with parity and baseline/selected speedup >1.05. A rejected candidate reports the actual baseline selection, not the candidate's gain. `selectedSpeedup = baselineMedianMs / selectedMedianMs`; time reduction is `100 * (1 - selectedMedianMs / baselineMedianMs)`.

Boundary cases are covered in the knowledge suite. The historical 0.9 [width control](../evidence/archive/0.9.0/strata/runtime-batch.json) selects width 4 at 1.149x / 12.98% less elapsed time. This improves reporting precision; it is not a newly optimized kernel in 0.9. Formal controller review status is recorded separately in the [handoff](handoff.md).

## Row policy and measured adoption in 0.14

`NativeEngine.Configuration` and its JNI call now carry separate `rowTile` and `decodeRows` fields. `rowTile` selects one/two output rows for eligible multi-column matrices; `decodeRows` selects one/two/four for single-column matrices. This distinction follows matrix shape, not an explicit generation-phase tag: a one-column prefill tail uses the single-column policy too. Product speculation remains off. Each policy change invalidates cached computation, alongside existing model/thread/batch/width/kernel conditions.

The two-row kernel reuses Q8 loads/corrections across adjacent output rows and retains each output's scale/accumulation order. Existing type/stride/workspace/CPU/reference guards remain; insufficient rows per worker and unsupported shapes fall back. No model-wide dequantized copy or scratch-space extension is introduced. ARM keeps the backend/reference route; no custom ARM/VNNI implementation was added.

`RuntimeSettings` uses kernel identity `q2-row-v3-phase` and persists both dimensions. Unmatched hardware/model/build keys retain the conservative width 1 / row 1 / decode 1 default. Legacy thread/batch/width calibration preserves the row dimensions it did not measure. The validated Outpost35 Bonsai 4B profile is 4/4 threads, batch 128, matrixWidth 4, rowTile 2 and decodeRows 1. It is a local measured profile, not a universal phone default or automatic startup benchmark.

The selected policy improves prompt-oriented work while retaining the old decoder. Combined two/four-row decode experiments did not justify product adoption. [Measurement and decisions](kernel-rows-0.14.md) records the paired controls, cancellation/cache/model-switch checks and limitations.

## Alternative-model research

The [model survey](model-alternatives.md) now has an executed [Spark 1.7B research slice](spark-candidate-results-2026-10-01.md). `GenerationPolicy` is explicit:0 retains the legacy formatter;2 uses the bounded Spark no-thinking formatter with full SWA storage;1 is its experimental compact-cache alternative. Sampler 0 is greedy,1 retains Bonsai's prior top-k 20/top-p 0.8/temperature 0.7, and2 uses Spark's top-p 0.95/temperature 1.0 without top-k. Seed is explicit. Existing product boolean calls map to their former policy and seed 42.

The Spark profile is hash-pinned in test assets and absent from ModelStore.PROFILES. Its marker/vocabulary checks, segmented literal-data tokenization, prefix retention check and lifecycle evidence are described in [candidate testing](candidate-testing.md). New formatter/cache policy invalidates context reuse. Compact/full first logits differed despite identical short text, so full storage remains the comparison policy; this is not evidence of a general quality loss or a validated compact optimization. Backend/model product pins and Q2 kernels remain unchanged. E-07/E-05 remain partial beyond this slice; [Engram](engram-review.md) is still conditional trained-architecture research.
