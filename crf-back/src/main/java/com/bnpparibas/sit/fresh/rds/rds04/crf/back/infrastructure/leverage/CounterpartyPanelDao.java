package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage;

/**
 * The single read behind CURRENT_LEVERAGE_TX_FLAGS.
 *
 * <p>All four fields in one row: the panel shows them together, so fetching them together is both
 * cheaper and guaranteed self-consistent — four separate reads could straddle an RMPM update and
 * show a ratio from before it beside a flag from after.
 */
public interface CounterpartyPanelDao extends JpaRepository<CounterpartyCharacteristics, Long> {

    @Query(value = """
            select cc.leveraged           as leveraged,
                   cc.covenant_structure  as covenantStructure,
                   cc.ecb_leverage_ratio  as ecbLeverageRatio,
                   cc.leveraged_date      as leveragedDate
            from counterparty_characteristics cc
            where cc.rmpmid = :rmpmid
            """, nativeQuery = true)
    Optional<PanelRow> findLeverageFlags(@Param("rmpmid") String rmpmid);

    interface PanelRow {
        Integer getLeveraged();
        Integer getCovenantStructure();
        BigDecimal getEcbLeverageRatio();
        LocalDate getLeveragedDate();
    }
}
