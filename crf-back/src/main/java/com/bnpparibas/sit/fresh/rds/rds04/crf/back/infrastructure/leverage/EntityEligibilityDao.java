package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage;

import java.util.Optional;

/**
 * The one read behind the Q-S06 rules.
 *
 * <p>All three comparisons in a single row: the chosen entity, the analysed one and the parent are
 * three rows of the same table, so one query answers "same as analysed", "same business group" and
 * "name differs" together. Three separate queries would triple the round trips for a check that
 * runs on every traversal.
 *
 * <p><b>Every association is joined explicitly, with LEFT JOIN.</b> A path such as
 * {@code selected.businessGroup.id} in the select clause is an IMPLICIT join, and Hibernate makes
 * implicit joins INNER unless it can read the foreign key column instead. Where it cannot, a chosen
 * counterparty with no business group drops the whole row, {@link #compare} returns empty, and the
 * resolver reads that as "nothing chosen" — the out-of-group rule then fires for the wrong reason,
 * or not at all if the mapping changes. The explicit joins keep the row whatever is missing.
 *
 * <p>The analysed counterparty's OWN group is selected, because that is the group the rule is
 * about: the parent may have no row (a company at the top of its group, or a gap in RMPM), and the
 * parent's group is then null for reasons that have nothing to do with the analyst's choice.
 */
public interface EntityEligibilityDao extends JpaRepository<CounterpartyCharacteristics, Long> {

    @Query("""
            select selected.rmpmid      as selectedRmpmid,
                   selected.companyName as selectedName,
                   selectedGroup.id     as selectedGroup,
                   analysedGroup.id     as analysedGroup,
                   parent.rmpmid        as parentRmpmid,
                   parent.companyName   as parentName
            from CounterpartyCharacteristics analysed
            left join analysed.businessGroup analysedGroup
            left join CounterpartyCharacteristics parent
                   on parent.rmpmid = analysed.parentRmpmid
            left join CounterpartyCharacteristics selected
                   on selected.rmpmid = :selected
            left join selected.businessGroup selectedGroup
            where analysed.rmpmid = :analysed
            """)
    Optional<EligibilityRow> compare(@Param("selected") String selected,
                                     @Param("analysed") String analysed);

    /** Aliases above must match these getters exactly — Spring maps the projection by name. */
    interface EligibilityRow {
        String getSelectedRmpmid();
        String getSelectedName();
        Long getSelectedGroup();
        Long getAnalysedGroup();
        String getParentRmpmid();
        String getParentName();
    }
}
