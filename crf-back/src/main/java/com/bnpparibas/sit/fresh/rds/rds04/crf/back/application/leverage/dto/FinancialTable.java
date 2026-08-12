package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.ComputedFinancials;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The financial table's resolved state for one request: what was computed, and the answer entries
 * those figures occupy.
 *
 * @param computed the five results, for {@code ValidationDomainService} to judge
 * @param overlay  dotted answer keys to values — {@code Q-F01.ebitda},
 *                 {@code Q-F01.adjustedEbitda} and so on — merged OVER the posted answers so that
 *                 traversal, the snapshot and the screen all read the same figures. A posted
 *                 value for one of these keys is discarded: they are read-only boxes, and a
 *                 client must not be able to move the ECB leverage ratio by posting one.
 */
public record FinancialTable(ComputedFinancials computed, Map<String, String> overlay) {

    /** No financial table on this form, or the walk has not reached it. */
    public static final FinancialTable NONE = new FinancialTable(null, Map.of());

    public FinancialTable {
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
