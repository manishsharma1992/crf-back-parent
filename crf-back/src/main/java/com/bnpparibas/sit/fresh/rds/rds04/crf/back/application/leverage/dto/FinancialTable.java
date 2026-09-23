package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.ComputedFinancials;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

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
 *                 traversal, the snapshot and the screen all read the same figures. A posted
 *                 value for a calculated key is discarded: they are read-only boxes, and a client
 *                 must not be able to move a leverage ratio by posting one.
 */
public record FinancialTable(Map<String, String> computed, Map<String, String> overlay) {

    /** No financial table on this form, or the walk has not reached it. */
    public static final FinancialTable NONE = new FinancialTable(Map.of(), Map.of());

    public FinancialTable {
        computed = computed == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(computed));
        overlay = overlay == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(overlay));
    }

    /**
     * The posted answers with the resolved figures written over them.
     *
     * <p>Insertion order is preserved so the frozen snapshot reads in screen order.
     */
    public Map<String, String> applyTo(Map<String, String> answers) {
        if (overlay.isEmpty()) {
            return answers;
        }
        Map<String, String> merged = new LinkedHashMap<>(answers);
        merged.putAll(overlay);
        return Collections.unmodifiableMap(merged);
    }
}
