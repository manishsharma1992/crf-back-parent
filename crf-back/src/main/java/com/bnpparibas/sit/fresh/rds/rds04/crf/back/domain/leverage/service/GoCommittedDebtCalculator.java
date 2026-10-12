package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.FinancialTableCalculator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.Question;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.input.DataField;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * General Obligor / Utilities, Table 1 ({@code Q-F-GO1}): Total Committed Debt Per Definition over
 * Adjusted EBITDA — the FED leverage ratio, and the only figure that fills {@code fedLeverageRatio}.
 *
 * <pre>
 *   Adjusted EBITDA              = Accounting EBITDA + Adjustment 1..8      (absent without the base)
 *   Total Committed Debt         = the five facility boxes, summed          (ZERO when all are blank)
 *   Total Committed Debt Per Def.= Total Committed Debt - Non recourse - Qualifying Subordinated
 *   FED leverage ratio           = Per Definition / Adjusted EBITDA         (absent on a zero denominator)
 *   Sector threshold             = 4 (General Obligor) | 6 (Utilities)      — from Q-MI01
 *   Escalation threshold         = 6 (General Obligor) | 7 (Utilities)      — from Q-MI01
 * </pre>
 *
 * <p><b>The only calculator that reads an answer.</b> Both thresholds depend on Main Industry, so
 * this overrides {@link #compute(Function, Function)} and declares Q-MI01 in
 * {@link #requiredAnswers()}. For any other industry — and through the one-argument
 * {@link #compute(Function)}, where the industry is unknown — the thresholds are LEFT OUT rather
 * than defaulted: the resolver computes this table on every request, and a General Obligor number
 * published on a REIT analysis would be one number wrong for one of them.
 *
 * <p><b>Thresholds are multiples</b> (4, not 0.04 and not "x4"): the ratio they are compared with is
 * a multiple too, so both sides of {@code n x field} sit on one scale. The "x" is display.
 *
 * <p><b>No debt entered means zero debt (Clara, 2026-10-12).</b> REIT refuses a blank Total
 * Committed Debt with a message; General Obligor does not: "if Total Committed Debt = 0, the ratio
 * is 0 … the decision tree can carry on." So the five blank boxes total ZERO here, Per Definition and
 * the ratio follow from it, and the walk continues — it never stops silently on a table with no
 * message to show. A ratio of 0 then asks the escalation questions, as confirmed for 0.0x.
 * Adjusted EBITDA is NOT treated this way: a missing or zero EBITDA still has no ratio, and that
 * case does carry a message.
 *
 * <p>Deliberately a copy of REIT's debt block rather than a shared helper beyond {@link FedAmounts}:
 * REIT's carve-out already shows these blocks diverge by business rule, and the interface's own
 * Javadoc gives the reason against a shared parent.
 */
@DomainDrivenDesign.DomainService
public final class GoCommittedDebtCalculator implements FinancialTableCalculator {

    static final String MAIN_INDUSTRY = "Q-MI01";
    static final String GENERAL_OBLIGOR = "GENERAL_OBLIGOR";
    static final String UTILITIES = "UTILITIES";

    // inputs
    static final String ACCOUNTING_EBITDA = "goCeAccountingEbitda";
    static final List<String> ADJUSTMENTS =
            IntStream.rangeClosed(1, 8).mapToObj(i -> "goCeAdjustment" + i).toList();
    static final String COMMITTED_LOAN_FACILITY = "goCeCommittedLoanFacility";
    static final String COMMITTED_LC_FACILITY = "goCeCommittedLCFacility";
    static final String FUNDED_DEBT_UNCOMMITTED_LOAN = "goCeFundedDebtOnUncommittedLoanFacilities";
    static final String OUTSTANDING_UNCOMMITTED_LC = "goCeOutstandingAmountsOnUncommittedLCFacilities";
    static final String OTHER_FINANCIAL_COMMITMENTS = "goCeOtherFinancialCommitments";
    static final String NON_RECOURSE_DEBT = "goCeNonRecourseDebt";
    static final String QUALIFYING_SUBORDINATED_DEBT = "goCeQualifyingSubordinatedDebt";

    // computed
    static final String ADJUSTED_EBITDA = "goCeAdjustedEbitda";
    static final String TOTAL_COMMITTED_DEBT = "goCeTotalCommittedDebt";
    static final String TOTAL_COMMITTED_DEBT_PER_DEFINITION = "goCeTotalCommittedDebtPerDefinition";
    static final String FED_LEVERAGE_RATIO = "goCeFedLeverageRatio";

    // constants, by industry
    static final String SECTOR_THRESHOLD = "goCeThresholdPerPolicyBasedOnSector";
    static final String ESCALATION_THRESHOLD = "goCeEscalationThreshold";

    private record Thresholds(BigDecimal sector, BigDecimal escalation) {
    }

    private static final Map<String, Thresholds> THRESHOLDS = Map.of(
            GENERAL_OBLIGOR, new Thresholds(new BigDecimal("4"), new BigDecimal("6")),
            UTILITIES, new Thresholds(new BigDecimal("6"), new BigDecimal("7")));

    @Override
    public boolean supports(Question question) {
        return question != null && question.fields().stream()
                .filter(field -> field != null)
                .map(DataField::key)
                .anyMatch(FED_LEVERAGE_RATIO::equals);
    }

    @Override
    public Set<String> requiredAnswers() {
        return Set.of(MAIN_INDUSTRY);
    }

    /** Industry unknown: every figure, and no thresholds. */
    @Override
    public Map<String, String> compute(Function<String, BigDecimal> inputs) {
        return compute(inputs, key -> null);
    }

    @Override
    public Map<String, String> compute(Function<String, BigDecimal> inputs, Function<String, String> answers) {
        BigDecimal adjustedEbitda = FedAmounts.plus(inputs.apply(ACCOUNTING_EBITDA),
                ADJUSTMENTS.stream().map(inputs).toArray(BigDecimal[]::new));

        // Blank boxes are zero debt for this table (Clara) — unlike REIT, where they are refused.
        BigDecimal totalCommittedDebt = FedAmounts.orZero(FedAmounts.sum(
                inputs.apply(COMMITTED_LOAN_FACILITY),
                inputs.apply(COMMITTED_LC_FACILITY),
                inputs.apply(FUNDED_DEBT_UNCOMMITTED_LOAN),
                inputs.apply(OUTSTANDING_UNCOMMITTED_LC),
                inputs.apply(OTHER_FINANCIAL_COMMITMENTS)));

        BigDecimal perDefinition = FedAmounts.less(totalCommittedDebt,
                inputs.apply(NON_RECOURSE_DEBT),
                inputs.apply(QUALIFYING_SUBORDINATED_DEBT));

        BigDecimal ratio = FedAmounts.divide(perDefinition, adjustedEbitda);

        Map<String, String> computed = new LinkedHashMap<>();
        FedAmounts.put(computed, ADJUSTED_EBITDA, adjustedEbitda);
        FedAmounts.put(computed, TOTAL_COMMITTED_DEBT, totalCommittedDebt);
        FedAmounts.put(computed, TOTAL_COMMITTED_DEBT_PER_DEFINITION, perDefinition);
        FedAmounts.put(computed, FED_LEVERAGE_RATIO, ratio);

        Thresholds thresholds = thresholdsFor(answers.apply(MAIN_INDUSTRY));
        if (thresholds != null) {
            FedAmounts.put(computed, SECTOR_THRESHOLD, thresholds.sector());
            FedAmounts.put(computed, ESCALATION_THRESHOLD, thresholds.escalation());
        }
        return computed;
    }

    /** Null for an unanswered Q-MI01 or any other industry. ({@code Map.of().get(null)} throws.) */
    private static Thresholds thresholdsFor(String industry) {
        return industry == null ? null : THRESHOLDS.get(industry.trim());
    }
}
