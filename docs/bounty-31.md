# Bounty 31 (poidh) — external acceptance bar

Status: reference record. Date of record: 2026-09-29. This document records an **external bounty**
that supplied the project's motivation and acceptance bar. It is not an internal requirement: it
creates no task, no acceptance criterion, and no status claim in this repository. Capability status
stays owned by [current-state.md](current-state.md) and task status by [roadmap.md](roadmap.md);
this document records and traces the external bar only.

## Source and provenance

- Canonical bounty page: `https://poidh.xyz/mainnet/bounty/31`
- Retrieval route: the public page is JavaScript-rendered, and its HTML exposes only a truncated
  200-character meta description. The full record was therefore retrieved from the same
  application's tRPC endpoint:
  `https://poidh.xyz/api/trpc/bounties.fetch?batch=1&input=%7B%220%22%3A%7B%22json%22%3A%7B%22id%22%3A31%2C%22chainId%22%3A1%7D%7D%7D`
- Retrieval date: **2026-09-29**.

The bounty text below is served by the poidh application. It is **not** a signed or
on-chain-verified artefact. It must be re-fetched from the tRPC endpoint above before any
submission decision is made against it.

## Bounty metadata

| Field | Value |
|---|---|
| id | 31 |
| onChainId | 31 |
| chainId | 1 (Ethereum mainnet) |
| title | Build the Best Offline AI Research App for Android ⛺ |
| amount | `1139000000000000000` wei = 1.139 ETH |
| issuer | `0x10fc964ef70c8467cd8c53e9ed9347422adf96a8` |
| createdAt | 1789763783 (epoch seconds) |
| inProgress | true |
| isMultiplayer | true |
| isVoting | false |
| isCanceled | false |
| deadline | null |
| hasClaims | true |
| hasParticipants | true |

Price observations, recorded as observations about a **floating ETH price**, not as fixed figures:
the `og:image` URL embedded a currency rate of 2677.39, the API reported `amountSort` 3049.547, and
an earlier public fund-raising post described the pot as about $2900.

## Verbatim bounty text

Reproduced exactly as retrieved on 2026-09-29. Do not paraphrase, translate, or correct it.

```
### What Vitalik is looking for

Build a casual info lookup and research tool that runs entirely offline on Android and is >50% as good as internet search + frontier AI models. People travel with their phones into offline situations all the time, and if you're literally offline, remote inference doesn't help.

His past attempts got ~10 tokens/sec from 1B models that break on anything interesting. He suggests extreme MoE might be the right architecture for phones (including newer variants like n-gram models): something like ~100B params, most living on disk, with <1B activated per token.

That's one promising direction, not a requirement. Any architecture that gets there is welcome.

OG post: https://x.com/VitalikButerin/status/2100695863026954698

*Note: This is an independent community bounty inspired by Vitalik Buterin's post. He is not affiliated with it or involved in judging unless he chooses to weigh in.*

### Requirements

Your submission must:

- run on Android and compatible GrapheneOS hardware
- operate within a maximum **12GB RAM environment**
- use no more than **50GB total** for the app, model weights, indexes, databases, and other offline assets (streaming weights from disk is expected)
- work completely offline once installed
- make no API calls, remote inference requests, web searches, or other network requests during use
- not require Google Play Services for core offline functionality
- handle useful research questions beyond simple factual recall, including explanation, comparison, synthesis, and reasoning
- respond at speeds that feel usable for real lookups on a phone
- be published in a public GitHub repository
- include all code, assets, dependencies, and instructions needed to reproduce the submission
- clearly document the models, datasets, indexes, and other resources used

Architecture is open. LLMs, RAG, MoE, n-grams, compressed knowledge bases, custom retrieval systems, or other approaches are allowed.

There is no parameter-count limit.

### Real-device requirement

The app must work on real Android hardware at the time of submission.

Someone with a compatible Android or GrapheneOS device should be able to visit the GitHub repo, follow the instructions, and get the app running locally within a few minutes without substantial debugging.

Any required models, databases, indexes, or other assets must either be included or have clear download and installation instructions.

### Proof

Post a public demo on **X or Farcaster** showing:

- the app running offline
- several example queries and responses (include ones a 1B model would fail on)
- a link to the public GitHub repo
- a brief explanation of your approach

Submit a relevant screenshot to poidh along with a link to the post and your GitHub repo.

The GitHub repo must contain the functional submitted version when the poidh claim is made.

### Winner 🏆

Submissions are judged against the requirements above and the bar described in Vitalik's post: a useful, fully offline research tool that's >50% as good as internet + frontier models.

If Vitalik publicly confirms that a submitted build meets that bar, that submission wins the entire prize pot, and the bounty creator will accept the confirmed claim. The confirmation must clearly refer to the submitted project or repository.

Otherwise, if no submission has been confirmed by October 31st, 2026, the creator and bounty contributors will either:

- wind down the bounty and return contributed funds
- select a winner they believe best satisfies the requirements and spirit of Vitalik's request

Submissions that are fraudulent, malicious, plagiarized, materially different from the version reviewed, or in violation of the requirements above may be disqualified.
```

