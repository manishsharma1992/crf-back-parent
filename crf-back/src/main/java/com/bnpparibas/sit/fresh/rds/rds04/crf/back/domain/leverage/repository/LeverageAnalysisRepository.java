package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.repository;

public interface LeverageAnalysisRepository {

    /**
     * A validated analysis, or empty if it does not exist or is still DRAFT.
     *
     * <p>The status lives in the WHERE clause rather than being checked after the
     * load. A caller that loads first and checks second has to remember to check;
     * a finder that cannot return a draft removes the choice.
     *
     * <p>Deliberately NOT a jsonb query. It would be perfectly possible to pull one
     * answer straight out of the payload with a jsonb path expression, and it
     * would be faster - but it would hardcode the payload's shape into SQL, and
     * that shape is versioned by the workbook. The same argument that kept the
     * BR03 snapshot out of SQL applies here, and applies harder: this feeds a
     * rating.
     */
    Optional<LeverageAnalysis> findValidatedByAnalysisUid(String analysisUid);
}
