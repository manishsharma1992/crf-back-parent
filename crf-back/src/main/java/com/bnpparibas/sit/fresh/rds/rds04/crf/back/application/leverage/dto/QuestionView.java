package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LocalizedQuestionLabel;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A question as the UI renders it: the authored content plus the analyst's answer and whether it
 * is the one awaiting input.
 *
 * <p>The authored parts are the domain records themselves — they are already the JSON shape, so
 * there is nothing to re-map.
 *
 * <p>Changed with the model: {@code order} is gone (screen order comes from the routing, so the
 * list order here IS the order), {@code external} is gone (nothing is computed elsewhere), and
 * {@code subtitle} / {@code note} are now {@link LocalizedQuestionLabel} so a note can carry
 * nested bullets — that is how the Support-Entity tooltip reaches the screen.
 *
 * @param answer      single-value answer, or the value the SYSTEM derived for a computed question
 * @param derived     true when {@code answer} was computed rather than typed — the UI shows it
 *                    read-only and does not post it back
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
                             boolean current) {

        String derivedValue = computedAnswers.get(question.key());
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

    /** Dotted keys under this question: checklist items and data-entry boxes alike. */
    private static Map<String, String> subAnswers(String questionKey, Map<String, String> answers) {
        String prefix = questionKey + '.';
        Map<String, String> sub = new LinkedHashMap<>();
        answers.forEach((key, value) -> {
            if (key.startsWith(prefix)) {
                sub.put(key.substring(prefix.length()), value);
            }
        });
        return Map.copyOf(sub);
    }
}
