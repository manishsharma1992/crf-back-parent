package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Function;

/**
 * One form's financial arithmetic.
 *
 * <p><b>This interface is the ONLY thing ECB and FED share, and it carries almost no behaviour.</b>
 * No abstract base class, no shared constants, no common input record. Every implementation owns
 * its field keys, its formulas, its constants and its own idea of what cannot be computed — those
 * are the things that differ per regulator, and a shared parent is how a change made for FED
 * quietly alters an ECB figure that was signed off last quarter.
 *
 * <p>What is NOT duplicated is the plumbing: finding the table's question, reading the prefilled
 * sources, writing the overlay onto the answer map, and doing it identically on the read and the
 * save path. Two copies of that would mean two places deciding how an absent figure is published,
 * and they would drift — the same failure the entity eligibility check ran into when the read and
 * save paths each had their own copy.
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li><b>Pure and total.</b> No I/O, no Spring, no clock, no state. Never throws on any input,
 *       including all-absent.</li>
 *   <li><b>Absent means absent.</b> A figure that cannot be computed is simply not in the map —
 *       never a blank, never a zero. That is what lets a mandatory calculated box stay unanswered
 *       and stop the walk of its own accord, and what makes a {@code range [...]} over it match
 *       nothing rather than match by accident.</li>
 *   <li><b>Strings out.</b> The answer map is {@code Map<String, String>} end to end, so
 *       formatting here saves the resolver a round trip. Plain notation, never scientific — this
 *       is what lands in the JSONB column.</li>
 *   <li><b>Bare field keys</b>, not dotted. The resolver prefixes the owning question.</li>
 * </ul>
 */
public interface FinancialTableCalculator {

    /** Whether this calculator owns the given question's table, recognised by a key only it declares. */
    boolean supports(Question question);

    /**
     * Every figure this table computes.
     *
     * <p><b>Computed, not published.</b> The result is what {@code ValidationDomainService} judges,
     * so it must be complete even when {@link #withheld} keeps some of it off the screen — that is
     * precisely how {@code ECB_ADJUSTED_EBITDA_ZERO} can fire on a box the analyst never sees.
     *
     * @param inputs bare field key to value; null for a box nobody filled, for one the prefill
     *               source does not hold, and for anything that would not parse
     */
    Map<String, String> compute(Function<String, BigDecimal> inputs);

    /**
     * Keys from {@link #compute} that must NOT be published to the answer map.
     *
     * <p>Two ways a form can stop the analyst at the financial table, and they are not the same:
     * <ul>
     *   <li><b>By absence</b> — the figure was never computable, so it is not in the map at all.
     *       FED works this way throughout, which is why the default here is empty.</li>
     *   <li><b>By withholding</b> — the figure WAS computable but the form refuses to stand behind
     *       it. ECB does this when EBITDA or Gross Debt is absent or zero, or when the adjustments
     *       cancel EBITDA out: the ratio exists arithmetically, but publishing it would let the
     *       walk continue past a table the BA says is unusable.</li>
     * </ul>
     * Either way the mandatory calculated box ends up unanswered and the walk stops there of its
     * own accord. Traversal is never gated, which is what keeps questions the answers never
     * reached off the screen.
     */
    default Set<String> withheld(Function<String, BigDecimal> inputs, Map<String, String> computed) {
        return Set.of();
    }
}
