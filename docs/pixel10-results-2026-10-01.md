# Pixel 10 Pro validation — 2026-10-01

Outpost runs on the owner-authorized physical Pixel 10 Pro. Pinned Spark 1.7B, Bonsai 4B and Qwen 1.5B generated locally; native admission passed 23 controls, the paired pilot passed 37 execution controls for 32 answers, and product chat/import/PDF checks passed 45 controls. Answer quality and latency remain material limitations. No product model was promoted and no native/product code changed during this trial.

## Device, artifacts and protocol

Observed Google Pixel 10 Pro / `blazer`, Tensor G5, ARM64, Android 17/API 37, September 5 security patch, 8 online CPUs and 15,949,000 KiB total memory. Actual kernel page size was 4,096 bytes; this is not a 16 KiB-page validation. The phone was USB-powered. Its system network settings stayed at airplane/Wi-Fi/mobile data `0/1/1`; the tested app has no INTERNET permission. This verifies local app execution, not an entire disconnected-phone mission. [Initial device](../evidence/research/pixel10-20261001/initial-device.json), [page size](../evidence/research/pixel10-20261001/page-size.json), [final device](../evidence/research/pixel10-20261001/final-device.json).

The app is the existing same-version 0.14.0/code 16 research build, SHA256 `93b91e7fe5e9ac0363488fb07b000f2ea4355199337946dc3f6cdeb9c3ff5d35`. Backend `86ea01d05ec237f89b78b41c8c1ee0f908141ac7` and model locks are unchanged. Admission/pilot test APK SHA256 is `7584f34c8d1e73558e1ba65d786872805477db204fb506acfe17144c41163634`; visual checks use `6097b92f3e8da208ca23f2962d4022176f8e6b35ad63011daf19cab78f4b4015`. Receipts preserve [native-pilot inputs](../evidence/research/pixel10-20261001/build-receipt-final.json) and [final visual-test inputs](../evidence/research/pixel10-20261001/build-receipt-chat-final.json). The intermediate visual APK ending `9911bb4` was built but not used for these runs. The frozen published `dist/outpost-0.14.0-user-test.apk` retains its original SHA256 `9d3506d7ee8103bbf213b95a69392ad6ea1130a3e86679b53630e831843f01a9`.

Phone configuration was 4 generation/prompt threads, batch 128, width 1, rowTile/decodeRows 1/1, context 2,048, mission output cap 192, deadline 120 seconds and speculation 0. Spark uses its verified no-thinking protocol with full SWA cache. CPU mask 7168 detects NEON, DotProd and I8MM; the custom Q2 ARM kernels are not compiled, so Bonsai selects the original reference path. The tuned x86 emulator configuration was not transferred to ARM.

## Executed runs

| Run | Result | Scope |
|---|---|---|
| [Admission](../evidence/runs/candidate-admission-20261001T095919Z-c0129c18/candidate-checks.json) | 23 controls passed | Model hashes, template/control tokens, UTF-8, cache versus cold parity, cancellation/recovery, switching, Qwen smoke |
| [Pilot](../evidence/runs/candidate-pilot-20261001T100306Z-07d419d8/candidate-checks.json) | 37 controls; 32 returned answers | 8 known synthetic development questions × 2 models × fixed/SQLite retrieval arms; greedy decoding |
| [Product chat](../evidence/runs/chat-20261001T104406Z-e60ee466/chat-checks.json) | 45 controls passed | Actual sampled Bonsai chat, context, cancellation/restart, Activity TXT/CSV/PDF import callbacks, original PDF page rendering |

Admission includes the previous 21 controls plus absence of product INTERNET permission and full Qwen file verification. Qwen received a functional smoke check, not the paired performance study. The pilot's execution gate allows deadline returns: a PASS does not mean every answer finished or was correct. No phone held-out replay or repeated sampled timing phase was executed.

The pilot began with the keyguard showing. The owner subsequently unlocked/opened the app; screen/foreground state and other phone activity were not controlled throughout. File hashing warmed storage pages. These are exploratory observed costs, not a controlled hardware comparison, confidence interval, cold-storage benchmark or battery-life experiment.

## Observed native costs

One fixed-evidence call per question is shown below. The retrieval arm repeats the same prompts; it is not an additional independent quality sample. Times include native load/preparation/prefill/decode, not all UI work. A deadline value is the cost of an incomplete answer.

| Question | Spark seconds / output tokens | Bonsai seconds / output tokens |
|---|---:|---:|
| Late arrival | 42.60 / 173 | 120.00 / 167, deadline |
| Applicable manual revision | 49.79 / 60 | 120.01 / 162, deadline |
| Assigned open issues | 62.72 / 103 | 68.46 / 60 |
| Seed-table calculation | 43.48 / 112 | 87.01 / 104 |
| Old spring observation | 40.01 / 89 | 58.33 / 70 |
| Missing road/location | 60.11 / 57 | 70.98 / 38 |
| Corrected booking | 34.94 / 54 | 71.93 / 39 |
| GPS and map explanation | 53.41 / 106 | 67.39 / 100 |

Spark finished all 16 pilot calls normally. Bonsai finished 12 and hit the 120-second deadline in 4: both arms of late arrival and manual revision. Across the eight fixed cases, descriptive median total/first-token times were 46.63/10.21 seconds for Spark and 71.45/29.19 seconds for Bonsai. Outputs differ in length and quality, and the Bonsai median includes deadline-limited observations; do not present their ratio as a general speedup.