## Requirements trace

The fourteen requirement ids below are declared in
[../eval/fixtures-v1.json](../eval/fixtures-v1.json) (`bounty_requirements`). The state column
describes this repository against each requirement. **No requirement is claimed as met.** The
`verifiable_in_current_scope` value comes from the manifest; it describes what the current scope
can verify, not what has been verified.

| Id | Requirement (short text) | Verifiable in current scope | State in this repository (source of truth) |
|---|---|---|---|
| R-ANDROID | The submission must run on Android. | partial | Runs on the x86_64 AOSP emulator only; no real-device behaviour observed. Source of truth: [current-state.md](current-state.md) capability inventory. |
| R-GRAPHENEOS | The submission must run on compatible GrapheneOS hardware. | no | Not verifiable: physical device execution is out of scope by user decision (ADR-003). Source of truth: [decisions.md](decisions.md) ADR-003. |
| R-RAM-12GB | The submission must work in an environment with at most 12 GB of RAM. | partial | No peak-memory measurement exists; a post-run PSS sample is not peak memory (P-04 pending). Source of truth: [current-state.md](current-state.md) known-failure table. |
| R-STORAGE-50GB | The submission must fit within 50 GB total for the app, weights, indexes and databases. | partial | Pinned host-side model sizes are recorded in the manifest and are far below the cap; the on-device installed footprint is not measured. Source of truth: [../eval/fixtures-v1.json](../eval/fixtures-v1.json) `identity.models`. |
| R-OFFLINE | The submission must work completely offline once installed. | partial | Emulator airplane mode is enforced by scripts; no instrumented proof of full offline behaviour exists. Source of truth: [current-state.md](current-state.md). |
| R-NONETWORK | No API calls, remote inference, web searches or other network requests during use. | yes | The application manifest declares no INTERNET permission, so the installed application cannot open sockets; verified structurally, not yet by a scored offline run. Source of truth: [../app/src/main/AndroidManifest.xml](../app/src/main/AndroidManifest.xml). |
| R-NOGMS | No Google Play Services for core offline functionality. | yes | AOSP system image on the emulator; no GMS dependency in the build files. Source of truth: [decisions.md](decisions.md) ADR-002. |
| R-BEYOND-RECALL | Handle explanation, comparison, synthesis and reasoning beyond simple factual recall. | partial | Fifteen fixtures across seven question families are defined in the manifest; none has been executed as a scored benchmark. Source of truth: [../eval/fixtures-v1.json](../eval/fixtures-v1.json). |
| R-SPEED | Usable lookup speed on a phone. | no | Emulator timing is recorded but is explicitly not a phone prediction (ADR-003, known-failure P-01). Source of truth: [current-state.md](current-state.md). |
| R-PUBLIC-REPO | Published in a public GitHub repository, reproducible and documented. | partial | A private GitHub remote is authorized by the user (2026-09-29); no remote is configured yet, and public visibility is deferred to the final usable version. Source of truth: [current-state.md](current-state.md). |
| R-REAL-DEVICE | Must work on real Android hardware at the time of submission. | no | Physical-device validation is deferred by user decision; the emulator remains the only execution target (ADR-003). Source of truth: [decisions.md](decisions.md) ADR-003. |
| R-PROOF | Public demo on X or Farcaster plus a poidh screenshot. | no | Out of scope: no public demo exists or is planned inside the repository's current scope. Source of truth: [../odd/tasks/eval-fixture-manifest.md](../odd/tasks/eval-fixture-manifest.md) scope boundary. |
| R-BAR-50PCT | More than 50% as good as internet search plus frontier AI models. | no | A comparison protocol is defined in [../eval/rubric-v1.md](../eval/rubric-v1.md), marked **proposed, not measured**; nothing in the repository currently scores this bar. Source of truth: [../eval/rubric-v1.md](../eval/rubric-v1.md). |
| R-ARCH-OPEN | Architecture open, no parameter-count limit. | informational | The architecture is documented as open and layered across world, regional and personal evidence; no single verification exists. Source of truth: [architecture.md](architecture.md). |

