package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LocalizedQuestionLabel;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.*;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A question as the UI renders it: the authored content plus the analyst's answer and whether it
 * is the one awaiting input.
 *
 * <p>The authored parts are the domain records themselves — they are already the JSON shape, so
 * there is nothing to re-map. That is why {@link DataField#editable()} and
 * {@link DataField#visible()} reach the client for free: the financial table needs both to know
 * which of its eighteen boxes the analyst may type into and which to render at all.
 *
 * <p>Changed with the model: {@code order} is gone (screen order comes from the routing, so the
 * list order here IS the order), {@code external} is gone (nothing is computed elsewhere), and
 * {@code subtitle} / {@code note} are now {@link LocalizedQuestionLabel} so a note can carry
 * nested bullets — that is how the Support-Entity tooltip reaches the screen.
 *
 * @param answer      single-value answer: typed here, derived by the tree, or copied from another
 *                    form
 * @param derived     true when the analyst did not type it HERE — computed or prefilled alike. The
 *                    UI renders it read-only and does not post it back. Question-scoped: a
 *                    DATA_ENTRY question is not derived even though most of its boxes are, so the
 *                    per-box decision is {@code field.editable()}, never this flag.
 * @param prefillFrom set when the answer was copied from another form, e.g. {@code FED/Q01}
 */
public record QuestionView(
        String key,
        QuestionType type,
        boolean mandatory,
        boolean computed,
        boolean editable,
        String prefillFrom,
        String fillsFlag,
        LocalizedQuestionLabel label,
        LocalizedQuestionLabel subtitle,
        LocalizedQuestionLabel note,
        List<Option> options,
        List<ChecklistItem> items,
        List<DataField> fields,
        String answer,
        boolean derived,
        Map<String, String> subAnswers,
        boolean current) {

    static QuestionView from(Question question,
                             Map<String, String> answers,
                             Map<String, String> computedAnswers,
                             Map<String, String> prefilledAnswers,
                             boolean current) {

        // Computed wins over prefilled on the same key, and both win over the posted map — which
        // will not contain them, since the analyst never typed them here.
        String derivedValue = computedAnswers.get(question.key());
        if (derivedValue == null) {
            derivedValue = prefilledAnswers.get(question.key());
        }
        String answer = derivedValue != null ? derivedValue : answers.get(question.key());

        return new QuestionView(
                question.key(), question.type(),
                question.mandatory(), question.computed(), question.editable(),
                question.prefillFrom(), question.fillsFlag(),
                question.label(), question.subtitle(), question.note(),
                question.options(), question.items(), question.fields(),
                answer,
                derivedValue != null,
                subAnswers(question.key(), answers),
                current);
    }

    /**
     * Dotted keys under this question: checklist items, data-entry boxes, and the two justification
     * halves that hang off a box ({@code ebitda.wording}, {@code ebitda.comment}).
     *
     * <p>Insertion order is preserved. {@code Map.copyOf} was here and returned an unordered map,
     * which is the trap semantic 8 exists to name — harmless while the client rendered from
     * {@code fields} rather than from this map, but a debugging map whose keys shuffle between two
     * reads of the same analysis is worse than useless.
     */
    private static Map<String, String> subAnswers(String questionKey, Map<String, String> answers) {
        String prefix = questionKey + '.';
        Map<String, String> sub = new LinkedHashMap<>();
        answers.forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                sub.put(key.substring(prefix.length()), value);
            }
        });
        return Collections.unmodifiableMap(sub);
    }
}
