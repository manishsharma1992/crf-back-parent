package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception;

import java.util.List;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.CompletenessBlocker;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.validation.FormCompleteness;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;

/**
 * Raised when validation is requested on an analysis whose form is not complete.
 *
 * <p>Defence in depth: the Validate button is only enabled when the server-computed
 * availability says so, but the transition must not rely on the client having
 * honoured it.
 *
 * <p><b>Carries the FormCompleteness whole rather than copying its parts.</b> An
 * earlier version took a blocker plus a list of field keys, and when the model
 * changed the exception kept the old parameter names and stopped compiling. There
 * is no second shape to keep in step now - the accessors below are conveniences
 * over one field, not a parallel copy of it.
 */
public class AnalysisNotValidatableException extends RuntimeException {

    private static final String MESSAGE = "Leverage analysis %s cannot be validated: %s%s";

    private final String analysisUid;
    private final transient FormCompleteness completeness;

    public AnalysisNotValidatableException(String analysisUid, FormCompleteness completeness) {
        super(String.format(MESSAGE, analysisUid, completeness.blocker(), onForm(completeness)));
        this.analysisUid = analysisUid;
        this.completeness = completeness;
    }

    private static String onForm(FormCompleteness completeness) {
        return completeness.blockingFormType()
                .map(form -> " on the " + form + " form")
                .orElse("");
    }

    public String getAnalysisUid() {
        return analysisUid;
    }

    public FormCompleteness getCompleteness() {
        return completeness;
    }

    public CompletenessBlocker getBlocker() {
        return completeness.blocker();
    }

    public LeverageFormType getBlockingForm() {
        return completeness.blockingForm();
    }

    public List<String> getBlockingMessageCodes() {
        return completeness.blockingMessageCodes();
    }
}
