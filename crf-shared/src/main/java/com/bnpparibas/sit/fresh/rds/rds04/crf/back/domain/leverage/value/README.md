# Leverage decision-tree domain model — iteration 2

Records only. No Spring, no Excel, no JPA — the definition model the parser builds, the
validator checks and the traversal engine walks.

## What changed from iteration 1

| Class | Change |
|---|---|
| `Question` | dropped `order` and `external`; added `valueRules`, `prefillFrom`, `note`, `fillsFlag` |
| `Condition` | added `fieldKey` (predicate against a DATA_ENTRY box) and `comparison` |
| `Comparison` | **new** — `field totalEcbDebt > 4 x field adjustedEbitda` |
| `ValueRule` | **new** — ordered `condition -> value` for COMPUTED questions |
| `DataField` | added `group`, `note`, `editable`, `derivedFrom`, `formula`, `fillsFlag`; dropped `computed` |
| `LabelDetails` | `bullets` is now `List<Bullet>` so notes can nest one level |
| `Bullet` | **new** |
| `DecisionTreeDefinition` | added `flags`, `flagValueSets`, `validationMessages`, `infoPanels` |
| `FlagDefinition` / `FlagValue` / `ValidationMessage` / `InfoPanel` / `Severity` / `ValidationRule` / `FlagStorage` | **new** catalogues |
| `QuestionType` | added `LOOKUP` |

## Unchanged, still needed

`Option`, `ChecklistItem`, `Section`, `Outcome`, `RecommendationOutcome`, `LeverageFormType`,
`DefinitionStatus`, `LocalizedLabel`, `LocalizedQuestionLabel`.

## Consequences for the validator

Three existing rules change meaning and one disappears:

- `EXTERNAL_NO_DERIVED_FROM` — **gone**. Replaced by: a COMPUTED question must carry
  `derivedFrom` XOR `valueRules`, never both and never neither.
- `isNumericValued(Question)` — ranges now target a **field**, so this becomes
  `field.type() == NUMERIC` resolved through `DecisionTreeDefinition.field(key)`.
- reachability exemption — was `COMPUTED && !external`, which would now exempt every computed
  node. Becomes `Question.isDisplayOnlyOutput()` (`COMPUTED && branches.isEmpty()`).
- `DATA_ENTRY_TERMINAL` — unchanged and still right: Q-F01 continues to Q-Q01.

New rules the catalogues make possible:

- every flag named by a branch, a `fillsFlag` or a field exists in `flags`
- for a CODE flag, the value exists in its set and `setBy` allows this form
- a `fillsFlag` question's option values all exist as codes of that flag's set
- every `questionKey` / `fieldKey` in `validationMessages` resolves
- an `InfoPanel.whenFlagKey` exists and `whenFlagValue` is one of its codes
- `Comparison` operands resolve to NUMERIC fields

## Known limit

`prefillFrom` (`FED/Q01`) crosses forms, and the validator sees ONE definition at a time. It can
check the shape but not that `Q01` exists on the FED form. Either validate the three definitions
together at import, or accept a runtime no-op when the source is absent. Worth deciding before
the parser writes it.
