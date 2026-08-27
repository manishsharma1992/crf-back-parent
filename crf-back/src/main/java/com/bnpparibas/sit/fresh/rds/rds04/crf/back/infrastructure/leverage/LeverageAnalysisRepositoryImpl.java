package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.repository.LeverageAnalysisRepository;

@Repository
@RequiredArgsConstructor
public class LeverageAnalysisRepositoryImpl implements LeverageAnalysisRepository {

    private final LeverageAnalysisDao leverageAnalysisDao;

    @Override
    public Optional<LeverageAnalysis> findValidatedByAnalysisUid(String analysisUid) {
        return leverageAnalysisDao.findValidatedByAnalysisUid(analysisUid);
    }
}
