package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage;

import com.bnpparibas.crf.back.infrastructure.leverage.persistence.AnalysisSnapshotJpaRepository;
import com.bnpparibas.crf.back.infrastructure.leverage.persistence.AnalysisSnapshotResolverImpl;
import com.bnpparibas.crf.back.infrastructure.leverage.persistence.LeverageAnalysisHistoryJpaRepository;
import com.bnpparibas.crf.back.infrastructure.leverage.persistence.LeverageAnalysisStatusJpaRepository;
import com.bnpparibas.crf.back.infrastructure.leverage.persistence.LeverageAnalysisStatusRepositoryImpl;
import com.bnpparibas.crf.shared.domain.leverage.port.AnalysisSnapshotResolver;
import com.bnpparibas.crf.shared.domain.leverage.port.LeverageAnalysisStatusRepository;
import com.bnpparibas.crf.shared.domain.leverage.service.FormCompletenessDomainService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the validation module. Domain classes stay free of Spring annotations;
 * every adapter and domain service is declared here.
 */
@Configuration
public class LeverageValidationBeanConfig {

    @Bean
    public FormCompletenessDomainService formCompletenessDomainService() {
        return new FormCompletenessDomainService();
    }

    @Bean
    public LeverageAnalysisStatusRepository leverageAnalysisStatusRepository(
            LeverageAnalysisStatusJpaRepository analysisRepository,
            LeverageAnalysisHistoryJpaRepository historyRepository) {
        return new LeverageAnalysisStatusRepositoryImpl(analysisRepository, historyRepository);
    }

    @Bean
    public AnalysisSnapshotResolver analysisSnapshotResolver(
            AnalysisSnapshotJpaRepository snapshotRepository,
            ObjectMapper objectMapper) {
        return new AnalysisSnapshotResolverImpl(snapshotRepository, objectMapper);
    }
}
