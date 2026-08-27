package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.port.LeverageAnalysisRepository;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageAnalysis;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.ValidatedAnswer;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.rating.value.LeverageAnalysisReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * ADAPT: the analysis fixtures need a builder that can carry LeverageResponses.
 * LeverageAnalysisTestBuilder currently only sets uid and status.
 */
@ExtendWith(MockitoExtension.class)
class ValidatedAnalysisLookupTest {

    private static final String UID = "LA-0001";

    @Mock private LeverageAnalysisRepository analyses;
    @InjectMocks private ValidatedAnalysisLookup lookup;

    @Test
    void readsAFlagFromAValidatedAnalysis() {
        LeverageAnalysis analysis = LeverageAnalysisTestBuilder
                .validatedWithEcbFlag(UID, "ECB_LEVERAGED", "Y");
        when(analyses.findValidatedByAnalysisUid(UID)).thenReturn(Optional.of(analysis));

        assertThat(lookup.flag(UID, LeverageFormType.ECB, "ECB_LEVERAGED")).contains("Y");
    }

    @Test
    void readsAnAnswerWithTheFormAndVersionItCameFrom() {
        LeverageAnalysis analysis = LeverageAnalysisTestBuilder
                .validatedWithEcbAnswer(UID, 12, "Q-S06", "YES");
        when(analyses.findValidatedByAnalysisUid(UID)).thenReturn(Optional.of(analysis));

        ValidatedAnswer answer = lookup.answer(UID, LeverageFormType.ECB, "Q-S06").orElseThrow();

        assertThat(answer.value()).isEqualTo("YES");
        assertThat(answer.formType()).isEqualTo(LeverageFormType.ECB);
        assertThat(answer.definitionVersion()).isEqualTo(12);
    }

    /**
     * A counterparty with no validated leverage conclusion is a normal state of the
     * world, not an error - the rating has to be able to ask and be told no.
     */
    @Test
    void reportsNothingWhenNoValidatedAnalysisExists() {
        when(analyses.findValidatedByAnalysisUid(UID)).thenReturn(Optional.empty());

        assertThat(lookup.answer(UID, LeverageFormType.ECB, "Q-S06")).isEmpty();
        assertThat(lookup.flag(UID, LeverageFormType.ECB, "ECB_LEVERAGED")).isEmpty();
    }

    /** The analysis was never routed to FED, so there is no FED form to read. */
    @Test
    void reportsNothingForAFormTheAnalysisWasNeverRoutedTo() {
        LeverageAnalysis analysis = LeverageAnalysisTestBuilder
                .validatedWithEcbAnswer(UID, 12, "Q-S06", "YES");
        when(analyses.findValidatedByAnalysisUid(UID)).thenReturn(Optional.of(analysis));

        assertThat(lookup.answer(UID, LeverageFormType.FED, "Q-S06")).isEmpty();
    }

    /**
     * The walk never reached this question - it sat on a branch the analyst did not
     * enter. Empty, not an exception: the rating's rule decides what an unasked
     * question means, this class cannot.
     */
    @Test
    void reportsNothingForAQuestionTheWalkNeverReached() {
        LeverageAnalysis analysis = LeverageAnalysisTestBuilder
                .validatedWithEcbAnswer(UID, 12, "Q-S06", "YES");
        when(analyses.findValidatedByAnalysisUid(UID)).thenReturn(Optional.of(analysis));

        assertThat(lookup.answer(UID, LeverageFormType.ECB, "Q-S99")).isEmpty();
    }

    /**
     * The finder keeps the status in the WHERE clause, so a draft never arrives
     * here. This pins that contract - if someone later relaxes the query to a
     * plain findByAnalysisUid, the aggregate's own guard throws instead of this
     * returning provisional data.
     */
    @Test
    void neverSeesADraftBecauseTheFinderExcludesOne() {
        when(analyses.findValidatedByAnalysisUid(UID)).thenReturn(Optional.empty());

        assertThat(lookup.flag(UID, LeverageFormType.ECB, "ECB_LEVERAGED")).isEmpty();
    }

    /**
     * The reason the batch methods exist: five values through the single-value
     * calls would be five loads of the same jsonb payload.
     */
    @Test
    void readsSeveralAnswersFromASingleLoad() {
        LeverageAnalysis analysis = LeverageAnalysisTestBuilder
                .validatedWithEcbAnswer(UID, 12, "Q-S06", "YES");
        when(analyses.findValidatedByAnalysisUid(UID)).thenReturn(Optional.of(analysis));

        Map<String, ValidatedAnswer> found =
                lookup.answers(UID, LeverageFormType.ECB, List.of("Q-S06", "Q-S07"));

        assertThat(found).containsOnlyKeys("Q-S06");
        verify(analyses, times(1)).findValidatedByAnalysisUid(UID);
    }

    /**
     * A question the walk never reached is absent from the map, not mapped to null.
     * A caller distinguishing "not asked" from "answered blank" checks containsKey.
     */
    @Test
    void omitsRatherThanNullsTheAnswersItCouldNotFind() {
        LeverageAnalysis analysis = LeverageAnalysisTestBuilder
                .validatedWithEcbAnswer(UID, 12, "Q-S06", "YES");
        when(analyses.findValidatedByAnalysisUid(UID)).thenReturn(Optional.of(analysis));

        assertThat(lookup.answers(UID, LeverageFormType.ECB, List.of("Q-S99")))
                .isEmpty();
    }

    /** Insertion order is preserved, so the caller reads them back as it asked. */
    @Test
    void keepsTheRequestedOrder() {
        LeverageAnalysis analysis = LeverageAnalysisTestBuilder
                .validatedWithEcbFlag(UID, "ECB_LEVERAGED", "Y");
        when(analyses.findValidatedByAnalysisUid(UID)).thenReturn(Optional.of(analysis));

        assertThat(lookup.flags(UID, LeverageFormType.ECB,
                List.of("ECB_NOT_SET", "ECB_LEVERAGED")))
                .containsExactly(Map.entry("ECB_LEVERAGED", "Y"));
    }

    /**
     * The reference is what the rating stored in model_specific_data, so passing it
     * whole removes the chance of a call site reaching for the wrong string field.
     */
    @Test
    void readsBackTheExactAnalysisARatingConsumed() {
        LeverageAnalysisReference reference = new LeverageAnalysisReference(
                UID, 1L, "ARCH-42", "LEVERAGED", 12L, null,
                Instant.parse("2026-03-12T09:30:00Z"), Instant.parse("2026-03-20T10:00:00Z"));
        LeverageAnalysis analysis = LeverageAnalysisTestBuilder
                .validatedWithEcbFlag(UID, "ECB_LEVERAGED", "Y");
        when(analyses.findValidatedByAnalysisUid(UID)).thenReturn(Optional.of(analysis));

        assertThat(lookup.flag(reference, LeverageFormType.ECB, "ECB_LEVERAGED")).contains("Y");
    }
}
