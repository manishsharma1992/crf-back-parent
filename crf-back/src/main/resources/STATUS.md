# Where we are, and what is left

## Done

| Area | State |
|---|---|
| Authoring template | v10 — 7 tabs, ECB tree complete end to end |
| Domain model (crf-shared) | 21 records/enums rewritten for the new grammar |
| `DecisionTreeValidator` | rewritten, 81 error codes |
| Excel parser (crf-datasync) | workbook port, catalogues, question tabs, expression grammar, assembler |
| `SourceLocator` | validation errors resolve to sheet/row/column/line |
| `DecisionTreeImportService` | all three forms, one transaction, DRY_RUN default |
| Import endpoint | 400 vs 422, POI contained in infrastructure |
| `DecisionTreeTraversalService` | rewritten, two states, Sonar-safe |
| Form state projection | `FormAnswers` adapter, `FormState`, `QuestionView`, assembler |
| Response snapshot | `Answer`, `SubAnswer`, `FormResponses`, provenance, justification |

## Priority 1 — get a workbook into the database

Blocking, in order:

1. **Compile.** `DecisionTreeValidatorTest` fixtures (arity changed on `Question`, `DataField`,
   `Condition`); anything still calling `effect.setFlags()`, `question.external()`,
   `field.computed()`.
2. **`DecisionTreeResolver`** — check `resolveActive` / `resolvePinned` still match the repository
   after the merge onto `LeverageDecisionTreeDefinitionRepository`.
3. **Wire the beans** — `PoiWorkbookSourceFactory`, the parser chain, `DecisionTreeAssembler`,
   `DecisionTreeImportService`. Config-based, no annotations on domain classes.
4. **Fill the real workbook** and run `mode=DRY_RUN` against it. Expect a first pass full of
   report lines; that is the tool working.
5. **Publish** and confirm three rows with JSONB that reads back.

The Hibernate JSON round-trip on a deep record graph is the one thing I could not verify by
inspection — run `the_stored_definition_round_trips_whole` early rather than at integration time.

## Priority 2 — preliminary regression

Changed under it: `TraversalResult` shape, `FormState`, `QuestionView`, `Answer`,
`FormResponses`, the traversal engine itself. The wire format did NOT change, which is the main
protection.

Two behaviour changes to check against the signed-off expectations:

- **Computed answers are now frozen.** The old assembler skipped them; Q06 will now appear in the
  preliminary snapshot with `COMPUTED` provenance. The outcome column is unchanged. If a test
  asserts an exact answer count, it will fail — and it should be updated, not the code.
- **`FormState.flags` is new and always present.** Additive; existing assertions pass.

Also confirm the outcome still lands on the aggregate, since that path is untouched but sits
downstream of everything that moved.

## Priority 3 — ECB domain layer

Not started. In dependency order:

1. **Financial calculations.** Five named calculations (`adjustedEbitda`, `totalEcbDebt`,
   `ecbLeverageRatio`, `totalNetFundedDebt`, `netFundedLeverageRatio`) as a domain service.
   Decide what a zero or negative adjusted EBITDA yields for the ratio — the BRs do not say, and
   Q-Q01/Q-Q02 route on it.
2. **Checklist save rules.** The three scenarios: coerce unanswered to NOT_APPLICABLE on ANY_YES,
   refuse the save when a blank has no YES, save all-NO as typed. Belongs in a save use case, ahead
   of the assembler.
3. **Field validations.** Four named rules — `MANDATORY`, `JUSTIFICATION_REQUIRED`,
   `MUST_BE_POSITIVE`, `SOURCE_EMPTY` — plus the three Q-S06 business-group checks that need RMPM.
4. **`SaveEcbFormUseCase`** — same shape as preliminary, plus flags to
   `counterparty_characteristics`.
5. **Preliminary → ECB/FED linking.** `TraversalResult.outcome()` and `Outcome.formsToShow()` are
   already there; the orchestration is not.
6. **Info panel provider** — the RMPM lookup behind `ecbLeveragedFlag is INR`.

## Open business questions

Carried forward, none blocking the import:

- Which `ecbLeveragedFlag` value the qualitative terminals carry — there is no code for "ECB
  Highly Leveraged" in the mapping, which suggests they are all `ECB_LEVERAGED (1)` with escalation
  carried separately. **Sushmitha.**
- Q-Q01's "or LBO transaction" contradicts its arithmetic-only rule.
- Q-Q02: negative RATIO (label) or negative DEBT (rule)?
- The flowchart shows Q-Q02 as "No" in the `<0 / >6` band, contradicting the BR.
- Whether the INFO panel's RMPM data is snapshotted with the analysis.
- Free adjustment rows and the "Cash" input in the mock-up, neither in the BRs.

## Housekeeping

- ArchUnit: `application` in crf-datasync is at 27 classes; split in `datasync/README.md`.
  `domain.leverage.value.tree` at 27; split in `domain/PACKAGING.md`.
- `getActiveLeverageDecisionTreeDefinition` returns a JPA entity from a domain port — worth
  making private on the Impl.
- Import endpoint has no authorisation.
