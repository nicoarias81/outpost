# Outpost 0.9.0 validation — 2026-09-30

> Historical record: observations, proposals and commands below describe the recorded release/session. For Outpost 0.12 use [current state](current-state.md), [handoff](handoff.md) and [current validation](validation-0.12.md). Original results and language are preserved.

> Later status: the integration/publication quota interruption described below was resolved before 0.10 (evidence integration `449bca6`, line-ending preservation `c4deeb9`). Its former pending state is historical, not a current blocker.

Implementation commit: `c51c73f` (`Add versioned knowledge packs, CSV evidence and executable evaluation`). Version code 11. Native numerical code, pinned backend and model weights are unchanged. Execution stayed inside Outpost35, the x86_64 Android emulator. The app and maintained project language are English.

## Delivery status and artifact identity

The implementation commit is in the canonical `E:/projects/outpost` repository. The final APK and test APK were built and installed there. Final documentation, evidence commits and `dist/` publication are **pending integration**: automatic approval review exhausted its usage quota before that write operation could execute. This was a review-service failure, not an unsafe-action decision. The reviewable documentation patch and a verified APK copy are in the local delivery directory described in the handoff. No public release or push was performed.

| Artifact | Identity |
|---|---|
| App | `app/build/outputs/apk/debug/app-debug.apk`, 9,143,471 bytes |
| App SHA-256 | `12f21ad9f56e7258b39d0721bcf4e4de3d21259637cc9bc1b719a790ddbfc8fc` |
| Test APK SHA-256 | `48ebb0bdca887de276a5b31194676be9e545edbbe234cb5ff979163563651648` |
| Build receipt UTC | `2026-09-30T12:27:47.6099518Z` |
| Production input fingerprint | `b0d1590805a1be03bcd1a6c0f5df57ceffecc63fc63a8a8e3f4c8d7faeca19ae` |
| Test input fingerprint | `41978a76ee918948f531bd9722bbb2c99dfc7f02cbc0cc3eb9c89061bee5dcc3` |
| Backend | `86ea01d05ec237f89b78b41c8c1ee0f908141ac7` |

The local delivery APK contains x86_64 code and no generator GGUF weights. Installed emulator models are separate. The build receipt binds captured inputs and outputs from a successful local build; it is not a signed reproducible-build attestation.

## Implemented behavior

- Schema 2 migrates original document IDs, bodies and FTS rows transactionally. Failure during an intermediate schema alteration rolls back without reseeding or deleting imports.
- Text and CSV evidence carries document revision, format, source identity, original-text hash, known content date and incorporation time. Exact passage/record locators resolve retained versions.
- Local JSON packs validate bounded structure, hashes and formats before atomic activation. Identical installs are idempotent; changed same-version files and downgrades are rejected. Cancellation/failure preserves the active version. Older versions remain addressable until pack removal.
- CSV preserves record/column relationships, quoted newlines, leading zeros and literal formulas. The English reader displays the selected record and original text. See the [format and limits](knowledge-packs-v1.md).
- Evaluation executes declared retrieval or fixed-evidence fixtures using the production prompt builder. Runs preserve prompts, selected sources, hashes, model/app settings, outputs and failures in unique directories. Attributed reviews are validated against exact results bytes.
- Runtime reporting distinguishes fastest, candidate and selected widths, speedup, and elapsed-time reduction. Build/publish scripts use source fingerprints and a successful build receipt; shared Windows argument quoting preserves spaces, quotes and backslashes.

## Focused verification

| Check | Result and provenance |
|---|---|
| Final Gradle build and Android Lint | Successful; `lint-results-debug.txt`: `No issues found.` |
| Knowledge, persistence and UI | [61/61 checks](../evidence/runs/knowledge-20260930T122749Z-f6084045/knowledge-checks.json), final test APK; migration success/failure, CSV, Unicode/JSON bounds, locators, version/cancel/tamper rollback, removal, runtime-policy boundaries, English UI |
| Functional app checks | [53/53](../evidence/archive/0.9.0/checks.json), version 0.9.0 |
| Qwen generation/cancel/recovery | [17/17](../evidence/archive/0.9.0/generation-checks.json), 0.9 development build before final bounded-input/test-output-folder refinements |
| Bonsai 4B UI, repeat and memory callback | [UI](../evidence/archive/0.9.0/bonsai-ui-bonsai4.json), [repeat](../evidence/archive/0.9.0/bonsai-warm-bonsai4.json), [callback](../evidence/archive/0.9.0/bonsai-trim-bonsai4.json); same development scope as above, simulated callback only |
| Runtime width sweep | [13 calls](../evidence/archive/0.9.0/strata/runtime-batch.json), parity retained, width 4 selected |
| Evaluation definition | `eval/validate.py` passes without warnings; v2 defines 15 fixtures, 12 runnable, 3 blocked |
| Review integrity | Four attributed review records validate against their frozen results and rubric v2 |
| Source snapshot | [Blocked-fixture smoke](../evidence/runs/20260930T122009Z-903d97da/run.json) preserves a checked snapshot; no model generation in this smoke |
| Host script probes | PowerShell syntax, PS7 argument round-trip including quotes/backslashes, and checksum-only verification without a build APK passed; PS5.1 was not separately executed |

