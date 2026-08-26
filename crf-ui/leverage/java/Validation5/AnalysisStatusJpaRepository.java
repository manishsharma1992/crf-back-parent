package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage.persistence;

import java.time.Instant;
import java.util.Optional;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Compare-and-set on the analysis status.
 *
 * <p>{@code leverage_analysis} carries no version column and Manish has asked that
 * the table stay untouched, so optimistic locking is expressed as a conditional
 * UPDATE. The {@code status = :expectedStatus} predicate is the lock: a second
 * concurrent request finds the row already in VALIDATED, updates zero rows, and
 * the adapter turns that into ConcurrentValidationException. No schema change,
 * same guarantee.
 *
 * <p>clearAutomatically/flushAutomatically keep the persistence context honest,
 * since the bulk update bypasses it.
 */
public interface AnalysisStatusJpaRepository
        extends JpaRepository<LeverageAnalysisJpaEntity, Long> {

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update LeverageAnalysisJpaEntity a
               set a.status = :newStatus,
                   a.validatedBy = :validatedBy,
                   a.validatedTimestamp = :validatedTimestamp
             where a.analysisUid = :analysisUid
               and a.status = :expectedStatus
            """)
    int compareAndSetStatus(@Param("analysisUid") String analysisUid,
                            @Param("expectedStatus") AnalysisStatus expectedStatus,
                            @Param("newStatus") AnalysisStatus newStatus,
                            @Param("validatedBy") String validatedBy,
                            @Param("validatedTimestamp") Instant validatedTimestamp);

    @Query("select a.id from LeverageAnalysisJpaEntity a where a.analysisUid = :analysisUid")
    Optional<Long> findIdByAnalysisUid(@Param("analysisUid") String analysisUid);
}
