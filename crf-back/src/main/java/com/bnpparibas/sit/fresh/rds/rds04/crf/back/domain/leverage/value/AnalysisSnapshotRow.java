package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import java.time.Instant;

/**
 * Spring Data interface projection over the join of leverage_analysis_history and
 * the frozen leverage_analysis row.
 *
 * <p>The flags JSONB is projected whole, as text. Extracting individual flags with
 * SQL JSONB path expressions was rejected: different workbook versions carry
 * different flag sets, so hardcoding paths into the query would silently break
 * replay of analyses validated under an earlier tree definition. Parsing happens
 * in Java, against the definition that was in force.
 */
public interface AnalysisSnapshotRow {

    String getAnalysisUid();

    String getFinancialId();

    String getFormType();

    /** Raw JSONB, cast to text in the query. Parsed by the resolver. */
    String getFormPayload();

    String getValidatedBy();

    Instant getValidatedTimestamp();

    String getChangedBy();

    Instant getChangedTimestamp();

    String getFromStatus();

    String getToStatus();
}
