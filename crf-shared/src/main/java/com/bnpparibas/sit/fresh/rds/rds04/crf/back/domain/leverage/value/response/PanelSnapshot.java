package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.response;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LocalizedLabel;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.List;
import java.util.Map;

/**
 * An info panel exactly as it was displayed, frozen with the analysis.
 *
 * <p>The panel shows what RMPM held about the counterparty at the moment of the decision —
 * "LOW RISK FED LEVERAGED since 10/02/2026, ratio 15%". Reading it live would show TODAY's values
 * when the analysis is reopened, so a 2026 file reviewed in 2028 would appear to have been decided
 * on evidence that did not exist yet. Freezing it is what makes the record reproducible.
 *
 * <p>{@code values} are already RESOLVED for display: an integer leveraged flag has been rendered
 * through its Flag Values entry before it lands here, because the code that renders it may itself
 * be reworded later.
 *
 * @param panelKey the catalogue entry this came from
 * @param values   field key to displayed text, in the order the panel listed them
 */
@DomainDrivenDesign.ValueObject
public record PanelSnapshot(String panelKey,
                            LocalizedLabel title,
                            List<String> fieldOrder,
                            Map<String, String> values) {

    public PanelSnapshot {
        fieldOrder = fieldOrder == null ? List.of() : List.copyOf(fieldOrder);
        values = values == null ? Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(values));
    }
}
