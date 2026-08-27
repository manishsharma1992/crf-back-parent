package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.aggregate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisStatus;

/**
 * Maps the existing {@code leverage_analysis_history} table. No columns added: it
 * is a status-transition audit log and that is all BR02 needs it to be. The BR03
 * snapshot is projected from the frozen analysis row rather than duplicated here.
 *
 * <p>Deliberately does NOT extend BaseEntity. The table has changed_by /
 * changed_timestamp and no created/modified pair, so inheriting the audit columns
 * would either fail on startup or force a schema change - and the transition
 * timestamp IS the creation timestamp for an append-only log, so a second pair
 * would be noise.
 *
 * <p>Append-only by construction: no setters, no update path.
 */
@Entity
@Table(name = "leverage_analysis_history")
public class LeverageAnalysisHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Mapped as an association rather than a raw Long. The column already exists;
     * this is mapping only, and it is what lets the BR03 query be JPQL with a
     * fetch join instead of a native query.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leverage_analysis_id", nullable = false, updatable = false)
    private LeverageAnalysis analysis;

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

    protected LeverageAnalysisHistory() {
        // required by JPA
    }

    public LeverageAnalysisHistory(LeverageAnalysis analysis,
                                    AnalysisStatus fromStatus,
                                    AnalysisStatus toStatus,
                                    String changedBy,
                                    Instant changedTimestamp) {
        this.analysis = analysis;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.changedBy = changedBy;
        this.changedTimestamp = changedTimestamp;
    }

    public Long getId() {
        return id;
    }

    public LeverageAnalysis getAnalysis() {
        return analysis;
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
