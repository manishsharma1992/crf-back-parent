package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * A leverage ratio that may not exist.
 *
 * <p><b>This is the only place in the application that divides.</b> Every other arithmetic
 * operation on the financial table is addition, which is total. Division is not: the denominator
 * of both ratios is Adjusted EBITDA, and a business rule blocks a zero one
 * ({@code ECB_ADJUSTED_EBITDA_ZERO}) — but a rule is a rule, and rules get reordered, bypassed by
 * a new caller, or switched off by a BA. So the arithmetic refuses to depend on the rule holding.
 * An {@link Undefined} ratio is returned; an {@code ArithmeticException} never reaches an analyst.
 *
 * <p><b>Undefined means ABSENT, not blank.</b> {@link #valueOrNull()} is null for an undefined
 * ratio, and a null value is NOT frozen as a sub-answer. That matters for routing: Q-Q02 tests
 * {@code field ecbLeverageRatio range [0 .. <4]}, and an unanswered box matches nothing, so an
 * undefined ratio falls through to the cross-multiplied lines below it — which are total and
 * still terminate the form. Writing a blank or a zero instead would silently route the
 * transaction as not leveraged.
 *
 * <p><b>Scale.</b> Division rounds at {@link #SCALE} decimal places, and that value is what is
 * stored and what every predicate reads. The screen formats to two decimals; the formatted value
 * is never fed back into a comparison. Rounding to two before comparing would let a true ratio of
 * 3.9994 display as 4.00 and skip the {@code [0 .. <4]} termination while the {@code > 4x} line
 * stayed false — the transaction would end ECB_LEVERAGED on a sub-4 ratio.
 */
@DomainDrivenDesign.ValueObject
public sealed interface Ratio {

    /** Decimal places kept on division. The screen shows two; the record keeps all of these. */
    int SCALE = 28;

    /** The quotient exists. */
    record Defined(BigDecimal value) implements Ratio {
        public Defined {
            Objects.requireNonNull(value, "a defined ratio must carry a value");
        }
    }

    /** The quotient does not exist, and says why. */
    record Undefined(Reason reason) implements Ratio {
        public Undefined {
            Objects.requireNonNull(reason, "an undefined ratio must record why");
        }
    }

    /**
     * Why a ratio could not be produced. Distinct reasons because they are distinct faults:
     * an absent operand means a source was never delivered, whereas a zero denominator means the
     * analyst's own adjustments cancelled the base out.
     */
    enum Reason {
        /** Total ECB Debt / Total Net Funded Debt could not be summed — its source was absent. */
        NUMERATOR_ABSENT,
        /** Adjusted EBITDA could not be summed — EBITDA was absent. */
        DENOMINATOR_ABSENT,
        /** Adjusted EBITDA summed to exactly zero. */
        DENOMINATOR_ZERO
    }

    /**
     * Divides, or explains why it could not. A null operand is absent, never zero — see
     * {@link Amounts#sumFrom}.
     */
    static Ratio of(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null) {
            return new Undefined(Reason.NUMERATOR_ABSENT);
        }
        if (denominator == null) {
            return new Undefined(Reason.DENOMINATOR_ABSENT);
        }
        if (denominator.signum() == 0) {
            return new Undefined(Reason.DENOMINATOR_ZERO);
        }
        return new Defined(numerator.divide(denominator, SCALE, RoundingMode.HALF_UP));
    }

    default boolean isDefined() {
        return this instanceof Defined;
    }

    /**
     * The quotient, or null when there is none.
     *
     * <p>Callers freezing a sub-answer must treat null as "write no box at all". Do not substitute
     * zero and do not substitute an empty string.
     */
    default BigDecimal valueOrNull() {
        return this instanceof Defined defined ? defined.value() : null;
    }
}
