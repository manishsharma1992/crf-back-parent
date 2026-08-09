package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage;

/**
 * The one read behind the Q-S06 rules.
 *
 * <p>All three comparisons in a single row: the chosen entity, the analysed one and the parent are
 * three rows of the same table, so one query answers "same as analysed", "same business group" and
 * "name differs" together. Three separate queries would triple the round trips for a check that
 * runs on every traversal.
 */
public interface EntityEligibilityDao extends JpaRepository<CounterpartyCharacteristics, Long> {

    @Query(value = """
            select selected.rmpmid            as selectedRmpmid,
                   selected.companyname       as selectedName,
                   selected.business_group_id as selectedGroup,
                   parent.rmpmid              as parentRmpmid,
                   parent.companyname         as parentName,
                   parent.business_group_id   as parentGroup
            from counterparty_characteristics analysed
            left join counterparty_characteristics parent
                   on parent.rmpmid = analysed.parent_rmpm_id
            left join counterparty_characteristics selected
                   on selected.rmpmid = :selected
            where analysed.rmpmid = :analysed
            """, nativeQuery = true)
    Optional<EligibilityRow> compare(@Param("selected") String selected,
                                     @Param("analysed") String analysed);

    interface EligibilityRow {
        String getSelectedRmpmid();
        String getSelectedName();
        Long getSelectedGroup();
        String getParentRmpmid();
        String getParentName();
        Long getParentGroup();
    }
}
