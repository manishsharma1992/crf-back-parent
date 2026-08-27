package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.validation;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One form's contribution to a validated analysis snapshot.
 *
 * <p>Sourced entirely from the frozen {@code FormResponses} - version, locale and
 * flags are already stored there. Nothing is recomputed and no tree is re-walked,
 * which is what makes the snapshot a record of what was concluded rather than of
 * what today's definition would conclude.
 *
 * <p>Note this carries {@code definitionVersion}, not a definition id. FormResponses
 * freezes the version it walked, and the version is what replays; the surrogate id
 * of the row that happened to hold it is an implementation detail of the import.
 */
@DomainDrivenDesign.ValueObject
public record FormSnapshot(LeverageFormType formType,
                           int definitionVersion,
                           String locale,
                           Map<String, String> flags) {

    public FormSnapshot {
        flags = flags == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(flags));
    }
}
