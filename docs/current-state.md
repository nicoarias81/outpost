# Current implementation and known gaps

Current implementation: Outpost 0.8.0 / version code 9. Fresh migration checks are recorded in [validation-0.8.md](validation-0.8.md); the optimization and quality-failure examples below remain explicitly historical 0.7 evidence.

## Capability inventory

| Area | State | Boundary |
|---|---|---|
| Android UI and source reader | Implemented | Java 17, API 28 minimum / 35 target; English UI and resource-backed labels |
| Text knowledge library | Implemented | Six attributed demo notes; SQLite FTS4 lexical passage search |
| User document import | Implemented | UTF-8 `.txt`, `.md`, `.markdown`; maximum 1,048,576 bytes per document |
| Local generation | Implemented and measured | Pinned Qwen 1.5B and Bonsai 1.7B/4B; Android process through JNI |
| Model import | Implemented | Exact size and SHA-256 allowlist; separate files per profile |
| Kev review | Experimental | First sentence against up to three passages; false positives recorded |
| Q2 dot kernel | Implemented and measured | Custom x86 AVX2/F16C; original reference fallback |
| Grouped prefill | Implemented and measured | Widths 1, 2, 4; validated Q2 g64 paths only |
| Runtime calibration | Implemented | Developer scripts persist a device/build/model-specific profile |
| Prompt cache | Implemented and measured | Exact tokens and compatible configuration; complete batch boundaries |
| Context speculation | Experimental | Bonsai 4B only in UI; opt-in, default off; no extra model |
| VNNI and ARM capability reporting | Implemented | Detects candidates; custom VNNI/ARM kernels not implemented |
| ARM portability | Compile check only | Ten native object builds; no complete ARM APK or execution |
| Knowledge package manager | Proposed | No versioned package installation or typed adapter contract yet |
| PDF, ZIM, OSM, routing, GPS | Proposed | No corresponding adapter or user workflow |
| English product baseline | Implemented | UI, main prompts, seed notes, retrieval and primary generation fixtures translated; historical bilingual reviewer probes retained |
| Standalone Git repository | Implemented | Independent local repository at `E:\projects\outpost`; no remote configured |

## Runtime limits and defaults

- Context: 2,048 tokens. UI generation: at most 192 output tokens and a 120-second native deadline, including model load and prompt evaluation.
- Bonsai sampler: top-k 20, top-p 0.8, temperature 0.7, seed 42. Qwen uses greedy decoding.
- Runtime settings validate 1–8 decode/prompt threads, batch size 16–512, and matrix width 1, 2, or 4.
- Uncalibrated UI profile: up to four available CPU threads, batch 128, width 1. The last saved emulator profile was 4/4 threads, batch 128, width 4.
- The previous 0.7 emulator remains preserved. Outpost uses a separate AVD on port 5582; delivered state and fresh checks are recorded in the 0.8 report.

## Known failures and missing guarantees

| ID | Finding | Evidence / next work |
|---|---|---|
| Q-01 | Traveler fixture answers around irrelevant restaurant information, misses the luggage task, and truncates | [Review](../evidence/0.7-before-outpost/speculation/mission-review.json); task-oriented evaluation and prompt/retrieval work |
| Q-02 | Mountaineer fixture incorrectly refuses `1450 - 1200 = 250 m` under the 0.7 fixture prompt | [Review](../evidence/0.7-before-outpost/speculation/mission-review.json); deterministic calculation and prompt comparison |
| Q-03 | Numeric citation checks detect missing/out-of-range references, not claim support | [ResearchPrompt](../app/src/main/java/dev/outpost/app/ResearchPrompt.java); provenance and claim-level evaluation |
| Q-04 | Kev can approve incorrect generated content | [0.3 report](validation-0.3.md); never use Kev as evaluation ground truth |
| N-01 | Serial and batched model logits can differ; the same seed does not ensure identical speculative text | [Audit](../evidence/0.7-before-outpost/speculation/speculation-energy-audit.json) |
| M-01 | Cache retains about 305 MiB additional post-run PSS in one control | [0.6 report](validation-0.6.md); peak memory and real pressure behavior remain unknown |
| K-01 | Lexical retrieval and tiny seed corpus do not cover real field missions | [Library](../app/src/main/java/dev/outpost/app/Library.java); packages and mission fixtures |
| K-02 | Database version upgrades currently throw instead of migrating | [Library](../app/src/main/java/dev/outpost/app/Library.java); migration path needed before schema changes |
| P-01 | No physical phone latency, thermal, battery, or peak-memory evidence | Emulator constraint remains active |
| R-01 | Resolved in 0.8: scripts use explicit settings and Outpost identifiers | [Migration record](repository-migration.md) |

The 0.6 mountaineer fixture did calculate 250 m, while the 0.7 fixture did not. Their system prompts differ; this is not proof that speculation introduced a numerical-reasoning regression. Compare controlled inputs before assigning a cause.

## Fresh English quality observations

The 0.8 mission run preserves text across baseline/grouped paths, but source copying and truncation remain problems. The traveler response is partial; the farmer response fails to identify F-28; the engineering answer overstates the absence of authorization records. The mountaineer arithmetic and driver manual-page lookup work in these fixtures. See the [fresh content review](../evidence/strata/mission-review.json). Historical 0.7 failures above are not silently rewritten.

## Release identity

The current debug APK is `dist/outpost-0.8.0-emulator-debug.apk`; its checksum and validation are linked from the [README](../README.md). The historical Brújula 0.7 artifact remains separate. Project code license, release signing, remote hosting, and public distribution remain open.
