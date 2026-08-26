package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.time.Instant;

/**
 * A row destined for {@code leverage_analysis_history}, produced by the aggregate
 * when a status transition succeeds.
 *
 * <p>Maps one-to-one onto the existing table; no column additions. The BR03
 * snapshot content is NOT duplicated here - it is read back by projecting over
 * the frozen {@code leverage_analysis} row (see AnalysisSnapshotResolver).
 */
@DomainDrivenDesign.ValueObject
public record AnalysisStatusChange(String analysisUid,
                                   AnalysisStatus fromStatus,
                                   AnalysisStatus toStatus,
                                   String changedBy,
                                   Instant changedTimestamp) {
}
