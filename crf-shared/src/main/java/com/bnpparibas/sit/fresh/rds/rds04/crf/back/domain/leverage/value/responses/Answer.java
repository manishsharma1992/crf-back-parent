package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LocalizedLabel;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LocalizedQuestionLabel;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.List;

/**
 * One question, frozen as the analyst saw and answered it.
 *
 * <p>Self-describing on purpose: the label and the display text are stored, not looked up. A
 * definition published next year may reword Q-T01 or drop an item entirely, and this record still
 * shows what was actually on screen when the decision was taken.
 *
 * <p><b>Changed for the ECB form.</b> Two additions, both forced by question types the preliminary
 * form does not use:
 * <ul>
 *   <li>{@code subAnswers} — a CHECKLIST's items and a DATA_ENTRY's boxes. Without it, ten of the
 *       twelve ECB questions could not be recorded at all.</li>
 *   <li>{@code provenance} replaces {@code boolean computed} — see {@link AnswerProvenance}.</li>
 * </ul>
 *
 * @param type  kept as a String, not the enum, deliberately: renaming a {@code QuestionType}
 *              constant must not strand a snapshot written under the old name
 * @param value single-value answer; for a CHECKLIST the aggregate that routed
 *              ({@code ANY_YES} / {@code ALL_NO}), for a DATA_ENTRY null — the values are in
 *              {@code subAnswers}
 */
@DomainDrivenDesign.ValueObject
public record Answer(String questionKey,
                     LocalizedQuestionLabel questionLabel,
                     String type,
                     String value,
                     LocalizedLabel valueLabel,
                     AnswerProvenance provenance,
                     List<SubAnswer> subAnswers) {

    public Answer {
        subAnswers = subAnswers == null ? List.of() : List.copyOf(subAnswers);
    }

    /** A question with one value: BOOLEAN, SINGLE_CHOICE, NUMERIC, TEXT, LOOKUP, COMPUTED. */
    public static Answer single(String questionKey, LocalizedQuestionLabel questionLabel, String type,
                                String value, LocalizedLabel valueLabel, AnswerProvenance provenance) {
        return new Answer(questionKey, questionLabel, type, value, valueLabel, provenance, List.of());
    }

    /** A CHECKLIST: the aggregate that routed, plus every item as the analyst left it. */
    public static Answer checklist(String questionKey, LocalizedQuestionLabel questionLabel,
                                   String aggregate, List<SubAnswer> items) {
        return new Answer(questionKey, questionLabel, "CHECKLIST", aggregate, null,
                AnswerProvenance.TYPED, items);
    }

    /** A DATA_ENTRY: no single value of its own, only its boxes. */
    public static Answer dataEntry(String questionKey, LocalizedQuestionLabel questionLabel,
                                   List<SubAnswer> fields) {
        return new Answer(questionKey, questionLabel, "DATA_ENTRY", null, null,
                AnswerProvenance.TYPED, fields);
    }

    public boolean isMultiPart() {
        return !subAnswers.isEmpty();
    }
}
