package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * The fuzzy counterparty search behind {@code LOOKUP/COUNTERPARTY}.
 *
 * <p>Native because it leans on {@code ibm_extension.word_similarity} and the {@code <%} operator,
 * neither of which JPQL can express. The operator is what uses the trigram index; the score in the
 * select list only orders what the operator already narrowed, so both are needed.
 */
public interface CounterpartyLookupDao extends JpaRepository<CounterpartyCharacteristics, Long> {

    /**
     * Counterparties whose fuzzy text is similar to the search term, best match first.
     *
     * <p>Capped in SQL rather than in Java: an autocomplete shows a handful, and fetching ten
     * thousand rows to discard all but five would put the cost on the database anyway.
     *
     * <p>Scoped through financials so the search offers what is reachable from the analysed row
     * rather than the whole table.
     */
    @Query(value = """
            select cc.rmpmid      as value,
                   cc.companyname as label
            from financials f
            left join counterparty c on f.counterparty_id = c.id
            left join counterparty_characteristics cc on c.characteristics_id = cc.id
            where :searchTerm operator(ibm_extension.<%) cc.fuzzy
            order by ibm_extension.word_similarity(:searchTerm, cc.fuzzy) desc
            limit :maxResults
            """, nativeQuery = true)
    List<LookupRow> search(@Param("searchTerm") String searchTerm,
                           @Param("maxResults") int maxResults);

    /** Spring Data interface projection — no entity, no mapping annotations. */
    interface LookupRow {
        String getValue();
        String getLabel();
    }
}
