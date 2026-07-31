# Packaging `domain.leverage.value.tree` — ArchUnit ≤ 20 classes

The tree package is at **27 types** and will keep growing (`ConditionEvaluator` and its context
are still to come). Splitting it by *reason to exist* rather than by type keeps every package well
under the limit and makes each one summarisable in a sentence.

## Proposal

Root stays `…domain.leverage.value.tree`.

| Package | Types | What it is |
|---|---|---|
| `.tree` | `DecisionTreeDefinition`, `Section`, `Question` | **3** — the aggregate and its spine |
| `.tree.routing` | `Branch`, `Condition`, `Effect`, `ValueRule`, `Range`, `Comparison`, `ComparisonOperator`, `Aggregate`, `ConditionEvaluator` | **9** — how the walk decides where to go next |
| `.tree.input` | `Option`, `ChecklistItem`, `DataField`, `DataFieldType` | **4** — what the analyst can answer |
| `.tree.label` | `LabelDetails`, `LocalizedQuestionLabel`, `Bullet` | **3** — display text and its bullets |
| `.tree.catalogue` | `FlagDefinition`, `FlagStorage`, `FlagValue`, `InfoPanel`, `ValidationMessage`, `ValidationRule`, `Severity`, `Outcome` | **8** — declared once, referenced from many places |

27 types, largest package 9, room to grow in each.

`…domain.leverage.value` keeps its 9 (`AnalysisStatus`, `DefinitionStatus`, `LeverageFormType`,
`LeverageSpreadsheetView`, `LocalizedLabel`, `QuestionType`, `RecommendationOutcome`,
`TraversalResult`, `ValidationResult`) and needs no change.

## Why these five and not something else

**`.routing` is the one that earns its keep.** It is the only sub-package with real behaviour
rather than data — `Condition`, `Range` and `Comparison` exist solely to be evaluated, and
`ConditionEvaluator` is their only consumer. Keeping them together means the evaluator and the
things it evaluates move as one unit, and an ArchUnit rule can say *nothing outside `.routing`
depends on `Comparison`* to stop routing logic leaking into parsers or the UI.

**`.catalogue` is the second-clearest line.** Everything in it is declared once on the Forms tab
and referenced by key from elsewhere. Nothing in it is part of a question; grouping it makes the
"declare once, reference by key" rule visible in the package tree.

**`.label` looks trivial at 3 types but is the most reused.** Splitting it out stops `.input` and
`.catalogue` from both looking like they own display text.

`Outcome` sits in `.catalogue` rather than the root because it is exactly the same shape of thing —
a named entry declared on the Forms tab and referenced by code. `RecommendationOutcome`, the enum
naming those entries, stays in `…value` alongside the other cross-cutting enums.

## Suggested ArchUnit rules to add alongside

- no package under `.tree` may depend on `…application` or `…infrastructure`
- only `.tree` and `.tree.routing` may depend on `.tree.routing`
- `.tree.label` depends on nothing else under `.tree`

## Order of work

Move `.label` first (3 types, no inbound dependencies from the others), then `.catalogue`, then
`.input`, then `.routing`. Each is a rename-package refactor; the only manual step is widening
anything package-private that ends up split from its collaborator — worth checking
`Condition.hasPredicate()` and `Question.isDisplayOnlyOutput()` before you start.
