package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage;

import java.util.List;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * BR03 read side, in JPQL.
 *
 * <p>No native query and no {@code ::text} cast. {@code responses} is mapped on
 * LeverageAnalysis with {@code @JdbcTypeCode(SqlTypes.JSON)} onto LeverageResponses,
 * so Hibernate hands back a typed object and the jsonb never becomes a String
 * anywhere in this module.
 *
 * <p>Returns entities with a fetch join rather than a DTO constructor expression.
 * A constructor expression would have to select {@code a.responses}, a
 * custom-JavaType attribute, into a record component - it may well work, but the
 * fetch join needs no such bet, costs the same single query, and the caller wants
 * essentially the whole analysis row anyway.
 *
 * <p>Only transitions that landed on VALIDATED are returned: the table is a
 * general status log, and a snapshot is only meaningful for a validation event.
 */
public interface AnalysisSnapshotDao extends JpaRepository<LeverageAnalysisHistory, Long> {

    @Query("""
            select h
              from LeverageAnalysisHistory h
              join fetch h.analysis a
             where a.analysisUid = :analysisUid
               and h.toStatus = :validatedStatus
             order by h.changedTimestamp desc
            """)
    List<LeverageAnalysisHistory> findSnapshots(@Param("analysisUid") String analysisUid,
                                                @Param("validatedStatus") AnalysisStatus validatedStatus);

    /** Keeps the enum out of the query text as a fully-qualified literal. */
    default List<LeverageAnalysisHistory> findValidatedSnapshots(String analysisUid) {
        return findSnapshots(analysisUid, AnalysisStatus.VALIDATED);
    }
}
