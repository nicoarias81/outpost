# Outpost 0.8.1 — Validation record

Status: maintained validation record for **Outpost 0.8.1 / version code 10**. This replaces [Outpost 0.8](validation-0.8.md) as the current record; that document remains the historical 0.8.0 baseline and was not rewritten. All execution below is on the dedicated AOSP Android 15 x86_64 emulator. No physical device, ARM runtime, or network access was involved.

## Identity and execution scope

- Application `dev.outpost.app`, version **0.8.1**, version code **10**, minSdk 28, targetSdk 35, compileSdk 35, native library `outpost_engine`, ABI `x86_64` only. Confirmed on the pulled APK with `aapt2 dump badging`: `application-label:'Outpost'`.
- Emulator: AVD `Outpost35` on `emulator-5582`, four virtual CPUs, 4 GiB configured RAM, AOSP Android 15 / API 35, no Google APIs, airplane mode on with Wi-Fi and mobile data off.
- Model under measurement: Ternary Bonsai 4B, `Q2_0 g64`, sha256 `9d968b04a3c9a794897bcc744c8072fb6a061c0e42efd03c989401ddf8baef0c`.
- Kernel selected: `Q2_0 AVX2/F16C`. AVX-VNNI and all ARM descriptors are not CPU-compatible on this target and are not compiled.

## Changes since 0.8.0

1. **Launcher label.** The launcher label is now `@string/app_name` instead of a double-encoded hardcoded `Brújula` literal. The old literal disagreed with `strings.xml` and with the R-04 acceptance criterion on resource-backed strings and stale identity references. `aapt2 dump badging` on the APK pulled off the device reports `application-label:'Outpost'`, and the app drawer shows the same.
2. **Explore screen opens on the question surface.** The slogan label, hero headline, sub-headline, the `START WITH A QUESTION` label and three hardcoded preset questions were removed, along with the now-dead helpers and seven unused string resources. A cold launch shows an empty question input with only the offline library note below it.
3. **Grouped prefill over eight activation columns.** The grouped path reuses one weight unpack across N activation columns and applies each activation block's own scale before the float accumulation, so the per-element arithmetic is unchanged and wider grouping is bit-exact by construction. Verified below across widths 1, 2, 4 and 8.
4. **Portable evidence retrieval in the test scripts.** The seven `test-*.ps1` scripts retrieve evidence with the portable `ProcessStartInfo.Arguments` string instead of `ArgumentList`, which exists only in PowerShell 6+ and is null on Windows PowerShell 5.1. Before this fix, each run passed its instrumentation and then threw `InvokeMethodOnNull` while copying evidence, losing the run's results.
5. **Calibration phases no longer overwrite dimensions they did not measure.** `calibrate` sweeps threads and batch with the grouped path held at width 1 and used to save width 1; the speculation profile diagnostic used to persist a profile and reset threads, prompt threads and batch to 4/4/128.
6. **Context speculation re-measured on the field missions.** It stays opt-in and default off. See the measurement section and the explicit non-claims.

## Verification completed

| Check | Result | Evidence |
|---|---|---|
| Build and Android Lint | `scripts/build.ps1 -Offline`, `BUILD SUCCESSFUL`; `:app:lintDebug` reported no issues | Local Gradle reports |
| Functional suite | 48 of 48 checks pass, recorded at version 0.8.1 | [Report](../evidence/checks.json) |
| Evaluation manifest validator | `python eval/validate.py` exits 0; declared identity matches `app/build.gradle` at 0.8.1 / code 10 | Local validator output |
| Q2 dot kernel | 16,241 vectors against the reference, zero bit mismatches; guard pages, runtime dispatch and fallback passed; microbenchmark speedup 5.79x (median of three warm-cache rounds, 20,000 dots each) | [Report](../evidence/optimization/kernel-numeric.json) |
| Grouped prefill guard | 10,023 bitwise comparisons, zero bit mismatches, real GGML graph dispatch confirmed, across widths 1, 2, 4 and 8, column counts 1/2/3/4/5/9/17 and 1/2/4 workers | [Report](../evidence/optimization/kernel-batch.json) |
| Teacher-forced audit of the batched path | At widths 2, 4 and 8: 24 positions each, `bitIdenticalPositions` 24/24, `sameTop1Positions` 24/24, maximum logit difference 0, mean KL 0 | [Report](../evidence/speculation/speculation-audit.json) |
| Field fixtures at width 1 vs width 8 | Five fixtures, byte-identical text and identical first-logits hashes per fixture | [Report](../evidence/strata/runtime-missions.json) |
| Speculation on field missions | Adaptive depth 3 vs baseline; identical text on all five | [Report](../evidence/speculation/speculation-missions.json) |
| Verbatim-copy control | Median decode speedup 1.215, 53 of 54 drafts accepted | [Report](../evidence/speculation/speculation-benchmark.json) |
| Bonsai 4B end-to-end UI generation | Completed with a sourced answer under the calibrated width-8 profile; fast path used | [Report](../evidence/bonsai-ui-bonsai4.json) |
| Calibration re-confirmation | Chose `{threads 4, promptThreads 4, batch 128, width 8}`; confirmation gain 0.85%, below the 5% adoption threshold | [Report](../evidence/strata/runtime-calibrate.json) |

The previous grouped-kernel ceiling of 7,932 comparisons covered only widths up to 4; the current guard doubles the covered widths and raises the comparison count to 10,023.

