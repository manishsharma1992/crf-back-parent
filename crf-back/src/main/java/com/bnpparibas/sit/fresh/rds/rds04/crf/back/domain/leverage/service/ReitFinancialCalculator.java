package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The Real Estate Investment Trusts financial table.
 *
 * <pre>
 *   Total Committed Debt              = the five facility boxes, summed
 *   Total Committed Debt Per Def.     = Total Committed Debt
 *                                     - Non recourse debt
 *                                     - Qualifying Subordinated Debt
 *   % Highly Secured Portion          = Highly Secured Portion / Total Committed Debt Per Def.
 *   Carve Out Highly Secured Debt     = NO when % &lt; 60%, otherwise YES
 *   Adjusted Total Committed Debt     = Total Committed Debt Per Def.            (carve-out NO)
 *                                     - Highly Secured Portion                   (carve-out YES)
 *   Net Operating Income / Total Debt = Net Operating Income / Adjusted Total Committed Debt
 *   Debt / Market Value Assets        = Adjusted / (Adjusted + Market Capitalization)
 * </pre>
 *
 * <p><b>The carve-out is the only branching calculation in either form.</b> It lives here rather
 * than in the workbook because the Formula column is documentation and nothing evaluates it — the
 * sheet was deliberately never made into an expression language.
 *
 * <p><b>The boundary is inclusive on the upper side.</b> "NO when below 60%, otherwise YES" means
 * a portion of exactly 60% carves out. Worth stating because the two readings differ on precisely
 * the value a BA is most likely to use as a test case.
 *
 * <p><b>Debt / Market Value Assets is the one deliberate exception to the blocking rule.</b> Every
 * other zero denominator in FED raises a blocking error; Clara confirmed this one returns nothing
 * silently. Note what that costs: it is read by the REIT leverage test on Q-RT20, so an absent
 * value makes that condition false, the test answers NO, and the classification comes out
 * FED_NOT_LEVERAGED. That is the agreed behaviour, not an oversight — but it is why
 * {@code reitAdjustedTotalCommittedDebt} wants a MUST_NOT_BE_ZERO row of its own (see the note
 * accompanying this change), since a full carve-out drives it to zero with every input box
 * legitimately filled.
 *
 * <p>The three thresholds are published here for the same reason as APLC's: they are mandatory and
 * non-editable on the Fields tab, so nothing else would ever fill them. All are stored as
 * fractions and rendered as percentages, which keeps both sides of every threshold comparison on
 * one scale.
 */
@DomainDrivenDesign.DomainService
public final class ReitFinancialCalculator implements FinancialTableCalculator {

    // inputs
    static final String COMMITTED_LOAN_FACILITY = "reitCommittedLoanFacility";
    static final String COMMITTED_LC_FACILITY = "reitCommittedLCFacility";
    static final String FUNDED_DEBT_UNCOMMITTED_LOAN = "reitFundedDebtOnUncommittedLoanFacilities";
    static final String OUTSTANDING_UNCOMMITTED_LC = "reitOutstandingAmountsOnUncommittedLCFacilities";
    static final String OTHER_FINANCIAL_COMMITMENTS = "reitOtherFinancialCommitments";
    static final String NON_RECOURSE_DEBT = "reitNonRecourseDebt";
    static final String QUALIFYING_SUBORDINATED_DEBT = "reitQualifyingSubordinatedDebt";
    static final String HIGHLY_SECURED_PORTION = "reitHighlySecuredPortionCommittedTotalDebt";
    static final String NET_OPERATING_INCOME = "reitNetOperatingIncome";
    static final String MARKET_CAPITALIZATION = "reitMarketCapitalization";

    // computed
    static final String TOTAL_COMMITTED_DEBT = "reitTotalCommittedDebt";
    static final String TOTAL_COMMITTED_DEBT_PER_DEFINITION = "reitTotalCommittedDebtPerDefinition";
    static final String PCT_HIGHLY_SECURED_PORTION = "reitPctHighlySecuredPortionCommittedTotalDebt";
    static final String CARVE_OUT = "reitCarveOutHighlySecuredDebt";
    static final String ADJUSTED_TOTAL_COMMITTED_DEBT = "reitAdjustedTotalCommittedDebt";
    static final String COMMITTED_DEBT_YIELD = "reitCommittedDebtYield";
    static final String DEBT_TO_MARKET_CAP = "reitDebtToMarketCap";

