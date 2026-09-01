package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.validation;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.Answer;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.AnswerProvenance;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.SubAnswer;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.List;
import java.util.Optional;

/**
 * One answer read back out of a validated analysis, with enough context that the
 * caller knows what it is holding.
 *
 * <p><b>Self-describing on purpose.</b> A bare String would let the rating record
 * "Q-S06 was YES" without recording which form or which workbook version said so.
 * A year later, when the tree has moved on, that String cannot be interpreted -
 * and for a regulatory input, an uninterpretable value is worse than none.
 *
 * <h2>{@code value} alone is not the answer</h2>
 *
 * <p>Three question shapes, three meanings, and only one of them is what a caller
 * naively expects:
 *
 * <ul>
 *   <li><b>Single-value</b> (BOOLEAN, SINGLE_CHOICE, NUMERIC, TEXT, LOOKUP,
 *       COMPUTED) - {@code value} is the answer and {@code subAnswers} is empty.</li>
 *   <li><b>CHECKLIST</b> - {@code value} is the AGGREGATE that routed
 *       ({@code ANY_YES} / {@code ALL_NO}), not any item's state. The items are in
 *       {@code subAnswers}. A caller wanting "did they tick the LBO box?" must
 *       look there; reading {@code value} answers a different question and answers
 *       it plausibly, which is why this is worth spelling out.</li>
 *   <li><b>DATA_ENTRY</b> - {@code value} is NULL. Every figure is in
 *       {@code subAnswers}. A rating reading {@code value} here gets null with no
 *       signal that it asked the wrong way.</li>
 * </ul>
 *
 * <p>Hence {@link #isMultiPart()} and {@link #subAnswer(String)}: a caller that
 * checks the first will not be caught by the second.
 *
 * <p><b>{@code type} is a String, not the enum</b>, mirroring the decision on
 * {@link Answer} for the same reason: renaming a QuestionType constant must not
 * strand a snapshot written under the old name.
 *
 * @param formType          which form the answer was given on
 * @param definitionVersion the workbook version that defined the question
 * @param questionKey       the key as authored in that version
 * @param type              the question shape, frozen as text
 * @param value             see above - null for DATA_ENTRY, an aggregate for CHECKLIST
 * @param provenance        typed by the analyst, computed by the tree, or prefilled
 *                          from another form - the rating may well care which
 * @param subAnswers        checklist items or data-entry boxes, as the analyst left them
 */
@DomainDrivenDesign.ValueObject
public record ValidatedAnswer(LeverageFormType formType,
                              int definitionVersion,
                              String questionKey,
                              String type,
                              String value,
                              AnswerProvenance provenance,
                              List<SubAnswer> subAnswers) {

    public ValidatedAnswer {
        subAnswers = subAnswers == null ? List.of() : List.copyOf(subAnswers);
    }

    /**
     * True when the meaning lives in {@link #subAnswers()} rather than in
     * {@link #value()}. Worth checking before trusting value on any question whose
     * shape the caller did not pin down itself.
     */
    public boolean isMultiPart() {
        return !subAnswers.isEmpty();
    }

    /**
     * One checklist item or data-entry box.
     *
     * <p>ADAPT if SubAnswer's key accessor is not {@code key()} - Answer holds
     * checklist items and data-entry boxes in the same list, so it presumably has a
     * single accessor for both.
     *
     * <p>Empty is normal: an item the analyst never reached, or a box that was not
     * on screen for the branch they walked.
     */
    public Optional<SubAnswer> subAnswer(String subKey) {
        return subAnswers.stream()
                .filter(sub -> sub.subKey().equals(subKey))
                .findFirst();
    }

    /**
     * Guards the DATA_ENTRY trap explicitly: returns the single value only when
     * there IS one, so a caller cannot silently receive null from a multi-part
     * question and treat it as "unanswered".
     */
    public Optional<String> singleValue() {
        return isMultiPart() ? Optional.empty() : Optional.ofNullable(value);
    }
}
