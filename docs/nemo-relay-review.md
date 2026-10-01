# NeMo Relay: tracing Outpost's answer pipeline

Reviewed **2026-09-30** against Outpost `5ca14f1` / 0.14.0. **Recommendation:** apply the lifecycle-tracing and controlled-comparison approach to the emulator harness before attributing answer failures to a model. Start with bounded local events around the existing Java/JNI pipeline. Evaluate a Relay/ATOF adapter later if interoperability justifies it. No Relay dependency, middleware, exporter, model or product behavior was changed by this review.

## Reference and verified scope

The supplied [NVIDIA article](https://developer.nvidia.com/blog/tracing-agent-harness-behavior-with-nvidia-nemo-relay/), published September 30, 2026, combines task verifiers with execution traces. Its case study reports Qwen's completion increasing from 19/27 to 22/27 while mean duration rises from 27 to 42 seconds: recovering more tasks can require more work. Those are external development results, not Outpost evidence. The live web example is explicitly unsuitable for model ranking without fixed responses and repetitions.

The article describes three views: raw ATOF lifecycle events, assembled ATIF trajectories, and OpenTelemetry spans with OpenInference semantics. A requested tool call is distinct from an observed successful tool completion. This distinction is useful even when the application has a fixed pipeline rather than an autonomous tool loop.

The [Relay repository](https://github.com/NVIDIA/NeMo-Relay/tree/872972c600599a6e9085c4e1799d07b980a1dab5) was pinned to `872972c600599a6e9085c4e1799d07b980a1dab5`. Its Cargo workspace declares 0.10.0, whereas the article tutorial specifies Relay 0.8.3 and Hermes 0.21.1. Do not assume the pinned main source and tutorial APIs are interchangeable or that the workspace version proves a published release. [Source provenance](../evidence/research/nemo-relay-20260930/source-manifest.json) records 26 inspected files and hashes; [article provenance](../evidence/research/nemo-relay-20260930/article-source.json) records its HTML identity. Upstream code was read, not built, installed or executed.

## What Relay provides, and the integration boundary

Relay supplies scopes, lifecycle events, subscribers/exporters and middleware around application/model/tool calls. It is not a GGML kernel optimizer or an Engram memory model. The [support matrix](https://github.com/NVIDIA/NeMo-Relay/blob/872972c600599a6e9085c4e1799d07b980a1dab5/README.md) lists Rust, Python and Node.js as supported, with raw C FFI and Go experimental. It does not establish supported Android Java/JNI packaging. This is an integration uncertainty, not proof that an Android port is impossible.

The tutorial's hosted model, API key, Docker terminal sandbox and Phoenix viewer are demonstration infrastructure. The [architecture](https://github.com/NVIDIA/NeMo-Relay/blob/872972c600599a6e9085c4e1799d07b980a1dab5/docs/about-nemo-relay/architecture.mdx) also supports local event files; adopting the observation method does not require a hosted model or NVIDIA GPU. A Relay HTTP gateway alone would not observe Outpost's in-process JNI inference and SQLite operations: instrumentation has to surround the boundaries we own.

Prefer an observation-only prototype first. Relay's awaited middleware can alter execution; asynchronous subscribers are a different mechanism. Adding middleware, routing or extra model calls during a tracing comparison would change the workload being measured. No new equipment executor, network tool or agent framework is needed for Outpost's current pipeline.

## Gaps found in the current app

Source inspection covered `MainActivity.sendMessage` / `finishTurn`, `ChatPrompt.prepare`, `NativeEngine.Result`, and the chat/evaluation wrappers. Their hashes are recorded in [local source inspection](../evidence/research/nemo-relay-20260930/outpost-source.json).

| Boundary | Already present | Additional trace evidence to propose |
|---|---|---|
| Turn and branch selection | Turn UUID, request counter, pending/complete/limit/canceled/error states | Correlated turn events and reason for deterministic places, clarification, setup-needed or generation path |
| OSM answer | Typed query/answer and exact source locators | Lookup duration, matched/ambiguous entities, scan bounds and route outcome; zero model calls explicitly recorded |
| Document retrieval | Query search; prior-question fallback only when initial results are empty | Separate attempts with trigger, hit counts, selected identities and durations |
| Prompt construction | At most three source excerpts, bounded recent turns and question | Which evidence/history survived selection and clipping; prompt version, counts and fixture-only content identity |
| Native generation | Load/prepare/prefill/decode/first-token/total timings, tokens, cache and stop data | Correlation with its parent turn, queue wait and first UI-visible output; retain native aggregate timings |
| Completion | Saved turn then UI update; save failure is logged | Distinguish generation stop, persistence result and visible UI completion |
| Evaluation | Unique run directories, artifact identities, raw outputs and attributed review | Join ordered execution evidence to the verifier/reviewer result without merging their meanings |

Existing evidence is useful; it does not yet form an ordered end-to-end lifecycle trace. A small model may appear to fail because relevant text never reached its prompt. A fast deterministic museum lookup and a generated document answer need different route labels. Generation success also does not prove that a turn was saved, displayed or correct.

## Proposed first slice: local emulator traces

E-08 is **proposed**, not implemented. Instrument the normal product path exercised by the test harness; keep the product UI unchanged and diagnostic collection explicitly enabled for tests. An instrumentation-only reimplementation of retrieval would not explain production behavior.

Use one run identity, one turn identity, unique span IDs and explicit parent links across the worker, inference and UI executors. Record start/end events and sparse marks such as first visible output. Carry IDs explicitly across queues; thread identity or thread-local state alone is insufficient. Measure durations with a monotonic clock within a process and keep wall-clock timestamps only for correlation. Queue wait, native compute and rendering are distinct intervals; parent duration must not be computed by blindly adding overlapping children.

Record model SHA, app/test/build identity, prompt/adapter/sampler versions, context/output/time budgets, threads/batch/kernel/cache policy, offline state and source snapshot identity once in the run envelope. Reuse existing native metrics rather than serializing per-token or per-matrix payloads. Label counts in characters, UTF-8 bytes or model tokens explicitly; they are not interchangeable across tokenizers.

Keep execution status separate from answer outcome: EOS, limit, timeout, cancellation, exception, missing model and process interruption must not become the same “success.” A trace with a missing end event is incomplete, not a zero-duration successful call. Scope-end also does not mean a verifier passed. Preserve partial traces, report dropped events/write failures, and bound event size, queue memory and file growth. Flush with a bounded deadline before evidence collection; process death can still lose buffered records and must remain visible.

Start with an explicitly versioned Outpost JSONL schema or a tested ATOF adapter. **Do not label arbitrary span JSON as ATOF-compatible.** The pinned [ATOF 0.1 specification](https://github.com/NVIDIA/NeMo-Relay/blob/872972c600599a6e9085c4e1799d07b980a1dab5/docs/reference/atof-event-format.mdx) has required envelope fields and start/end correlation rules; ATIF conversion additionally needs semantic payload mappings. Metadata-only traces have deliberately limited replay capability. No serializer or converter was implemented here.

## Local data and payload policy

For synthetic/public fixtures already approved for evidence, full prompt/source/output capture can remain a separately identified evaluation artifact. Generic diagnostic events should default to opaque run-local IDs, counters, timings and categorical reasons. Avoid raw questions, paths, document titles, coordinates, source text, exception strings and per-token dumps. Plain hashes of sensitive values are not anonymization; raw content-derived identifiers should not enter shareable diagnostics by default.

Relay's `enable_full_payloads=false` is **not a no-content switch**: its pinned [configuration guide](https://github.com/NVIDIA/NeMo-Relay/blob/872972c600599a6e9085c4e1799d07b980a1dab5/docs/configure-plugins/observability/configuration.mdx) says repeated LLM starts still contain the current user turn. Export configuration and redaction need explicit review. No remote endpoint, cloud collector, API key or automatic upload is part of this proposed Outpost slice. A local desktop viewer may be considered later for exported development fixtures; it must not become an app runtime dependency.

## Evaluation sequence and decision

1. Check event integrity using actual emulator routes: deterministic OSM with no model; generation; empty-search fallback; source clipping; cancellation; native failure; persistence failure; interrupted process. Confirm parent links, observed starts/ends and terminal states without fabricated completions.
2. Compare trace disabled/enabled on identical workloads. Account for writer serialization, queueing, flush, disk bytes and sampled peak memory; preserve text/stop behavior and report measurement noise. “Asynchronous” does not prove zero overhead.
3. Add traces to both arms of the [model study](model-alternatives.md). Keep fixed-evidence generation and real retrieval as separate comparisons. Freeze fixtures, snapshots, budgets and versions; repeat paired runs. Tracing itself is not a quality judge or a confounder-free benchmark.
4. Optimize task completion first, then explain cost through branch choice, retries, clipping, model calls and elapsed time. If a fallback recovers useful evidence but takes longer, record both effects. Compare complete-answer latency and failures alongside tokens/s; no phone energy inference from emulator timings.

**Adoption decision:** borrow the method now as a documented next experiment; defer embedding the SDK until Android support, footprint, lifecycle and interoperability have evidence. E-07 model admission can proceed independently. E-08 is valuable before making causal claims about why one model or harness policy performs better. No speedup, trace format conformance or implementation result is claimed by this review.

## Residency/I/O correlation follow-up — 2026-10-01

[P-07 in the Spark-X2.5/storage review](spark-x25-storage-review.md) proposes correlating residency/fault/I/O observations with E-08 turn traces. Separate model compute from exposed storage wait and preserve unavailable-counter status. Per-token trace emission and asynchronous exporters can themselves perturb latency; measure overhead before drawing conclusions. The NVMe report supplies a hypothesis, not a reproduced phone/emulator measurement.
