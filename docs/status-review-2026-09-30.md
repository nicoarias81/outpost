# Independent status review — 2026-09-30

Reviewed implementation HEAD: `68f9fe3450715b2583a2d3b1cc327ea2a558e202`, branch `codex/outpost`, clean at inspection. Application: Outpost 0.8.1 / version code 10. This review inspected history, source changes, raw evidence and live emulator state, reran the read-only evaluation validator, and recomputed timing summaries. It did not rebuild, run model inference, rerun benchmarks, or modify application behavior.

## Assessment

The project has advanced in runtime capability, reproducibility, evaluation definition and UI focus. The additional width-8 kernel capability is numerically covered but provides only a small extra latency reduction in the recorded workload, so keeping width 4 on this emulator is supported by the current selection policy. Context speculation still lacks a representative-workload benefit and appropriately remains off by default.

Product breadth is still limited by the six-note text library and missing knowledge adapters. A versioned evaluation definition now exists, but its fixtures have not been consumed/executed as scored runs by the harness. The current work is stronger infrastructure, not yet a demonstration of the general contextual-question product goal.

## Changes confirmed in source and history

- Added `eval/fixtures-v1.json`, `eval/rubric-v1.md`, and the host-side consistency validator. There are 15 defined fixtures: 11 marked runnable and 4 blocked. The external bounty has a dated requirements/provenance document.
- The Explore screen now starts at the question input, without the hero/slogan or hardcoded example questions. Functional checks increased from 48 to 53. The launcher label now resolves from `app_name`.
- Grouped prefill accepts widths 1/2/4/8 in Java, JNI, dispatch and the matrix kernel. `sum[8]` extends the prior per-output accumulation; the numerical algorithm was not otherwise replaced. Graph checks now cover width 8.
- Calibration preserves unmeasured dimensions and re-confirms a retained width at the selected thread/batch combination. The speculation profile phase no longer persists its diagnostic configuration. The batch phase prefers the narrowest grouping within 5% of the fastest candidate.
- Evidence retrieval uses a shared quoted `ProcessStartInfo.Arguments` string for Windows PowerShell compatibility. A local artifact publication/checksum script was added. A private GitHub repository is recorded in the maintained handoff; remote visibility was not independently queried in this review.

## Performance from the stored paired controls

The latest [width sweep](../evidence/archive/pre-0.9/strata/runtime-batch.json) records version 0.8.1, a 222-token prompt and a 32-token output budget, with three rounds per width:

| Group width | Median prefill | Median total | Interpretation |
|---|---:|---:|---|
| 1 | 23.066 s | 29.333 s | Reference configuration for this sweep |
| 2 | 20.511 s | 27.005 s | Candidate |
| 4 | 19.082 s | 25.259 s | Saved choice; 13.89% less total time than width 1 |
| 8 | 18.374 s | 24.884 s | 1.48% less total time and 3.71% less prefill time than width 4 |

Width 4 was already available in the prior release. Therefore, the marginal benefit of the newly added width 8 in this sweep is **1.48%**, not the entire improvement over width 1. The wider capability remains useful to test on other supported environments, but the current evidence does not justify selecting it by default here. Three sweeps are described in the optimization record, one of which is retained only as ignored local output; the table above is independently recomputed from the committed latest JSON.

The separate [calibration confirmation](../evidence/archive/pre-0.9/strata/runtime-calibrate.json) gives median totals 26.623 s for width 1 and 23.338 s for width 4. That is **1.14076x speedup and 12.34% less elapsed time**. The code's `widthConfirmationGain` is `baseline / candidate - 1`; its 14.08% value is a speedup increment, not a percentage of baseline time saved. The saved profile remains 4 decode threads, 4 prompt threads, batch 128, width 4.

The [synthetic copy control](../evidence/archive/pre-0.9/speculation/speculation-benchmark.json) gives 12.146 -> 9.993 s median decode: **1.21545x speedup, or 17.73% less time**. Each adaptive copy run accepted 53/54 draft tokens. That is a favorable output shape for context matching, not a general field-work estimate.

The [five speculative mission pairs](../evidence/archive/pre-0.9/speculation/speculation-missions.json) total 33.253 s baseline decode versus 33.528 s speculative decode: **0.83% slower**, with 2 of 11 proposals accepted. Driver decode increases 1.270 -> 1.586 s; farmer increases 2.385 -> 2.508 s. The traveler row launched zero proposals, so its lower elapsed time cannot be attributed to speculative acceptance. These are a small fixed-order set, not a powered statistical comparison.

