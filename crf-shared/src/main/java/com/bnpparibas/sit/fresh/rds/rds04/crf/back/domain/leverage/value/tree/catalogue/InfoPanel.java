package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.catalogue;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LocalizedLabel;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.List;

/**
 * A read-only block of existing RMPM data, shown when a flag takes a given value.
 *
 * <p>The rule lives HERE and not on a branch, so every terminal that sets the flag to that value
 * shows the panel — Q-T01, Q-C01 and Q-T03 all set INR and none of them mentions it.
 *
 * <p>A panel is NOT an answer and NOT an output flag: it is what RMPM currently holds, never
 * routed on and never edited. {@code leveragedFlag} and {@code covenantStructure} arrive as
 * integers and are rendered through {@link FlagValue}.
 *
 * @param source a LOGICAL name such as {@code COUNTERPARTY_CHARACTERISTICS} — never a table or a
 *               JPA class. A stored definition outlives any persistence refactor.
 */
@DomainDrivenDesign.ValueObject
public record InfoPanel(String key,
                        LocalizedLabel title,
                        String source,
                        List<String> fields,
                        String whenFlagKey,
                        String whenFlagValue) {

    public InfoPanel {
        fields = fields == null ? List.of() : List.copyOf(fields);
    }
}
