package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import com.bnpparibas.crf.shared.domain.leverage.model.AnalysisStatus;
import com.bnpparibas.crf.shared.domain.leverage.model.CompletenessBlocker;
import com.bnpparibas.crf.shared.domain.leverage.model.CompletenessInput;
import com.bnpparibas.crf.shared.domain.leverage.model.FormCompleteness;
import com.bnpparibas.crf.shared.domain.leverage.model.FormCompletenessInput;
import com.bnpparibas.crf.shared.domain.leverage.model.RequiredField;
import com.bnpparibas.crf.shared.domain.leverage.model.TraversalState;
import java.util.List;

/**
 * BR01 - decides whether a leverage analysis is complete enough to be validated.
 *
 * <p>Stateless and free of Spring stereotypes; wired by LeverageValidationBeanConfig.
 *
 * <p>Called from two places, and that is the point: the read path surfaces the
 * result as canValidate on the form-state payload, and ValidateLeverageAnalysisUseCase
 * re-evaluates it before the transition. One rule, evaluated twice, so a stale or
 * hand-crafted client request cannot slip past.
 */
public class FormCompletenessDomainService {

    /**
     * Evaluates completeness across every applicable form.
     *
     * <p>Forms are examined in display order and the first blocker wins, rather
     * than collecting every problem in the analysis. An incomplete preliminary
     * form usually explains why the downstream forms look wrong, so reporting all
     * three at once would bury the one thing worth fixing.
     */
    public FormCompleteness evaluate(AnalysisStatus status, CompletenessInput input) {
        if (status != AnalysisStatus.DRAFT) {
            return FormCompleteness.notInDraft();
        }
        for (FormCompletenessInput form : input.forms()) {
            FormCompleteness result = evaluateForm(form);
            if (!result.canValidate()) {
                return result;
            }
        }
        return FormCompleteness.complete();
    }

    /**
     * Blockers are checked in precedence order within a form, so the caller never
     * receives "3 mandatory fields missing" for a walk that has not even reached a
     * terminal node - those fields may not turn out to be visible at all.
     */
    private FormCompleteness evaluateForm(FormCompletenessInput form) {
        if (form.traversalState() != TraversalState.TERMINAL) {
            return FormCompleteness.blockedBy(
                    CompletenessBlocker.TRAVERSAL_NOT_TERMINAL, form.formType());
        }
        List<String> missing = unansweredKeys(form.requiredFields());
        if (!missing.isEmpty()) {
            return FormCompleteness.missingFields(form.formType(), missing);
        }
        if (form.blockingValidationMessages() > 0) {
            return FormCompleteness.blockedBy(
                    CompletenessBlocker.BLOCKING_VALIDATION_MESSAGES, form.formType());
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