The recorded 5.79x dot-product microbenchmark remains relative to the scalar dot implementation, not a new 5.79x improvement over Outpost 0.8.0 or an end-to-end phone speedup. Recorded numerical checks include 16,241 dot vectors, 10,023 grouped comparisons and a 24-position teacher-forced audit at each width 2/4/8. These measurements were not rerun during this review.

## Live state independently checked

`emulator-5582` reports AVD `Outpost35`, boot completed, QEMU enabled, airplane mode 1, Wi-Fi 0 and mobile data 0. The installed app reports version 0.8.1 / code 10. Its SHA-256 matches both the current local build and published artifact:

`5551f8047b1746b74e8e04ab0f7778d6c3e08457cc9134000b1c783bd8511676`.

Bonsai 4B is selected. The current build's recorded calibration uses width 4; the retained width-8 preference belongs to the older build key. No speculation preference file is present, so the code's default false applies. The preserved emulator at 5580 is also present and was not modified. The retained lint report says no issues; this review did not run another build.

## Evaluation progress and remaining gap

`eval/validate.py` completed with exit 0. It reports one warning: the `bonsai_policy_string` source symbol cannot be extracted reliably from `TernaryChecks.java`, so that prompt identity is not source-verified by this validator. Successful validation establishes manifest/lock/source consistency within its checks, not evaluated answer quality. [Validator scope](../eval/README.md).

The matrix now represents seven question families and several evidence conditions. It remains `status: design`, and the manifest is not consumed by the instrumentation harness. The next work is to wire and execute it, record complete build/prompt/fixture identities, and score outputs against the rubric.

Existing [runtime mission outputs](../evidence/archive/pre-0.9/strata/runtime-missions.json) still contain the known farmer/traveler truncation and the engineering overstatement. An important lead is that [the speculation harness's baseline](../evidence/archive/pre-0.9/speculation/speculation-missions.json) answers the farmer with F-28 and gives a complete traveler response. These harnesses differ in system/prompt wrapping and execution context; this is not proof of a quality gain from speculation. It motivates a controlled E-02 comparison of complete prompts, sampler, width and cache state before attributing failures to model capacity.

## Findings and corrections from this review

1. **Evidence provenance drift — corrected in maintained documentation.** The 0.8.1 report said every referenced JSON except `checks.json` declared 0.8.0. The latest `runtime-batch.json` and `runtime-calibrate.json` both declare 0.8.1, while `bonsai-ui-bonsai4.json` has no version or width field. The raw records were preserved; the prose now distinguishes them.
2. **Speedup versus time reduction — corrected in prose.** The report described the calibration ratio as 14.1% of total time saved. The corresponding reduction is 12.34%. No acceptance algorithm or raw number was changed. A future harness change should name ratio and time-reduction fields explicitly if both are reported.
3. **Handoff history reference — corrected.** The initial implementation has been replayed into current history as `63d9e85`. The old `d9ddeb8` is not an ancestor of current HEAD. The handoff now identifies the active history instead of requiring successors to infer the rewrite.
4. **Unmeasured optimization dismissal — qualified.** Equal instruction counts do not independently prove equal cycles or no end-to-end benefit. The activation-byte-sum cache idea remains an unmeasured hypothesis; it was not adopted, but no measured conclusion of zero benefit exists.

The prior controller review of `a52f2b3` remains incomplete, and `R3-width-report-coupling` remains open. This source/status inspection is not a replacement acknowledgement for that separate controller workflow. On the current batch code and saved data, `gain` refers to the selected width 4 versus width 1, not fastest width 8 versus width 1. That reporting distinction should be made explicit when resolving the finding; its original intent cannot be established from its identifier alone.

## Recommended next work

1. Finish review of the calibration/script changes in a small bounded source range and resolve the opaque reporting finding with explicit candidate, selected-width and metric semantics.
2. Wire the manifest into executable evaluation runs and freeze app/test APK or commit identities, prompts, evidence, sampler and cache configuration. Keep raw artifacts immutable per run instead of relying on a reused filename and app version alone.
3. Run controlled prompt/retrieval experiments on the observed quality failures, including the contrast between the two mission harnesses.
4. Implement the evidence/locator contract and a safe database migration, followed by one source adapter justified by the question-family evaluation. The current six-note TXT/Markdown library remains the primary product-coverage limitation.
5. Keep wider kernels and speculative methods as measured experiments; keep emulator-only scope and the current conservative defaults. ARM APK/runtime, real phones, thermal/battery/peak RAM and broad field usefulness remain unvalidated.
