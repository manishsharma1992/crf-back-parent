package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * The small arithmetic the FED calculators need, kept inside the FED package on purpose.
 *
 * <p>ECB has {@code Amounts} and it does much the same job. Reaching for it would put every FED
 * figure one refactor away from an ECB decision it has nothing to do with — and the instruction
 * for this module is that the two forms do not share infrastructure. Thirty lines duplicated is a
 * cheaper price than a coupling nobody can see.
 *
 * <p><b>The rules, which are the same everywhere in FED:</b>
 * <ul>
 *   <li>A missing BASE makes the result absent. {@code totalBSDebt} is not zero just because
 *       nobody typed it, and a total computed from a figure we do not have is a fiction.</li>
 *   <li>A missing ADDEND or SUBTRAHEND is zero. An adjustment left blank is an adjustment of
 *       nothing, which is what an analyst means by leaving it blank.</li>
 *   <li>A zero denominator yields absent, never an exception and never a zero. Division is the
 *       only place FED can fail to produce a figure, and the failure has to stay quiet enough for
 *       the mandatory-box rules on the Forms tab to be the thing that speaks.</li>
 * </ul>
 */
public final class FedAmounts {

    /**
     * A multiple ("5.00x") is stored at the two decimals the screen shows.
     *
     * <p>Was 28 for every division (Clara, 2026-10-16). A ratio of 0.000000000000245 showed as
     * 0.00x while the routing still read it as positive, so the figure on screen and the decision
     * taken from it disagreed. Storing what is displayed makes them the same number: the
     * thresholds, the escalation tests and the snapshot all read the rounded value.
     */
    public static final int MULTIPLE_SCALE = 2;

    /**
     * A fraction rendered as a percentage ("62.35%") keeps FOUR decimals: two for the percentage
     * the screen shows, two more because the fraction is that percentage divided by a hundred.
     */
    public static final int FRACTION_SCALE = 4;

    private FedAmounts() {
    }

    /** Reads one input, treating absent and unparseable alike. */
    public static Optional<BigDecimal> at(Function<String, BigDecimal> inputs, String key) {
        return Optional.ofNullable(inputs.apply(key));
    }

    /** Absent addend counts as zero. */
    public static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** {@code base - (first + rest...)}, absent when the base is absent. */
    public static BigDecimal less(BigDecimal base, BigDecimal... subtrahends) {
        if (base == null) {
            return null;
        }
        BigDecimal result = base;
        for (BigDecimal subtrahend : subtrahends) {
            result = result.subtract(orZero(subtrahend));
        }
        return result;
    }

    /**
     * {@code base + addends...}, absent when the base is absent. The mirror of {@link #less}.
     *
     * <p>For Adjusted EBITDA, which is the first FED figure that ADDS to a base. {@link #sum} would
     * be wrong there: it produces a value whenever ANY addend is present, so a typed adjustment
     * with no Accounting EBITDA would become an "Adjusted EBITDA" and a ratio would be worked out
     * from it — exactly the fiction the first rule above forbids.
     */
    public static BigDecimal plus(BigDecimal base, BigDecimal... addends) {
        if (base == null) {
            return null;
        }
        BigDecimal result = base;
        for (BigDecimal addend : addends) {
            result = result.add(orZero(addend));
        }
        return result;
    }

    /** A running total of boxes the analyst may each have left blank; absent only when ALL are. */
    public static BigDecimal sum(BigDecimal... addends) {
        BigDecimal total = null;
        for (BigDecimal addend : addends) {
            if (addend != null) {
                total = total == null ? addend : total.add(addend);
            }
        }
        return total;
    }

    /**
     * A leverage MULTIPLE — debt over EBITDA, debt over equity — rounded half-up to two decimals,
     * or absent when either side is absent or the denominator is zero.
     *
     * <p>Absent rather than an exception, and absent rather than zero: a ratio we could not work
     * out is a different fact from a ratio that came to nothing, and the routing reads them
     * differently — an absent figure matches no condition at all.
     */
    public static BigDecimal multiple(BigDecimal numerator, BigDecimal denominator) {
        return divide(numerator, denominator, MULTIPLE_SCALE);
    }

    /** A FRACTION shown as a percentage — REIT's carve-out, yield, debt to market value. */
    public static BigDecimal fraction(BigDecimal numerator, BigDecimal denominator) {
        return divide(numerator, denominator, FRACTION_SCALE);
    }

    /*
     * The old public divide(n, d) is gone on purpose rather than kept as an alias: every caller has
     * to say whether it produces a multiple or a percentage, and the compiler lists the ones that
     * have not yet said.
     */
    private static BigDecimal divide(BigDecimal numerator, BigDecimal denominator, int scale) {
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return null;
        }
        return numerator.divide(denominator, scale, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    /** Publishes a figure, or leaves the key out entirely when there is nothing to publish. */
    public static void put(Map<String, String> out, String key, BigDecimal value) {
        if (value != null) {
            out.put(key, value.toPlainString());
        }
    }
}
