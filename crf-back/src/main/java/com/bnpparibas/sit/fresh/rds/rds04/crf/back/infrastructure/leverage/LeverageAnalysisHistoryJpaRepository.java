package com.bnpparibas.crf.back.infrastructure.leverage.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

public interface LeverageAnalysisHistoryJpaRepository
        extends JpaRepository<LeverageAnalysisHistoryJpaEntity, Long> {
}
