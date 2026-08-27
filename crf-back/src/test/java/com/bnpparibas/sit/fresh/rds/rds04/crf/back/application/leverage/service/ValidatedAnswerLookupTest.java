package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception.AnalysisNotValidatedException;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageAnalysis;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import org.junit.jupiter.api.Test;

/**
 * The status guard lives on the aggregate, so it is tested there. A future caller
 * that finds a draft by some other route still cannot read it downstream.
 */
class ValidatedAnswerLookupTest {

    @Test
    void refusesToReadAnswersFromADraft() {
        LeverageAnalysis draft = LeverageAnalysisTestBuilder
                .draftWithEcbAnswer("LA-0001", 12, "Q-S06", "YES");

        assertThatThrownBy(() -> draft.validatedAnswerTo(LeverageFormType.ECB, "Q-S06"))
                .isInstanceOf(AnalysisNotValidatedException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    void refusesToReadFlagsFromADraft() {
        LeverageAnalysis draft = LeverageAnalysisTestBuilder
                .draftWithEcbAnswer("LA-0001", 12, "Q-S06", "YES");

        assertThatThrownBy(() -> draft.validatedFlag(LeverageFormType.ECB, "ECB_LEVERAGED"))
                .isInstanceOf(AnalysisNotValidatedException.class);
    }

    @Test
    void readsAnswersOnceValidated() {
        LeverageAnalysis validated = LeverageAnalysisTestBuilder
                .validatedWithEcbAnswer("LA-0001", 12, "Q-S06", "YES");

        assertThat(validated.validatedAnswerTo(LeverageFormType.ECB, "Q-S06"))
                .get()
                .extracting("value", "definitionVersion")
                .containsExactly("YES", 12);
    }
}
