# Fixing the preliminary module against the new engine

Ordered exposition → management → domain, as asked. **The exposition layer needs no change at
all** — that is the design goal, not luck.

## The wire format is untouched

Both controllers still post a flat `Map<String, String>` with dotted sub-keys, and
`GetFormStateRequest` / `SavePreliminaryFormRequest` are unchanged. Preliminary is signed off
against that shape, so the engine's richer view of answers — typed items, numeric fields,
cross-form lookups — is absorbed by an ADAPTER at the application boundary instead of by a changed
contract.

`FormAnswers.of(definition, answers)` is that adapter. It needs the definition because the same
dotted key means different things by question type: under a CHECKLIST it is an item answer, under a
DATA_ENTRY a numeric box.

| Was | Now |
|---|---|
| `traversal.resolve(def, request.answers())` | `traversal.resolve(def, FormAnswers.of(def, request.answers()))` |
| `result.status() == TraversalResult.Status.TERMINAL` | `result.isComplete()` |
| `result.visibleQuestionKeys()` | `result.path()` |
| `result.nextUnansweredKey()` / `externalQuestionKey()` | `result.pendingQuestion()` |
| `result.flagsToSet()` | `result.flags()` |

`assembler.assemble(def, request.answers(), result)` keeps taking the RAW map — the projection
renders what was posted, so it should not see the adapter.

## FormState

- `Status.AWAITING_EXTERNAL` and `ExternalRequest` **removed**. Nothing replaces them: no form
  waits on the rating motor any more, and the UI never resubmits a computed value.
- `flags` **added at the top level**, populated on every state. The LBO flag is filled by the very
  first question, so flags cannot wait for the terminal. A catalogued flag nothing has set is
  absent from the map and renders blank.
- `OutcomeView` unchanged, but now null for ECB and FED — they express results as flags, not as a
  recommendation. Only PRELIMINARY produces one.

## QuestionView

- `order` removed — screen order comes from routing, so the LIST order is the order.
- `external` removed.
- `subtitle` / `note` are now `LocalizedQuestionLabel`, so a note carries nested bullets. That is
  how the Support-Entity tooltip reaches the screen.
- added `prefillFrom`, `fillsFlag`, and `derived` — `derived` tells the UI an answer was computed,
  so it renders read-only and does not post it back.

## STRANDED throws rather than rendering

There is nothing an analyst can do about a definition that was published around the validator, so
`StrandedTraversalException` surfaces it as a fault rather than as a screen the UI has no case for.
If you would rather it degrade quietly, it is a two-line change to map it to `IN_PROGRESS` with a
null `nextQuestionKey` — but a silent dead form is harder to diagnose than a 500.

## Not fixed here, and it will not compile until it is

`PreliminaryResponseAssembler` was not attached. Its signature is fine
(`assemble(def, answers, result, locale)`), but if it reads `result.status()`,
`visibleQuestionKeys()` or `flagsToSet()` it needs the same mechanical renames as the table above.

`DecisionTreeResolver` is assumed unchanged — `resolveActive(formType)` and
`resolvePinned(formType, version)`.

Package paths here use `domain.leverage.value.tree` and `domain.leverage.value`; the attached files
still had the older `domain.leverage.tree`, so adjust imports if that move is still in flight.

## Regression cover

`FormStateAssemblerTest` keeps every original assertion that still has meaning — in-progress
mapping, current-question marking, answers on visible questions, catalogue-plus-branch flag
merging — and drops only the AWAITING_EXTERNAL case. New cases cover the path-based visible list,
mid-form flags, derived answers, sub-answer splitting, and the adapter itself.
