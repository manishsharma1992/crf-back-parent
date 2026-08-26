package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage.config;

import java.time.Clock;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.port.AnalysisSnapshotResolver;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.port.AnalysisStatusRepository;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service.AnalysisCompletenessDomainService;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage.persistence.AnalysisSnapshotJpaRepository;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage.persistence.AnalysisSnapshotResolverImpl;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage.persistence.AnalysisStatusJpaRepository;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage.persistence.AnalysisStatusRepositoryImpl;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage.persistence.LeverageAnalysisHistoryJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the parts that carry no Spring stereotype: the domain service and the two
 * adapters. The application services use @Service and @RequiredArgsConstructor
 * like the rest of the leverage package, so they are not declared here.
 */
@Configuration
public class LeverageValidationBeanConfig {

    @Bean
    public AnalysisCompletenessDomainService analysisCompletenessDomainService() {
        return new AnalysisCompletenessDomainService();
    }

    @Bean
    public AnalysisStatusRepository analysisStatusRepository(
            AnalysisStatusJpaRepository analysisRepository,
            LeverageAnalysisHistoryJpaRepository historyRepository) {
        return new AnalysisStatusRepositoryImpl(analysisRepository, historyRepository);
    }

    @Bean
    public AnalysisSnapshotResolver analysisSnapshotResolver(
            AnalysisSnapshotJpaRepository snapshotRepository,
            ObjectMapper objectMapper) {
        return new AnalysisSnapshotResolverImpl(snapshotRepository, objectMapper);
    }

    /**
     * Injected rather than calling Instant.now() so validation timestamps are
     * controllable in tests - BR03 snapshot ordering depends on them. Declare
     * once, application-wide, if one does not exist already.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
