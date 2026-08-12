package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * The FINSTAR reads behind {@link com.bnpparibas.sit.fresh.rds.rds04.crf.back
 * .application.leverage.ports.FinancialsResolver}.
 *
 * <p><b>One query for all three figures, not one per figure.</b> The previous shape —
 * {@code findAttribute(id, name)} — would issue three round trips per traversal, and a traversal
 * runs on every answer. Worse, three separate reads can disagree if FINSTAR is written between
 * them, and the ratio would then be computed from figures that never coexisted.
 *
 * <p>Native and projected rather than mapped to an entity: nothing else in the application needs
 * a Financials entity, and three columns do not justify one.
 */
public interface FinancialsDerivationDao extends JpaRepository<Financials, Long> {

    /**
     * The three prefilled figures of the financial table.
     *
     * <p>A null column comes back as a null component and MUST NOT be read as zero — that
     * distinction is the whole of {@code SOURCE_EMPTY}.
     */
    @Query(value = """
            select f.ebitda     as ebitda,
                   f.gross_debt as grossDebt,
                   f.net_debt   as netDebt
            from financials f
            where f.id = :financialsId
            """, nativeQuery = true)
    Optional<FinancialFigures> findFiguresById(@Param("financialsId") Long financialsId);

    /** Spring Data interface projection — no entity, no mapping annotations. */
    interface FinancialFigures {
        BigDecimal getEbitda();

        BigDecimal getGrossDebt();

        BigDecimal getNetDebt();
    }
}
