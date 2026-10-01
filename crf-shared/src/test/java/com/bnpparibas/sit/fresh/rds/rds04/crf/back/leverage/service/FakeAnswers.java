package com.bnpparibas.sit.fresh.rds.rds04.crf.back.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.ItemAnswer;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.TraversalAnswers;

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
 *
 * <p><b>Boxes are stored as RAW TEXT, not as BigDecimal.</b> They used to be parsed on the way in,
 * which meant this fake could not even hold {@code reitOriginationDate} — {@code new
 * BigDecimal("2024-06-30")} throws — so no unit test could express a DATE or a code in a financial
 * table, and the one defect that stalled every REIT analysis was unreachable from here. Parsing on
 * the way OUT is also what {@code FormAnswers} does, so the fake now behaves like the thing it
 * stands in for.
 */
public final class FakeAnswers implements TraversalAnswers {

    private final Map<String, String> answers;
    private final Map<String, String> fields;
    private final Map<String, Map<String, ItemAnswer>> items;
    private final Map<String, String> crossForm;
    private final Map<String, String> derived;

    private FakeAnswers(Map<String, String> answers,
                        Map<String, String> fields,
                        Map<String, Map<String, ItemAnswer>> items,
                        Map<String, String> crossForm,
                        Map<String, String> derived) {
        this.answers = answers;
        this.fields = fields;
        this.items = items;
        this.crossForm = crossForm;
        this.derived = derived;
    }

    public static FakeAnswers empty() {
        return new FakeAnswers(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
    }

    public static FakeAnswers of(String questionKey, String value) {
        return empty().with(questionKey, value);
    }

    public FakeAnswers with(String questionKey, String value) {
        Map<String, String> copy = new LinkedHashMap<>(answers);
        copy.put(questionKey, value);
        return new FakeAnswers(copy, fields, items, crossForm, derived);
    }

    /**
     * A box's contents, as the analyst typed them.
     *
     * <p>Any text: {@code "1000"}, {@code "2024-06-30"}, {@code "YES"}. What will not parse as a
     * figure is absent to {@link #fieldValue} and present to {@link #fieldText}, which is the
     * distinction the two accessors exist for.
     */
    public FakeAnswers withField(String fieldKey, String value) {
        Map<String, String> copy = new LinkedHashMap<>(fields);
        copy.put(fieldKey, value);
        return new FakeAnswers(answers, copy, items, crossForm, derived);
    }

    public FakeAnswers withItem(String questionKey, String itemKey, ItemAnswer answer) {
        Map<String, Map<String, ItemAnswer>> copy = new LinkedHashMap<>(items);
        Map<String, ItemAnswer> forQuestion =
                new LinkedHashMap<>(copy.getOrDefault(questionKey, Map.of()));
        forQuestion.put(itemKey, answer);
        copy.put(questionKey, forQuestion);
        return new FakeAnswers(answers, fields, copy, crossForm, derived);
    }

    public FakeAnswers withCrossForm(String formAndQuestionKey, String value) {
        Map<String, String> copy = new LinkedHashMap<>(crossForm);
        copy.put(formAndQuestionKey, value);
        return new FakeAnswers(answers, fields, items, copy, derived);
    }

    /**
     * A value reference data answered, keyed as authored: {@code COUNTERPARTY/PARENT},
     * {@code COUNTERPARTY/MOODYS_ISSUER_RATING}.
     *
     * <p>Set it to {@code "/"} to stand for a counterparty carrying no such rating — a value
     * rather than an omission, because omitting it leaves a COMPUTED question unanswered and halts
     * the walk with no message.
     */
    public FakeAnswers withDerived(String source, String value) {
        Map<String, String> copy = new LinkedHashMap<>(derived);
        copy.put(source, value);
        return new FakeAnswers(answers, fields, items, crossForm, copy);
    }

    @Override
    public Optional<String> answerOf(String questionKey) {
        return Optional.ofNullable(answers.get(questionKey));
    }

    /** What CONDITIONS read. A box holding something that is not a figure is absent here. */
    @Override
    public Optional<BigDecimal> fieldValue(String fieldKey) {
        return fieldText(fieldKey).flatMap(FakeAnswers::toNumber);
    }

    /** What COMPLETENESS reads. A box holding anything at all is present here. */
    @Override
    public Optional<String> fieldText(String fieldKey) {
        return blankToEmpty(fields.get(fieldKey));
    }

    @Override
    public Optional<String> derivedAnswer(String source) {
        return blankToEmpty(derived.get(source));
    }

    @Override
    public Map<String, ItemAnswer> itemAnswers(String questionKey) {
        return items.getOrDefault(questionKey, Map.of());
    }

    @Override
    public Optional<String> crossFormAnswer(String formAndQuestionKey) {
        return Optional.ofNullable(crossForm.get(formAndQuestionKey));
    }

    private static Optional<String> blankToEmpty(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    private static Optional<BigDecimal> toNumber(String raw) {
        try {
            return Optional.of(new BigDecimal(raw));
        } catch (NumberFormatException ex) {
            return Optional.empty();
        }
    }
}