Earlier runs identify earlier app/test hashes and remain unchanged. The final knowledge suite introduced unique app-side output directories; its final run is the one linked above. The source-snapshot smoke preceded the final test APK and the runner's last expansion to include all root build/wrapper inputs. That last snapshot-list expansion was inspected on the host; a requested post-commit smoke did not execute because the approval service blocked the encompassing command. No native numerical change was made, so prior dot/grouped/audit results remain historical and were not relabeled as fresh 0.9 tests.

## Runtime result

The width sweep measured median total times of 27,596 / 26,337 / 24,015 / 22,991 ms for widths 1 / 2 / 4 / 8. The narrowest width within 5% of the fastest is 4. It passes the baseline/candidate > 1.05 and parity rules and remains selected at 4 decode threads, 4 prompt threads and batch 128.

Selected speedup is **1.149x**, corresponding to **12.98% less elapsed time** than width 1 in this control. Width 8 is 4.26% faster in elapsed time than width 4 here; the policy retains width 4. This is remeasurement of the existing kernel, not a new 0.9 kernel speedup over 0.8.1 and not phone performance.

The report/policy ambiguity associated with `R3-width-report-coupling` was addressed with explicit fields and boundary tests. That implementation evidence does not retroactively grant native-controller review authority to `a52f2b3`; its formal controller review remains outstanding.

## Reviewed answer quality

These are manual **assistant** development reviews by the implementation agent, not blinded human or qualified field studies. Counts refer only to attempted fixture/variant pairs. Critical failures remain separate from dimension scores; first-useful-information time is unmeasured where explicitly marked null.

| Run | Scope | Attributed outcome |
|---|---|---|
| [110012](../evidence/runs/20260930T110012Z-473b8b0a/review-v2.json) | Early 0.8.1 build; v1; Bonsai 4B; baseline vs longer candidate; 22 answers, 4 blocked fixtures | Baseline: 7 satisfied / 3 partial / 1 failed. Candidate: 7 / 1 / 3. Candidate rejected; production system text unchanged. `review-v2.json` supersedes the unversioned initial `review.json`. |
| [114446](../evidence/runs/20260930T114446Z-946e75ae/review.json) | 0.9; v2 baseline Bonsai 4B; 96-token output budget; 12 answers, 3 blocked | 7 satisfied / 4 partial / 1 failed. CSV output truncates and misses citation. |
| [120757](../evidence/runs/20260930T120757Z-a4334247/review.json) | Bonsai 4B CSV diagnostic at the actual 192-token UI budget | Partial. Correct row, value and distinct date meanings, but cites the conflicting note instead of the CSV source. 133 output tokens, 53.409 s. |
| [120854](../evidence/runs/20260930T120854Z-6f4d0987/review.json) | Qwen on four selected problem cases; 192-token budget | 1 satisfied / 2 partial / 1 failed. Authorization wording improves; pharmacy answer loops on citations, conflict synthesis invents applicability, CSV conflates dates/omits row and citations. |

The diagnostic set does not justify switching the default generator or adopting the longer prompt. The production `ResearchPrompt.SYSTEM` is unchanged; `VERSION = evidence-v1` makes its identity explicit. The model may fail after correct retrieval; source inspection remains independently useful. No arithmetic tool, claim-support verifier or general date-aware personal-file finder is implemented.

## Evidence preservation and remaining limits

- `evidence/0.7-before-outpost` remains the original historical baseline.
- `evidence/archive/0.8.0` reconstructs 63 records from Git `63d9e85`, with its own `PROVENANCE.json`.
- `evidence/archive/pre-0.9` preserves the mutable report paths before this implementation. Historical 0.8/0.8.1 links now address these archives.
- New evaluation and knowledge runs are unique. Legacy functional/runtime scripts still overwrite fixed paths; the delivery snapshot freezes their current bytes. Commit evidence separately from behavior.

PDF/Office/ZIM/OSM, geographic coverage/routing/GPS, deterministic tools, persistent mission context and full ARM app execution remain pending. Pack catalogs, signatures, storage estimates, resumable downloads and rollback UI are absent. Retained source versions consume space until removal. Peak memory, real pressure recovery, phone latency, thermals, battery, GrapheneOS behavior, public distribution and bounty compliance are unverified. Speculation remains off; locked Bonsai 4B still has no MTP heads.

## Later integration note — 0.10 session

The temporary approval-service quota block described above was resolved on resumption. Evidence was committed as `449bca6`; `c4deeb9` preserves exact immutable evidence bytes across Git checkouts. The prepared 0.9 documentation was applied before the 0.10 chat work. Its frozen C-workspace APK/delivery remains the 0.9 artifact; the next canonical local publication is the 0.10 user-test candidate. Do not apply the old documentation patch over newer files.