    // constants
    static final String EXCLUSION_THRESHOLD = "reitThresholdForExclusionOfHighlySecuredDebt";
    static final String COMMITTED_DEBT_YIELD_THRESHOLD = "reitCommittedDebtYieldThreshold";
    static final String DEBT_TO_MARKET_VALUE_THRESHOLD = "reitDebtToMarketValueAssetsLeverageThreshold";

    static final String CARVE_OUT_YES = "YES";
    static final String CARVE_OUT_NO = "NO";

    private static final BigDecimal EXCLUSION = new BigDecimal("0.60");
    private static final BigDecimal YIELD_THRESHOLD = new BigDecimal("0.08");
    private static final BigDecimal MARKET_VALUE_THRESHOLD = new BigDecimal("0.85");

    @Override
    public boolean supports(Question question) {
        return question != null && question.fields().stream()
                .filter(field -> field != null)
                .map(DataField::key)
                .anyMatch(DEBT_TO_MARKET_CAP::equals);
    }

    @Override
    public Map<String, String> compute(Function<String, BigDecimal> inputs) {
        BigDecimal totalCommittedDebt = FedAmounts.sum(
                inputs.apply(COMMITTED_LOAN_FACILITY),
                inputs.apply(COMMITTED_LC_FACILITY),
                inputs.apply(FUNDED_DEBT_UNCOMMITTED_LOAN),
                inputs.apply(OUTSTANDING_UNCOMMITTED_LC),
                inputs.apply(OTHER_FINANCIAL_COMMITMENTS));

        BigDecimal perDefinition = FedAmounts.less(totalCommittedDebt,
                inputs.apply(NON_RECOURSE_DEBT),
                inputs.apply(QUALIFYING_SUBORDINATED_DEBT));

        BigDecimal highlySecured = inputs.apply(HIGHLY_SECURED_PORTION);
        BigDecimal pct = FedAmounts.divide(highlySecured, perDefinition);
        String carveOut = carveOut(pct);
        BigDecimal adjusted = adjusted(carveOut, perDefinition, highlySecured);

        BigDecimal yield = FedAmounts.divide(inputs.apply(NET_OPERATING_INCOME), adjusted);
        BigDecimal debtToMarketCap = FedAmounts.divide(adjusted,
                FedAmounts.sum(adjusted, FedAmounts.orZero(inputs.apply(MARKET_CAPITALIZATION))));

        Map<String, String> computed = new LinkedHashMap<>();
        FedAmounts.put(computed, TOTAL_COMMITTED_DEBT, totalCommittedDebt);
        FedAmounts.put(computed, TOTAL_COMMITTED_DEBT_PER_DEFINITION, perDefinition);
        FedAmounts.put(computed, PCT_HIGHLY_SECURED_PORTION, pct);
        if (carveOut != null) {
            computed.put(CARVE_OUT, carveOut);
        }
        FedAmounts.put(computed, ADJUSTED_TOTAL_COMMITTED_DEBT, adjusted);
        FedAmounts.put(computed, COMMITTED_DEBT_YIELD, yield);
        FedAmounts.put(computed, DEBT_TO_MARKET_CAP, debtToMarketCap);
        FedAmounts.put(computed, EXCLUSION_THRESHOLD, EXCLUSION);
        FedAmounts.put(computed, COMMITTED_DEBT_YIELD_THRESHOLD, YIELD_THRESHOLD);
        FedAmounts.put(computed, DEBT_TO_MARKET_VALUE_THRESHOLD, MARKET_VALUE_THRESHOLD);
        return computed;
    }

    /**
     * Absent percentage means absent carve-out, not NO. Defaulting to NO would publish an Adjusted
     * Total Committed Debt worked out from a percentage we could not calculate, and the analyst
     * would have no way to tell it apart from a real one.
     */
    private String carveOut(BigDecimal pct) {
        if (pct == null) {
            return null;
        }
        return pct.compareTo(EXCLUSION) < 0 ? CARVE_OUT_NO : CARVE_OUT_YES;
    }

    private BigDecimal adjusted(String carveOut, BigDecimal perDefinition, BigDecimal highlySecured) {
        if (carveOut == null || perDefinition == null) {
            return null;
        }
        return CARVE_OUT_NO.equals(carveOut)
                ? perDefinition
                : perDefinition.subtract(FedAmounts.orZero(highlySecured));
    }
}
