# Discovery note: personal knowledge and field work

Date: 2026-09-29. Status: **qualitative input and proposed experiments**, not validated demand or an approved feature expansion. Source: a short group discussion pasted by the project owner. This note paraphrases the relevant ideas; names, phone numbers, and the raw chat are not retained.

## What the discussion actually contributes

Most suggestions were hypothetical. One participant recalled a concrete previous workflow: visit disconnected equipment, carry manuals and prepared scripts on a laptop, and connect locally to diagnose/configure it. That recollection is more specific evidence of a task and workaround than a general statement that offline AI might be useful. It still needs follow-up on current frequency, equipment, permissions, and whether a phone could replace the laptop.

Another comment challenged the premise that losing signal is common. Treat that as a product-risk hypothesis, not a geographic coverage fact. The discussion does not establish market size, willingness to pay, regular use, or the ability of the current model to solve these tasks.

## Preserve the original world-knowledge objective

The user clarified that Vitalik's example represents the type of question: specific, contextual requests that require relevant facts, constraints, comparison, synthesis or recommendations. The [question-family specification](offline-world-knowledge.md) keeps this general capability central. Restaurants are illustrative subject matter, not a mandatory standalone benchmark or product vertical. Personal document recall contributes to the same general assistant.

## Jobs suggested by the feedback

| Job | User-intent example, paraphrased | Required assets/capabilities | What the model adds | Proposed first check |
|---|---|---|---|---|
| Find something I already have | Find the issue list downloaded roughly two weeks ago | Selected local files, content and metadata search, exact original locators | Resolve vague descriptions and summarize the relevant records | Identify the correct document/version and return exact issue rows |
| Understand my surroundings | Show useful information for this visit | Prepared regional information and a confirmed or simulated position | Relate the situation to saved material and ask for missing context | Distinguish inside/outside package coverage and stale/missing location |
| Think something through | Discuss a project or organize a personal concern | Conversation context; optional explicitly saved notes | Clarifying questions, alternatives, reflection and structure | Coherent multi-turn discussion without invented personal history |
| Continue work when connected | Prepare a task now for a future connection | Persistent local task/draft, explicit intent, optional future connector | Draft a payload or turn a request into structured parameters | Simulate reconnect/retry/cancel without sending anything |
| Diagnose equipment nearby | Use a manual and a cable connection to inspect a device | Exact device/firmware identity, local transport, protocol adapter, reviewed procedures | Explain observations against the manual and select an applicable check | Read-only fake-device diagnosis with a traceable report |

The personal-concern suggestion is a signal for reflective conversation. It is not evidence of clinical efficacy and does not justify positioning Outpost as a psychologist. A future reflection mode should offer listening, organization, and user control of saved notes without diagnosing or implying professional authority. This is separate from the current source-grounded document-answer mode, which requires retrieved passages.

## Product interpretation

The common opportunity is continuity: information and useful work stay available when an internet service cannot be used. Coverage loss, unavailable remote services, connectivity cost, and privacy preferences are possible reasons to test separately; none is established by this small discussion.

There are at least three independent states to model: internet reachability, communication with a local device, and availability of local information/context. A future regional assistant should also expose location freshness and accuracy separately. A saved map or last position must not be presented as a live observation.

Two product directions emerge: broad personal recall/reflection, and a narrower professional field assistant. The field direction has a concrete recalled workflow, applicable documents, and testable outcomes. It is a strong candidate for further discovery; this note does not eliminate the original traveler, farmer, engineer, mountaineer, and driver contexts.

## Illustrative personal-recall experiment: recover the issue list

Proposed prompt: **Find the open issues from the attachment I downloaded about two weeks ago. Show their IDs, status, and owner, and open the original.**

Use a deliberately small synthetic pack: one issue spreadsheet, a revised issue spreadsheet, a similarly named unrelated file, a PDF inspection report, and a configuration note. A stable JSON/CSV fixture can validate the retrieval contract before committing to every document parser; that is not a claim of Excel support. Stage actual PDF, DOCX, and XLSX adapters separately as they become available. Legacy DOC/XLS formats need their own feasibility decisions.

Acceptance:

1. Retrieve the correct file or ask a useful clarification when version/project/date clues remain ambiguous. Do not choose a recent but unrelated file merely because its timestamp fits.
2. Preserve exact IDs, statuses, owners, empty values, and row relationships. Show document version and sheet/row or page/section locators.
3. Distinguish email receipt, attachment download, file modification, content revision, and Outpost import dates. Missing email metadata remains unknown; it cannot be inferred from a file's modification time.
4. Apply structured filters such as open status before optional prose generation. Return inspectable records even if the model is absent, busy, or slow.
5. Handle renamed files, duplicate versions, absent documents, and a provider whose original URI is unavailable offline. Persistent offline availability requires a verified local copy, not just a picker entry.
6. Measure time to the correct source/rows, wrong-version selection, missing/altered rows, citation navigation, preparation effort, and useful completion. Do not score by fluent output or a keyword alone.

