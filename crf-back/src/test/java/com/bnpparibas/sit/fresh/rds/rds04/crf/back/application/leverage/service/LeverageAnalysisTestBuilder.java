package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisStatus;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageAnalysis;

/**
 * ADAPT ME. The aggregate's construction is the one thing in this suite I could
 * not write for you - LeverageAnalysis has a shape only the codebase knows.
 *
 * <p>Kept in one class on purpose: every test that needs an aggregate comes
 * through here, so wiring it up is a single edit rather than a sweep.
 *
 * <p>If the aggregate has no public constructor or builder, a package-private test
 * factory on the aggregate itself is preferable to reflection - reflection here
 * would keep the suite green through a refactor that ought to break it.
 */
public final class LeverageAnalysisTestBuilder {

    private LeverageAnalysisTestBuilder() {
    }

    public static LeverageAnalysis draft(String analysisUid) {
        return LeverageAnalysis.builder()
                .analysisUid(analysisUid)
                .status(AnalysisStatus.DRAFT)
                .build();
    }

    public static LeverageAnalysis validated(String analysisUid) {
        return LeverageAnalysis.builder()
                .analysisUid(analysisUid)
                .status(AnalysisStatus.VALIDATED)
                .build();
    }
}
