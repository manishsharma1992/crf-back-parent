package com.bnpparibas.sit.fresh.rds.rds04.crf.back.leverage.dto;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service.TraversalAnswers;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.ItemAnswer;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DataField;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeDefinition;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.Question;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.QuestionType;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Adapts the flat {@code Map<String, String>} the UI posts to the {@link TraversalAnswers} the
 * engine reads.
 *
 * <p><b>The wire format does not change.</b> The UI has always sent {@code "Q01" -> "YES"} for a
 * plain answer and {@code "Q-B01A.sovereign" -> "NO"} for a sub-answer, and the preliminary form is
 * signed off against exactly that. The engine grew a richer view of answers — typed items, numeric
 * fields, cross-form lookups — so the translation belongs HERE, at the application boundary, rather
 * than in a changed contract.
 *
 * <p>The definition is needed because the same dotted key means different things by question type:
 * under a CHECKLIST it is an item answer, under a DATA_ENTRY a numeric box.
 *
 * <p><b>Blank is absent.</b> An empty or whitespace value is treated as unanswered, which matches
 * what a browser sends for a cleared input and preserves the engine's rule that an unanswered
 * question matches no condition.
 */
public final class FormAnswers implements TraversalAnswers {

    private final Map<String, String> raw;
    private final Map<String, String> crossForm;
    private final Map<String, Question> questionsByKey;
    private final Map<String, String> fieldOwners;

    private FormAnswers(Map<String, String> raw,
                        Map<String, String> crossForm,
                        Map<String, Question> questionsByKey,
                        Map<String, String> fieldOwners) {
        this.raw = raw;
        this.crossForm = crossForm;
        this.questionsByKey = questionsByKey;
        this.fieldOwners = fieldOwners;
    }

    public static FormAnswers of(DecisionTreeDefinition definition, Map<String, String> answers) {
        return of(definition, answers, Map.of());
    }

    /**
     * @param crossFormAnswers answers already given on ANOTHER form, keyed as authored
     *                         ({@code FED/Q01}). Empty for the preliminary form, which prefills
     *                         from nothing.
     */
    public static FormAnswers of(DecisionTreeDefinition definition,
                                 Map<String, String> answers,
                                 Map<String, String> crossFormAnswers) {
        Map<String, Question> byKey = new LinkedHashMap<>();
        Map<String, String> owners = new LinkedHashMap<>();
        for (Question question : definition.questions()) {
            byKey.put(question.key(), question);
            for (DataField field : question.fields()) {
                owners.put(field.key(), question.key());
            }
        }
        return new FormAnswers(
                answers == null ? Map.of() : Map.copyOf(answers),
                crossFormAnswers == null ? Map.of() : Map.copyOf(crossFormAnswers),
                byKey, owners);
    }

    /** The map exactly as posted — what the snapshot and the projection still work from. */
    public Map<String, String> raw() {
        return raw;
    }

    @Override
    public Optional<String> answerOf(String questionKey) {
        return value(questionKey);
    }

    @Override
    public Optional<BigDecimal> fieldValue(String fieldKey) {
        String owner = fieldOwners.get(fieldKey);
        if (owner == null) {
            return Optional.empty();
        }
        return value(owner + '.' + fieldKey).flatMap(FormAnswers::toNumber);
    }

    @Override
    public Map<String, ItemAnswer> itemAnswers(String questionKey) {
        Question question = questionsByKey.get(questionKey);
        if (question == null || question.type() != QuestionType.CHECKLIST) {
            return Map.of();
        }
        Map<String, ItemAnswer> items = new LinkedHashMap<>();
        String prefix = questionKey + '.';
        raw.forEach((key, value) -> collectItem(items, prefix, key, value));
        return Map.copyOf(items);
    }

    private void collectItem(Map<String, ItemAnswer> items, String prefix, String key, String value) {
        if (!key.startsWith(prefix) || isBlank(value)) {
            return;
        }
        toItemAnswer(value).ifPresent(answer -> items.put(key.substring(prefix.length()), answer));
    }

    @Override
    public Optional<String> crossFormAnswer(String formAndQuestionKey) {
        return Optional.ofNullable(crossForm.get(formAndQuestionKey)).filter(v -> !isBlank(v));
    }

    private Optional<String> value(String key) {
        String value = raw.get(key);
        return isBlank(value) ? Optional.empty() : Optional.of(value.trim());
    }

    /**
     * Accepts the enum names the engine uses and the {@code YES}/{@code NO} the UI has always
     * sent. An unrecognised token is treated as unanswered rather than guessed at.
     */
    private static Optional<ItemAnswer> toItemAnswer(String raw) {
        String token = raw.trim().toUpperCase().replace(' ', '_');
        for (ItemAnswer candidate : ItemAnswer.values()) {
            if (candidate.name().equals(token)) {
                return Optional.of(candidate);
            }
        }
        return Optional.empty();
    }

    private static Optional<BigDecimal> toNumber(String raw) {
        try {
            return Optional.of(new BigDecimal(raw));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