This extends the current library in a testable direction. Outpost 0.8 only imports TXT/Markdown; it does not index all phone files, read email, or parse PDF/DOCX/XLSX. The user must deliberately provide documents or grant an appropriate future import scope. No message or attachment access is inferred from these suggestions.

## Field diagnosis: turn the remembered workaround into an experiment

Keep three steps distinct: **identify/read**, **explain/propose**, and **apply/verify**. Begin with the first two on a simulated device. Supply an exact manual revision, device identity, firmware version, and a few predefined observations. Have Outpost identify a plausible fault and produce a report with source references and the check that would distinguish alternatives.

Prepared procedures/scripts should declare an ID/version/hash, supported device/firmware, typed parameters, preconditions, observation or mutation behavior, expected results, timeout, and recovery behavior. A known script can remain deterministic; the model helps select and explain it. No model-generated arbitrary shell commands should become executable merely because they appear in an answer or manual.

Android exposes USB host APIs, but host support is device-dependent and communicating with a USB device requires the appropriate user permission. The API alone does not establish serial-console, Ethernet, vendor-protocol, or repair compatibility. Select one explicit transport/device family before planning a real adapter. [Android USB host documentation](https://developer.android.com/develop/connectivity/usb/host).

A later write experiment would require an inspectable change plan, current-device preconditions, scoped user authorization, and post-change verification. Model identity checks and rollback feasibility must be specific to the procedure; recovery cannot be promised generically. Real cables/devices remain outside the current emulator-only scope. A fake transport can test disconnects, mismatched firmware, timeouts, stale observations, and refusal to run an incompatible procedure without claiming physical compatibility.

## Work prepared offline and executed later

The suggestion does not specify what an event means. A local reminder, queued report upload, outbound message, and remote configuration change are different jobs. Discovery should identify the intended task before selecting a connector or scheduler.

A proposed queue item should contain a stable ID, action type, exact destination and payload/version, creation/expiry times, preconditions, authorization scope, attempt history, and result. Suggested states are draft, ready, waiting for connection, executing, succeeded, failed, canceled, and needs review. Reconnection is a scheduling condition, not consent. Changed payloads, expired authorization, or changed target state may require review; the queue must not silently broaden an earlier instruction.

Retries need a duplicate-prevention strategy. A timeout after a remote service accepted a request is an unknown outcome, not proof of failure; reconcile it before retrying a non-idempotent action. Persist cancellation and history across app/process restarts. Start with a local mock service and a simulated network state, without real delivery.

Android WorkManager supports network constraints and retries, but operating-system scheduling affects when work actually runs; it is not an exact instant-of-reconnection guarantee. [Work request constraints and scheduling](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).

Outpost currently has no INTERNET permission or connectors. A connected executor would be a separate architectural decision, including whether it belongs in a companion component, build variant, or the main app. No such permission, service, or automation has been added. Local IP transports likewise need an explicit permissions review even when the device network has no internet route.

## Implications for the two domains

Keep inference and knowledge as the core domains. Extend the application coordinator with explicit context, optional bounded local tools, and eventually a persistent action ledger. A third model or a cloud backend is not implied. The [architecture](architecture.md) records this as a proposal.

Knowledge needs document provenance, optional receipt/download context, row-aware structured data, equipment applicability, and inspectable originals. Inference needs task-dependent behavior: document retrieval/extraction, constrained troubleshooting, or reflection. Do not force every job through the same three-passage short-answer prompt or reviewer.

Performance priorities also differ. Document recall values fast correct retrieval and structured extraction before generation. Reflection values conversational continuity and streaming. Diagnosis values correct observations, bounded tool timeouts, and applicability checks. Continue Bonsai optimization, but evaluate its contribution to these outcomes rather than assuming tokens per second is the only bottleneck.

## What to prioritize and what to leave open

**Corrected recommendation after clarification:** evaluate contextual fact-finding, recommendations, comparisons, synthesis and bounded troubleshooting across several subjects and evidence conditions. The issue-list and restaurant questions are example fixtures within that matrix, not two fixed demos that define the product. Shared evidence and retrieval foundations remain useful; device diagnosis, reconnection actions and reflection are additional hypotheses.

Follow-up discovery should ask for a recent real incident, the file/device types involved, how often the problem recurs, time lost, the existing workaround, what users actually prepare before leaving, and whether they would choose the phone over a laptop. Use anonymized or synthetic artifacts for initial tests. Compare time/outcome against file search, the existing manual reader, and the prepared scripts—not just against a model without context.

The strongest test of the coverage objection is behavioral: does the workflow save enough effort during a real incident to justify preparation and storage, and is it useful when connectivity happens to be available too? Do not resolve that question with this group's hypothetical answers alone.
