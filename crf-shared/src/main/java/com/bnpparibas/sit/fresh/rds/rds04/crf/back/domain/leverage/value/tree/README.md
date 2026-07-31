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

---

# Iteration 2b — validator

`DecisionTreeValidator` and `ValidationResult` rewritten against the model above. Package for
everything here is `...crf.back.domain.leverage.value.tree`, in **crf-shared**; `Bullet` and
`LabelDetails` sit one level up in `...leverage.value`.

## Rules that changed

| Old | New |
|---|---|
| `EXTERNAL_NO_DERIVED_FROM`, `EXTERNAL_NO_BRANCHES` | **gone**. Replaced by `COMPUTED_NO_SOURCE` / `COMPUTED_BOTH_SOURCES` — a COMPUTED question needs `derivedFrom` XOR `valueRules` |
| reachability exemption `COMPUTED && !external` | `Question.isDisplayOnlyOutput()` |
| `COMPUTED_NO_BRANCH` | `NO_BRANCH`, now applied to any reachable dead end |
| `isNumericValued` = `NUMERIC \|\| external COMPUTED` | ranges resolve a **field** through the form-wide field index; a question operand must be `NUMERIC` |
| `CHECKLIST_BRANCH_NOT_AGGREGATE` | also accepts a branch testing ANOTHER question — Q-T01 needs `Q01 is NO -> Q-T02` ahead of `ALL_NO` |

## New rule groups

- **Field identity** — `DATA_FIELD_DUPLICATE_IN_FORM`. Conditions name a field bare, so keys must
  be unique across the whole form, not just within a question.
- **Comparison** — operands exist and are NUMERIC, operator and multiplier present.
- **Value rules** — only on COMPUTED, assign a declared option, condition validated.
- **Flags** — every flag named by a branch / `fillsFlag` / field is catalogued; a CODE value
  exists in its set; `setBy` permits this form; a `fillsFlag` question's options are all codes of
  that set.
- **Catalogues** — validation messages resolve their question and field, are unique and bilingual;
  info panels resolve their flag and value.
- **Shape only** — `PREFILL_BAD_FORMAT` checks `FORM/QUESTION_KEY`. Whether the FED answer exists
  is resolved at traversal time, not at import.

81 stable codes in total.

## Test fixtures need rewriting

`DecisionTreeValidatorTest` will not compile: `Question` and `DataField` changed arity, and
`Condition` gained two components. The builders to change are `bool`, `checklist`, `external`
(now `computedRuled`), `computedOutput`, `dataEntry`, `eq`, `agg`, `in`, `ranges`, `allOf`, plus
`def(...)` for the four new catalogues. `externalRatioWithRangesAndDefault_passes` and
`externalComputedWithoutDerivedFrom_fails` should become field-scoped range and
`COMPUTED_NO_SOURCE` cases.
