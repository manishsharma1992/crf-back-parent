package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.ItemAnswer;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Immutable {@link TraversalAnswers} for tests. Each {@code with…} returns a copy, so a base set
 * of answers can be shared between cases without one test leaking into the next.
 *
 * <p>Returns {@link Optional#empty()} for anything not set — never a blank string, never zero.
 * A fake that substituted a default would quietly break the rule the whole grammar rests on.
 */
public final class FakeAnswers implements TraversalAnswers {

    private final Map<String, String> answers;
    private final Map<String, BigDecimal> fields;
    private final Map<String, Map<String, ItemAnswer>> items;
    private final Map<String, String> crossForm;

    private FakeAnswers(Map<String, String> answers,
                        Map<String, BigDecimal> fields,
                        Map<String, Map<String, ItemAnswer>> items,
                        Map<String, String> crossForm) {
        this.answers = answers;
        this.fields = fields;
        this.items = items;
        this.crossForm = crossForm;
    }

    public static FakeAnswers empty() {
        return new FakeAnswers(Map.of(), Map.of(), Map.of(), Map.of());
    }

    public static FakeAnswers of(String questionKey, String value) {
        return empty().with(questionKey, value);
    }

    public FakeAnswers with(String questionKey, String value) {
        Map<String, String> copy = new LinkedHashMap<>(answers);
        copy.put(questionKey, value);
        return new FakeAnswers(copy, fields, items, crossForm);
    }

    public FakeAnswers withField(String fieldKey, String value) {
        Map<String, BigDecimal> copy = new LinkedHashMap<>(fields);
        copy.put(fieldKey, new BigDecimal(value));
        return new FakeAnswers(answers, copy, items, crossForm);
    }

    public FakeAnswers withItem(String questionKey, String itemKey, ItemAnswer answer) {
        Map<String, Map<String, ItemAnswer>> copy = new LinkedHashMap<>(items);
        Map<String, ItemAnswer> forQuestion =
                new LinkedHashMap<>(copy.getOrDefault(questionKey, Map.of()));
        forQuestion.put(itemKey, answer);
        copy.put(questionKey, forQuestion);
        return new FakeAnswers(answers, fields, copy, crossForm);
    }

    public FakeAnswers withCrossForm(String formAndQuestionKey, String value) {
        Map<String, String> copy = new LinkedHashMap<>(crossForm);
        copy.put(formAndQuestionKey, value);
        return new FakeAnswers(answers, fields, items, copy);
    }

    @Override
    public Optional<String> answerOf(String questionKey) {
        return Optional.ofNullable(answers.get(questionKey));
    }

    @Override
    public Optional<BigDecimal> fieldValue(String fieldKey) {
        return Optional.ofNullable(fields.get(fieldKey));
    }

    @Override
    public Map<String, ItemAnswer> itemAnswers(String questionKey) {
        return items.getOrDefault(questionKey, Map.of());
    }

    @Override
    public Optional<String> crossFormAnswer(String formAndQuestionKey) {
        return Optional.ofNullable(crossForm.get(formAndQuestionKey));
    }
}
