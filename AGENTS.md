# Agent entry point — Outpost

Read [docs/handoff.md](docs/handoff.md) first. It contains the current product interpretation, implementation baseline, known failures, active constraints, next tasks, and resumption checklist. For emulator work, also read [docs/emulator-runbook.md](docs/emulator-runbook.md): it covers host settings, identity checks, boot/offline state, APK/model readiness, test flags, evidence and recovery. Then read the specific architecture, runtime, knowledge or evaluation document relevant to your change. [docs/index.md](docs/index.md) maps the rest.

## Persistent project constraints

- Work in the standalone Outpost repository, currently `E:\projects\outpost`. The old Brújula workspace is a preserved baseline, not the working project.
- Run model inference and runtime tests only inside the Android emulator. Host builds and static/model-header inspection are allowed; physical Pixel execution has not been authorized. Use explicit emulator serials; Outpost's documented instance is `emulator-5582`. Preserve the old `emulator-5580` instance/data.
- Use English for maintained documentation, code comments, default UI resources, and primary fixtures. Retain original language in historical evidence, imported user content, and explicit multilingual probes.
- The product is a general offline knowledge assistant for practical, contextual questions. Vitalik's restaurant example illustrates question types; it does not specify a vegan/restaurant app, mandatory diet feature, or two fixed product demos. Public, regional, and personal material may be combined.
- Keep inference and knowledge as separate technical domains. Action queues, equipment adapters, and reflection remain proposals, not implemented features or authorization to operate external systems.
- Preserve pinned dependencies/model hashes, the native reference fallback, historical evidence, and failed results. CPU support, compiled kernel availability, and enabled policy are separate. The current APK packages both ARM64 and x86_64; do not claim ARM runtime support from compilation/linking or phone performance from emulator timing.
- Treat file/folder imports as snapshots: changed versions remain searchable. Pack activation is a different contract. The product library starts empty; synthetic corpus/providers belong only to the test APK. Current harness cleanup hides provider roots and revokes grants without unregistering the provider.
- Update the owning documentation and evidence for a behavior change. Run focused verification; do not rerun model suites for a docs-only change. A harness PASS, citation marker, or reviewer score does not establish answer correctness.
- Models, SDKs, AVDs, caches, local settings, private documents, and signing material remain outside Git. Do not introduce a remote, publish, send messages, or operate a real device merely because a discovery document discusses that capability.

The roadmap distinguishes implemented work, pending tasks, proposed experiments and open decisions. Future user instructions can refine that scope; record changes in the handoff and relevant decision rather than silently following an obsolete proposal.
