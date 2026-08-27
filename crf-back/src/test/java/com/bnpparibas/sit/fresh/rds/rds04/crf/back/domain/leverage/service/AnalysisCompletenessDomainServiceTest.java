package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisStatus;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.CompletenessBlocker;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.CompletenessInput;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.FormCompleteness;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.FormCompletenessInput;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.TraversalState;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The rule is a pure function, so these are the cheapest tests in the module and
 * the ones worth reading first - every BR01 decision is settled here.
 */
class AnalysisCompletenessDomainServiceTest {

    private final AnalysisCompletenessDomainService rule = new AnalysisCompletenessDomainService();

    private static FormCompletenessInput form(LeverageFormType type, TraversalState state, String... codes) {
        return new FormCompletenessInput(type, state, List.of(codes));
    }

    private static FormCompletenessInput done(LeverageFormType type) {
        return form(type, TraversalState.TERMINAL);
    }

    @ParameterizedTest
    @EnumSource(value = AnalysisStatus.class, names = "DRAFT", mode = EnumSource.Mode.EXCLUDE)
    void refusesAnythingOtherThanDraft(AnalysisStatus status) {
        FormCompleteness result = rule.evaluate(status,
                new CompletenessInput(List.of(done(LeverageFormType.PRELIMINARY))));

        assertThat(result.canValidate()).isFalse();
        assertThat(result.blocker()).isEqualTo(CompletenessBlocker.NOT_IN_DRAFT);
        assertThat(result.blockingForm()).isNull();
    }

    @Test
    void allowsValidationWhenEveryRequiredFormIsTerminalAndClean() {
        FormCompleteness result = rule.evaluate(AnalysisStatus.DRAFT, new CompletenessInput(List.of(
                done(LeverageFormType.PRELIMINARY),
                done(LeverageFormType.ECB),
                done(LeverageFormType.FED))));

        assertThat(result.canValidate()).isTrue();
        assertThat(result.blocker()).isEqualTo(CompletenessBlocker.NONE);
        assertThat(result.blockingMessageCodes()).isEmpty();
    }

    @Test
    void blocksWhenAWalkHasNotFinished() {
        FormCompleteness result = rule.evaluate(AnalysisStatus.DRAFT, new CompletenessInput(List.of(
                done(LeverageFormType.PRELIMINARY),
                form(LeverageFormType.ECB, TraversalState.PENDING_INPUT))));

        assertThat(result.blocker()).isEqualTo(CompletenessBlocker.FORM_INCOMPLETE);
        assertThat(result.blockingForm()).isEqualTo(LeverageFormType.ECB);
    }

    /**
     * A stranded definition must not be reported as an incomplete form: the fix is
     * a re-import, and telling the analyst to fill something in would send them
     * looking for a field that does not exist.
     */
    @Test
    void distinguishesAStrandedDefinitionFromAnUnfinishedWalk() {
        FormCompleteness result = rule.evaluate(AnalysisStatus.DRAFT, new CompletenessInput(List.of(
                form(LeverageFormType.PRELIMINARY, TraversalState.STRANDED))));

        assertThat(result.blocker()).isEqualTo(CompletenessBlocker.DEFINITION_STRANDED);
        assertThat(result.blockingForm()).isEqualTo(LeverageFormType.PRELIMINARY);
    }

    @Test
    void blocksOnOutstandingErrorsAndCarriesTheirCodes() {
        FormCompleteness result = rule.evaluate(AnalysisStatus.DRAFT, new CompletenessInput(List.of(
                done(LeverageFormType.PRELIMINARY),
                form(LeverageFormType.ECB, TraversalState.TERMINAL,
                        "JUSTIFICATION_REQUIRED", "MUST_BE_POSITIVE"))));

        assertThat(result.blocker()).isEqualTo(CompletenessBlocker.BLOCKING_ERRORS);
        assertThat(result.blockingForm()).isEqualTo(LeverageFormType.ECB);
        assertThat(result.blockingMessageCodes())
                .containsExactly("JUSTIFICATION_REQUIRED", "MUST_BE_POSITIVE");
    }

    /**
     * Precedence within a form. An unfinished walk has not raised all its
     * violations yet, so leading with an error count would report a number that
     * changes as the analyst types.
     */
    @Test
    void reportsTheUnfinishedWalkRatherThanTheErrorsItHasRaisedSoFar() {
        FormCompleteness result = rule.evaluate(AnalysisStatus.DRAFT, new CompletenessInput(List.of(
                form(LeverageFormType.PRELIMINARY, TraversalState.PENDING_INPUT, "SOME_ERROR"))));

        assertThat(result.blocker()).isEqualTo(CompletenessBlocker.FORM_INCOMPLETE);
        assertThat(result.blockingMessageCodes()).isEmpty();
    }

    /**
     * Precedence across forms. An incomplete preliminary is what decides whether
     * ECB and FED are even asked for, so reporting the downstream form first would
     * bury the one thing worth fixing.
     */
    @Test
    void reportsTheEarliestFormWhenSeveralAreBlocked() {
        FormCompleteness result = rule.evaluate(AnalysisStatus.DRAFT, new CompletenessInput(List.of(
                form(LeverageFormType.PRELIMINARY, TraversalState.PENDING_INPUT),
                form(LeverageFormType.ECB, TraversalState.PENDING_INPUT),
                form(LeverageFormType.FED, TraversalState.STRANDED))));

        assertThat(result.blockingForm()).isEqualTo(LeverageFormType.PRELIMINARY);
    }

    /**
     * Guards against a vacuous pass. If the required-form list ever arrives empty
     * the loop finds nothing to complain about, so this pins the behaviour rather
     * than leaving it to be discovered.
     */
    @Test
    void treatsAnEmptyFormListAsValidatable() {
        FormCompleteness result = rule.evaluate(AnalysisStatus.DRAFT, new CompletenessInput(List.of()));

        assertThat(result.canValidate()).isTrue();
    }
}
