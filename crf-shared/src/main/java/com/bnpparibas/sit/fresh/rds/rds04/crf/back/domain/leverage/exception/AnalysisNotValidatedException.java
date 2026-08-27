package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisStatus;

/**
 * Raised when a validated-only read is attempted on an analysis that has not been
 * validated.
 *
 * <p>The mirror image of AnalysisNotModifiableException: that one refuses writes to
 * a validated analysis, this one refuses regulatory reads of a draft. Between them
 * they say DRAFT is writable and unreadable-downstream, VALIDATED is readable and
 * frozen.
 */
public class AnalysisNotValidatedException extends RuntimeException {

    private static final String MESSAGE =
            "Leverage analysis %s is in status %s; only a validated analysis can be read downstream";

    private final String analysisUid;
    private final AnalysisStatus status;

    public AnalysisNotValidatedException(String analysisUid, AnalysisStatus status) {
        super(String.format(MESSAGE, analysisUid, status));
        this.analysisUid = analysisUid;
        this.status = status;
    }

    public String getAnalysisUid() {
        return analysisUid;
    }

    public AnalysisStatus getStatus() {
        return status;
    }
}
