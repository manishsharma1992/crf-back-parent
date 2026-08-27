package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.FormState;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.ValidationMessageView;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.Severity;

/**
 * The ONLY place the tests construct a FormState.
 *
 * <p>FormState has grown - FormStateAssembler passes FlagView and panels that the
 * record in this change set does not declare - and it will grow again. Every test
 * that needs one comes through here, so a signature change is a single edit
 * rather than a sweep through the suite.
 *
 * <p>ADAPT the two constructor calls below to the current FormState signature.
 */
public final class FormStateFixture {

    private FormStateFixture() {
    }

    public static FormState completed(LeverageFormType formType,
                                      FormState.OutcomeView outcome,
                                      ValidationMessageView... messages) {
        return build(formType, FormState.Status.COMPLETED, outcome, List.of(messages));
    }

    public static FormState inProgress(LeverageFormType formType,
                                       ValidationMessageView... messages) {
        return build(formType, FormState.Status.IN_PROGRESS, null, List.of(messages));
    }

    public static FormState.OutcomeView outcomeShowing(LeverageFormType... formsToShow) {
        return new FormState.OutcomeView("LEVERAGED", "Leveraged", List.of(formsToShow), Map.of());
    }

    public static ValidationMessageView error(String messageKey) {
        return new ValidationMessageView(messageKey, Severity.ERROR, "Q-S01", null, "text");
    }

    public static ValidationMessageView warning(String messageKey) {
        return new ValidationMessageView(messageKey, Severity.WARNING, "Q-S01", null, "text");
    }

    private static FormState build(LeverageFormType formType,
                                   FormState.Status status,
                                   FormState.OutcomeView outcome,
                                   List<ValidationMessageView> messages) {
        return new FormState(formType, 1, status, List.of(), null, Map.of(), outcome,
                messages, Instant.EPOCH, null, null);
    }
}
