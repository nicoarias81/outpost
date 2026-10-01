from pathlib import Path

base=Path(__file__).resolve().parent/'files'
def change(name,old,new):
    path=base/name;text=path.read_text(encoding='utf-8');assert old in text,(name,old);path.write_text(text.replace(old,new),encoding='utf-8')
change('docs/inference-runtime.md','Status: implementation reference for Outpost 0.14.0. The owned Q2 wrappers now support guarded output-row reuse and separate multi-column/single-column policies; vendor backend, model weights and sampler remain pinned and unchanged.','Status: implementation reference for Outpost0.15.0. The owned wrappers support guarded x86 and ARM Q2 paths. ARM adds exact-lane DotProd, prepared activations/column/decode-row reuse and persistent workers; vendor backend, model weights and sampler remain pinned and unchanged.')
change('docs/inference-runtime.md','| ARM NEON / DotProd / I8MM | Android ARM HWCAP/HWCAP2 as applicable | Descriptors; custom optimized kernels pending |','| ARM NEON | Baseline ARM SIMD | Available through the original backend fallback |\n| ARM DotProd | NEON and HWCAP_ASIMDDP | Compiled guarded custom Q2 path, validated on the registered Pixel |\n| ARM I8MM | NEON and HWCAP2_I8MM | Descriptor only; no custom I8MM kernel |')
change('docs/inference-runtime.md','That phone detects NEON, DotProd and I8MM but selects the Q2 reference path; custom ARM kernels remain pending.','That initial trial selected the backend reference path. The subsequent [0.15 validation](validation-0.15.md) admits custom DotProd and persistent workers; I8MM remains pending.')
change('docs/inference-runtime.md','## Cache invariants','''## ARM policy in0.15

The Q2 ARM baseline uses NEON and emulates dot products. Native SDOT groups products differently, so the wrapper explicitly preserves the old lane grouping, fused float accumulation and final reduction. Prepared activation data stays inside the existing per-graph workspace; model weights are not expanded or rewritten. The exact Pixel/Bonsai4 preset uses4 decode workers,6 prompt workers,batch128,width8,prefillRows1/decodeRows4 and no affinity. It is gated by the recorded OS fingerprint,8 CPUs,featureMask7168,model identity and active DotProd path. Other keys keep conservative matrix settings.

ARM configuration defaults use session-owned pools; x86 defaults keep their previous worker lifecycle. Pools are paused after each request and cache release, and freed on close. A context is destroyed before any referenced pool is freed or resized because the CPU backend retains that pointer. Research affinity is restricted to eligible Outpost threads and restores the caller's original mask; no fixed affinity is shipped. ARM calibration uses `q2-arm-dot-pool-v1`.

Changing decoder thread count can change the backend's split-KV attention reduction and therefore rounding. The6/6 candidate was rejected after a long answer diverged. The adopted4/6 policy matches every sampling-logit hash and token in complete confirmation. Logit tracing and extended per-session deadlines are explicit test APIs, disabled in ordinary app requests. Research comparison uses300 seconds on both arms; product output remains192 tokens and120 seconds.

## Cache invariants''')
change('docs/inference-runtime.md','ARM keeps the backend/reference route; no custom ARM/VNNI implementation was added.','That0.14 x86 experiment did not add ARM/VNNI kernels; the separate0.15 ARM policy is described above.')
change('docs/inference-runtime.md','Backend/model product pins and Q2 kernels remain unchanged. E-07/E-05','That Spark admission slice did not change Q2 kernels; subsequent0.15 ARM work is separate. Backend/model pins remain unchanged. E-07/E-05')
change('docs/optimizations.md','## Measurements and attribution','''## Current ARM result

[0.15 validation](validation-0.15.md) adopts guarded DotProd, activation/column/decode-row reuse and persistent workers for the tested Pixel/Bonsai preset. Three exact full-trace pairs show205.05→35.52s and262.63→42.32s native medians; first-token medians41.08→12.38s and45.37→14.13s. Both comparison arms used300-second research deadlines; normal app limits are unchanged. This is a combined runtime improvement on two known workloads, not a DotProd-only or universal device speedup.

Six decode workers were rejected after attention-reduction drift, and fixed affinity was slower in the controller probe. The selected policy retains4 decode/6 prompt workers. Kernel correctness, seeded output equality and answer quality remain distinct. [The experiment record](arm-optimization-2026-10-01.md) preserves successful and rejected runs.

## Measurements and attribution''')
change('docs/optimizations.md','The [first physical trial](pixel10-results-2026-10-01.md) confirms all three ISA capabilities but selects the unoptimized Q2 reference. Require numerical/guard/lifecycle checks and controlled full-answer benefit before enabling an ARM candidate; other devices remain outside scope','The first trial used the backend NEON reference. [0.15](validation-0.15.md) now adopts guarded DotProd after numerical, lifecycle and full-logit confirmation. I8MM, additional shapes/devices and energy remain open; other phones require explicit scope')
change('docs/pixel10-testing.md','## Current status','''## Current0.15 operations

[Current validation](validation-0.15.md) supersedes the reference-path timings below. Product ARM calls enable DotProd only when supported and use persistent workers; the exact tested Pixel/Bonsai4 key selects4 decode/6 prompt,batch128,width8,rows1/4,no affinity. Normal calls retain120 seconds/192 output tokens. `test-arm.ps1` supports numeric/controller/tune/lifecycle/trace/confirm research; execute through the isolation wrapper and inspect its journal before resuming.

The status-only ARM test Activity may display over keyguard using standard Android show-when-locked/turn-screen-on APIs and requires resumed/focused/interactive state. Device authentication stays enabled. The isolated product UI suite can do the same only with an active app-private isolation marker and empty chat/library/drafts. It clears the temporary Activity task state and never displays the original private UI. Direct non-isolated visual tests still require an unlocked device. The wrapper removes its marker after original file hashes are restored. No global screen/security/radio setting is changed by this policy.

Use `test-x86-regression.ps1` for bounded ABI regression; it archives prior fixed-name reports and restores the original Outpost35 APKs after checking the new build. Do not use legacy scripts that overwrite historical host outputs without archiving them.

## Initial phone-trial checkpoint (historical)''')
change('docs/pixel10-testing.md','The owner must unlock the phone; the harness checks the keyguard and keeps its Activity window awake.','For current isolated runs, the test-only visibility policy above can keep synthetic UI visible over keyguard; ordinary/direct visual runs require an unlocked phone.')
change('docs/emulator-runbook.md','## Identity and last observed state','''##0.15 compatibility checkpoint

The0.15 x86 regression verifies numeric kernels, dispatch, grouped graphs and a real Bonsai decoder pair, then restores the previously installed app/test APK bytes. [Current validation](validation-0.15.md) owns those identities. Do not assume the emulator has the latest local APK after this wrapper; verify before `-SkipInstall`. Emulator-5580 remains preserved.

## Identity and last observed state''')
print('Runtime and operational documentation staged.')
