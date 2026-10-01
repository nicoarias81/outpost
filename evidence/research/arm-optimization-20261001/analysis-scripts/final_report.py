"""Render final release documentation from verified run identities; invoke after all gates."""
from pathlib import Path
import json,sys
root=Path('E:/projects/outpost');here=Path(__file__).resolve().parent
def read(p):return json.loads(p.read_text(encoding='utf-8-sig'))
manifest=read(Path(sys.argv[1]))
assert manifest['version']=='0.15.0' and manifest['passed']
metrics=read(root/manifest['metricsPath'])
rows=[]
for case in metrics['cases']:
    before=case['arms']['legacy'];after=case['arms']['optimized']
    rows.append(f"| {'Late arrival' if case['case']=='case0' else 'Manual applicability'} | {case['tokens']} | {before['medianFirstTokenMs']/1000:.2f} → {after['medianFirstTokenMs']/1000:.2f} s | {before['medianTotalMs']/1000:.2f} → {after['medianTotalMs']/1000:.2f} s | {case['nativeTotalMedianRatio']:.2f}× |")
text=f'''# Outpost 0.15 validation — Pixel ARM optimization

Validated on the registered Pixel 10 Pro on 2026-10-01. This release enables guarded ARM DotProd kernels and persistent inference workers, and supplies a measured Bonsai4 preset for the exact tested Pixel/OS/model key. Model weights, ChatPrompt, sampling, context2048, product output192 and deadline120 seconds are unchanged. Existing model selection, documents and conversation are preserved.

## Matched-output latency

Three rotated pairs per synthetic question. The corrected candidate uses4 decode workers,6 prompt workers, batch128, width8, prefill rows1/decode rows4 and persistent workers without affinity. The comparison uses the previous4/4-worker backend path with disposable workers. Every emitted token, stop reason and every sampling-logit hash matches across the paired arms.

| Case | Output tokens | Median first token | Median native total | Total-time ratio |
|---|---:|---:|---:|---:|
{chr(10).join(rows)}

Both arms had an explicit300-second research deadline to obtain a complete reference sequence; normal app calls still have120 seconds. EOS and cap status are recorded in [the metrics]({manifest['metricsLink']}). These are known synthetic workloads on one USB-powered phone, with sampled screen/thermal state and no energy claim. They do not establish a universal device speedup or improve the model's factual reliability.

## Correctness and rejected choices

The ARM reference already used NEON. The new SDOT path preserves the emulated dot's individual lanes and fused float accumulation order. Matrix preparation reuses activations and unpacked weights across columns/rows.16,241 direct vector/guard cases and144,384,240 matrix-value comparisons passed with zero bit differences. Strided inputs, odd tails,1/4/6 workers,9 shape groups and runtime fallback are covered.

The exploratory6/6 worker choice was rejected: the backend's decode attention divides KV reductions by worker count, changing floating-point rounding. It diverged at output token90 in a long answer despite matching the initial logits and first64 tokens. A per-step trace found the first changed distribution at step1;4 decode/6 prompt workers preserve the trace. Fixed four-core affinity also lost to unpinned workers in the bounded controller probe. These rejected records remain preserved; neither policy is enabled.

Persistent workers are owned by a model session, paused after requests/cache release, resized safely and freed on close. No global scheduler, CPU governor, radio or security policy is changed. Unmatched hardware/OS/model keys retain conservative matrix settings, and unsupported DotProd hardware uses the backend fallback. The x86 path remains available.

## Artifact and regression gates

- App: `{manifest['artifactName']}`, versionCode17, SHA256 `{manifest['appSha256']}`.
- Test APK SHA256: `{manifest['testApkSha256']}`.
- Native/backend revision stays `86ea01d05ec237f89b78b41c8c1ee0f908141ac7`.
- [Evidence manifest]({manifest['manifestLink']}) binds confirmation, lifecycle, model admission, visual checks and x86 regression to exact APK/report hashes.
- Lint:0 errors and3 existing upstream warnings. Both ARM64 and x86_64 compile/package.

The physical UI suite runs against isolated synthetic state and restores the original app directories with matching hashes. Its lock-screen visibility is test-only and requires an active isolation marker plus empty personal stores/drafts. The normal app's lock-screen behavior is unchanged. The original Outpost35 APKs are restored after x86 checks; emulator-5580 is untouched. The frozen0.14 APK remains intact.

## Remaining work

Broader phone/OS coverage,16KiB pages, sustained energy/thermal tests, large personal/regional collections and independent field-quality review remain open. I8MM, KV-policy changes and additional model-specific tuning are separate experiments. Preserve decode reduction semantics when changing worker counts; initial-logit or short-output equality alone is insufficient.

See [the implementation/research record](arm-optimization-2026-10-01.md), [runtime](inference-runtime.md) and [handoff](handoff.md).
'''
(here/'files/docs/validation-0.15.md').write_text(text,encoding='utf-8')
print('Final validation document staged from the verified manifest.')
