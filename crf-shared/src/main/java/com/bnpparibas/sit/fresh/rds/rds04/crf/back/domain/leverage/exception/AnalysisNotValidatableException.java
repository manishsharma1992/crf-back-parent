package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception;

import com.bnpparibas.crf.shared.domain.leverage.model.CompletenessBlocker;
import java.util.List;

/**
 * Raised when validation is requested on an analysis whose form is not complete.
 *
 * <p>Defence in depth: the Validate button is only rendered when the
 * server-computed canValidate flag is true, but the transition must not rely on
 * the client having honoured it.
 */
public class AnalysisNotValidatableException extends RuntimeException {

    private static final String MESSAGE = "Leverage analysis %s cannot be validated: %s";

    private final String analysisUid;
    private final CompletenessBlocker blocker;
    private final transient List<String> missingFieldKeys;

    public AnalysisNotValidatableException(String analysisUid,
                                           CompletenessBlocker blocker,
                                           List<String> missingFieldKeys) {
        super(String.format(MESSAGE, analysisUid, blocker));
        this.analysisUid = analysisUid;
        this.blocker = blocker;
        this.missingFieldKeys = List.copyOf(missingFieldKeys);
    }

    public String getAnalysisUid() {
        return analysisUid;
    }

    public CompletenessBlocker getBlocker() {
        return blocker;
    }

    public List<String> getMissingFieldKeys() {
        return missingFieldKeys;
    }
}
