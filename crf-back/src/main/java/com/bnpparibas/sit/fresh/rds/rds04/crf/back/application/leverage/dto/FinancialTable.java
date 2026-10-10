package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.ComputedFinancials;

import java.util.*;

/**
 * The financial table's resolved state for one request: what was computed, and the answer entries
 * those figures occupy.
 *
 * <p><b>{@code computed} is now a field-keyed map rather than {@code ComputedFinancials}.</b> That
 * record describes the five ECB results and dispatches on five ECB field keys, so a FED analysis
 * reaching {@code ValidationDomainService} through it read EVERY calculated box as empty —
 * MUST_NOT_BE_ZERO could never fire and MANDATORY fired on every computed box, leaving the APLC
 * table permanently unsaveable with errors pointing at boxes nobody can type into. A map keyed by
 * field key is what all three calculators already produce, and it takes a form-specific type out
 * of a service both forms share. ECB's figures are unchanged; only their container is.
 *
 * @param computed every figure the calculator worked out, keyed by bare field key, for
 *                 {@code ValidationDomainService} to judge. COMPLETE even when some of it is
 *                 withheld from {@code overlay} — that is how {@code ECB_ADJUSTED_EBITDA_ZERO}
 *                 fires on a box the analyst never sees.
 * @param overlay  dotted answer keys to values — {@code Q-F01.ebitda},
 *                 {@code Q-F01.adjustedEbitda} and so on — merged OVER the posted answers so that
 *                 traversal, the snapshot and the screen all read the same figures.
 * @param owned    dotted answer keys of every box the analyst may NOT type into — calculated boxes
 *                 and locked prefills. {@link #applyTo} drops whatever the client posted for them
 *                 BEFORE writing the overlay, so a figure that is no longer computable ends up
 *                 absent rather than carried over from the last response.
 *
 * <p><b>Why {@code owned} exists.</b> This Javadoc used to promise that a posted value for a
 * calculated key is discarded, but {@code applyTo} only ever wrote the overlay OVER the answers —
 * so a key missing from the overlay kept the client's copy. The screen posts every box back,
 * disabled ones included, so a ratio that stopped being computable survived from the previous
 * response: it answered the mandatory box, traversal read it, and the snapshot froze it with
 * CALCULATED provenance (Clara, REIT feedback #3 and #4). An overlay can only add; removal has to
 * be said explicitly, and {@code owned} is where it is said.
 */
public record FinancialTable(Map<String, String> computed, Map<String, String> overlay, Set<String> owned) {

    /** No financial table on this form, or the walk has not reached it. */
    public static final FinancialTable NONE = new FinancialTable(Map.of(), Map.of(), Set.of());

    public FinancialTable {
        computed = computed == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(computed));
        overlay = overlay == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(overlay));
        owned = owned == null ? Set.of() : Collections.unmodifiableSet(new LinkedHashSet<>(owned));
    }

    /**
     * The posted answers, minus the client's copy of every server-owned box, with the resolved
     * figures written over them.
     *
     * <p>The early return is on BOTH being empty, not only the overlay: a table whose every figure
     * became uncomputable has an empty overlay and is exactly the case that most needs clearing.
     *
     * <p>Insertion order is preserved so the frozen snapshot reads in screen order. That is why
     * only the owned keys the overlay does NOT replace are removed: a key that is removed and put
     * back moves to the end of a LinkedHashMap, while one that is simply overwritten keeps its place.
     */
    public Map<String, String> applyTo(Map<String, String> answers) {
        if (overlay.isEmpty() && owned.isEmpty()) {
            return answers;
        }
        Map<String, String> merged = new LinkedHashMap<>(answers);
        for (String key : owned) {
            if (!overlay.containsKey(key)) {
                merged.remove(key);   // no longer computable, or withheld: absent, not stale
            }
        }
        merged.putAll(overlay);
        return Collections.unmodifiableMap(merged);
    }
}
