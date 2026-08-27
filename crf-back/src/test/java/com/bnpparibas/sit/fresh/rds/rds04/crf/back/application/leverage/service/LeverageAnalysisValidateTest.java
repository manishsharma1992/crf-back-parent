package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception.AnalysisNotModifiableException;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception.AnalysisNotValidatableException;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisStatus;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisStatusChange;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.FormCompleteness;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageAnalysis;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import org.junit.jupiter.api.Test;

/**
 * The invariant lives in the aggregate, so it is tested there rather than only
 * through the use case. A future caller that bypasses ValidateLeverageAnalysisUseCase
 * still cannot move a validated analysis.
 */
class LeverageAnalysisValidateTest {

    private static final Instant NOW = Instant.parse("2026-03-12T09:30:00Z");

    @Test
    void movesADraftToValidatedAndReportsTheTransition() {
        LeverageAnalysis analysis = LeverageAnalysisTestBuilder.draft("LA-0001");

        AnalysisStatusChange change = analysis.validate("manish", NOW, FormCompleteness.complete());

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.VALIDATED);
        assertThat(analysis.getValidatedBy()).isEqualTo("manish");
        assertThat(analysis.getValidatedTimestamp()).isEqualTo(NOW);
        assertThat(change.fromStatus()).isEqualTo(AnalysisStatus.DRAFT);
        assertThat(change.toStatus()).isEqualTo(AnalysisStatus.VALIDATED);
    }

    @Test
    void refusesToValidateAnIncompleteAnalysisAndLeavesItUntouched() {
        LeverageAnalysis analysis = LeverageAnalysisTestBuilder.draft("LA-0001");
        FormCompleteness blocked = FormCompleteness.blockingErrors(
                LeverageFormType.ECB, List.of("JUSTIFICATION_REQUIRED"));

        assertThatThrownBy(() -> analysis.validate("manish", NOW, blocked))
                .isInstanceOf(AnalysisNotValidatableException.class)
                .hasMessageContaining("ECB");

        assertThat(analysis.getStatus()).isEqualTo(AnalysisStatus.DRAFT);
        assertThat(analysis.getValidatedBy()).isNull();
    }

    /** BR02: once validated, an analysis can neither be edited nor returned to draft. */
    @Test
    void refusesASecondValidation() {
        LeverageAnalysis analysis = LeverageAnalysisTestBuilder.validated("LA-0001");

        assertThatThrownBy(() -> analysis.validate("manish", NOW, FormCompleteness.complete()))
                .isInstanceOf(AnalysisNotModifiableException.class);
    }

    @Test
    void allowsModificationOnlyWhileDraft() {
        assertThatCode(() -> LeverageAnalysisTestBuilder.draft("LA-0001").assertModifiable())
                .doesNotThrowAnyException();

        assertThatThrownBy(() -> LeverageAnalysisTestBuilder.validated("LA-0001").assertModifiable())
                .isInstanceOf(AnalysisNotModifiableException.class)
                .hasMessageContaining("VALIDATED");
    }

    @Test
    void becomesAvailableToRatingOnlyOnceValidated() {
        assertThat(LeverageAnalysisTestBuilder.draft("LA-0001").isAvailableForRating()).isFalse();
        assertThat(LeverageAnalysisTestBuilder.validated("LA-0001").isAvailableForRating()).isTrue();
    }
}
