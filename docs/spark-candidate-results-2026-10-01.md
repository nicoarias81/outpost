# Spark-X2.5-1.7B admission and comparison

**Decision:** Spark now runs through a pinned emulator research path. Keep it outside product Settings and retain the existing product selection. Neither model establishes a reliable replacement in this study. The compact Spark cache also remains experimental after a numerical-equivalence gate failed.

This work implements the first E-07 candidate slice and advances E-05 with actual responses, not publisher benchmarks. See [commands and protocol](candidate-testing.md), [metrics](../evidence/research/spark-admission-20261001/metrics.json), and the attributed [pilot](../evidence/research/spark-admission-20261001/pilot-review.json), [reserved-case](../evidence/research/spark-admission-20261001/heldout-review.json) and [sampled timing](../evidence/research/spark-admission-20261001/timing-review.json) reviews. All execution used offline **Outpost35 / emulator-5582**, x86_64, four virtual CPUs and 4 GiB configured RAM. ARM64 compilation is not phone runtime evidence.

## Implemented and verified

- Verified the official 1,107,457,856-byte Spark Q4_K_M file on the host and inside Android. SHA-256: `902bde2522394954ac17821b3e5fd0df02defbc6944f122253f2580acf0503f4`. Weights remain outside Git and the APK. [Header/provenance](../evidence/research/spark-admission-20261001/verified-model-header.json).
- Added an explicit two-message/no-tools/no-thinking Spark protocol, separate sampler policy and cache-policy identity. Existing product calls keep their prior greedy/Bonsai behavior. Spark comparison uses **full SWA storage, policy 2**; compact storage is policy 1, for rejected/conditional research only.
- Discovered that the backend recognizes USER_DEFINED tokens even with special parsing disabled. Segmenting source content after `<` prevents complete control/reasoning/tool markers while preserving literal text bytes. Android checks cover normal-template token parity, Unicode, empty system text and quoted control strings.
- Added a retained-window check before compact-cache prefix reuse. A successful suffix removal does not restore evicted history; unavailable history starts cold. Full-cache tests cover long exact/partial reuse, earlier source changes, cancellation/recovery and model switching.
- Added a guarded provisioning script, immutable run wrapper, frozen test-only missions, sampled memory/fault observations and a result summarizer. No Relay SDK, external telemetry, physical device, storage policy or network permission was added.

Final [admission](../evidence/runs/candidate-admission-20261001T072903Z-83456a7a/candidate-checks.json) passed **21 execution checks**. Real product chat/PDF/import/persistence/cancellation regression passed **45 checks**, including actual Bonsai generation: [chat run](../evidence/runs/chat-20261001T085833Z-62f694d5/chat-checks.json). Both ABIs build; lint reports 0 errors and 3 existing upstream warnings. The product APK contains no candidate fixtures or GGUF.

## What the quality study found

The pilot used eight development questions, fixed evidence and actual SQLite retrieval, with both models. Within each model, the two arms produced identical prompts and texts for every question. Those 32 records are **eight unique questions**, not 32 independent quality samples. The retrieval corpus was a tiny isolated fixture collection; this does not evaluate a real personal library or regional coverage.

The next 24 questions were frozen before candidate execution and used fixed evidence. No model, prompt or sampler was tuned from their outputs. Both sweeps used greedy decoding, `chat-v1.1`, 2,048 context, 192 output tokens and a 120-second deadline. Greedy diagnostics are **not** the current product Bonsai sampler or proof of a model's best configuration.

| Attributed development review | Spark 1.7B | Bonsai 4B |
|---|---:|---:|
| Pilot: fully supported outcomes / 8 | 5 | 4 |
| Reserved: fully supported outcomes / 24 | 13 | 13 |
| Reserved: partial / 24 | 6 | 5 |
| Reserved: failed / 24 | 5 | 6 |
| Reserved: output-limit stops / 24 | 1 | 0 |

These are the implementing assistant's descriptive **candidate-review-v1** judgments, not independent expert review, statistical accuracy estimates, or the older evidence-only rubric-v2 numeric scores. Full/partial/fail labels and text hashes are preserved per case. A partial answer does not satisfy the full acceptance outcome. Source-slot alignment was checked against the actual supplied sources.

Material examples:

- Spark handled the pilot firmware range correctly, while Bonsai incorrectly excluded 3.2 from 3.0–3.4. Spark omitted the documented technician condition, so its answer was still partial.
- Both struggled with the pilot issue list: Bonsai included another person's task; Spark listed a closed task and then said not to count it. In the reserved Mira variant, Spark filtered correctly and Bonsai again changed the task owner.
- Spark chose the bridge-crossing east loop while immediately quoting that west avoids the bridge. It also refused a supported CCS2 lookup and the calculation `1,800 / 0.6`. Bonsai answered that calculation correctly.
- Bonsai asserted an unknown archive was closed, inferred current stock from old snapshots, and invented a causal link between a pump's filter and reduced flow.
- Both fabricated source `[1]` in a general manual-versus-log explanation with no documents. Other answers had correct facts but missing or wrong citations. The pilot GPS explanations also failed review; the review records link the primary fact-check references.

