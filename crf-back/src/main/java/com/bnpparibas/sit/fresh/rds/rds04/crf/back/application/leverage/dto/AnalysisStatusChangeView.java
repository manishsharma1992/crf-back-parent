package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto;

import java.time.Instant;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisStatus;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisStatusChange;

/**
 * What the validate endpoint returns.
 *
 * <p>Returns the transition rather than 204 so the client can render the
 * validated-by / validated-at stamp and flip the form to readonly without a
 * second round trip - the analyst clicked once and should see the result of that
 * click, not a spinner while the page refetches itself.
 *
 * <p>fromStatus is included because the client cannot infer it. Today it is always
 * DRAFT, but hard-coding that assumption into the UI is how a screen ends up
 * lying the day a second transition exists.
 */
public record AnalysisStatusChangeView(String analysisUid,
                                       AnalysisStatus fromStatus,
                                       AnalysisStatus toStatus,
                                       String changedBy,
                                       Instant changedTimestamp) {

    public static AnalysisStatusChangeView from(AnalysisStatusChange change) {
        return new AnalysisStatusChangeView(
                change.analysisUid(),
                change.fromStatus(),
                change.toStatus(),
                change.changedBy(),
                change.changedTimestamp());
    }
}
