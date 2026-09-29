# Local data, provenance, and trust boundaries

Status: description of current controls and concrete requirements for the planned knowledge workflows. This is not a security certification.

## Current boundary

The [manifest](../app/src/main/AndroidManifest.xml) requests no permissions, has no network SDK/service, and disables application backup with extraction rules. Source documents and models are imported through Android's picker and stored in application-owned storage. There is no analytics, account, synchronization, arbitrary tool execution, or cloud inference feature.

An external document provider may use its own network connection. Offline testing must select genuinely local files. Disabling application backup does not imply a fully audited data-loss or device-compromise protection model.

## Inputs and controls

| Input / risk | Current control | Remaining work |
|---|---|---|
| Incorrect model or quantization layout | Exact profile size/hash allowlist before loading | Review parser exposure when updating backend or generalizing model support |
| Oversized or invalid text | 1 MiB bound, UTF-8 import validation, NUL/empty-content checks | Broader file types need format-specific limits and extraction recovery |
| Search syntax injection | Only normalized letters/digits form FTS terms; parameterized database queries | Reassess any future query language or structured filter layer |
| Instructions embedded in sources | Sources framed as data; role-delimiter sanitization; no tool/network executor | Prompt adherence is not guaranteed; future tools need independent allowlist/input validation |
| Unsupported claim with a plausible citation | Source reader and numeric reference checks | Claim-level support evaluation remains incomplete |
| Rejected speculative continuation | Target verification and KV rollback; only confirmed text emitted | Numerical differences can still change target sampling |
| Stale source or incorrect revision | Basic source/date metadata visible | Separate content/import dates, revision applicability, and explicit coverage metadata |
| Native runtime failure / memory retention | Request limits, cancellation, context invalidation and lifecycle cleanup | Peak-memory/pressure/process-death evaluation |

Model verification pins content identity; it does not certify the truth of model output. A source's hash likewise does not guarantee its accuracy or timeliness.

## Planned package and tool boundary

Keep content files read-only from the perspective of inference. A retrieved instruction cannot enable an action, network access, or a permission. The coordinator must validate tool name, typed parameters, budgets, and applicability against user-confirmed context.

Package extraction must reject path escapes and invalid manifests and activate only a fully validated installation. Updates must preserve the old readable package until the new one succeeds. Per-source licenses and attribution travel with exported or redistributed content. Publisher authentication, if introduced, is separate from checksums.

PDF/OCR and geographic inputs introduce new parsing dependencies and uncertainty. Preserve originals for inspection, including page imagery where extraction may misread a number or unit. Do not infer live electrical status, road safety, or current availability from a stored record.

## Data retention and evidence

Debug builds and test harnesses contain diagnostic interfaces and export JSON/screenshots. They are development artifacts. Production signing, removal/restriction of test surfaces, and a release review are pending.

Tests currently use demonstration or fictitious field content. Before publishing any evidence, inspect it for imported personal documents, location/asset identifiers, local paths, or sensitive operational details. A new runtime run may overwrite fixed evidence filenames; preserve the original experimental record before rerunning.

Do not commit GGUF weights, AVD disks, private app data, signing keys, SDKs, build caches, or unrelated workspace files. The [migration plan](repository-migration.md) defines preservation and staging boundaries. A user-facing export/delete/retention workflow is future work; document its exact behavior when implemented rather than claiming it exists now.
