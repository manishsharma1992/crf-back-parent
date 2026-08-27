package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage;

@Repository
public interface LeverageAnalysisDao extends JpaRepository<LeverageAnalysis, Long> {

    /**
     * Hibernate materialises `responses` into LeverageResponses through the
     * @JdbcTypeCode(SqlTypes.JSON) mapping, so the whole frozen payload arrives
     * typed and the navigation happens in Java. No jsonb operators, no cast.
     *
     * <p>One row, keyed by a unique business identifier, with a jsonb column that
     * is already being read whole elsewhere. There is nothing here to optimise
     * until a profiler says otherwise.
     */
    @Query("""
            select a
              from LeverageAnalysis a
             where a.analysisUid = :analysisUid
               and a.status = :validatedStatus
            """)
    Optional<LeverageAnalysis> findValidated(@Param("analysisUid") String analysisUid,
                                             @Param("validatedStatus") AnalysisStatus validatedStatus);

    default Optional<LeverageAnalysis> findValidatedByAnalysisUid(String analysisUid) {
        return findValidated(analysisUid, AnalysisStatus.VALIDATED);
    }
}
