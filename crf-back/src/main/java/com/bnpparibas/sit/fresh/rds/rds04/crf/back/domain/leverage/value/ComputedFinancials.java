package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * The five calculated boxes of the financial table.
 *
 * <p>The three totals are nullable — null when a source they depend on was absent. The two ratios
 * are {@link Ratio}, which carries its own absence and the reason for it.
 *
 * <p><b>Derived state, never an input.</b> These values are recomputed from the typed boxes on
 * every traversal and every save. Nothing ever reads a previously stored calculated value back in
 * as an operand; that is what keeps a re-open of an old analysis honest, and what makes the
 * calculation trivially idempotent.
 */
@DomainDrivenDesign.ValueObject
public record ComputedFinancials(BigDecimal adjustedEbitda,
                                 BigDecimal totalEcbDebt,
                                 BigDecimal totalNetFundedDebt,
                                 Ratio ecbLeverageRatio,
                                 Ratio netFundedLeverageRatio) {

    /**
     * The value for a {@code CALC/x} field key, or empty when there is none.
     *
     * <p>Empty means the box is NOT frozen as a sub-answer — absent, never blank, never zero.
     *
     * <p>An unrecognised key returns empty rather than raising, mirroring the AREA-map dispatch in
     * {@code DerivedValueResolverImpl}: a workbook naming a calculation this release does not
     * implement degrades to a missing box, which the validator reports, instead of failing the
     * whole import or the whole save.
     */
    public Optional<BigDecimal> valueOf(String fieldKey) {
        return Optional.ofNullable(switch (fieldKey) {
            case FinancialInputs.ADJUSTED_EBITDA -> adjustedEbitda;
            case FinancialInputs.TOTAL_ECB_DEBT -> totalEcbDebt;
            case FinancialInputs.TOTAL_NET_FUNDED_DEBT -> totalNetFundedDebt;
            case FinancialInputs.ECB_LEVERAGE_RATIO -> ecbLeverageRatio.valueOrNull();
            case FinancialInputs.NET_FUNDED_LEVERAGE_RATIO -> netFundedLeverageRatio.valueOrNull();
            default -> null;
        });
    }

    /**
     * True when Adjusted EBITDA exists and is exactly zero — the fact
     * {@code ECB_ADJUSTED_EBITDA_ZERO} fires on.
     *
     * <p>Exposed as a fact rather than as a message so {@code ValidationDomainService} stays pure:
     * it receives this alongside the inputs and decides, the same way it receives entity
     * eligibility rather than reaching for a repository.
     */
    public boolean adjustedEbitdaIsZero() {
        return Amounts.isZero(adjustedEbitda);
    }
}
