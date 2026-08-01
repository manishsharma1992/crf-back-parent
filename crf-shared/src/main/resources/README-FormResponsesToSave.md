# `leverage.value.responses` — what the new model breaks

Short answer: **`Answer` and `FormResponses` cannot record an ECB analysis at all.**
`SpreadsheetSelection` is untouched; `LeverageResponses` needs two convenience methods and nothing
else.

The preliminary form hid this. Every preliminary question has exactly one value and one conclusion
(`RecommendationOutcome`), so a snapshot of `questionKey -> value` was complete. Neither holds for
ECB.

## Gap 1 — multi-part answers have nowhere to go

`Answer.value` is a single `String`. On the ECB form:

| Question | Shape | Fits today? |
|---|---|---|
| Q-B01A / Q-B01B | 3–4 checklist items | no |
| Q-T01 | 10 checklist items | no |
| Q-F01 | 17 numeric boxes, several with a justification | no |

That is ten of the twelve ECB questions. Completing an ECB form today would freeze the routing
aggregate and silently discard which criteria were ticked — for a regulatory record, the ticks are
the evidence.

**Fix:** `Answer.subAnswers : List<SubAnswer>`. One `SubAnswer` shape serves items and boxes; the
snapshot only ever reads them back for display and audit, so two near-identical records would earn
nothing. `justification` is null for an item.

## Gap 2 — ECB's conclusion has nowhere to go

Preliminary concludes with a `RecommendationOutcome`, held on the aggregate. **ECB and FED have no
outcome** — their whole result is the flags a terminal branch set (`ecbLeveragedFlag`,
`ecbCovenantStructure`, `ecbLeverageRatio`, `escalatedTransactions`, `ecbLboFlag`). Nothing in
`FormResponses` can hold them.

**Fix:** `FormResponses.flags : Map<String, String>`.

Worth being deliberate that this DUPLICATES `counterparty_characteristics`. It is not redundancy:
the counterparty row says what is true of the counterparty *now* and is overwritten by the next
analysis; this says what *this* analysis concluded and must survive that. Absent means unset — a
catalogued flag nothing filled is missing from the map, never blank.

## Gap 3 — `boolean computed` is too coarse

There are now four origins, and the difference is auditable:

- **TYPED** — the analyst chose it
- **COMPUTED** — derived by the tree's rules (Q-S04's level, Q-Q01 from the debt multiple)
- **CALCULATED** — arithmetic over the financial table (the ratio)
- **PREFILLED** — copied from the FED form; nobody answered it *here*
- **SYSTEM_ASSIGNED** — items forced to NOT_APPLICABLE once a YES settled the block

PREFILLED matters most: when ECB and FED disagree, "who answered this, and on which form?" is the
first question asked.

**Fix:** `AnswerProvenance provenance` replaces `boolean computed`.

⚠ **This one is not additive.** Removing a record component strands existing JSON. If preliminary
snapshots exist anywhere you care about, either backfill (`computed=true -> COMPUTED`, else
`TYPED`) or keep `computed` as a deprecated component alongside `provenance` and drop it later.
Everything else here is a pure addition and reads back fine.

## Kept deliberately

**`type` stays a `String`, not `QuestionType`.** Renaming an enum constant must not strand a
snapshot written under the old name. The same reasoning applies to `value`, which stays the
canonical code rather than a typed enum.

## Open questions

**Should the INFO panel be snapshotted?** When the flag is INR the analyst is shown RMPM's current
leverage data. Reopening a 2026 analysis in 2028 would show 2028 values, so the record is not
reproducible — but it is RMPM's data, not ours. My instinct is to freeze it, since it was on screen
when the decision was taken, but it is a business call.

**Are `subtitle` and `note` worth freezing?** Only `questionLabel` is stored. The Support-Entity
tooltip is a page of criteria the analyst was told to apply — arguably part of what they saw. Cheap
to add now, awkward later.

**Should `SubAnswer` carry the flag it filled?** `ecbLeverageRatio` is both a box and a flag. It is
recoverable by joining to the definition, but storing it makes the snapshot standalone.

## Knock-on

`PreliminaryResponseAssembler` builds `Answer` and `FormResponses`, so it changes with them, and it
is the class that will also need `result.flags()` and the new `TraversalResult` accessors. Send it
and I will do it in the same shape as the rest.
