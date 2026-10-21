package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.TraversalResult;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeDefinition;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.Question;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The rule the workbook states for {@code Prefill From}, enforced on the answer map: <i>if the
 * source has a value, the field is filled and locked; otherwise the analyst answers it here.</i>
 *
 * <p>So while FED holds a Q-S06, any Q-S06 of ECB's own — posted, or replayed from a snapshot —
 * is dropped before the walk, and the copy is the only value left to win. Without this, an ECB
 * answer recorded as TYPED (a stale copy the screen posted back in the moment FED's Q-S06 was
 * empty) shadowed every later copy: Clara corrected FED's counterparty, and ECB kept the wrong one
 * however often she saved (21/10).
 *
 * <p>When the source is empty, nothing is dropped: the question is the analyst's again, and what
 * they typed here is the answer.
 *
 * <p>Applied on BOTH the read and the save path, which also repairs analyses already carrying the
 * stale TYPED value: the replayed answer is dropped on the next read, and the next save freezes
 * the copy as PREFILLED. No data migration is needed.
 *
 * <p>Generic on purpose — driven by {@link Question#prefillFrom()}, so ECB's Q01 is covered by the
 * same line, and a new prefilled question needs no change here.
 */
public final class CopiedAnswers {

    private CopiedAnswers() {
    }

    /**
     * @param definition the form being walked
     * @param own        this form's answers, posted or replayed
     * @param crossForm  sibling forms' stored answers, keyed as authored ({@code FED/Q-S06})
     * @return {@code own} without the answers a present copy supersedes
     */
    public static Map<String, String> withoutSupersededAnswers(DecisionTreeDefinition definition,
                                                        Map<String, String> own,
                                                        Map<String, String> crossForm) {
        Map<String, String> kept = new LinkedHashMap<>(own);
        for (Question question : definition.questions()) {
            if (hasCopy(question.prefillFrom(), crossForm)) {
                kept.remove(question.key());
            }
        }
        return Collections.unmodifiableMap(kept);
    }

    /**
     * The answer a question ended up with after the walk: computed, else copied, else this form's
     * own — the same precedence {@code QuestionView.from} shows on screen, so a check reads the
     * value the analyst is looking at.
     */
    public static String walkedAnswer(String questionKey, Map<String, String> own, TraversalResult result) {
        String computed = result.computedAnswers().get(questionKey);
        if (computed != null) {
            return computed;
        }
        String copied = result.prefilledAnswers().get(questionKey);
        return copied != null ? copied : own.get(questionKey);
    }

    private static boolean hasCopy(String source, Map<String, String> crossForm) {
        if (source == null || source.isBlank()) {
            return false;
        }
        String copied = crossForm.get(source);
        return copied != null && !copied.isBlank();
    }
}
