# Outpost 0.14.0 validation — 2026-09-30

Version code 16 preserves the 0.13 offline place feature and adds a guarded multi-column Q2 row-reuse policy. Models, sampler, context/output budgets and pinned vendor backend remain unchanged. All model/numerical/runtime execution used the dedicated offline Outpost35 x86_64 emulator; ARM64 is compiled/linked/packaged only.

## Exact artifact

- Local debug candidate: `dist/outpost-0.14.0-user-test.apk`, **34,136,262 bytes**.
- App SHA-256: `9d3506d7ee8103bbf213b95a69392ad6ea1130a3e86679b53630e831843f01a9`.
- Final test APK SHA-256: `8e8b2284dd6e30f448180d65cf1b95444a8c94c3ca21a145d0100598460c9317`.
- Build UTC: `2026-09-30T20:12:32.9368961Z`; [receipt](../evidence/releases/0.14.0/build-receipt.json).
- Main source fingerprint: `dd36801e501d3f412d626f0799694e15d0cbc661be4a65cf1d721da1e79be2e6`.
- Final test source fingerprint: `4e57cb3a08654c2f3982be1b5d89618150d94a3cd61a7a5412b9ce9da6c07139`.
- [Package manifest](../evidence/releases/0.14.0/manifest.json): ARM64 and x86_64, no test corpus/classes/provider or generator GGUF in the user APK.

Local publication/checksum verification passed. The timing/lifecycle confirmation used **identical app bytes** and test APK `65ae5eb3553a402e80b3a6b9c1faa01fa13c16d5010f8a8f150cb4f271c43752`. Only test-harness width/tail coverage changed afterward; the final tests below use the final test APK. No timing rerun is falsely attributed to that final harness. Lint has 0 errors and 3 existing upstream BouncyCastle warnings ([report](../evidence/releases/0.14.0/lint-results-debug.txt)). This is not a production-signed/public release.

## Numerical and functional checks

| Scope | Result | Final evidence |
|---|---|---|
| New row arithmetic, phase gates, reference/stride/tail/worker/guard checks | 4,234 comparisons, zero failures| [Row suite](../evidence/runs/rows-graphs-20260930T201235Z-7fb5dec2/rows-checks.json) |
| Existing grouped suite under prefillRows2/decodeRows 1 | 10,023 comparisons, zero bit mismatches; widths 1/2/4/8 and odd tails| [Same row suite](../evidence/runs/rows-graphs-20260930T201235Z-7fb5dec2/rows-checks.json) |
| Actual-shape controls | 108 decode-shape plus 36 prefill/cache cases; parity and dispatch pass| [Same row suite](../evidence/runs/rows-graphs-20260930T201235Z-7fb5dec2/rows-checks.json) |
| Place lookup/proximity, absent-model chat, synthetic and frozen real OSM |63 passed| [Places](../evidence/runs/places-20260930T201242Z-7c60846b/place-checks.json) |
| Chat/files/PDF/persistence with actual Bonsai 4B generation |45 passed| [Chat](../evidence/runs/chat-20260930T201311Z-50362faa/chat-checks.json) |
| OSM import/SAF/source reading |39 passed| [OSM](../evidence/runs/osm-20260930T201411Z-0cc9f6a9/osm-checks.json) |
| Recursive folders |30 passed| [Folders](../evidence/runs/folders-20260930T201419Z-6a4c5918/folder-checks.json) |
| Knowledge/migration/CSV/packs/locators/policy |60 passed| [Knowledge](../evidence/runs/knowledge-20260930T201432Z-c209afab/knowledge-checks.json) |

The full-model chat checks actually returned the remembered codeword Cedar on the initial/follow-up turns and F-92 from the imported PDF source. They check integration with the measured profile, cancellation and persistence, not broad factual quality. No new dataset is seeded into product storage; test imports/turns are removed afterward.

## Performance and selected policy

The [confirmed experiment](kernel-rows-0.14.md) compares three alternating pairs per complete-answer case at 4/4 threads, batch 128, grouped width 4, with the product 192-token cap and normal EOS. Median generation-call time fell 25.085→22.742 seconds for the reservation and 27.246→25.683 seconds for the manual, **9.34% and 5.74% less time**. The ratio of summed medians is 1.081×, or 7.46% less time. Outputs and first-logit hashes matched exactly; separate exploratory teacher-forced checks matched all 16 positions per variant over the complete vocabulary.

This is a small emulator development comparison against the retained kernel at the same width 4, not against the conservative width 1 default, a UI latency study, a phone prediction or proof of improved answer quality. The long-answer check showed why applying grouped decode too was undesirable. Only the multi-column policy is selected; the previous single-column decoder remains.

Final confirmation validated exact-prefix reuse (133 tokens), cache invalidation on row-policy change, cancellation after 3 tokens, zero-cache recovery, Qwen/non-Q2 fallback and model switching before writing the profile. [Post-validation state](../evidence/releases/0.14.0/emulator.json) independently verifies the saved key/configuration, Bonsai 4B selection, airplane mode 1, Wi-Fi 0 and mobile data 0.

The saved profile is scoped to this device/API/CPU/app/kernel/model key: 4 decode threads, 4 prompt threads, batch 128, width 4, multi-column `rowTile=2`, single-column `decodeRows=1`, kernel identity `q2-row-v3-phase`. Other keys default conservatively; no startup auto-calibration or experimental product UI was added. A one-column prompt tail follows the single-column policy. The native context key includes both row settings.

## Adverse and historical evidence

Decode-only and combined-policy attempts remain in unique run folders linked from [the experiment record](kernel-rows-0.14.md). The incomplete combined confirmation was deliberately stopped with a targeted force-stop after its decode regression appeared; the raw "Process crashed" log and incremental outputs are preserved with an analyst note. No profile was applied from that incomplete attempt. It is not described as an unexplained kernel crash or a successful confirmation.

The 0.13 feature artifact/receipt/results remain frozen in [0.13 validation](validation-0.13.md). The evidence-only fixture matrix is now v7 with current app identity and full-ABI validation; its three broader blocked tasks remain blocked. The new kernel does not establish MTP, custom ARM/VNNI support, peak memory, energy/thermal improvement or physical-device compatibility.

## Reproduce

Follow [development](development.md) and the [runbook](emulator-runbook.md). Use `test-rows.ps1 -Phase graphs` for numeric/shape controls, `-Phase confirm` for complete-answer/lifecycle confirmation, and `-ApplyProfile` only when a passing measured profile should be persisted. Use `test-places.ps1`, `test-chat.ps1 -Generate` and the ingestion regressions for the final integration. All suites run sequentially and enforce emulator/build/APK identity. New builds or changed models require their own evidence.
