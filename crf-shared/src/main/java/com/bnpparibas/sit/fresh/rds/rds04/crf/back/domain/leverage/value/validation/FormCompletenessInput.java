package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.validation;

import java.util.List;

/**
 * Completeness facts for ONE form of an analysis.
 *
 * <p>Introduced because an analysis routes to PRELIMINARY plus ECB, FED, or both,
 * so there is no single traversal to speak of. The earlier flat CompletenessInput
 * carried one TraversalState and silently could not represent the both case - the
 * same modelling mistake as the formType column.
 *
 * @param formType                   which form these facts describe
 * @param traversalState             outcome of walking this form's tree
 * @param requiredFields             visible mandatory fields for this form, in
 *                                   form order, justifications included
 * @param blockingValidationMessages blocking alerts raised against this form
 */
public record FormCompletenessInput(LeverageFormType formType,
                                    TraversalState traversalState,
                                    List<RequiredField> requiredFields,
                                    int blockingValidationMessages) {

    public FormCompletenessInput {
        requiredFields = List.copyOf(requiredFields);
    }
}
