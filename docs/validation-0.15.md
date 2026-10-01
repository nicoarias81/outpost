# Outpost 0.15 validation — Pixel ARM optimization

Validated on the registered Pixel 10 Pro on 2026-10-01. This release enables guarded ARM DotProd kernels and persistent inference workers, and supplies a measured Bonsai 4B preset for the exact tested Pixel/OS/model key. Model weights, ChatPrompt, sampling, context 2048, product output 192 and deadline 120 seconds are unchanged. Existing model selection, documents and conversation are preserved.

## Matched-output latency

Three rotated pairs per synthetic question. The corrected candidate uses 4 decode workers, 6 prompt workers, batch 128, width 8, prefill rows 1/decode rows 4 and persistent workers without affinity. The comparison uses the previous 4/4-worker backend path with disposable workers. Every emitted token, stop reason and every sampling-logit hash matches across the paired arms.

| Case | Output tokens | Median first token | Median native total | Total-time ratio |
|---|---:|---:|---:|---:|
| Late arrival | 133 | 41.08 → 12.38 s | 205.05 → 35.52 s | 5.77× |
| Manual applicability | 162 | 45.37 → 14.13 s | 262.63 → 42.32 s | 6.21× |

Both arms had an explicit 300-second research deadline to obtain a complete reference sequence; normal app calls still have 120 seconds. EOS and cap status are recorded in [the metrics](../evidence/research/arm-optimization-20261001/confirmation-metrics.json). These are known synthetic workloads on one USB-powered phone, with sampled screen/thermal state and no energy claim. They do not establish a universal device speedup or improve the model's factual reliability.

## Product-chat observations

The final UI suite used the actual selected 4/6-worker preset and normal 120-second product deadline. These are single observations from the previous and final UI runs, not the repeated-pair protocol above. All three answers remained identical.

| UI request | Previous native total | 0.15 native total | 0.15 first token |
|---|---:|---:|---:|
| Reply only Cedar | 18.805 s | 9.385 s | 9.090 s |
| Remember the prior code name | 19.835 s | 9.813 s | 9.474 s |
| Cite the filter in the imported PDF | 27.580 s | 11.350 s | 7.479 s |

[Previous UI report](../evidence/runs/chat-20261001T104406Z-e60ee466/chat-checks.json) and [final UI report](../evidence/runs/chat-20261001T141139Z-ef7751e2/chat-checks.json) preserve prompts, outputs, profile and timing. Six synthetic screenshots were reviewed; product UI remains English and chat-first.

## Correctness and rejected choices

The ARM reference already used NEON. The new SDOT path preserves the emulated dot's individual lanes and fused float accumulation order. Matrix preparation reuses activations and unpacked weights across columns/rows. 16,241 direct vector/guard cases and 144,384,240 matrix-value comparisons passed with zero bit differences. Strided inputs, odd tails, 1/4/6 workers, 9 shape groups and runtime fallback are covered.

The exploratory 6/6 worker choice was rejected: the backend's decode attention divides KV reductions by worker count, changing floating-point rounding. It diverged at output token 90 in a long answer despite matching the initial logits and first 64 tokens. A per-step trace found the first changed distribution at step 1; 4 decode/6 prompt workers preserve the trace. Fixed four-core affinity also lost to unpinned workers in the bounded controller probe. These rejected records remain preserved; neither policy is enabled.

Persistent workers are owned by a model session, paused after requests/cache release, resized safely and freed on close. No global scheduler, CPU governor, radio or security policy is changed. Unmatched hardware/OS/model keys retain conservative matrix settings, and unsupported DotProd hardware uses the backend fallback. The x86 path remains available.

## Artifact and regression gates

- App: `outpost-0.15.0-user-test.apk`, versionCode 17, SHA256 `dc8af7cb9b7d92fdee9b93c27a7879482be77338dc6a62c592e86249517f9719`.
- Test APK SHA256: `608614371ce9acdb340928215e7b6352b578da6a87098a343a0117dac405f726`.
- Native/backend revision stays `86ea01d05ec237f89b78b41c8c1ee0f908141ac7`.
- [Evidence manifest](../evidence/releases/0.15.0/manifest.json) binds confirmation, lifecycle, model admission, visual checks and x86 regression to exact APK/report hashes.
- Lint: 0 errors and 3 existing upstream warnings. Both ARM64 and x86_64 compile/package.

The physical UI suite runs against isolated synthetic state and restores the original app directories with matching hashes. Its lock-screen visibility is test-only and requires an active isolation marker plus empty personal stores/drafts. The normal app's lock-screen behavior is unchanged. The original Outpost35 APKs are restored after x86 checks; emulator-5580 is untouched. The frozen 0.14 APK remains intact.

## Remaining work

Broader phone/OS coverage, 16 KiB pages, sustained energy/thermal tests, large personal/regional collections and independent field-quality review remain open. I8MM, KV-policy changes and additional model-specific tuning are separate experiments. Preserve decode reduction semantics when changing worker counts; initial-logit or short-output equality alone is insufficient.

See [the implementation/research record](arm-optimization-2026-10-01.md), [runtime](inference-runtime.md) and [handoff](handoff.md).
