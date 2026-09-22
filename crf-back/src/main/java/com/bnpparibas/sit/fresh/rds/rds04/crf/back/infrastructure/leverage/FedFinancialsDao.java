package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * The FINSTAR reads behind {@code FedFinancialSourcesResolver}.
 *
 * <p><b>Its own repository, not a second method on {@code FinancialsDerivationDao}.</b> Spring Data
 * allows several repositories over one entity, and that is the point: the ECB query and this one
 * can each gain or lose a column without the other's projection changing shape underneath it.
 * Same physical table, separate contracts.
 *
 * <p>Same shape as the ECB DAO otherwise, for the same reasons — one native query for every figure
 * rather than one per figure, so the numbers come from a single read and cannot disagree, and a
 * projection rather than an entity, because two columns do not justify one.
 *
 * <p><b>Columns read:</b> exactly the ones FED's Fields rows name in {@code Derived From} —
 * {@code gross_debt} for {@code aplcTotalBSDebt}, {@code total_equity} for
 * {@code aplcBookValueOfEquity}. REIT prefills nothing from FINSTAR. Adding a source is a column
 * here plus one line in {@code FedFinancialSourcesResolverImpl}.
 */
@Repository
public interface FedFinancialsDao extends JpaRepository<Financials, Long> {

    /** A null column comes back as a null component and MUST NOT be read as zero. */
    @Query(value = """
            select f.gross_debt   as grossDebt,
                   f.total_equity as totalEquity
            from financials f
            where f.id = :financialsId
            """, nativeQuery = true)
    Optional<FedFinancialFigures> findFedFiguresById(@Param("financialsId") Long financialsId);

    /** Spring Data interface projection — no entity, no mapping annotations. */
    interface FedFinancialFigures {
        BigDecimal getGrossDebt();

        BigDecimal getTotalEquity();
    }
}

