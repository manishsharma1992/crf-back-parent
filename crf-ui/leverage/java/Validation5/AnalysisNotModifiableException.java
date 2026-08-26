package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisStatus;

/**
 * Raised when a mutating operation is attempted on an analysis that is no longer
 * modifiable.
 *
 * <p>The single source of truth for "may this analysis be changed?" is
 * {@code LeverageAnalysis#assertModifiable()}. Every mutating use case must go
 * through it rather than testing the status inline, so that the planned
 * relaxation (allow edit/delete of a VALIDATED analysis not yet consumed by a
 * rating) is a one-method change.
 */
public class AnalysisNotModifiableException extends RuntimeException {

    private static final String MESSAGE = "Leverage analysis %s is not modifiable in status %s";

    private final String analysisUid;
    private final AnalysisStatus status;

    public AnalysisNotModifiableException(String analysisUid, AnalysisStatus status) {
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
