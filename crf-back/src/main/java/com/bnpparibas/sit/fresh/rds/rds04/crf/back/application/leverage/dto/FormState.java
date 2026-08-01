package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;

import java.util.List;
import java.util.Map;

/**
 * Everything the UI needs to render the form after one traversal pass.
 *
 * <p><b>Two statuses now, not three.</b> {@code AWAITING_EXTERNAL} and its {@code ExternalRequest}
 * are gone: every calculation happens in the domain layer, so a form never waits on the
 * rating motor and the UI never has to resubmit a computed value. Nothing replaces them — the walk
 * simply carries on to the next question.
 *
 * @param definitionVersion the version this state was computed against — the UI MUST echo it on
 *                          the next call to pin the session
 * @param flags             output flags filled so far, ALWAYS present even mid-form. A catalogued
 *                          flag nothing has set yet is absent from this map, and the UI renders it
 *                          blank; that is all "remains empty" ever meant.
 */
public record FormState(
        LeverageFormType formType,
        int definitionVersion,
        Status status,
        List<QuestionView> visibleQuestions,
        String nextQuestionKey,
        Map<String, String> flags,
        OutcomeView outcome) {

    public FormState {
        visibleQuestions = visibleQuestions == null ? List.of() : List.copyOf(visibleQuestions);
        flags = flags == null ? Map.of() : Map.copyOf(flags);
    }

    public enum Status { IN_PROGRESS, COMPLETED }

    /**
     * The terminal recommendation. PRELIMINARY only — ECB and FED express their result as flags,
     * so their terminal state carries an outcome of null and a populated {@code flags}.
     */
    public record OutcomeView(
            String code,
            String displayValue,
            List<LeverageFormType> formsToShow,
            Map<String, String> flags) {

        public OutcomeView {
            formsToShow = formsToShow == null ? List.of() : List.copyOf(formsToShow);
            flags = flags == null ? Map.of() : Map.copyOf(flags);
        }
    }
}
