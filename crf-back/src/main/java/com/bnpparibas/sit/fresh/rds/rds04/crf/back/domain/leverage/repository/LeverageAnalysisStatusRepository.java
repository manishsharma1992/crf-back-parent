package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.repository;

import com.bnpparibas.crf.shared.domain.leverage.model.AnalysisStatusChange;
import com.bnpparibas.crf.shared.domain.leverage.model.LeverageAnalysisStatus;
import java.time.Instant;

/**
 * Write port for the BR02 transition.
 *
 * <p>The status change is expressed as a compare-and-set rather than a
 * load-mutate-save, because {@code leverage_analysis} has no version column and
 * the table is stable enough that adding one is not warranted. The conditional
 * UPDATE gives the same guarantee at zero schema cost.
 */
public interface LeverageAnalysisStatusRepository {

    /**
     * Atomically moves the analysis from {@code expectedStatus} to
     * {@code newStatus}, stamping validated_by / validated_timestamp.
     *
     * @return true if exactly one row was updated; false if another request won
     *         the race and the row is no longer in {@code expectedStatus}
     */
    boolean compareAndSetStatus(String analysisUid,
                                LeverageAnalysisStatus expectedStatus,
                                LeverageAnalysisStatus newStatus,
                                String validatedBy,
                                Instant validatedTimestamp);

    /** Appends the audit row to {@code leverage_analysis_history}. */
    void appendHistory(AnalysisStatusChange statusChange);
}
