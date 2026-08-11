package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.FinancialInputs.DebtAdjustments;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.FinancialInputs.EbitdaAdjustments;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.FinancialInputs.Sources;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.math.BigDecimal;

/**
 * The five ECB financial calculations of Q-F01.
 *
 * <p><b>Purity.</b> No I/O, no Spring, no clock, no state. In: {@link FinancialInputs}. Out:
 * {@link ComputedFinancials}. Total on every input, including all-null — it never throws, so a
 * mistyped box or a missing source degrades to an absent result rather than to a stack trace on
 * an analyst's screen.
 *
 * <pre>
 *   Adjusted EBITDA        = EBITDA
 *                          + Reported LTM adjustment
 *                          + Pro-forma perimeter adjustment
 *                          + IFRS 16 adjustment
 *                          + Forward looking adjustment
 *                          + Other justified adjustment
 *
 *   Total ECB Debt         = Gross Debt
 *                          + Committed Undrawn Debt
 *                          + New Drawn Debt
 *                          + New Committed Undrawn Debt
 *                          + IFRS 16 adjustment
 *                          + Other adjustment
 *
 *   Total Net Funded Debt  = Net Debt + New Drawn Debt
 *
 *   ECB Leverage Ratio       = Total ECB Debt        / Adjusted EBITDA
 *   Net Funded Leverage Ratio = Total Net Funded Debt / Adjusted EBITDA
 * </pre>
 *
 * <p><b>Negatives are ordinary.</b> EBITDA, Adjusted EBITDA, the debt totals and both ratios may
 * all be negative, and none of that is an error. A negative Total ECB Debt is in fact what Q-Q02
 * routes on directly.
 *
 * <p><b>Zero is not.</b> A zero denominator is blocked upstream by
 * {@code ECB_ADJUSTED_EBITDA_ZERO}, but this service does not rely on that: see {@link Ratio}.
 *
 * <p><b>What is NOT here.</b> Q-Q01 and Q-Q02 do not divide. They compare
 * {@code totalEcbDebt > 4 x adjustedEbitda} — cross-multiplied, and therefore defined at every
 * value of Adjusted EBITDA including zero. Those predicates belong to the condition evaluator
 * reading the frozen boxes, not to this class. The ratios exist to be displayed and to fill the
 * {@code ecbLeverageRatio} flag; the only routing that reads a ratio is a {@code range} test,
 * which an absent ratio correctly matches nothing.
 */
@DomainDrivenDesign.DomainService
public final class FinancialCalculationDomainService {

    public ComputedFinancials compute(FinancialInputs inputs) {
        Sources sources = inputs.sources();
        BigDecimal adjustedEbitda = adjustedEbitda(sources, inputs.ebitdaAdjustments());
        BigDecimal totalEcbDebt = totalEcbDebt(sources, inputs.debtAdjustments());
        BigDecimal totalNetFundedDebt = totalNetFundedDebt(sources, inputs.debtAdjustments());

        return new ComputedFinancials(
                adjustedEbitda,
                totalEcbDebt,
                totalNetFundedDebt,
                Ratio.of(totalEcbDebt, adjustedEbitda),
                Ratio.of(totalNetFundedDebt, adjustedEbitda));
    }

    private BigDecimal adjustedEbitda(Sources sources, EbitdaAdjustments adjustments) {
        return Amounts.sumFrom(sources.ebitda(),
                adjustments.reportedLtm(),
                adjustments.proFormaPerimeter(),
                adjustments.ifrs16(),
                adjustments.forwardLooking(),
                adjustments.otherJustified());
    }

    private BigDecimal totalEcbDebt(Sources sources, DebtAdjustments adjustments) {
        return Amounts.sumFrom(sources.grossDebt(),
                adjustments.committedUndrawn(),
                adjustments.newDrawn(),
                adjustments.newCommittedUndrawn(),
                adjustments.ifrs16(),
                adjustments.other());
    }

    /**
     * Net Debt already nets cash off inside FINSTAR, which is why the mock-up's separate "Cash"
     * input is ignored — subtracting it here would net the same cash twice.
     */
    private BigDecimal totalNetFundedDebt(Sources sources, DebtAdjustments adjustments) {
        return Amounts.sumFrom(sources.netDebt(), adjustments.newDrawn());
    }
}