A relevant source, normal EOS, or a fluent explanation did not prevent these failures. Source extraction, temporal reasoning, typed filtering/arithmetic, citation validation and explicit uncertainty remain necessary work. Results describe the deployed quantized configurations and prompts; they do not isolate training, quantization and backend effects or certify equivalence to a higher-precision reference.

## Timing and memory: bounded observations

Two development questions were repeated in three alternating pairs. Every measured call followed an eight-token same-model warmup, used no prompt-cache reuse, and ended at normal EOS. Spark used temperature 1.0/top-p 0.95/no top-k; Bonsai used temperature 0.7/top-p 0.8/top-k 20; seed 42 throughout. Repeated texts were identical within each model/case, so these are timing repetitions rather than independent quality samples.

| Native complete-answer median | Spark | Bonsai | Interpretation |
|---|---:|---:|---|
| Late arrival | 30.857 s, 71 tokens | 39.776 s, 85 tokens | Spark was faster, but its answer misattributed the user-provided arrival time to the reservation. Bonsai's reviewed answer passed |
| Applicable manual | 51.725 s, 170 tokens | 46.003 s, 98 tokens | Spark was slower and unnecessarily deferred the supported filter answer. Bonsai identified the filter but confused a required isolation procedure with its unconfirmed completion |

The sampled modes changed both answer length and quality relative to greedy. No clean quality-preserving model win follows from these two controls. Larger sampled evaluations, additional seeds and fresh cases remain pending; do not treat the greedy review as the product sampler's accuracy.

Maximum observed process PSS across the six measured timing calls was **1,269.8 MiB for Spark** and **1,483.6 MiB for Bonsai**. This is sampled whole-process memory, not a continuous peak or minimum phone-RAM requirement. The sampler requested 200 ms but observed gaps reached 700 ms in these timing rows and 1,617 ms elsewhere. It ran concurrently and its overhead was not separately measured. Native time excludes retrieval/UI and differs from tap-to-useful-answer latency. Different output lengths/tokenizers, model-specific kernel policies and uncontrolled host contention also limit comparisons.

Hash verification warms file pages. Model-cold does not mean storage-cold. Fault counters are not physical-read counts. No NVMe, flash-pressure, energy, thermal, GrapheneOS or physical Pixel claim is made.

## Compact-cache experiment and preserved failures

The backend defaults to full SWA storage. Explicit compact storage reduced logical F16 KV payload from 112 to 59.5 MiB at this configuration, including cache padding. A long control showed **53.67 MiB less sampled PSS**, but initial-logit hashes differed from full storage despite identical `J-83` output. [Decision data](../evidence/research/spark-admission-20261001/compact-cache-decision.json). Drift magnitude, downstream sampling changes and wider quality were not measured. Full cache remains the comparison default.

All failed attempts remain intact, with separate analyst notes:

| Run | Meaning |
|---|---|
| [065759 / 07fa8637](../evidence/runs/candidate-admission-20261001T065759Z-07fa8637/analyst-note.json) | Harness rejected uppercase `SDK` before model execution; corrected label normalization |
| [065952 / f5d33e44](../evidence/runs/candidate-admission-20261001T065952Z-f5d33e44/analyst-note.json) | Real USER_DEFINED marker handling defect; fixed before comparison |
| [071004 / 35b00c68](../evidence/runs/candidate-admission-20261001T071004Z-35b00c68/analyst-note.json) | Incorrect eviction expectation under full-cache defaults; text/logit parity had passed |
| [071945 / d48b1353](../evidence/runs/candidate-admission-20261001T071945Z-d48b1353/analyst-note.json) | Compact-cache candidate failed strict initial-logit parity; not adopted |

The earlier [19-check admission](../evidence/runs/candidate-admission-20261001T070356Z-49c50808/candidate-checks.json) is an intermediate checkpoint, superseded by the final 21-check admission. Binary stdin transfers also failed before successful binary ADB push/copy/hash provisioning; [provisioning history](../evidence/research/spark-admission-20261001/provisioning.json) records that separately from model behavior.

## Exact build and continuation

Implementation checkpoints: `0192f61`, `ce99dea`, `d2a5bac`, `6b79584`, `ac179bd`, final comparison policy `55d11e4`. Backend and existing model pins are unchanged. [Validation identity](../evidence/research/spark-admission-20261001/validation.json) and the frozen [build receipt](../evidence/research/spark-admission-20261001/build-receipt.json) own exact hashes.

The same-version **research** app is 34,143,830 bytes, SHA-256 `93b91e7fe5e9ac0363488fb07b000f2ea4355199337946dc3f6cdeb9c3ff5d35`; test APK SHA-256 `1fb39c9df410c54de0bedcf09097df3ca2fb621d6745d4cb3a08bf2e6ccc1ef6`. It remains version 0.14.0/code16 for this development slice. The published local `dist/outpost-0.14.0-user-test.apk` remains the earlier `9d3506d7…` artifact. It was not replaced or reissued. Always check hashes, not just version labels.

Continue with LFM2.5/Qwen3.5 admission, fresh practical cases and explicit sampled-policy comparisons. Investigate structured row filtering, unit/time/version operations and citation failures as separate hypotheses; do not patch this reserved set and call it unseen validation. P-04 now has bounded sampled observations, while true peak/pressure/recovery work remains. P-07 storage profiling and E-08 full pipeline traces remain open. A new product model/default or phone execution requires its own evidence and scope; no such promotion occurred here.
