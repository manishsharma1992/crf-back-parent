package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.math.BigDecimal;
import java.util.function.Function;

/**
 * Everything the five calculations read: three prefilled sources and ten analyst adjustments.
 *
 * <p><b>Every component is nullable and null means ABSENT.</b> A zero is a number the analyst or
 * FINSTAR supplied; a null is the absence of one. {@link Amounts} is where that distinction turns
 * into behaviour.
 *
 * <p>The groups mirror the Fields tab's Group column, so the arithmetic in
 * {@link FinancialCalculationDomainService} reads like the business rule it implements rather
 * than like a loop over a map. That legibility is the point — this is a regulated calculation and
 * a reviewer should be able to line the code up against the ECB definition without a debugger.
 *
 * <p><b>The field keys live here</b>, next to the arithmetic that consumes them, because they are
 * a published schema and the two must move together. {@code Formula} on the Fields tab is
 * documentation only; nothing evaluates it, so the sheet never becomes an expression language.
 */
@DomainDrivenDesign.ValueObject
public record FinancialInputs(Sources sources,
                              EbitdaAdjustments ebitdaAdjustments,
                              DebtAdjustments debtAdjustments) {

    // ---- source keys (Derived From = FINANCIALS/x)
    public static final String EBITDA = "ebitda";
    public static final String GROSS_DEBT = "grossDebt";
    public static final String NET_DEBT = "netDebt";

    // ---- EBITDA adjustment keys (editable)
    public static final String REPORTED_LTM_ADJUSTMENT = "reportedLtmAdjustment";
    public static final String PRO_FORMA_PERIMETER_ADJUSTMENT = "proFormaPerimeterAdjustment";
    public static final String IFRS16_ADJUSTMENT_EBITDA = "ifrs16AdjustmentEbitda";
    public static final String FORWARD_LOOKING_ADJUSTMENT = "forwardLookingAdjustment";
    public static final String OTHER_JUSTIFIED_ADJUSTMENT = "otherJustifiedAdjustment";

    // ---- debt adjustment keys (editable)
    public static final String COMMITTED_UNDRAWN_DEBT = "committedUndrawnDebt";
    public static final String NEW_DRAWN_DEBT = "newDrawnDebt";
    public static final String NEW_COMMITTED_UNDRAWN_DEBT = "newCommittedUndrawnDebt";
    public static final String IFRS16_ADJUSTMENT_DEBT = "ifrs16AdjustmentDebt";
    public static final String OTHER_ADJUSTMENT_DEBT = "otherAdjustmentDebt";

    // ---- calculated keys (Derived From = CALC/x)
    public static final String ADJUSTED_EBITDA = "adjustedEbitda";
    public static final String TOTAL_ECB_DEBT = "totalEcbDebt";
    public static final String ECB_LEVERAGE_RATIO = "ecbLeverageRatio";
    public static final String TOTAL_NET_FUNDED_DEBT = "totalNetFundedDebt";
    public static final String NET_FUNDED_LEVERAGE_RATIO = "netFundedLeverageRatio";

    /**
     * Read from FINSTAR and never editable. When one of these is missing the analyst cannot fix it
     * here, which is why the message points at FINSTAR rather than at the box.
     *
     * @param netDebt not rendered on screen — {@code Visible = No} — but part of the record,
     *                because Total Net Funded Debt cannot be audited without it
     */
    public record Sources(BigDecimal ebitda, BigDecimal grossDebt, BigDecimal netDebt) {
    }

    /** The five boxes that adjust EBITDA. Each requires a wording and a comment when filled. */
    public record EbitdaAdjustments(BigDecimal reportedLtm,
                                    BigDecimal proFormaPerimeter,
                                    BigDecimal ifrs16,
                                    BigDecimal forwardLooking,
                                    BigDecimal otherJustified) {

        public static final EbitdaAdjustments NONE = new EbitdaAdjustments(null, null, null, null, null);
    }

    /**
     * The five boxes that adjust Gross Debt.
     *
     * <p>{@code newDrawn} is the one box read by two calculations: it feeds Total ECB Debt AND
     * Total Net Funded Debt. It is not duplicated on the Fields tab — Net Funded's row order does
     * not repeat it — so the coupling lives here and nowhere else.
     */
    public record DebtAdjustments(BigDecimal committedUndrawn,
                                  BigDecimal newDrawn,
                                  BigDecimal newCommittedUndrawn,
                                  BigDecimal ifrs16,
                                  BigDecimal other) {

        public static final DebtAdjustments NONE = new DebtAdjustments(null, null, null, null, null);
    }

    /**
     * Builds the inputs from whatever holds the posted and prefilled boxes.
     *
     * <p>The caller supplies a lookup rather than a map so the application layer stays free to
     * resolve a box however it likes — from the posted answer, from the FINANCIALS channel, or
     * from a default — without this record knowing about any of it. A key the lookup does not
     * know must return null, meaning absent.
     */
    public static FinancialInputs from(Function<String, BigDecimal> byFieldKey) {
        return new FinancialInputs(
                new Sources(
                        byFieldKey.apply(EBITDA),
                        byFieldKey.apply(GROSS_DEBT),
                        byFieldKey.apply(NET_DEBT)),
                new EbitdaAdjustments(
                        byFieldKey.apply(REPORTED_LTM_ADJUSTMENT),
                        byFieldKey.apply(PRO_FORMA_PERIMETER_ADJUSTMENT),
                        byFieldKey.apply(IFRS16_ADJUSTMENT_EBITDA),
                        byFieldKey.apply(FORWARD_LOOKING_ADJUSTMENT),
                        byFieldKey.apply(OTHER_JUSTIFIED_ADJUSTMENT)),
                new DebtAdjustments(
                        byFieldKey.apply(COMMITTED_UNDRAWN_DEBT),
                        byFieldKey.apply(NEW_DRAWN_DEBT),
                        byFieldKey.apply(NEW_COMMITTED_UNDRAWN_DEBT),
                        byFieldKey.apply(IFRS16_ADJUSTMENT_DEBT),
                        byFieldKey.apply(OTHER_ADJUSTMENT_DEBT)));
    }
}
