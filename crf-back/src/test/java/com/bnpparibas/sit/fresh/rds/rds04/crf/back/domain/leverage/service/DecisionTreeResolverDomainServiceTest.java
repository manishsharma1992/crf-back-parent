package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.repository.LeverageDecisionTreeDefinitionRepository;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.DefinitionStatus;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeDefinition;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.exceptions.DefinitionNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.RecommendationOutcome.ECB;
import static com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.LeverageTreeFixtures.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The resolver's whole job is choosing WHICH definition a session walks, so these tests are about
 * two rules and nothing else: the clock is the only source of "now", and a definition that is not
 * PUBLISHED is not usable however it was found.
 *
 * <p>The second rule is the one that matters in practice. A DRAFT reaching an analyst would let an
 * unreviewed tree decide a regulatory classification, so it is refused at the point of resolution
 * rather than filtered somewhere downstream.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DecisionTreeResolverDomainServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-28T10:15:30Z");

    private LeverageDecisionTreeDefinitionRepository repository;
    private DecisionTreeResolverDomainService resolver;

    @BeforeEach
    void setUp() {
        repository = mock(LeverageDecisionTreeDefinitionRepository.class);
        resolver = new DecisionTreeResolverDomainService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private DecisionTreeDefinition published(int version) {
        return defWith(LeverageFormType.PRELIMINARY, version, DefinitionStatus.PUBLISHED, "Q1",
                sc("Q1", List.of(end(dflt(), ECB))));
    }

    private DecisionTreeDefinition draft(int version) {
        return defWith(LeverageFormType.PRELIMINARY, version, DefinitionStatus.DRAFT, "Q1",
                sc("Q1", List.of(end(dflt(), ECB))));
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ResolveActive {

        /** "Now" must come from the injected clock, or the resolution is not reproducible. */
        @Test
        void returns_the_published_definition_asking_at_the_clocks_instant() {
            DecisionTreeDefinition definition = published(7);
            when(repository.findActive(LeverageFormType.PRELIMINARY, NOW)).thenReturn(definition);

            assertSame(definition, resolver.resolveActive(LeverageFormType.PRELIMINARY));
            verify(repository).findActive(eq(LeverageFormType.PRELIMINARY), eq(NOW));
        }

        @Test
        void nothing_active_raises_definition_not_found() {
            when(repository.findActive(eq(LeverageFormType.PRELIMINARY), any())).thenReturn(null);
            assertThrows(DefinitionNotFoundException.class,
                    () -> resolver.resolveActive(LeverageFormType.PRELIMINARY));
        }

        /** A DRAFT must never reach an analyst, whatever the repository returned. */
        @Test
        void a_draft_raises_definition_not_found() {
            when(repository.findActive(eq(LeverageFormType.PRELIMINARY), any())).thenReturn(draft(3));
            assertThrows(DefinitionNotFoundException.class,
                    () -> resolver.resolveActive(LeverageFormType.PRELIMINARY));
        }

        @Test
        void each_form_is_resolved_independently() {
            when(repository.findActive(eq(LeverageFormType.ECB), any()))
                    .thenReturn(defWith(LeverageFormType.ECB, 2, DefinitionStatus.PUBLISHED, "Q1",
                            sc("Q1", List.of(end(dflt(), ECB)))));

            assertEquals(LeverageFormType.ECB, resolver.resolveActive(LeverageFormType.ECB).formType());
            verify(repository, never()).findActive(eq(LeverageFormType.PRELIMINARY), any());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ResolvePinned {

        /** The reason versions exist: an analysis started in March keeps walking March's rules. */
        @Test
        void returns_the_exact_version_asked_for() {
            DecisionTreeDefinition definition = published(4);
            when(repository.findByVersion(LeverageFormType.PRELIMINARY, 4)).thenReturn(definition);

            assertSame(definition, resolver.resolvePinned(LeverageFormType.PRELIMINARY, 4));
            verify(repository).findByVersion(LeverageFormType.PRELIMINARY, 4);
        }

        @Test
        void an_unknown_version_raises_definition_not_found() {
            when(repository.findByVersion(LeverageFormType.PRELIMINARY, 99)).thenReturn(null);
            assertThrows(DefinitionNotFoundException.class,
                    () -> resolver.resolvePinned(LeverageFormType.PRELIMINARY, 99));
        }

        @Test
        void a_pinned_draft_raises_definition_not_found() {
            when(repository.findByVersion(LeverageFormType.PRELIMINARY, 5)).thenReturn(draft(5));
            assertThrows(DefinitionNotFoundException.class,
                    () -> resolver.resolvePinned(LeverageFormType.PRELIMINARY, 5));
        }

        /** Pinning must not consult the clock at all — the version alone decides. */
        @Test
        void pinning_never_asks_what_is_active() {
            when(repository.findByVersion(LeverageFormType.PRELIMINARY, 4)).thenReturn(published(4));

            resolver.resolvePinned(LeverageFormType.PRELIMINARY, 4);

            verify(repository, never()).findActive(any(), any());
        }
    }
}
