# Contextual questions about the world

Status: product direction clarified by the user on 2026-09-29; bounded chat/document/OSM implementation and development evaluation now exist; broad question-family quality remains open. This correction replaces the previous interpretation that made the restaurant example a mandatory standalone benchmark.

## What the example means

The user supplied a statement attributed to Vitalik about useful mobile offline knowledge. The restaurant question illustrates the **type and difficulty of questions**: specific, contextual, sometimes specialized, and requiring relevant facts, constraints, comparison, or judgment. It is not a request for a vegan app, a restaurant product, a mandatory diet-specific feature, or a fixed restaurant test that defines success.

Outpost should help answer practical questions about the world using the information available locally. Personal documents can contribute to the same answer. World, regional, and personal information are content sources, not three different products or a requirement to route every query through a geographic catalog.

The supplied social statement is product input, not independently verified publication text or a formal change to bounty rules. The [earlier travel study](travel-evaluation.md) is a historical example and does not define current product specialization.

## Question families to evaluate

Examples below are illustrative. They describe proposed tests, not implemented capabilities or real-world recommendations.

| Question family | Example | Work needed beyond fluent generation |
|---|---|---|
| Specific fact in context | What power-plug type is used in this country? | Identify the relevant context and retrieve applicable reference information |
| Contextual discovery | Where can I find a pharmacy near this station? | Resolve the place, retrieve actual entities, distinguish distance from route, expose unknown current conditions |
| Recommendation with constraints | What could I visit on foot in the two hours I have? | Combine location, time, preferences, accessible local records and an explicit ranking criterion |
| Comparison and compatibility | Which of these adapters works with this equipment, and why? | Preserve exact models/specifications, compare requirements, and explain tradeoffs |
| Explanation and application | Why does altitude affect cooking time, and how does that relate to my situation? | Retrieve relevant concepts, explain causality, and avoid unsupported precision |
| Cross-source synthesis | Using my booking and the saved transport timetable, how can I get there? | Combine personal and public material, reconcile dates and locations, and identify missing/live information |
| Bounded troubleshooting | What might explain this pump losing pressure, given these readings? | Match equipment/manual revision, distinguish observations from hypotheses, and identify the next useful missing fact |

A query can belong to several families. Restaurants, electrical adapters, reservations, trails, and equipment are varied subject matter used to test generalization. No one example becomes a dedicated product vertical merely because it is easy to describe.

## Common problem structure

1. Understand the user's goal and the actual question, including references such as here, this device, best, or the file from last week.
2. Determine which context and constraints matter. Location is relevant to some questions; other questions require an equipment model, a document revision, preferences, or no extra context at all.
3. Retrieve applicable local evidence, using the appropriate source/index rather than assuming every answer is a text passage or a map entity.
4. Compare, connect, calculate, or rank where the question requires it. Use bounded deterministic tools for exact calculations or structured filtering.
5. Explain a useful answer and make its supporting material inspectable. For recommendations, state the criteria and tradeoffs rather than implying an unsupported universal best.
6. Ask only for context that materially changes the answer. Distinguish insufficient coverage, conflicting evidence, and information that cannot be current offline.

Useful answers may be assembled from multiple sources even when no document contains the final response verbatim. Finding a matching file is one operation within this broader capability; it does not by itself test interpretation, synthesis, or recommendation quality.

## Implications for inference and knowledge

The two technical domains remain inference and knowledge. The knowledge domain can combine general references, regional/activity packages, personal documents, structured records, and user observations. The inference layer interprets the request and explains what follows from those inputs. The coordinator handles confirmed context and bounded tools.

Do not hard-code a single three-passage prompt, place filter, or lookup workflow as the solution to every question family. At the same time, the examples do not automatically authorize new tool executors, remote services, or physical-device actions. The separate action/reflection ideas remain discovery proposals.

Model training knowledge can assist language and reasoning, but it cannot establish the current state of a specific business, device, route, or timetable. Source applicability, dates, and coverage remain important. Source retrieval alone also does not establish that the model's inference is correct.

## Evaluation matrix

Organize evaluation around **question family × context/constraints × evidence conditions**, sampling different subjects and combinations of world, regional and personal material. Those content categories are coverage dimensions, not two fixed demos that alone define success.

Include direct facts, multi-source reasoning, recommendation tradeoffs, exact compatibility, missing constraints, ambiguous references, obsolete/conflicting records, and absent coverage. Vary the wording and domain, and hold out entities/questions so the evaluation tests transfer rather than memorized examples. Include some questions that need no clarification and some where clarification is essential.

Score separately:

- Correct interpretation of the user's objective and applicable context.
- Retrieval of relevant, sufficient evidence and preservation of exact values/identities.
- Correct comparison, synthesis, filtering, calculation, or reasoning as required.
- Recommendation usefulness under the stated constraints, including justified criteria.
- Supported claims, inspectable sources, handling of uncertainty, and proportionate clarification.
- Time to useful information, completed-answer latency, memory/storage, and preparation effort.

Keep knowledge-coverage, retrieval, fixed-evidence inference, and end-to-end failures distinguishable. A correct source ID, fluent text, reviewer agreement, or one successful restaurant/issue-list example cannot establish broad capability. All execution remains in the emulator.

## Scope boundary

The product goal is a general offline knowledge assistant that understands practical, contextual questions and uses available knowledge to answer them usefully. The next evaluation should cover several question families and subjects. Individual examples guide test construction; they do not imply a diet-specific product, an exclusive travel app, a fixed catalog, or an unbounded promise to know every fact offline.

Outpost 0.12 supplies persistent bounded chat, TXT/Markdown/CSV/PDF and recursive imports, exact sources and basic OSM feature lookup. The v5 evidence-only matrix defines 15 cases (12 runnable, 3 blocked), with historical attributed0.9 reviews. Semantic/spatial retrieval, tools, complete prepared missions and held-out product-chat quality remain pending. The current three-source prompt is an implementation budget, not the general solution to every question family. See [current state](current-state.md) and [evaluation](evaluation.md).
