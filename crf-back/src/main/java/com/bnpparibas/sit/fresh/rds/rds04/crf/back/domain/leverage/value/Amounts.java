package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import java.math.BigDecimal;

/**
 * Null-safe addition for the financial table, carrying the one rule that is easy to get wrong.
 *
 * <p><b>An absent adjustment is zero. An absent source is absent.</b> They are not the same rule
 * and collapsing them is the bug this class exists to prevent. If EBITDA never arrived from
 * FINSTAR, Adjusted EBITDA is not "the sum of the ten adjustments" — it does not exist, the form
 * blocks, and the analyst is sent to fix the source. If the analyst simply left the IFRS 16 box
 * empty, that box contributes nothing and the sum is perfectly good.
 *
 * <p>Hence the asymmetry in {@link #sumFrom}: a null base propagates, a null addend is skipped.
 *
 * <p>Nothing here rounds. Addition of {@link BigDecimal} is exact and the scale of the result is
 * the largest scale of its operands, so the full precision that arrived from FINSTAR survives to
 * the division in {@link Ratio}. The only rounding in the module is that division.
 */
public final class Amounts {

    private Amounts() {
    }

    /**
     * {@code base + addends}, skipping null addends, or null when {@code base} itself is null.
     *
     * @param base    the prefilled source value; null means the source was never delivered
     * @param addends analyst adjustments; null means the box was left empty
     */
    public static BigDecimal sumFrom(BigDecimal base, BigDecimal... addends) {
        if (base == null) {
            return null;
        }
        BigDecimal total = base;
        for (BigDecimal addend : addends) {
            if (addend != null) {
                total = total.add(addend);
            }
        }
        return total;
    }

    /** True when the value is present and exactly zero — {@code MUST_NOT_BE_ZERO}. */
    public static boolean isZero(BigDecimal value) {
        return value != null && value.signum() == 0;
    }

    /** True when the value is absent OR zero — what blocks the form on a FINANCIALS source. */
    public static boolean isAbsentOrZero(BigDecimal value) {
        return value == null || value.signum() == 0;
    }

    /**
     * True when the value is present and strictly below zero — {@code MUST_BE_POSITIVE}.
     *
     * <p>Zero passes. The three debt boxes carrying that rule default to zero on screen, so a
     * strict test would fail a form the analyst has not touched.
     */
    public static boolean isNegative(BigDecimal value) {
        return value != null && value.signum() < 0;
    }
}
