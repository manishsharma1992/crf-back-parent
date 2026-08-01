# DecisionTreeTraversalService — rewrite

## Placement: crf-shared, `domain.leverage.service`

Keep it in crf-shared. It is a **pure domain service**: a function of a definition and a set of
answers, with no repository, no clock and no Spring. It belongs beside the model it walks, and
crf-datasync can use it too — the obvious use is validating a published tree by walking it.

What does NOT belong here, and should sit in crf-back's application layer:

- loading the definition (`LeverageDecisionTreeDefinitionRepository`)
- persisting answers, and the save-time rules — checklist NA coercion, blocking messages, the
  `CALC/` field arithmetic
- deciding which forms to open from a preliminary outcome

That split is what keeps this class testable without a database, and it is why the tests below run
in milliseconds.

| Type | Package |
|---|---|
| `DecisionTreeTraversalService`, `TraversalAnswers` | `domain.leverage.service` |
| `ConditionEvaluator` | `domain.leverage.value.tree` (unchanged home) |
| `TraversalResult`, `TraversalState`, `ItemAnswer` | `domain.leverage.value` |

## Two states, not three

`AWAITING_EXTERNAL` is gone with the rating-motor call. Every calculation is local now, so a walk
never pauses on another service and never needs resuming by a callback. `STRANDED` is added — not
a real state so much as a way to make a definition published around the validator fail visibly
instead of hanging a request thread.

## Structure, and why Sonar stays quiet

The walk is a private `Walk` object holding the state one run needs, so each rule is a short method
rather than a long loop with accumulating locals. Every method is a flat sequence of guard clauses
— no nesting, so Sonar's nesting increments never apply. Highest cognitive complexity is `run()`
at about 5.

`Step` is a tiny record with four factories (`pending`, `stranded`, `end`, `goTo`). Without it,
`step()` would have to signal three different outcomes through nulls.

## The rules the tests pin

**An unanswered question matches nothing.** Not the default, not an exception — false. This is
what lets Q-S04 carry one value rule per inbound path and keep the unused ones quiet
(`rules_about_paths_not_taken_stay_silent`).

**Authored order is business logic.** Q-T01's `Q01 is NO` sits above `ALL_NO` because ALL_NO is
true on both LBO paths. Swap those lines and `the_lbo_test_wins_over_all_no_on_the_transaction_
block` fails.

**A derived value cannot always decide the route.** Q-S04 yields BORROWER on a path that ENDS the
form and on one that continues, so its branches test how it was reached
(`the_same_derived_value_routes_differently_by_path`).

**The multiple is not the ratio.** With a negative adjusted EBITDA, `totalEcbDebt > 6 x
adjustedEbitda` and "ratio > 6" disagree, which is why the BR is written as a multiple and why
Q-Q02's 0–4 rule must come first (`the_low_ratio_rule_wins_over_the_debt_multiple`).

**A checklist is answered once any item is YES.** The YES settles the block, so unanswered siblings
never hold the walk up. NOT_APPLICABLE never triggers.

**Nothing writes an empty flag.** A flag no branch named is absent from the map
(`unset_flags_are_absent_rather_than_blank`).

## The walk layers its own knowledge

`Walk` implements `TraversalAnswers` and delegates to the caller's, layering values it has computed
over the answers given. Without that, a condition on Q-S04 could not see the level Q-S04 just
derived. It also answers a checklist lookup with its aggregate, so a branch can test ANOTHER
question's checklist.

## Linking PRELIMINARY to ECB / FED

`TraversalResult.outcome()` already carries the recommendation, and `Outcome.formsToShow()` says
which forms it opens. The orchestration — walk PRELIMINARY, read the outcome, open those forms,
apply the outcome's forced flags — is a crf-back application service, not this class. Nothing here
needs to change when you add it.

## Open

**`fieldValue` returns `BigDecimal`, so a LOOKUP inside a DATA_ENTRY has nowhere to go.** The
industry lookup will need a text-valued field accessor when it lands.

**Prefill is read-only here.** The walk uses the FED answer if there is one; whether the field is
then locked on screen is the UI's business, and whether the prefilled value is re-validated is the
save path's.
