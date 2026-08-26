/*
 * ---------------------------------------------------------------------------
 * FRAGMENTS, NOT COMPLETE FILES.
 *
 * Two mapping-only additions. Neither touches the database: both columns
 * (leverage_analysis_history.leverage_analysis_id and
 * leverage_analysis.financial_id) already exist. Mapping them as associations
 * is what lets the BR03 query be JPQL instead of native.
 * ---------------------------------------------------------------------------
 */

// --- in LeverageAnalysisHistoryJpaEntity ------------------------------------
// Replaces the raw `private Long leverageAnalysisId;` field.

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leverage_analysis_id", nullable = false, updatable = false)
    private LeverageAnalysisJpaEntity analysis;

// The constructor then takes the analysis reference rather than its id, which
// also removes the extra findIdByAnalysisUid lookup in
// AnalysisStatusRepositoryImpl.appendHistory.


// --- in LeverageAnalysisJpaEntity -------------------------------------------
// Note the column is financial_id, not financials_id.

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "financial_id", nullable = false)
    private FinancialJpaEntity financial;


// --- responses, if it is not already mapped ---------------------------------
// Hibernate 6 reads and writes jsonb through the JDBC type descriptor, so no
// ::text cast is ever needed in the query. Do NOT add this if the entity
// already maps `responses` onto a typed object - use that type instead.

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "responses", columnDefinition = "jsonb")
    private String responses;
