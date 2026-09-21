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
final class FedAmounts {

    /** Clara: everything stored at 28 decimal places, displayed as a percentage or an "x". */
    static final int SCALE = 28;

    private FedAmounts() {
    }

    /** Reads one input, treating absent and unparseable alike. */
    static Optional<BigDecimal> at(Function<String, BigDecimal> inputs, String key) {
        return Optional.ofNullable(inputs.apply(key));
    }

    /** Absent addend counts as zero. */
    static BigDecimal orZero(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    /** {@code base - (first + rest...)}, absent when the base is absent. */
    static BigDecimal less(BigDecimal base, BigDecimal... subtrahends) {
        if (base == null) {
            return null;
        }
        BigDecimal result = base;
        for (BigDecimal subtrahend : subtrahends) {
            result = result.subtract(orZero(subtrahend));
        }
        return result;
    }

    /** A running total of boxes the analyst may each have left blank; absent only when ALL are. */
    static BigDecimal sum(BigDecimal... addends) {
        BigDecimal total = null;
        for (BigDecimal addend : addends) {
            if (addend != null) {
                total = total == null ? addend : total.add(addend);
            }
        }
        return total;
    }

    /**
     * {@code numerator / denominator} at {@link #SCALE}, or absent when either side is absent or
     * the denominator is zero.
     *
     * <p>Absent rather than an exception, and absent rather than zero: a ratio we could not work
     * out is a different fact from a ratio that came to nothing, and the routing reads them
     * differently — an absent figure matches no condition at all.
     */
    static BigDecimal divide(BigDecimal numerator, BigDecimal denominator) {
        if (numerator == null || denominator == null || denominator.signum() == 0) {
            return null;
        }
        return numerator.divide(denominator, SCALE, RoundingMode.HALF_UP).stripTrailingZeros();
    }

    /** Publishes a figure, or leaves the key out entirely when there is nothing to publish. */
    static void put(Map<String, String> out, String key, BigDecimal value) {
        if (value != null) {
            out.put(key, value.toPlainString());
        }
    }
}
