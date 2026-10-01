# Stack audit evidence — 2026-10-01

- `cpu-summary-v2.json` is the current derived sample report. It adds app/report identities and validated clock/process metadata to the preserved first summary; function percentages are unchanged.
- `build-receipt.json` identifies the released0.15 app with the new profiling test APK. The exact matching unstripped ELF is kept in ignored `.local/stack-audit/baseline/`.
- `half-loop-build-receipt.json`, `half-loop-disassembly.txt` and `half-loop-metrics.json` identify the rejected research variant. Its complete source snapshots and raw numerical/model reports are in the linked run directories in the owning document.
- `trial-restoration.json` verifies installed baseline APK hashes and restoration of original on-phone directory hashes.
- `restored-source-build-receipt.json` and `rebuild-container-comparison.json` preserve the final successful build. Identical source fingerprints and every APK entry's payload match the released product, but incremental ZIP layout differs. The genuine earlier APK/receipt pair was restored to local build outputs after this check, rather than overwriting the frozen release or relabeling a new container.

The initial profiler prototype did not compile with `Process.pid()` in this Android SDK; it was changed to the supported process termination API before phone execution. The research hook initially lacked its header include and was repaired before phone execution. App-UID permission probes that omitted `--in-app` or attempted a child command stopped before sampling; the supported own-process attachment succeeded without changing profiling/security properties. These setup failures have no model-performance result.

See [the owning analysis](../../../docs/inference-stack-audit-2026-10-01.md) for decisions, constraints and reproduction. Raw profiling uses leaf instruction samples only, no captured stacks. It covers synthetic app activity under the isolation protocol, not user documents or conversations.