Observed process PSS maxima were 1,445,718 KiB for Spark-labelled calls and 1,663,231 KiB for Bonsai-labelled calls. Switching-call boundary samples can include the prior model; these are not isolated steady-model memory requirements. Maximum sampling gaps were 1,238/1,341 ms despite the requested 200 ms cadence. Battery-temperature boundary samples ranged from 33.8 to 38.1°C during the pilot, with Android thermal status 0/1 (none/light). They can miss in-call peaks and do not prove absence of throttling.

Median process-CPU/wall ratios were approximately 3.51 and 3.81, including profiling and any other activity in that process. Spark recorded no major-page-fault increments; Bonsai recorded 27 and 2 in its first two calls and zero thereafter. This motivates investigating the CPU/memory execution path before assuming storage stalls in this warmed trial. Fault counters do not count physical reads or quantify storage latency. [Derived metrics](../evidence/research/pixel10-20261001/pilot-analysis.json) and [paired-input validation](../evidence/research/pixel10-20261001/paired-summary/metrics.json) retain the exact values.

## Answer review

The same implementing assistant reviewed the actual outputs using candidate-review-v1. This is a known synthetic development set, not independent, blinded or qualified field review. Spark had 5 pass / 2 partial / 1 fail across eight fixed cases; Bonsai had 3 pass / 2 partial / 3 fail. Duplicate arms are not counted as new questions. [Attributed review, text hashes and source slots](../evidence/research/pixel10-20261001/pilot-review.json) preserve each judgment.

- Spark answered the manual revision correctly; Bonsai incorrectly excluded firmware 3.2 from 3.0–3.4 and concluded no replacement filter was documented.
- Bonsai attributed another person's issue to Noor. Spark listed a closed issue among open items before excluding it in a note.
- Both calculated 54 kg from the seed table and preserved its provisional status. Both declined to infer today's water availability/quality from the old spring observation and requested context when road/location was absent.
- Both used the corrected booking's rule, but Bonsai linked it to the wrong current source number. Spark's late-arrival answer also contained a confusing contradiction about whether arrangements were needed.
- Both GPS/map explanations contained material errors; Bonsai additionally invented citation 1 without supplied documents. The evaluator separately checked [NASA's receiver/satellite explanation](https://spaceplace.nasa.gov/gps/en/) and [Google's offline-map documentation](https://support.google.com/maps/answer/6291838?hl=en). Those pages were not model input and do not authorize importing Google Maps data.

These results preserve Spark as research-only and the existing product model choices. Better kernel throughput would not fix the observed factual, filtering and citation errors by itself.

## Visual trial and data restoration

The first visual attempt [stopped at its privacy precondition](../evidence/runs/chat-20261001T103943Z-fcf12cbe/chat-checks.json) because a conversation/library already existed. It produced no personal-content report or screenshot. The subsequent wrapper stopped only Outpost, renamed its database and preference directories into an app-private isolation directory, and ran against temporary state. Original document storage was absent; any temporary document directory was retained separately. Models and evidence stayed available. The wrapper's `finally` restored the original directories and verified matching aggregate file hashes. No personal contents left the phone, no user record was deleted, and preferences/model selection were restored. [Restoration journal](../evidence/runs/pixel-isolation-20261001T104400Z-4cf26208/isolation.json).

The foreground product chat produced `Cedar` in 18.805 seconds, used prior-turn context to repeat it in 19.835 seconds, and returned the correct cited filter F-92 in 27.580 seconds. First-token times were 18.504, 19.553 and 15.134 seconds. These three short sampled-policy UI requests are separate from the greedy mission pilot and independently show that prefill latency remains noticeable.

All six captures were reviewed for the English chat home, Settings, synthetic document list, original PDF page, follow-up conversation and sourced answer. Examples: [chat home](../evidence/runs/chat-20261001T104406Z-e60ee466/chat-home.png), [PDF page](../evidence/runs/chat-20261001T104406Z-e60ee466/chat-pdf.png), [sourced answer](../evidence/runs/chat-20261001T104406Z-e60ee466/chat-sourced.png). This is a bounded visual check, not a complete accessibility, keyboard, system-picker/provider, recursive-folder or OSM suite on Android 17.

The requested keep-awake experiment respected the phone's existing administrative policy. Temporary screen timeout was restored to 30,000 ms and USB stay-on to 0; airplane/Wi-Fi/mobile-data settings were preserved. The isolation journal is `restored`, with no pending restoration. [Screen restoration](../evidence/research/pixel10-20261001/screen-control.json). The APK and verified model files remain installed for further authorized work.

## Next work

1. P-02: implement guarded ARM Q2 NEON/DotProd candidates against the retained reference, then test real shapes, full-answer parity and complete request cost on this registered device. I8MM is a candidate, not an enabled optimization.
2. Repeat controlled foreground, sampled-policy comparisons with stable screen/charging/thermal conditions and no concurrent app interaction. Keep hardware/kernel comparisons separate from answer length and correctness. Qwen's practical performance comparison remains open.
3. Address structured filtering, numerical applicability and citation binding; compare model/prompt changes against these preserved failures. Keep deterministic OSM operations separate from prose generation.
4. Extend phone UI/provider/folder/OSM, process-death and storage-pressure coverage. Sustained thermal/energy measurement, other devices, 16 KiB pages, GrapheneOS and field-user acceptance remain open.

Use [the Pixel runbook](pixel10-testing.md) for guarded commands and recovery. The initial wrapper variable collision and privacy-precondition stop remain recorded; neither was a native-model crash.
