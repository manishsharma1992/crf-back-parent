package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import java.time.Instant;

import com.bnpparibas.crf.shared.domain.leverage.model.AnalysisStatus;

/**
 * DTO projection for the BR03 read, populated by a JPQL constructor expression.
 *
 * <p>A record rather than an interface projection because JPQL constructor
 * expressions need a concrete type; interface projections only bind cleanly to
 * native queries, which is what we are moving away from here.
 *
 * <p>There is no formType column. Applicable forms are derived from which of the
 * three definition ids are non-null, which is also the only way to express the
 * ECB-and-FED case.
 *
 * <p>{@code responses} is typed String on the assumption that
 * LeverageAnalysisJpaEntity maps the jsonb column with
 * {@code @JdbcTypeCode(SqlTypes.JSON)} onto a String. If it already maps onto a
 * typed object, change this field to that type and delete the Jackson parsing in
 * AnalysisSnapshotResolverImpl - the mapping is then done once, by Hibernate.
 */
public record AnalysisSnapshotRow(String analysisUid,
                                  String financialArchiveId,
                                  String recommendedOutcome,
                                  Long preliminaryDefinitionId,
                                  Long ecbDefinitionId,
                                  Long fedDefinitionId,
                                  String responses,
                                  String validatedBy,
                                  Instant validatedTimestamp,
                                  String changedBy,
                                  Instant changedTimestamp,
                                  AnalysisStatus fromStatus,
                                  AnalysisStatus toStatus) {
}
