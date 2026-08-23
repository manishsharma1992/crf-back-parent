package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception;

/**
 * Raised when the compare-and-set status transition affected zero rows, meaning
 * another request validated the same analysis first.
 *
 * <p>This is the substitute for JPA optimistic locking: {@code leverage_analysis}
 * carries no version column and the table is stable, so the guard is expressed as
 * a conditional UPDATE instead.
 */
public class ConcurrentValidationException extends RuntimeException {

    private static final String MESSAGE =
            "Leverage analysis %s was validated concurrently by another request";

    private final String analysisUid;

    public ConcurrentValidationException(String analysisUid) {
        super(String.format(MESSAGE, analysisUid));
        this.analysisUid = analysisUid;
    }

    public String getAnalysisUid() {
        return analysisUid;
    }
}
