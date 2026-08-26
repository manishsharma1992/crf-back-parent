# Leverage validation module — consolidated change set

Everything below is **additive**, with one exception noted in §5.
`GetLeverageFormStateUseCase` and `SaveLeverageFormUseCase` keep their existing
method bodies.

---

## 1. New domain (`domain.leverage`)

| File | Purpose |
|---|---|
| `value/CompletenessBlocker` | why validation is refused |
| `value/FormCompletenessInput` | per-form facts: traversal state + blocking message keys |
| `value/CompletenessInput` | those facts for every required form |
| `value/FormCompleteness` | the verdict, plus which form blocked |
| `value/AnalysisStatusChange` | the row destined for `leverage_analysis_history` |
| `value/AnalysisSnapshotView`, `value/FormSnapshot` | BR03 read model |
| `service/AnalysisCompletenessDomainService` | BR01 rule, pure function |
| `port/AnalysisStatusRepository` | BR02 write port |
| `port/AnalysisSnapshotResolver` | BR03 read port |
| `exception/*` | not-modifiable, not-validatable, concurrent-validation |
| `LeverageAnalysis.ADDITIONS` | `assertModifiable()`, `validate()` — paste into the aggregate |

**The rule is two conditions, not four.** `DecisionTreeTraversalService`
refuses TERMINAL while a mandatory box is empty, so TERMINAL already means
"every mandatory answer on the walked path is present". Justifications arrive as
ERROR violations per Sushmitha. So: every required form TERMINAL, no ERRORs.

## 2. New application (`application.leverage`)

| File | Purpose |
|---|---|
| `service/AnalysisCompletenessService` | reads each required form, applies the rule |
| `service/ValidateLeverageAnalysisUseCase` | BR02 transition |
| `dto/ValidationAvailability` | BR01 payload |
| `api/LeverageValidationController` | two new endpoints |

**Direction of dependency matters.** `AnalysisCompletenessService` sits *above*
`GetLeverageFormStateUseCase` and calls it once per required form. The reverse
would recurse forever: form A's state computes completeness → reads form B's
state → computes completeness → reads form A's state.

**Which forms are required** comes from the PRELIMINARY outcome's `formsToShow`,
not the three definition-id columns — a pinned definition only records that a
form was *opened*.

## 3. New infrastructure (`infrastructure.leverage`)

Compare-and-set on status (no `@Version` column, no schema change), the history
entity, and the BR03 projection in JPQL.

`EntityMapping.ADDITIONS` — two mapping-only associations over columns that
already exist, which is what lets the BR03 query avoid a native `::text` cast.

## 4. Rating side (`domain.rating`)

`LeverageAnalysisReference` sits inside `model_specific_data`, composed into the
model value objects that are leverage-backed. Denormalised deliberately —
a validated analysis is frozen, so the copies cannot drift, and the rating row
then makes a complete audit statement without a live join.

## 5. The one change to existing code

```java
// SaveLeverageFormUseCase.save(), first thing after the load
analysis.assertModifiable();
```

Without it a tab opened before someone else validated will write to a VALIDATED
analysis. This also answers the open question in that class's Javadoc: an ERROR
does **not** block the save — autosave keeps recording progress — it blocks
*validation*, which is enforced on the validate endpoint.

## 6. Angular

- `state.validatedAt !== null` → `formGroup.disable()` — no payload change needed
- `GET /validation-availability` → Validate button state, refreshed after each save

Both are display concerns. `assertModifiable()` is what actually prevents the write.

---

## Known gaps

- **STRANDED degrades.** `FormState.Status` has only IN_PROGRESS/COMPLETED, so a
  stranded definition is reported to the analyst as an incomplete form. Validation
  is still correctly refused; only the explanation is wrong. Fix is a third
  `Status` value, whenever someone is next in that class anyway.
- **Flags path in `responses`** — `AnalysisSnapshotResolverImpl.readFlags` assumes
  `{ "ECB": { "flags": {…} } }`. One method to change if it differs.
- **Two bugs spotted in `GetLeverageFormStateUseCase`**, unrelated to this work:
  `crossFormAnswers` references an undeclared `target` where it means `formType`;
  `derivedSources` calls `startWith` rather than `startsWith`.
