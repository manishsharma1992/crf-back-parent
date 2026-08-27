package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.FormState;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception.StrandedTraversalException;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service.AnalysisCompletenessDomainService;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisStatus;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.CompletenessBlocker;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.CompletenessInput;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.FormCompleteness;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageAnalysis;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.TraversalState;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.fixture.FormStateFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Uses the REAL domain rule and mocks only the form-state read. Stubbing the rule
 * would leave the interesting part - which forms get gathered, and what happens
 * when one of them blows up - asserted against nothing.
 */
@ExtendWith(MockitoExtension.class)
class AnalysisCompletenessServiceTest {

    private static final String UID = "LA-0001";

    @Mock private LeverageAnalysisRepository analyses;
    @Mock private GetLeverageFormStateUseCase formState;

    private AnalysisCompletenessService service;
    private LeverageAnalysis analysis;

    @BeforeEach
    void setUp() {
        service = new AnalysisCompletenessService(analyses, formState,
                new AnalysisCompletenessDomainService());
        analysis = LeverageAnalysisTestBuilder.draft(UID);
    }

    private void stub(LeverageFormType formType, FormState state) {
        when(formState.get(UID, formType.name(), null)).thenReturn(state);
    }

    private void stubStranded(LeverageFormType formType) {
        when(formState.get(UID, formType.name(), null))
                .thenThrow(new StrandedTraversalException(formType, 1));
    }

    /**
     * The preliminary outcome decides which downstream forms are required - not
     * the definition-id columns, which only record that a form was opened.
     */
    @Test
    void requiresBothDownstreamFormsWhenTheOutcomeAsksForBoth() {
        stub(LeverageFormType.PRELIMINARY, FormStateFixture.completed(LeverageFormType.PRELIMINARY,
                FormStateFixture.outcomeShowing(LeverageFormType.ECB, LeverageFormType.FED)));
        stub(LeverageFormType.ECB, FormStateFixture.completed(LeverageFormType.ECB, null));
        stub(LeverageFormType.FED, FormStateFixture.completed(LeverageFormType.FED, null));

        assertThat(service.evaluate(analysis).canValidate()).isTrue();
        verify(formState).get(UID, LeverageFormType.FED.name(), null);
    }

    @Test
    void blocksWhenOneOfTwoDownstreamFormsIsUnfinished() {
        stub(LeverageFormType.PRELIMINARY, FormStateFixture.completed(LeverageFormType.PRELIMINARY,
                FormStateFixture.outcomeShowing(LeverageFormType.ECB, LeverageFormType.FED)));
        stub(LeverageFormType.ECB, FormStateFixture.completed(LeverageFormType.ECB, null));
        stub(LeverageFormType.FED, FormStateFixture.inProgress(LeverageFormType.FED));

        FormCompleteness result = service.evaluate(analysis);

        assertThat(result.blocker()).isEqualTo(CompletenessBlocker.FORM_INCOMPLETE);
        assertThat(result.blockingForm()).isEqualTo(LeverageFormType.FED);
    }

    /**
     * An unfinished preliminary has no outcome, so there is nothing downstream to
     * ask for yet. Reading ECB anyway would be wasted work and could throw.
     */
    @Test
    void stopsAtThePreliminaryFormWhenItHasNoOutcomeYet() {
        stub(LeverageFormType.PRELIMINARY, FormStateFixture.inProgress(LeverageFormType.PRELIMINARY));

        FormCompleteness result = service.evaluate(analysis);

        assertThat(result.blockingForm()).isEqualTo(LeverageFormType.PRELIMINARY);
        verify(formState).get(UID, LeverageFormType.PRELIMINARY.name(), null);
        verify(formState, org.mockito.Mockito.never()).get(UID, LeverageFormType.ECB.name(), null);
    }

    /**
     * FormStateAssembler throws on a stranded walk. Left uncaught it would take the
     * availability endpoint down with a 500 - and would do so even when the broken
     * form is not the one the analyst is looking at.
     */
    @Test
    void turnsAStrandedDownstreamDefinitionIntoABlockerRatherThanAFailure() {
        stub(LeverageFormType.PRELIMINARY, FormStateFixture.completed(LeverageFormType.PRELIMINARY,
                FormStateFixture.outcomeShowing(LeverageFormType.ECB)));
        stubStranded(LeverageFormType.ECB);

        FormCompleteness result = service.evaluate(analysis);

        assertThat(result.blocker()).isEqualTo(CompletenessBlocker.DEFINITION_STRANDED);
        assertThat(result.blockingForm()).isEqualTo(LeverageFormType.ECB);
    }

    @Test
    void turnsAStrandedPreliminaryDefinitionIntoABlockerToo() {
        stubStranded(LeverageFormType.PRELIMINARY);

        FormCompleteness result = service.evaluate(analysis);

        assertThat(result.blocker()).isEqualTo(CompletenessBlocker.DEFINITION_STRANDED);
        assertThat(result.blockingForm()).isEqualTo(LeverageFormType.PRELIMINARY);
    }

    /**
     * Per the BA, only ERROR blocks. A warning left in the list would grey out the
     * button on a form Sushmitha considers finished.
     */
    @Test
    void ignoresWarningsAndCountsOnlyErrors() {
        stub(LeverageFormType.PRELIMINARY, FormStateFixture.completed(LeverageFormType.PRELIMINARY,
                FormStateFixture.outcomeShowing(LeverageFormType.ECB)));
        stub(LeverageFormType.ECB, FormStateFixture.completed(LeverageFormType.ECB, null,
                FormStateFixture.warning("ECB_ADJUSTED_EBITDA_ZERO")));

        assertThat(service.evaluate(analysis).canValidate()).isTrue();
    }

    @Test
    void blocksOnAnErrorAndReportsItsMessageKey() {
        stub(LeverageFormType.PRELIMINARY, FormStateFixture.completed(LeverageFormType.PRELIMINARY,
                FormStateFixture.outcomeShowing(LeverageFormType.ECB)));
        stub(LeverageFormType.ECB, FormStateFixture.completed(LeverageFormType.ECB, null,
                FormStateFixture.warning("ADVISORY"),
                FormStateFixture.error("JUSTIFICATION_REQUIRED")));

        FormCompleteness result = service.evaluate(analysis);

        assertThat(result.blocker()).isEqualTo(CompletenessBlocker.BLOCKING_ERRORS);
        assertThat(result.blockingMessageCodes()).containsExactly("JUSTIFICATION_REQUIRED");
    }

    @Test
    void refusesANonDraftAnalysisBeforeLookingAtAnyForm() {
        LeverageAnalysis validated = LeverageAnalysisTestBuilder.validated(UID);
        stub(LeverageFormType.PRELIMINARY, FormStateFixture.completed(LeverageFormType.PRELIMINARY,
                FormStateFixture.outcomeShowing()));

        assertThat(service.evaluate(validated).blocker()).isEqualTo(CompletenessBlocker.NOT_IN_DRAFT);
    }
}
