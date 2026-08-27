package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisStatus;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.CompletenessBlocker;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.CompletenessInput;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.FormCompleteness;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.FormCompletenessInput;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.TraversalState;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

/**
 * BR01 - decides whether an analysis is complete enough to be validated.
 *
 * <p><b>A pure function.</b> Every fact it needs has already been computed by the
 * read or save pipeline; this service only applies the rule to them. That is what
 * lets the same rule be evaluated on the read path, to enable the button, and
 * again on the write path, to guard the transition - one rule, two callers, no
 * possibility of an enabled button that throws on click.
 *
 * <p><b>Two conditions, not four.</b> The walk reaching TERMINAL already means
 * every mandatory answer on the path is present, and the BA has confirmed that
 * missing justifications surface as ERROR violations. So completeness is:
 * every applicable form TERMINAL, and no ERRORs standing.
 */
@DomainDrivenDesign.DomainService
public final class AnalysisCompletenessDomainService {

    /**
     * Forms are examined in display order and the first blocker wins. An
     * incomplete PRELIMINARY usually explains why the downstream forms look
     * wrong - it is what decides whether they are even asked for - so reporting
     * all three at once would bury the one thing worth fixing.
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
     * State is checked before errors: a walk that has not finished has not raised
     * all its violations yet, so leading with an error count on a half-answered
     * form would report a number that changes as the analyst types.
     */
    private FormCompleteness evaluateForm(FormCompletenessInput form) {
        if (form.traversalState() == TraversalState.STRANDED) {
            return FormCompleteness.blockedBy(CompletenessBlocker.DEFINITION_STRANDED, form.formType());
        }
        if (form.traversalState() != TraversalState.TERMINAL) {
            return FormCompleteness.blockedBy(CompletenessBlocker.FORM_INCOMPLETE, form.formType());
        }
        if (!form.blockingMessageCodes().isEmpty()) {
            return FormCompleteness.blockingErrors(form.formType(), form.blockingMessageCodes());
        }
        return FormCompleteness.complete();
    }
}
