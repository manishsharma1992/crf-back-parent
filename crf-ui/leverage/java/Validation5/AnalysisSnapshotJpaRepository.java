package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage.persistence;

import java.util.List;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * BR03 read side, in JPQL.
 *
 * <p>No native query and no ::text cast. The jsonb column is converted by the
 * entity's JDBC type mapping, not by SQL, so the query never mentions Postgres
 * types. This also keeps the flag-extraction logic out of SQL, which matters for
 * replaying analyses answered under an older tree definition.
 *
 * <p>The joins rely on two associations being mapped on the entities - see
 * LeverageAnalysisHistoryJpaEntity.analysis and LeverageAnalysisJpaEntity.financial.
 * Both are mapping-only changes over columns that already exist.
 */
public interface AnalysisSnapshotJpaRepository
        extends JpaRepository<LeverageAnalysisHistoryJpaEntity, Long> {

    @Query("""
            select new com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage.persistence.AnalysisSnapshotRow(
                       a.analysisUid,
                       f.archiveId,
                       a.recommendedOutcome,
                       a.preliminaryDefinitionId,
                       a.ecbDefinitionId,
                       a.fedDefinitionId,
                       a.responses,
                       a.validatedBy,
                       a.validatedTimestamp,
                       h.changedBy,
                       h.changedTimestamp,
                       h.fromStatus,
                       h.toStatus)
              from LeverageAnalysisHistoryJpaEntity h
              join h.analysis a
              join a.financial f
             where a.analysisUid = :analysisUid
               and h.toStatus = :validatedStatus
             order by h.changedTimestamp desc
            """)
    List<AnalysisSnapshotRow> findSnapshots(@Param("analysisUid") String analysisUid,
                                            @Param("validatedStatus") AnalysisStatus validatedStatus);

    /**
     * The status is bound rather than written as an enum literal so the query text
     * stays free of fully-qualified class names.
     */
    default List<AnalysisSnapshotRow> findValidatedSnapshots(String analysisUid) {
        return findSnapshots(analysisUid, AnalysisStatus.VALIDATED);
    }
}
