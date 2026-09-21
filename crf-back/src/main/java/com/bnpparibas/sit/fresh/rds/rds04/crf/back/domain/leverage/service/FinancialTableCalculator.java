package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import java.math.BigDecimal;
import java.util.Map;
import java.util.function.Function;

/**
 * One form's financial arithmetic.
 *
 * <p><b>This interface is the ONLY thing ECB and FED share, and it deliberately carries no
 * behaviour.</b> No abstract base class, no shared constants, no common input record. Every
 * implementation owns its field keys, its formulas, its constants and its own idea of what cannot
 * be computed — because those are the things that differ per regulator, and a shared parent is how
 * a change made for FED quietly alters an ECB figure that was signed off last quarter.
 *
 * <p>What is NOT duplicated is the plumbing around it: finding the table's question, reading
 * FINSTAR, writing the overlay onto the answer map, and doing that identically on the read and the
 * save path. That belongs in one resolver. Two copies of the plumbing would mean two places
 * deciding how an absent figure is published, and they would drift — the same failure the entity
 * eligibility check ran into when the read path and the save path each had their own copy.
 *
 * <p>So: <b>arithmetic decoupled, mechanism shared.</b>
 *
 * <h2>Contract</h2>
 * <ul>
 *   <li><b>Pure and total.</b> No I/O, no Spring, no clock, no state. Never throws, on any input
 *       including all-absent — a mistyped box degrades to an absent figure, not to a stack trace
 *       on an analyst's screen.</li>
 *   <li><b>Absent means absent.</b> A figure that cannot be computed is simply not in the returned
 *       map. It is never a blank string and never a zero. That is what lets a mandatory calculated
 *       box stay unanswered and stop the walk of its own accord, and what makes a
 *       {@code range [...]} over it match nothing rather than match by accident.</li>
 *   <li><b>Strings, not numbers, on the way out.</b> The answer map is {@code Map<String, String>}
 *       end to end and the overlay is merged straight into it, so formatting here keeps the
 *       resolver from round-tripping every value. Always plain notation, never scientific — this
 *       is what lands in the JSONB column.</li>
 *   <li><b>Keys are bare field keys</b>, not dotted. The resolver prefixes the owning question.</li>
 * </ul>
 */
public interface FinancialTableCalculator {

    /** Scale for every division. Clara: 28 decimal places, rendered as a percentage or an "x". */
    int SCALE = 28;

    /**
     * Whether this calculator owns the given question's table.
     *
     * <p>Each implementation recognises its own table by a field key only it declares, rather than
     * by the question key — the key is the BA's to choose and a rename must not silently switch
     * the arithmetic off.
     */
    boolean supports(Question question);

    /**
     * @param inputs bare field key to value; empty for a box the analyst has not filled, for one
     *               FINSTAR does not hold, and for anything that would not parse
     * @return computed field key to formatted value, omitting anything not computable
     */
    Map<String, String> compute(Function<String, BigDecimal> inputs);
}

