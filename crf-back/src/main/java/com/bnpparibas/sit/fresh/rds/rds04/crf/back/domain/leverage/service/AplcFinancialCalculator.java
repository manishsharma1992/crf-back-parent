package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;
/**
 * The Aviation Portfolio Leasing Company financial table.
 *
 * <pre>
 *   Applicable Funded Debt        = Total B/S Debt
 *                                 - Non recourse debt
 *                                 - Highly Secured Portion of Gross Debt
 *                                 - Qualifying Funded Subordinated Debt
 *
 *   Adjusted Book Value of Equity = Book Value of Equity - Equity Held within SPVs
 *
 *   Debt / Equity                 = Applicable Funded Debt / Adjusted Book Value of Equity
 * </pre>
 *
 * <p><b>Negatives are ordinary.</b> A negative Debt/Equity is not an error — it is a leveraged
 * outcome the flag cascade reads directly ({@code range [<0]} on Q-APLC02). Nothing here clamps or
 * rejects one.
 *
 * <p><b>Zero equity is the one thing that cannot be computed.</b> The ratio is then absent, the
 * mandatory box stays unanswered, and the walk stops at the table — with
 * {@code FED_APLC_ADJ_BOOK_VALUE_EQUITY_ZERO} on the Forms tab telling the analyst why. The block
 * is expressed as absence rather than as a gate, exactly as the ECB table does it, because a gated
 * traversal puts questions on screen that the answers never reached.
 *
 * <p><b>The three constants are published here, not authored as data.</b> They are
 * {@code Mandatory = Yes, Editable = No} on the Fields tab, so if nothing filled them the table
 * would never be complete and no APLC analysis could ever finish.
 * {@code aplcImpliedDebtToEquity} is display-only — confirmed with Clara, read by no formula,
 * condition or flag rule — but it still has to be published to be shown.
 */
@DomainDrivenDesign.DomainService
public final class AplcFinancialCalculator implements FinancialTableCalculator {

    // inputs
    static final String TOTAL_BS_DEBT = "aplcTotalBSDebt";
    static final String NON_RECOURSE_DEBT = "aplcNonRecourseDebt";
    static final String HIGHLY_SECURED_PORTION_GROSS_DEBT = "aplcHighlySecuredPortionGrossDebt";
    static final String QUALIFYING_FUNDED_SUBORDINATED_DEBT = "aplcQualifyingFundedSubordinatedDebt";
    static final String BOOK_VALUE_OF_EQUITY = "aplcBookValueOfEquity";
    static final String EQUITY_HELD_SPVS = "aplcEquityHeldSPVs";

    // computed
    static final String APPLICABLE_FUNDED_DEBT = "aplcApplicableFundedDebt";
    static final String ADJUSTED_BOOK_VALUE_OF_EQUITY = "aplcAdjustedBookValueOfEquity";
    static final String DEBT_TO_EQUITY_RATIO = "aplcDebtToEquityRatio";

    // constants
    static final String IMPLIED_DEBT_TO_EQUITY = "aplcImpliedDebtToEquity";
    static final String LEVERAGE_THRESHOLD = "aplcDebtToEquityLeverageThreshold";
    static final String ESCALATION_THRESHOLD = "aplcDebtToEquityEscalationThreshold";

    private static final BigDecimal IMPLIED = new BigDecimal("4");
    private static final BigDecimal LEVERAGE = new BigDecimal("5");
    private static final BigDecimal ESCALATION = new BigDecimal("6.5");

    @Override
    public boolean supports(Question question) {
        return question != null && question.fields().stream()
                .filter(field -> field != null)
                .map(DataField::key)
                .anyMatch(DEBT_TO_EQUITY_RATIO::equals);
    }

    @Override
    public Map<String, String> compute(Function<String, BigDecimal> inputs) {
        BigDecimal applicableFundedDebt = FedAmounts.less(
                inputs.apply(TOTAL_BS_DEBT),
                inputs.apply(NON_RECOURSE_DEBT),
                inputs.apply(HIGHLY_SECURED_PORTION_GROSS_DEBT),
                inputs.apply(QUALIFYING_FUNDED_SUBORDINATED_DEBT));

        BigDecimal adjustedBookValueOfEquity = FedAmounts.less(
                inputs.apply(BOOK_VALUE_OF_EQUITY),
                inputs.apply(EQUITY_HELD_SPVS));

        BigDecimal debtToEquity = FedAmounts.divide(applicableFundedDebt, adjustedBookValueOfEquity);

        Map<String, String> computed = new LinkedHashMap<>();
        FedAmounts.put(computed, APPLICABLE_FUNDED_DEBT, applicableFundedDebt);
        FedAmounts.put(computed, ADJUSTED_BOOK_VALUE_OF_EQUITY, adjustedBookValueOfEquity);
        FedAmounts.put(computed, DEBT_TO_EQUITY_RATIO, debtToEquity);
        FedAmounts.put(computed, IMPLIED_DEBT_TO_EQUITY, IMPLIED);
        FedAmounts.put(computed, LEVERAGE_THRESHOLD, LEVERAGE);
        FedAmounts.put(computed, ESCALATION_THRESHOLD, ESCALATION);
        return computed;
    }
}
