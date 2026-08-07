package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Dispatch tests. The SQL is the DAO's business; what matters here is which sources reach a
 * handler, which are ignored, and that an unknown one leaves the form usable.
 */
class DerivedValueResolverImplTest {

    private final CounterpartyDerivationDao counterpartyDao = mock(CounterpartyDerivationDao.class);
    private final FinancialsDerivationDao financialsDao = mock(FinancialsDerivationDao.class);

    private final AnalysisSubject subject = new AnalysisSubject("12345678", 42L);

    private DerivedValueResolverImpl resolver() {
        DerivedValueResolverImpl resolver = new DerivedValueResolverImpl(counterpartyDao, financialsDao);
        resolver.registerAreas();
        return resolver;
    }

    private static CounterpartyDerivationDao.ParentCounterparty parent(String rmpmid, String name) {
        CounterpartyDerivationDao.ParentCounterparty row =
                mock(CounterpartyDerivationDao.ParentCounterparty.class);
        when(row.getRmpmid()).thenReturn(rmpmid);
        when(row.getCompanyName()).thenReturn(name);
        return row;
    }

    @Test
    void the_parent_is_rendered_as_id_and_name() {
        when(counterpartyDao.findParentOf("12345678"))
                .thenReturn(Optional.of(parent("87654321", "ACME HOLDING SA")));

        Map<String, String> resolved =
                resolver().resolve(Set.of("COUNTERPARTY/PARENT"), subject, "EN");

        assertEquals("87654321 - ACME HOLDING SA", resolved.get("COUNTERPARTY/PARENT"));
    }

    /** No parent is not an error — Q-S05 simply stays blank and the walk treats it as unanswered. */
    @Test
    void a_counterparty_with_no_parent_yields_nothing() {
        when(counterpartyDao.findParentOf(any())).thenReturn(Optional.empty());

        assertTrue(resolver().resolve(Set.of("COUNTERPARTY/PARENT"), subject, "EN").isEmpty());
    }

    /**
     * The case that decides between a map and a sealed hierarchy: a definition published against a
     * newer authoring template must not take the request down.
     */
    @Test
    void an_unknown_area_is_skipped_rather_than_raised() {
        assertDoesNotThrow(() ->
                assertTrue(resolver().resolve(Set.of("RMPM/SOMETHING_NEW"), subject, "EN").isEmpty()));
        verifyNoInteractions(counterpartyDao, financialsDao);
    }

    @Test
    void an_unknown_attribute_in_a_known_area_is_skipped() {
        assertTrue(resolver().resolve(Set.of("COUNTERPARTY/GRANDPARENT"), subject, "EN").isEmpty());
        verifyNoInteractions(counterpartyDao);
    }

    @Test
    void a_source_with_no_separator_is_skipped() {
        assertTrue(resolver().resolve(Set.of("COUNTERPARTY"), subject, "EN").isEmpty());
    }

    @Test
    void the_area_is_matched_case_insensitively_and_trimmed() {
        when(counterpartyDao.findParentOf("12345678"))
                .thenReturn(Optional.of(parent("87654321", "ACME")));

        assertEquals(1, resolver().resolve(Set.of(" counterparty/PARENT "), subject, "EN").size());
    }

    /** Nothing to look up: no subject means no queries, not a null dereference. */
    @Test
    void a_missing_rmpmid_queries_nothing() {
        assertTrue(resolver().resolve(Set.of("COUNTERPARTY/PARENT"),
                new AnalysisSubject(null, 42L), "EN").isEmpty());
        verifyNoInteractions(counterpartyDao);
    }

    @Test
    void an_empty_source_set_short_circuits() {
        assertTrue(resolver().resolve(Set.of(), subject, "EN").isEmpty());
        verifyNoInteractions(counterpartyDao, financialsDao);
    }

    /** Several sources resolve independently; one failing does not lose the others. */
    @Test
    void sources_resolve_independently() {
        when(counterpartyDao.findParentOf("12345678"))
                .thenReturn(Optional.of(parent("87654321", "ACME")));

        Map<String, String> resolved = resolver().resolve(
                Set.of("COUNTERPARTY/PARENT", "RMPM/UNKNOWN"), subject, "EN");

        assertEquals(1, resolved.size());
        assertTrue(resolved.containsKey("COUNTERPARTY/PARENT"));
    }

    /** The result records a decision and must not be edited by its caller. */
    @Test
    void the_result_is_unmodifiable() {
        when(counterpartyDao.findParentOf(any())).thenReturn(Optional.empty());
        Map<String, String> resolved = resolver().resolve(Set.of("COUNTERPARTY/PARENT"), subject, "EN");

        assertThrows(UnsupportedOperationException.class, () -> resolved.put("X", "Y"));
    }
}
