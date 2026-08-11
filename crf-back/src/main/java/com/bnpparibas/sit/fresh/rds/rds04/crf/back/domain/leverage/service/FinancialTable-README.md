That's exactly the answer I needed — eight non-editable (your seven plus the new `netDebt`), ten editable adjustments. The sheet was right and the javadoc was stale, so in `DataField` that `@param editable` line should read:

```
 * @param editable    false for the two ratios, the three totals, and anything read from FINSTAR.
 *                    The ten adjustments are the only boxes the analyst types into — a
 *                    FINANCIALS/ box is prefilled and READ-ONLY; when it is wrong the fix is in
 *                    FINSTAR, which is what SOURCE_EMPTY tells the analyst.
```

On to the domain. Files are above — six of them, all pure, no Spring, no I/O.Package placed at `domain.leverage.financial` — move it to wherever `ChecklistCoercionDomainService` sits if that's `value.tree`, and rename freely.

Four decisions in there worth your eye, since they're the ones that would be expensive to change later:

**`FinancialInputs` names its fields explicitly rather than carrying a map.** A loop over a map would be shorter and would survive the BA adding an eleventh adjustment without a recompile. I went the other way because this calculation is the regulated part — a reviewer should be able to read `adjustedEbitda()` against the ECB definition line by line. The eleventh adjustment needs a code change either way, since `Formula` is documentation only.

**`newDrawn` is the one box two calculations read.** It feeds Total ECB Debt and Total Net Funded Debt, and the Fields tab doesn't repeat it under group 2, so nothing in the workbook records that coupling. It lives in `DebtAdjustments`' javadoc and nowhere else — worth knowing before someone reorganises the record.

**`totalNetFundedDebt` doesn't subtract cash.** Net Debt is already net of cash inside FINSTAR, so the mock-up's Cash box would double-count. That's why the workbook says to ignore it, and I've put the reason in the method rather than just the instruction.

**`valueOf` returns `Optional.empty()` for an unknown CALC key** instead of throwing, matching the AREA-map degradation. A workbook naming a calculation this release doesn't implement produces a missing box that the validator reports, not a failed save.

The test set pins both screenshots numerically, so if crf-next ever diverges from crf on a live analysis you'll see it here first. `aRatioJustBelowFourStaysBelowFourInTheStoredValue` is the one that guards the scale decision — it asserts the screen shows `4.00` while the stored value stays under four, which is exactly the divergence that would route a sub-4 transaction as leveraged.

Next natural step is `ValidationDomainService` — the four field rules (`SOURCE_EMPTY`, `MUST_NOT_BE_ZERO`, `JUSTIFICATION_REQUIRED`, `MUST_BE_POSITIVE`) as pure private methods taking `ComputedFinancials` as a pre-resolved fact. Say the word and I'll write those against your existing service, though I'll want to see it first so the new methods match how `EntityEligibility` is already threaded through.