**Evidence provenance.** Every JSON file cited above except `checks.json` records `version` 0.8.0, because that field is the identity of the build that produced the run and these measurements were taken before the version was raised. `evidence/checks.json` is the only file cited here that the 0.8.1 build produced. Read the `version` field as run provenance, never as a statement about the current release, and do not "refresh" the older evidence to match 0.8.1: re-running a phase would replace a preserved measurement, and the recorded build identity would then be a claim rather than a fact.

## Measured runtime observations

**Width sweep.** Three alternating rounds with rotating order, 222-token prompt and 32-token output. The recorded run that adopted width 8 reports median prefill 21,594 / 20,224 / 18,449 / 17,419 ms and median total 26,450 / 25,097 / 23,255 / 22,189 ms for widths 1 / 2 / 4 / 8 ([runtime-batch](../evidence/strata/runtime-batch.json)), so width 8 is 19.3% less prefill and 16.1% less total than width 1, and 5.6% less prefill and 4.6% less total than width 4. A second sweep of the same control, retained only as local git-ignored output, gave 18.2% and 14.5% against width 1 and 3.5% and 2.2% against width 4. Width 8's advantage over width 4 therefore lies between 3.5% and 5.6% of prefill and between 2.2% and 4.6% of total across the two sweeps. Every width keeps identical first-logits hashes and identical text, on the farmer-record control in the sweep and on all five field fixtures. These are single-emulator observations, not controlled measurements of physical-phone performance.

**Speculation on field missions.** Adaptive depth 3 against baseline, decode milliseconds: traveler 7,322 -> 7,118; farmer 2,385 -> 2,508; field engineer 10,426 -> 10,419; mountaineer 11,850 -> 11,897; driver 1,270 -> 1,586. Aggregate decode time is 0.8% slower. Farmer (+5.2%) and driver (+24.9%) regress; traveler improves by 2.8%; field engineer and mountaineer stay within 0.5% of baseline. All five keep identical text. A synthetic verbatim-copy control shows a median decode speedup of 1.215 (12,146 -> 9,993 ms) with 53 of 54 drafts accepted, which is why the synthetic case alone is not sufficient evidence to turn speculation on.

**End-to-end UI generation.** Real generation with Bonsai 4B under the calibrated width-8 profile completed with a sourced answer: 114 tokens, first token at 17,650 ms, 39,446 ms total, fast path used. This is one emulator observation, not a benchmark.

**Calibration re-confirmation.** Re-confirmation on this target chose `{threads 4, promptThreads 4, batch 128, width 8}` with a confirmation gain of 0.85%, below the 5% adoption threshold, so it kept the incumbent threads and batch rather than chasing noise.

## Artifact and publication

- `dist/outpost-0.8.1-emulator-debug.apk`, **8,973,271 bytes**, SHA-256 `5551f8047b1746b74e8e04ab0f7778d6c3e08457cc9134000b1c783bd8511676`, with an LF-terminated sidecar `dist/outpost-0.8.1-emulator-debug.apk.sha256` that verifies with `sha256sum -c`.
- The APK declares no `uses-permission` at all, so it cannot perform network requests.
- `dist/` is ignored by Git and is not guaranteed to exist in a fresh clone.
- Publication is reproducible: [publish-artifact.ps1](../scripts/publish-artifact.ps1) reads the version from `app/build.gradle`, copies the built APK into `dist/` under the versioned name, and writes an LF sidecar; `-Verify` re-checks an existing artifact against its sidecar.
- Fixed defect: nine earlier sidecars in `dist/` were written with CRLF endings, which made `sha256sum -c` fail on non-Windows hosts even though the recorded hashes were correct. All were normalized to LF, and all ten sidecars now verify.
- [outpost-0.8.0-emulator-debug.apk](../dist/outpost-0.8.0-emulator-debug.apk) and its sidecar remain in place as the historical 0.8.0 build.

## Explicit non-claims

- All results are emulator measurements on x86_64. They do not establish ARM support, Pixel or GrapheneOS behaviour, battery life, thermal behaviour, or phone latency. The APK contains only x86_64 code.
- Post-run PSS samples are not peak memory. No peak-memory or memory-pressure claim is made here.
- A passing harness, a citation index in range, or a favourable reviewer score does not establish answer correctness. The mission fixtures still record the known 0.8 failures — the farmer fixture does not identify F-28, the field engineer answer overstates the absence of authorization records, and the traveler answer is partial ([content review](../evidence/strata/mission-review.json)); this document does not re-review them and does not claim they improved.
- Width 8 is adopted on bit-exact parity and a monotone trend rather than on a large margin: across two sweeps its advantage over width 4 is 3.5% to 5.6% of prefill and 2.2% to 4.6% of total, so part of the observed range sits below the 5% working threshold.
- No bounty requirement is claimed as met. Device, GrapheneOS, RAM, storage, phone-speed and public-repository requirements cannot be verified in emulator-only, private-repository scope; the trace lives in [bounty-31](bounty-31.md).
- The 0.8.0 record in [validation-0.8.md](validation-0.8.md) and everything under `evidence/` remain historical and were not rewritten.

## Remaining scope

Field workflows on real hardware, larger knowledge packages, OSM/ZIM/PDF adapters, ARM APK execution, real memory pressure, physical phone performance, thermal behaviour, and battery use remain unvalidated or unimplemented as described in the [roadmap](roadmap.md).
