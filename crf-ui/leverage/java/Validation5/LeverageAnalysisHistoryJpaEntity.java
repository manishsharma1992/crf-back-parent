package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisStatus;

/**
 * Maps the existing {@code leverage_analysis_history} table. No columns added:
 * the table is a status-transition audit log and that is all BR02 needs it to be.
 * The BR03 snapshot content is projected from the frozen leverage_analysis row
 * instead of being duplicated here.
 *
 * <p>Append-only by construction - there is no setter and no update path.
 */
@Entity
@Table(name = "leverage_analysis_history")
public class LeverageAnalysisHistoryJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "leverage_analysis_id", nullable = false)
    private Long leverageAnalysisId;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", nullable = false)
    private AnalysisStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false)
    private AnalysisStatus toStatus;

    @Column(name = "changed_by", nullable = false)
    private String changedBy;

    @Column(name = "changed_timestamp", nullable = false)
    private Instant changedTimestamp;

    protected LeverageAnalysisHistoryJpaEntity() {
        // required by JPA
    }

    public LeverageAnalysisHistoryJpaEntity(Long leverageAnalysisId,
                                            AnalysisStatus fromStatus,
                                            AnalysisStatus toStatus,
                                            String changedBy,
                                            Instant changedTimestamp) {
        this.leverageAnalysisId = leverageAnalysisId;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.changedTimestamp = changedTimestamp;
    }

    public Long getId() {
        return id;
    }

    public Long getLeverageAnalysisId() {
        return leverageAnalysisId;
    }

    public AnalysisStatus getFromStatus() {
        return fromStatus;
    }

    public AnalysisStatus getToStatus() {
        return toStatus;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public Instant getChangedTimestamp() {
        return changedTimestamp;
    }
}
