package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service.FinancialTableCalculator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.Question;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.input.DataField;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

/**
 * General Obligor / Utilities, Table 2 ({@code Q-F-GO2}): Adjusted Net Funded Total Debt over
 * Adjusted EBITDA, which classifies a leveraged obligor as high or low risk.
 *
 * <pre>
 *   Adjusted EBITDA                = Accounting EBITDA + Adjustment 1..8        (absent without the base)
 *   Adj. Gross Funded Total Debt   = Total B/S debt - Non recourse - Highly Secured - Qualifying Funded Sub.
 *   Adj. Net Funded Total Debt     = Adj. Gross Funded Total Debt - Cash        (MAY BE NEGATIVE)
 *   Ratio                          = Adj. Net Funded Total Debt / Adjusted EBITDA
 *   Sector threshold               = 4, whatever the industry
 * </pre>
 *
 * <p><b>Reads nothing outside its own boxes.</b> Until Clara's correction of 2026-10-12 the ratio's
 * numerator was Table 1's Total Committed Debt Per Definition, which would have needed a resolution
 * order and a cross-table input. It is now this table's own net funded debt, so this is an ordinary
 * one-argument calculator, exactly like APLC and REIT.
 *
 * <p><b>A negative net funded debt is a real figure</b> — cash above debt — so the ratio is published
 * whatever its sign. The classification reads the signs itself: negative EBITDA with negative net
 * debt is low risk, negative EBITDA alone is high risk, and only then is the ratio compared with 4x.
 */
@DomainDrivenDesign.DomainService
public final class GoNetFundedDebtCalculator implements FinancialTableCalculator {

    // inputs
    static final String ACCOUNTING_EBITDA = "goRaAccountingEbitda";
    static final List<String> ADJUSTMENTS =
            IntStream.rangeClosed(1, 8).mapToObj(i -> "goRaAdjustment" + i).toList();
    static final String TOTAL_BS_DEBT = "goRaTotalBSDebt";
    static final String NON_RECOURSE_DEBT = "goRaNonRecourseDebt";
    static final String HIGHLY_SECURED = "goRaHighlySecuredGrossFundedDebt";
    static final String QUALIFYING_FUNDED_SUBORDINATED_DEBT = "goRaQualifyingFundedSubordinatedDebt";
    static final String CASH = "goRaCash";

    // computed
    static final String ADJUSTED_EBITDA = "goRaAdjustedEbitda";
    static final String ADJUSTED_GROSS_FUNDED_TOTAL_DEBT = "goRaAdjustedGrossFundedTotalDebt";
    static final String ADJUSTED_NET_FUNDED_TOTAL_DEBT = "goRaAdjustedFundedTotalDebt";
    static final String FED_LEVERAGE_RATIO = "goRaFedLeverageRatio";

    // constant
    static final String SECTOR_THRESHOLD = "goRaThresholdPerPolicyBasedOnSector";
    private static final BigDecimal THRESHOLD = new BigDecimal("4");

    @Override
    public boolean supports(Question question) {
        return question != null && question.fields().stream()
                .filter(field -> field != null)
                .map(DataField::key)
                .anyMatch(FED_LEVERAGE_RATIO::equals);
    }

    @Override
    public Map<String, String> compute(Function<String, BigDecimal> inputs) {
        BigDecimal adjustedEbitda = FedAmounts.plus(inputs.apply(ACCOUNTING_EBITDA),
                ADJUSTMENTS.stream().map(inputs).toArray(BigDecimal[]::new));

        BigDecimal grossFunded = FedAmounts.less(inputs.apply(TOTAL_BS_DEBT),
                inputs.apply(NON_RECOURSE_DEBT),
                inputs.apply(HIGHLY_SECURED),
                inputs.apply(QUALIFYING_FUNDED_SUBORDINATED_DEBT));

        BigDecimal netFunded = FedAmounts.less(grossFunded, inputs.apply(CASH));
        BigDecimal ratio = FedAmounts.divide(netFunded, adjustedEbitda);

        Map<String, String> computed = new LinkedHashMap<>();
        FedAmounts.put(computed, ADJUSTED_EBITDA, adjustedEbitda);
        FedAmounts.put(computed, ADJUSTED_GROSS_FUNDED_TOTAL_DEBT, grossFunded);
        FedAmounts.put(computed, ADJUSTED_NET_FUNDED_TOTAL_DEBT, netFunded);
        FedAmounts.put(computed, FED_LEVERAGE_RATIO, ratio);
        FedAmounts.put(computed, SECTOR_THRESHOLD, THRESHOLD);
        return computed;
    }
}
