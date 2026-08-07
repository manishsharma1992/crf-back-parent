package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * The reference-data reads behind {@link DerivedValueResolver}.
 *
 * <p>Native because the parent lives in the same table as the child, reached by a self-join on
 * {@code parent_rmpm_id}. Expressing that as an entity association would put a self-referencing
 * relationship on CounterpartyCharacteristics that nothing else in the application wants.
 */
public interface CounterpartyDerivationDao extends JpaRepository<CounterpartyCharacteristics, Long> {

    /**
     * The parent counterparty's own id and name, in ONE round trip.
     *
     * <p>The obvious reading is two queries — find the parent's rmpmid, then look that up for its
     * name. The self-join gets both at once, which matters because this runs on every traversal,
     * and a traversal runs on every answer.
     *
     * <p>Returns empty when the counterparty has no parent, or when the parent's own row is
     * missing. Both mean the same thing to the form: nothing to fill Q-S05 with.
     */
    @Query(value = """
            select parent.rmpmid    as rmpmid,
                   parent.companyname as companyName
            from counterparty_characteristics cc
            join counterparty_characteristics parent on parent.rmpmid = cc.parent_rmpm_id
            where cc.rmpmid = :rmpmid
            """, nativeQuery = true)
    Optional<ParentCounterparty> findParentOf(@Param("rmpmid") String rmpmid);

    /** Spring Data interface projection — no entity, no mapping annotations. */
    interface ParentCounterparty {
        String getRmpmid();
        String getCompanyName();
    }
}
