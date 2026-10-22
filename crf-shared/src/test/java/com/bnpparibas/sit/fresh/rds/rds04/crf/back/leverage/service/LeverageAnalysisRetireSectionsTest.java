package com.bnpparibas.sit.fresh.rds.rds04.crf.back.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.aggregate.LeverageAnalysis;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.FormResponses;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Option A (Clara, 22/10): the analysis holds only the forms its route requires.
 *
 * <p>Built through {@code start(...)} with nulls for the financials and the spreadsheet selection,
 * which this rule never reads. If {@code LeverageResponses.initial(null)} refuses a null selection,
 * pass whatever the other aggregate tests use.
 */
class LeverageAnalysisRetireSectionsTest {

    private final FormResponses fedSnapshot = mock(FormResponses.class);
    private final FormResponses ecbSnapshot = mock(FormResponses.class);
    private final LeverageDecisionTreeDefinition fedPin = mock(LeverageDecisionTreeDefinition.class);
    private final LeverageDecisionTreeDefinition ecbPin = mock(LeverageDecisionTreeDefinition.class);

    private LeverageAnalysis analysis;

    @BeforeEach
    void bothFormsAnswered() {
        analysis = LeverageAnalysis.start("CRF-1", null, null, mock(LeverageDecisionTreeDefinition.class));
        analysis.pinDecisionTree(LeverageFormType.FED, fedPin);
        analysis.pinDecisionTree(LeverageFormType.ECB, ecbPin);
        analysis.recordFed(fedSnapshot);
        analysis.recordEcb(ecbSnapshot);
    }

    @Test
    @DisplayName("FED + ECB -> ECB only: FED's answers and pin go, ECB's stay")
    void ecbOnlyRetiresFed() {
        analysis.retireSectionsNotIn(List.of(LeverageFormType.ECB));

        assertThat(analysis.responsesFor(LeverageFormType.FED)).isNull();
        assertThat(analysis.decisionTreeFor(LeverageFormType.FED)).isNull();
        assertThat(analysis.responsesFor(LeverageFormType.ECB)).isSameAs(ecbSnapshot);
        assertThat(analysis.decisionTreeFor(LeverageFormType.ECB)).isSameAs(ecbPin);
    }

    @Test
    @DisplayName("Not required: both routed forms go, the preliminary stays")
    void notRequiredRetiresBoth() {
        analysis.retireSectionsNotIn(List.of());

        assertThat(analysis.responsesFor(LeverageFormType.FED)).isNull();
        assertThat(analysis.responsesFor(LeverageFormType.ECB)).isNull();
        assertThat(analysis.decisionTreeFor(LeverageFormType.PRELIMINARY)).isNotNull();
    }

    @Test
    @DisplayName("Same route again: nothing is touched")
    void sameRouteKeepsEverything() {
        analysis.retireSectionsNotIn(List.of(LeverageFormType.FED, LeverageFormType.ECB));

        assertThat(analysis.responsesFor(LeverageFormType.FED)).isSameAs(fedSnapshot);
        assertThat(analysis.responsesFor(LeverageFormType.ECB)).isSameAs(ecbSnapshot);
    }

    @Test
    @DisplayName("FED back on the route after retirement: a fresh form, pinned on its next save")
    void returningFormRepins() {
        analysis.retireSectionsNotIn(List.of(LeverageFormType.ECB));
        LeverageDecisionTreeDefinition activeNow = mock(LeverageDecisionTreeDefinition.class);

        analysis.pinDecisionTree(LeverageFormType.FED, activeNow);

        assertThat(analysis.decisionTreeFor(LeverageFormType.FED)).isSameAs(activeNow);
    }

    @Test
    @DisplayName("Never on a validated analysis")
    void validatedIsRefused() {
        analysis.validate("clara", java.time.Instant.now(), mockCompleteness());

        assertThatThrownBy(() -> analysis.retireSectionsNotIn(List.of()))
                .isInstanceOf(com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception.AnalysisNotModifiableException.class);
        assertThat(analysis.responsesFor(LeverageFormType.FED)).isSameAs(fedSnapshot);
    }

    private static com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.validation.FormCompleteness mockCompleteness() {
        var completeness = mock(com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.validation.FormCompleteness.class);
        org.mockito.Mockito.when(completeness.canValidate()).thenReturn(true);
        return completeness;
    }
}
