package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage;

import java.time.Instant;

import com.bnpparibas.crf.shared.domain.leverage.model.AnalysisStatusChange;
import com.bnpparibas.crf.shared.domain.leverage.model.LeverageAnalysisStatus;
import com.bnpparibas.crf.shared.domain.leverage.port.LeverageAnalysisStatusRepository;

/**
 * Adapter for the BR02 write path. Deliberately carries no Spring stereotype -
 * wired by LeverageValidationBeanConfig, consistent with FinancialsResolverImpl
 * and the rest of the adapters.
 */
public class LeverageAnalysisStatusRepositoryImpl implements LeverageAnalysisStatusRepository {

    private static final String UNKNOWN_ANALYSIS = "Unknown leverage analysis uid: %s";

    private final LeverageAnalysisStatusJpaRepository analysisRepository;
    private final LeverageAnalysisHistoryJpaRepository historyRepository;

    public LeverageAnalysisStatusRepositoryImpl(
            LeverageAnalysisStatusJpaRepository analysisRepository,
            LeverageAnalysisHistoryJpaRepository historyRepository) {
        this.analysisRepository = analysisRepository;
        this.historyRepository = historyRepository;
    }

    @Override
    public boolean compareAndSetStatus(String analysisUid,
                                       LeverageAnalysisStatus expectedStatus,
                                       LeverageAnalysisStatus newStatus,
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
