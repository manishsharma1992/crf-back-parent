package com.bnpparibas.crf.back.infrastructure.leverage.persistence;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * BR03 read side. A native query rather than JPQL because of the {@code ::text}
 * cast on the JSONB column.
 *
 * <p>Only rows whose transition landed on VALIDATED are returned - the table is a
 * general status-transition log, and a snapshot is only meaningful for a
 * validation event.
 */
public interface AnalysisSnapshotJpaRepository
        extends JpaRepository<LeverageAnalysisHistoryJpaEntity, Long> {

    @Query(value = """
            select a.analysis_uid        as analysisUid,
                   a.financial_id        as financialId,
                   a.form_type           as formType,
                   a.form_payload::text  as formPayload,
                   a.validated_by        as validatedBy,
                   a.validated_timestamp as validatedTimestamp,
                   h.changed_by          as changedBy,
                   h.changed_timestamp   as changedTimestamp,
                   h.from_status         as fromStatus,
                   h.to_status           as toStatus
              from leverage_analysis_history h
              join leverage_analysis a
                on a.id = h.leverage_analysis_id
             where a.analysis_uid = :analysisUid
               and h.to_status = 'VALIDATED'
             order by h.changed_timestamp desc
            """, nativeQuery = true)
    List<AnalysisSnapshotRow> findSnapshotsByAnalysisUid(@Param("analysisUid") String analysisUid);
}
