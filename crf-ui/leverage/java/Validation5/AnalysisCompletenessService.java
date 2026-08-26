package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import java.util.ArrayList;
import java.util.List;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.FormState;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.ValidationMessageView;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service.AnalysisCompletenessDomainService;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.CompletenessInput;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.FormCompleteness;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.FormCompletenessInput;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.Severity;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.TraversalState;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gathers the per-form facts BR01 needs and hands them to the domain rule.
 *
 * <p><b>Built on top of GetLeverageFormStateUseCase, not inside it.</b> This
 * service calls the existing read for each applicable form and reads the answer
 * off the FormState it already returns. Nothing in the read or save path changes.
 *
 * <p>That direction is not just about avoiding regression - the reverse would not
 * work. If project() computed completeness, and completeness needed the other
 * forms' states, each nested read would compute completeness again and recurse
 * without end. Keeping this a layer ABOVE the form-state read is what makes the
 * dependency acyclic.
 *
 * <p><b>Which forms count.</b> Not the three definition-id columns - a pinned
 * definition only records that a form was opened. The PRELIMINARY outcome's
 * formsToShow decides whether ECB, FED or both are required. An analysis whose
 * preliminary walk has not finished has no outcome yet, so the list stops at
 * PRELIMINARY and the rule blocks there, which is the right thing to tell the
 * analyst anyway.
 */
@Service
@DomainDrivenDesign.ApplicationService
@RequiredArgsConstructor
public class AnalysisCompletenessService {

    private final LeverageAnalysisRepository analyses;
    private final GetLeverageFormStateUseCase formState;
    private final AnalysisCompletenessDomainService completenessRule;

    @Transactional(readOnly = true)
    public FormCompleteness evaluate(String analysisUid) {
        LeverageAnalysis analysis = analyses.findByAnalysisUid(analysisUid)
                .orElseThrow(() -> new AnalysisNotFoundException(analysisUid));
        return evaluate(analysis);
    }

    @Transactional(readOnly = true)
    public FormCompleteness evaluate(LeverageAnalysis analysis) {
        List<FormCompletenessInput> forms = new ArrayList<>();
        FormState preliminary = read(analysis, LeverageFormType.PRELIMINARY);
        forms.add(toInput(LeverageFormType.PRELIMINARY, preliminary));

        for (LeverageFormType formType : downstreamForms(preliminary)) {
            forms.add(toInput(formType, read(analysis, formType)));
        }
        return completenessRule.evaluate(analysis.getStatus(), new CompletenessInput(forms));
    }

    private List<LeverageFormType> downstreamForms(FormState preliminary) {
        return preliminary.outcome() == null
                ? List.of()
                : preliminary.outcome().formsToShow();
    }

    private FormState read(LeverageAnalysis analysis, LeverageFormType formType) {
        return formState.get(analysis.getAnalysisUid(), formType, null);
    }

    /**
     * Only ERROR severity blocks. Per the BA: unanswered mandatory questions and
     * outstanding financial-field justifications are errors; anything advisory is
     * not, and must not keep the button greyed out.
     *
     * <p>The traversal state is inferred from FormState.Status, which carries only
     * IN_PROGRESS and COMPLETED. STRANDED therefore arrives here as IN_PROGRESS
     * and is reported to the analyst as an incomplete form rather than as a broken
     * definition. Accepted for now: validation is still correctly refused, only
     * the explanation is wrong. Giving FormState.Status a third value is the fix
     * when someone is next in that class for another reason.
     */
    private FormCompletenessInput toInput(LeverageFormType formType, FormState state) {
        TraversalState traversalState = state.status() == FormState.Status.COMPLETED
                ? TraversalState.TERMINAL
                : TraversalState.PENDING_INPUT;

        List<String> blocking = state.validationMessages().stream()
                .filter(message -> message.severity() == Severity.ERROR)
                .map(ValidationMessageView::messageKey)
                .toList();

        return new FormCompletenessInput(formType, traversalState, blocking);
    }
}
