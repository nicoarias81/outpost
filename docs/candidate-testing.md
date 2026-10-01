# Candidate model testing

Research work starting 2026-10-01 from `9cca240`. Spark-X2.5-1.7B is a pinned **test-only** profile, not a fourth product model. Product Settings, existing model locks and Bonsai selection remain unchanged. This development build retains version 0.14.0/code16; identify it by source fingerprints and APK hashes. The frozen `dist/outpost-0.14.0-user-test.apk` is a separate published local artifact and must not be overwritten with different same-version bytes.

## Preparation and commands

Read the [emulator runbook](emulator-runbook.md), verify Outpost35 / emulator-5582, and keep all model/numerical execution inside that emulator. Resolve Python/SDK/JDK/Gradle through local settings. Run sequentially from the repository:

```powershell
# Use the configured Python executable for this script.
python scripts/prepare-spark-research.py
./scripts/build.ps1 -Offline
./scripts/test-candidate.ps1 -Phase admission
./scripts/test-candidate.ps1 -Phase pilot -SkipInstall
./scripts/test-candidate.ps1 -Phase heldout -SkipInstall
./scripts/test-candidate.ps1 -Phase timing -SkipInstall
```

Provisioning validates the dedicated offline emulator, downloads only the pinned official file when absent, hashes it, uses binary ADB file transfer and verifies the app-private copy. It does not select a product model or install an APK. The admission wrapper installs both freshly built APKs unless `-SkipInstall` is used; that option verifies installed hashes. Keep other runtime/install workflows idle during a run. No host model execution or physical phone is involved.

The test profile and templates are bound by [the lock](../app/src/androidTest/assets/candidates/spark17-lock.json); [mission definitions](../app/src/androidTest/assets/candidates/missions-v1.json) contain eight development and 24 held-out cases. They are authored synthetic text records, not real field data or a PDF/CSV parser benchmark. Expected outcomes are preserved for review and never passed to the model. The product APK contains neither these fixtures nor the candidate profile.

## Protocol and sampler

`NativeEngine.GenerationPolicy` selects the legacy built-in formatter or an explicit Spark two-message/no-tools/no-thinking formatter. The product's boolean sampling method delegates to its prior greedy/Bonsai policy unchanged. Spark uses either greedy diagnostics or its recorded temperature1.0/top-p0.95/no-top-k sampler with explicit seed. Product default sampling is not replaced.

The Spark formatter matches the pinned GGUF template for system/user messages with `enable_thinking=false`. It emits exact vocabulary delimiters and tokenizes message bodies separately. The pinned backend still recognizes USER_DEFINED tokens with special parsing disabled, so content is additionally split after each `<` before tokenization. All special spellings in this pinned vocabulary begin with that character; the split preserves text bytes but prevents complete control/reasoning/tool markers in source content. There is no automatic BOS insertion. It rejects other architectures/vocabularies and disallows the unvalidated speculative path. Adapter identity is `spark-two-message-no-thinking-v1`; support is bounded to the verified research file, not arbitrary Spark models/templates. Template and vocabulary parity checks execute on Android.

The 2,048-token context, 192-token mission output and 120-second native deadline remain unchanged. Existing context cache compatibility now includes formatter identity. Spark prefix reuse also checks the backend's retained position range: suffix removal cannot restore already evicted sliding-window history. If the full preceding 512-token window is unavailable, the engine starts cold. The research harness tests short/long exact reuse, late partial reuse and earlier-source rewinds against cold results, cancellation/recovery and model switching. A safe cold fallback is recorded rather than presented as cache acceleration.

## Phases and evidence

- **Admission:** full Android model hashes, exact template/token parity, literal control strings, UTF-8, a context crossing the512-token sliding window, cancellation, recovery and legacy Bonsai/Qwen paths. These are integration checks, not quality scores.
- **Pilot:** eight development tasks, each with fixed evidence and actual SQLite text retrieval, paired across Spark1.7B and Bonsai4B. Both use greedy diagnostics. Isolated per-run databases are removed after each fixture; product library/conversation are not modified. Retrieval includes the product's empty-result previous-question fallback.
- **Heldout:** 24 predeclared tasks, fixed evidence, paired greedy runs. No parameter/prompt changes based on held-out outputs. This isolates generation and does not establish full retrieval quality or field acceptance.
- **Timing:** first two development tasks, three alternating pairs using each model's recorded sampled policy and seed42. Each measured call follows a same-model eight-token warmup. Exclude warmup rows; evaluate EOS/quality/length before comparing completion times. This is a development timing control, not a held-out win or a seed sweep.

All phases record full synthetic prompts, selected source identities where applicable, output, token IDs, cache use, stop reasons, native phase timings and explicit configuration. Model-cold calls can still have warm storage pages after hash verification; do not label them cold-storage measurements. Bonsai uses its measured4/4-thread, batch128, width4, rows2/1 policy; Spark begins with4/4 threads, batch128 and width/rows1. They are current best-known baseline versus conservative candidate, not identically tuned kernels.

PSS/RSS and self process fault counters are sampled at a requested 200 ms interval. Reports include actual maximum sampling gap; unavailable fields remain absent/null. Sampled maxima can miss peaks, and the sampler adds overhead to both arms. A model-switch call can include the previous model in its initial sample; use post-warmup timing rows for steady-model comparisons. Fault counters do not count physical storage reads. No host storage tuning or cold-cache forcing is performed.

`evidence/runs/candidate-*` preserves run identity, build/APK hashes, frozen fixtures/lock, relevant source snapshots, logs and partial result JSON. A running/failed record remains distinguishable from a returned result. The wrapper verifies run/fixture identity and execution status; it never scores answer correctness from a PASS line. Review quality separately against supported outcomes and attribute the reviewer. Existing fixtures-v7 remains the independent historical evidence-only evaluation protocol.

## Status

Implementation and dual-ABI build/lint prepared; admission and comparative results are recorded in the subsequent run/validation checkpoint. No candidate is promoted by this preparation document. E-07/E-05, P-04/P-07 and E-08 remain distinct: sampled native-call memory does not implement complete lifecycle tracing or a storage benchmark.
