package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import java.util.ArrayList;
import java.util.List;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.FormState;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.ValidationMessageView;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception.AnalysisNotFoundException;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception.StrandedTraversalException;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service.AnalysisCompletenessDomainService;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.CompletenessInput;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.FormCompleteness;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.FormCompletenessInput;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageAnalysis;
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
 * service calls the existing read for each required form and reads the answer off
 * the FormState it already returns. Nothing in the read or save path changes.
 *
 * <p>That direction is not merely tidy - the reverse would not work. If project()
 * computed completeness, and completeness needed the other forms' states, each
 * nested read would compute completeness again and recurse without end. Keeping
 * this a layer ABOVE the form-state read is what makes the dependency acyclic.
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

    /**
     * Entry point for the availability endpoint.
     *
     * <p>The overload below is deliberately NOT annotated. A @Transactional method
     * calling another one through {@code this} bypasses the proxy anyway - the
     * annotation would be decorative, and Sonar is right to flag it. The overload
     * runs inside whichever transaction its caller already holds: this one, or the
     * validate use case's.
     */
    @Transactional(readOnly = true)
    public FormCompleteness evaluate(String analysisUid) {
        return evaluate(analyses.findByAnalysisUid(analysisUid)
                .orElseThrow(() -> new AnalysisNotFoundException(analysisUid)));
    }

    public FormCompleteness evaluate(LeverageAnalysis analysis) {
        List<FormCompletenessInput> forms = new ArrayList<>();

        FormState preliminary = readOrNull(analysis, LeverageFormType.PRELIMINARY);
        forms.add(preliminary == null
                ? stranded(LeverageFormType.PRELIMINARY)
                : toInput(LeverageFormType.PRELIMINARY, preliminary));

        for (LeverageFormType formType : downstreamForms(preliminary)) {
            FormState state = readOrNull(analysis, formType);
            forms.add(state == null ? stranded(formType) : toInput(formType, state));
        }
        return completenessRule.evaluate(analysis.getStatus(), new CompletenessInput(forms));
    }

    /**
     * FormStateAssembler THROWS on a stranded walk rather than returning a state,
     * so a broken definition would otherwise take the availability endpoint down
     * with it - and would do so even when the stranded form is not the one the
     * analyst is looking at.
     *
     * <p>Caught here and turned into a blocker instead. The analyst gets a disabled
     * button with an accurate reason; validation is refused either way, but a
     * refusal is a far better answer than a 500.
     *
     * <p>Note this is the ONLY exception swallowed in this class. AnalysisNotFound
     * and anything else still propagate - a stranded definition is a known,
     * reportable state, not a signal to keep going regardless.
     */
    private FormState readOrNull(LeverageAnalysis analysis, LeverageFormType formType) {
        try {
            return formState.get(analysis.getAnalysisUid(), formType.name(), null);
        } catch (StrandedTraversalException e) {
            return null;
        }
    }

    private List<LeverageFormType> downstreamForms(FormState preliminary) {
        return preliminary == null || preliminary.outcome() == null
                ? List.of()
                : preliminary.outcome().formsToShow();
    }

    private FormCompletenessInput stranded(LeverageFormType formType) {
        return new FormCompletenessInput(formType, TraversalState.STRANDED, List.of());
    }

    /**
     * Only ERROR severity blocks. Per the BA: unanswered mandatory questions and
     * outstanding financial-field justifications are errors; anything advisory is
     * not, and must not keep the button greyed out.
     *
     * <p>COMPLETED maps to TERMINAL safely: FormStateAssembler sets COMPLETED only
     * on a terminal walk and throws on a stranded one, so the two-value Status
     * loses nothing here.
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
