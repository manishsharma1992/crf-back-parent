package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception.AnalysisNotFoundException;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception.AnalysisNotValidatableException;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception.ConcurrentValidationException;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.port.AnalysisStatusRepository;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisStatus;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisStatusChange;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.FormCompleteness;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageAnalysis;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * BR02. The interesting assertions here are the negative ones - what must NOT
 * happen when the compare-and-set loses.
 */
@ExtendWith(MockitoExtension.class)
class ValidateLeverageAnalysisUseCaseTest {

    private static final String UID = "LA-0001";
    private static final String USER = "manish";
    private static final Instant NOW = Instant.parse("2026-03-12T09:30:00Z");

    @Mock private LeverageAnalysisRepository analyses;
    @Mock private AnalysisStatusRepository statusRepository;
    @Mock private AnalysisCompletenessService completenessService;

    private ValidateLeverageAnalysisUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new ValidateLeverageAnalysisUseCase(analyses, statusRepository,
                completenessService, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private LeverageAnalysis draftAnalysis() {
        // ADAPT: build through whatever factory or builder the aggregate exposes.
        return LeverageAnalysisTestBuilder.draft(UID);
    }

    @Test
    void validatesADraftAnalysisAndRecordsTheTransition() {
        LeverageAnalysis analysis = draftAnalysis();
        when(analyses.findByAnalysisUid(UID)).thenReturn(Optional.of(analysis));
        when(completenessService.evaluate(analysis)).thenReturn(FormCompleteness.complete());
        when(statusRepository.compareAndSetStatus(UID, AnalysisStatus.DRAFT,
                AnalysisStatus.VALIDATED, USER, NOW)).thenReturn(true);

        AnalysisStatusChange change = useCase.validate(UID, USER);

        assertThat(change.fromStatus()).isEqualTo(AnalysisStatus.DRAFT);
        assertThat(change.toStatus()).isEqualTo(AnalysisStatus.VALIDATED);
        assertThat(change.changedBy()).isEqualTo(USER);
        assertThat(change.changedTimestamp()).isEqualTo(NOW);

        ArgumentCaptor<AnalysisStatusChange> appended =
                ArgumentCaptor.forClass(AnalysisStatusChange.class);
        verify(statusRepository).appendHistory(appended.capture());
        assertThat(appended.getValue().analysisUid()).isEqualTo(UID);
    }

    /**
     * The aggregate is mutated in memory but deliberately never saved - the
     * compare-and-set is the write. If someone later "fixes" this by adding a
     * save, the flush would issue an unconditional status update and quietly
     * defeat the concurrency guard. This test is the tripwire for that.
     */
    @Test
    void neverSavesTheAggregate() {
        LeverageAnalysis analysis = draftAnalysis();
        when(analyses.findByAnalysisUid(UID)).thenReturn(Optional.of(analysis));
        when(completenessService.evaluate(analysis)).thenReturn(FormCompleteness.complete());
        when(statusRepository.compareAndSetStatus(any(), any(), any(), any(), any())).thenReturn(true);

        useCase.validate(UID, USER);

        verify(analyses, never()).save(any());
    }

    /**
     * The loser of a race must leave nothing behind. A history row for a
     * transition that did not happen is worse than no row at all.
     */
    @Test
    void failsAndAppendsNoHistoryWhenAnotherRequestWonTheRace() {
        LeverageAnalysis analysis = draftAnalysis();
        when(analyses.findByAnalysisUid(UID)).thenReturn(Optional.of(analysis));
        when(completenessService.evaluate(analysis)).thenReturn(FormCompleteness.complete());
        when(statusRepository.compareAndSetStatus(any(), any(), any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> useCase.validate(UID, USER))
                .isInstanceOf(ConcurrentValidationException.class)
                .hasMessageContaining(UID);

        verify(statusRepository, never()).appendHistory(any());
    }

    /**
     * The button is a hint, not the decision. A hand-crafted POST against an
     * incomplete analysis must be refused without touching the database.
     */
    @Test
    void refusesAnIncompleteAnalysisWithoutWriting() {
        LeverageAnalysis analysis = draftAnalysis();
        when(analyses.findByAnalysisUid(UID)).thenReturn(Optional.of(analysis));
        when(completenessService.evaluate(analysis)).thenReturn(
                FormCompleteness.blockedBy(
                        com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.CompletenessBlocker.FORM_INCOMPLETE,
                        LeverageFormType.ECB));

        assertThatThrownBy(() -> useCase.validate(UID, USER))
                .isInstanceOf(AnalysisNotValidatableException.class)
                .hasMessageContaining("ECB");

        verifyNoInteractions(statusRepository);
    }

    @Test
    void failsOnAnUnknownAnalysis() {
        when(analyses.findByAnalysisUid(UID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> useCase.validate(UID, USER))
                .isInstanceOf(AnalysisNotFoundException.class);

        verifyNoInteractions(statusRepository, completenessService);
    }

    /** A second click on an already-validated analysis is refused by the aggregate. */
    @Test
    void refusesAnAnalysisThatIsAlreadyValidated() {
        LeverageAnalysis analysis = LeverageAnalysisTestBuilder.validated(UID);
        when(analyses.findByAnalysisUid(UID)).thenReturn(Optional.of(analysis));
        when(completenessService.evaluate(analysis)).thenReturn(FormCompleteness.notInDraft());

        assertThatThrownBy(() -> useCase.validate(UID, USER))
                .isInstanceOf(com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception.AnalysisNotModifiableException.class);

        verify(statusRepository, never()).compareAndSetStatus(any(), any(), any(), any(), any());
    }
}
