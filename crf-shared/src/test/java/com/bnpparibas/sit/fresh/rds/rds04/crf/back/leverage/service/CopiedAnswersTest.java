package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeDefinition;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.Question;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * "If the source has a value, the field is filled and locked" — the copy supersedes ECB's own.
 *
 * <p>Question and DecisionTreeDefinition are mocked rather than built: only {@code key} and
 * {@code prefillFrom} matter here, and the records carry a dozen other components.
 */
class CopiedAnswersTest {

    private static final String WRONG_BG = "11111111";
    private static final String RIGHT_BG = "22222222";

    private final DecisionTreeDefinition ecb = definition(
            question("Q01", "FED/Q01"),
            question("Q-S06", "FED/Q-S06"),
            question("Q-B01A", null));

    @Test
    @DisplayName("Clara 21/10: a stale TYPED Q-S06 gives way to FED's corrected one")
    void presentCopySupersedesOwnAnswer() {
        Map<String, String> kept = CopiedAnswers.withoutSupersededAnswers(ecb,
                Map.of("Q-S06", WRONG_BG, "Q-B01A", "ALL_NO"),
                Map.of("FED/Q-S06", RIGHT_BG));

        assertThat(kept).doesNotContainKey("Q-S06").containsEntry("Q-B01A", "ALL_NO");
    }

    @Test
    @DisplayName("FED has no Q-S06: what the analyst typed on ECB is the answer")
    void absentCopyKeepsOwnAnswer() {
        Map<String, String> kept = CopiedAnswers.withoutSupersededAnswers(ecb,
                Map.of("Q-S06", WRONG_BG),
                Map.of("FED/Q01", "NO"));

        assertThat(kept).containsEntry("Q-S06", WRONG_BG);
    }

    @Test
    @DisplayName("A blank copy is no copy")
    void blankCopyKeepsOwnAnswer() {
        Map<String, String> kept = CopiedAnswers.withoutSupersededAnswers(ecb,
                Map.of("Q-S06", WRONG_BG),
                Map.of("FED/Q-S06", "  "));

        assertThat(kept).containsEntry("Q-S06", WRONG_BG);
    }

    @Test
    @DisplayName("Generic: Q01 follows the same rule without being named in the code")
    void everyPrefilledQuestionIsCovered() {
        Map<String, String> kept = CopiedAnswers.withoutSupersededAnswers(ecb,
                Map.of("Q01", "NO"),
                Map.of("FED/Q01", "YES"));

        assertThat(kept).doesNotContainKey("Q01");
    }

    @Test
    @DisplayName("Dotted table keys are never touched")
    void subAnswersAreUntouched() {
        Map<String, String> kept = CopiedAnswers.withoutSupersededAnswers(ecb,
                Map.of("Q-F01.ebitda", "100"),
                Map.of("FED/Q-S06", RIGHT_BG));

        assertThat(kept).containsEntry("Q-F01.ebitda", "100");
    }

    private static DecisionTreeDefinition definition(Question... questions) {
        DecisionTreeDefinition definition = mock(DecisionTreeDefinition.class);
        when(definition.questions()).thenReturn(List.of(questions));
        return definition;
    }

    private static Question question(String key, String prefillFrom) {
        Question question = mock(Question.class);
        when(question.key()).thenReturn(key);
        when(question.prefillFrom()).thenReturn(prefillFrom);
        return question;
    }
}
