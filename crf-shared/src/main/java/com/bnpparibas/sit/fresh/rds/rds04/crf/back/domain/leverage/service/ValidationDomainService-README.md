Everything's there. Two things I found in the code that change what we agreed, so those first — then the service.

## The block does not currently stop the walk

`DecisionTreeTraversalService.dataEntryAnswer` answers Q-F01 when *every mandatory box has a value*:

```java
boolean complete = question.fields().stream()
        .filter(DataField::mandatory)
        .allMatch(field -> given.fieldValue(field.key()).isPresent());
```

A zero EBITDA is present. So the walk continues past Q-F01, Q-Q01 computes `debt > 4 × 0` → YES, and the analyst gets a leveraged verdict on data the BA told us to refuse. My earlier claim that semantic #1 would stop the walk for free was wrong — presence is not validity, and the walk only checks presence.

The minimal fix keeps traversal free of the save path's per-field concerns: `dataEntryAnswer` also requires that no **source rule** fires — `SOURCE_EMPTY` and `MUST_NOT_BE_ZERO`, the two that need nothing but a number. `JUSTIFICATION_REQUIRED` and `MUST_BE_POSITIVE` stay out of the walk, so a missing comment refuses the save without retracting questions from the screen.

## Which needs the calculated boxes inside `TraversalAnswers`

`fieldValue("adjustedEbitda")` resolves through `FormAnswers` to `raw.get("Q-F01.adjustedEbitda")` — and nothing posts that, because it's `CALC/`. So Q-Q01's `field totalEcbDebt > 4 x field adjustedEbitda` reads two empty operands today and quietly answers NO whatever the figures say.

So the application layer has to compute the financials and decorate `FormAnswers` with them before walking. That's one small wrapper, and it makes both the comparison and the block work off the same lookup.

## Module split — this one blocks compilation

`ValidationDomainService` is in **crf-shared**. `EntityEligibility` and the five financial classes you placed are in **crf-back**. crf-shared cannot see crf-back, so the service can't reference either.

Move these into `crf-shared`:

- `domain/leverage/value/` — `Ratio`, `Amounts`, `ComputedFinancials`, `FinancialInputs`, `EntityEligibility`
- `domain/leverage/service/` — `FinancialCalculationDomainService`

Both packages already exist in crf-shared, so nothing new to create. Separately, `FormAnswers` imports `domain.leverage.value.tree.DataField` but `DataField` lives in `domain.leverage.value` — worth a grep, several files look stale on that.