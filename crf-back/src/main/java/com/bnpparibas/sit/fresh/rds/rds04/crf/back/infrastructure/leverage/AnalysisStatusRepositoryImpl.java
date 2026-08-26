package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage;

import java.time.Instant;

import com.bnpparibas.crf.shared.domain.leverage.model.AnalysisStatusChange;
import com.bnpparibas.crf.shared.domain.leverage.model.AnalysisStatus;
import com.bnpparibas.crf.shared.domain.leverage.port.AnalysisStatusRepository;

/**
 * Adapter for the BR02 write path. Deliberately carries no Spring stereotype -
 * wired by LeverageValidationBeanConfig, consistent with FinancialsResolverImpl
 * and the rest of the adapters.
 */
public class AnalysisStatusRepositoryImpl implements AnalysisStatusRepository {

    private static final String UNKNOWN_ANALYSIS = "Unknown leverage analysis uid: %s";

    private final AnalysisStatusJpaRepository analysisRepository;
    private final LeverageAnalysisHistoryJpaRepository historyRepository;

    public AnalysisStatusRepositoryImpl(
            AnalysisStatusJpaRepository analysisRepository,
            LeverageAnalysisHistoryJpaRepository historyRepository) {
        this.analysisRepository = analysisRepository;
        this.historyRepository = historyRepository;
    }

    @Override
    public boolean compareAndSetStatus(String analysisUid,
                                       AnalysisStatus expectedStatus,
                                       AnalysisStatus newStatus,
                                       String validatedBy,
                                       Instant validatedTimestamp) {
        int updated = analysisRepository.compareAndSetStatus(
                analysisUid, expectedStatus, newStatus, validatedBy, validatedTimestamp);
        return updated == 1;
    }

    @Override
    public void appendHistory(AnalysisStatusChange statusChange) {
        Long analysisId = analysisRepository.findIdByAnalysisUid(statusChange.analysisUid())
                .orElseThrow(() -> new IllegalStateException(
                        String.format(UNKNOWN_ANALYSIS, statusChange.analysisUid())));
        historyRepository.save(new LeverageAnalysisHistoryJpaEntity(
                analysisId,
                statusChange.fromStatus(),
                statusChange.toStatus(),
                statusChange.changedBy(),
                statusChange.changedTimestamp()));
    }
}