## Scope boundary — user decisions, 2026-09-29

These are decisions, not proposals:

- Physical device execution stays out of scope, and the emulator remains the only execution target.
  ADR-003 stays active.
- A **private** GitHub repository is authorized. Public visibility is deferred until a final usable
  version exists. No remote, no release, and no licence selection happen as part of this.
- The bounty requirements about real device, GrapheneOS, RAM, storage, real-device latency, public
  repository and public proof are **recorded and traced but cannot be verified in the current
  scope**. They must never be reported as met.
- The bounty's question-quality requirement (explanation, comparison, synthesis, reasoning beyond
  simple factual recall) is in scope as an evaluation target.

## Third-party commentary note

The often-quoted phrasing "tell me the best vegan restaurants in [city I am currently in]" comes
from a public third-party X thread, **not from the bounty text**. It illustrates a question type
(specific, contextual, place-dependent), not a product vertical. Per ADR-017 and
[offline-world-knowledge.md](offline-world-knowledge.md), individual examples guide fixture
construction; they do not define a vegan/restaurant product or a fixed mandatory test.

Mirrors of that commentary, labelled as **secondary, non-authoritative mirrors**:

- `https://nitter.jaydenha.uk/poidhxyz/status/2103871237810471177`
- `https://nitter.jaydenha.uk/kennyistyping`
- `https://digg.com/ai/mcgpa8l0`

The original post the bounty cites is `https://x.com/VitalikButerin/status/2100695863026954698`,
labelled here as **unreviewed**: this repository has not independently verified its contents.

## Where the requirements meet the roadmap

This is a **mapping, not progress**: it names the roadmap tasks that would move a requirement, and
says nothing about how far any of them has progressed. Task status lives in
[roadmap.md](roadmap.md).

| Requirement | Roadmap tasks that would move it |
|---|---|
| R-BAR-50PCT | E-01, E-05, W-01, W-02, W-03 |
| R-RAM-12GB | P-04 |
| R-SPEED | X-06 |
| R-REAL-DEVICE | X-06 |
| R-GRAPHENEOS | X-06 |
| R-PUBLIC-REPO | R-05 |
| R-BEYOND-RECALL | W-01, W-02, W-03 |

X-06 requires the user to explicitly lift the emulator-only execution restriction (ADR-003) before
any device work begins. R-PROOF additionally depends on a public repository and public
distribution decisions that are out of scope today.

## Closing note

This document is **not a status list**. Capability status stays owned by
[current-state.md](current-state.md) and task status by [roadmap.md](roadmap.md). Nothing here
authorizes a claim that any bounty requirement is satisfied.
