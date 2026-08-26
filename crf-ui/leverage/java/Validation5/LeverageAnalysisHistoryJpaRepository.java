package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LeverageAnalysisHistoryJpaRepository
        extends JpaRepository<LeverageAnalysisHistoryJpaEntity, Long> {
}
