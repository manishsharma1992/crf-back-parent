package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import com.bnpparibas.crf.shared.domain.leverage.model.CompletenessBlocker;
import com.bnpparibas.crf.shared.domain.leverage.model.CompletenessInput;
import com.bnpparibas.crf.shared.domain.leverage.model.FormCompleteness;
import com.bnpparibas.crf.shared.domain.leverage.model.AnalysisStatus;
import com.bnpparibas.crf.shared.domain.leverage.model.RequiredField;
import com.bnpparibas.crf.shared.domain.leverage.model.TraversalState;
import java.util.List;

/**
 * BR01 - decides whether a leverage form is complete enough to be validated.
 *
 * <p>Stateless and free of Spring stereotypes; wired as a bean by configuration in
 * crf-back, consistent with the rest of the domain services.
 *
 * <p>Called from two places, and that is the point: the read path
 * (GetLeverageFormStateUseCase) surfaces the result as {@code canValidate} on the
 * form-state payload, and the write path (ValidateLeverageAnalysisUseCase)
 * re-evaluates it before the transition. One rule, evaluated twice, so a stale or
 * hand-crafted client request cannot slip past.
 */
public class FormCompletenessDomainService {

    /**
     * Evaluates completeness. Blockers are checked in precedence order and the
     * first one wins, so the caller always receives the most actionable reason
     * rather than a pile of downstream noise.
     */
    public FormCompleteness evaluate(AnalysisStatus status, CompletenessInput input) {
        if (status != AnalysisStatus.DRAFT) {
            return FormCompleteness.blockedBy(CompletenessBlocker.NOT_IN_DRAFT);
        }
        if (input.traversalStatus() != TraversalState.TERMINAL) {
            return FormCompleteness.blockedBy(CompletenessBlocker.TRAVERSAL_NOT_TERMINAL);
        }
        List<String> missing = unansweredKeys(input.requiredFields());
        if (!missing.isEmpty()) {
            return FormCompleteness.missingFields(missing);
        }
        if (input.blockingValidationMessages() > 0) {
            return FormCompleteness.blockedBy(CompletenessBlocker.BLOCKING_VALIDATION_MESSAGES);
        }
        return FormCompleteness.complete();
    }

    private List<String> unansweredKeys(List<RequiredField> requiredFields) {
        return requiredFields.stream()
                .filter(field -> !field.isAnswered())
                .map(RequiredField::key)
                .toList();
    }
}
