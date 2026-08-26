package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.validation;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One form's contribution to a validated analysis snapshot.
 *
 * <p>An analysis always carries PRELIMINARY and then, depending on the
 * preliminary outcome, ECB, FED, or both. Rather than a single formType field -
 * which cannot express the both case - the snapshot holds one of these per
 * applicable form.
 *
 * @param formType     PRELIMINARY / ECB / FED
 * @param definitionId the tree definition this form was answered against; this is
 *                     what makes the snapshot replayable, since a later workbook
 *                     version may define a different flag set
 * @param flags        resolved flags for this form, in definition order
 */
public record FormSnapshot(LeverageFormType formType, Long definitionId, Map<String, String> flags) {

    public FormSnapshot {
        flags = new LinkedHashMap<>(flags);
    }

    @Override
    public Map<String, String> flags() {
        return new LinkedHashMap<>(flags);
    }
}
