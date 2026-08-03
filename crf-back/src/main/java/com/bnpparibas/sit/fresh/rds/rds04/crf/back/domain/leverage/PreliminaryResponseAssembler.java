package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.analysis;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.ItemAnswer;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LocalizedLabel;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.TraversalResult;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.*;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.*;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.*;

/**
 * Freezes the answered questions of one form into a self-describing {@link FormResponses}
 * snapshot.
 *
 * <p>Self-describing on purpose: labels and display text are STORED, not looked up. A definition
 * published next year may reword a question or drop an item, and this record still shows what was
 * on screen when the decision was taken.
 *
 * <p><b>Nothing here is preliminary-specific any more.</b> The class keeps its name so the
 * signed-off use case compiles untouched, but every branch below is driven by question TYPE, so
 * the ECB and FED save paths can use it as-is. Rename it {@code FormResponseAssembler} when ECB
 * lands — no logic changes with the name.
 *
 * <p><b>Only the path is frozen.</b> Questions on a road not taken are not part of the record.
 */
@DomainDrivenDesign.DomainService
public final class PreliminaryResponseAssembler {

    /**
     * @param answers the flat map exactly as posted, dotted keys and all — the snapshot records
     *                what the analyst submitted, so it reads the submission rather than a
     *                re-interpretation of it
     * @param panels  info panels ALREADY RESOLVED for display. They come in rather than being
     *                looked up here because they are RMPM data, and a domain service that reached
     *                for a counterparty row would stop being a pure function of its arguments.
     *                Empty for the preliminary form, which shows no panels.
     */
    public FormResponses assemble(DecisionTreeDefinition definition,
                                  Map<String, String> answers,
                                  TraversalResult result,
                                  String locale,
                                  List<PanelSnapshot> panels) {

        Map<String, Question> byKey = index(definition);
        List<Answer> frozen = new ArrayList<>();

        for (String key : result.path()) {
            Question question = byKey.get(key);
            if (question == null) {
                continue;
            }
            freeze(question, answers, result).ifPresent(frozen::add);
        }
        return new FormResponses(definition.version(), locale, frozen, result.flags(), panels);
    }

    private Optional<Answer> freeze(Question question, Map<String, String> answers, TraversalResult result) {
        return switch (question.type()) {
            case CHECKLIST -> checklist(question, answers);
            case DATA_ENTRY -> dataEntry(question, answers);
            default -> single(question, answers, result);
        };
    }

    // ------------------------------------------------------------------ single-value questions

    /**
     * A COMPUTED question is frozen too, with {@code COMPUTED} provenance. The old assembler
     * skipped these because the only one was the preliminary recommendation, which is also a typed
     * column — but on the ECB form Q-S04's level of calculation and Q-Q01/Q-Q02's leveraged
     * verdicts are computed AND are exactly what a regulator would ask about.
     */
    private Optional<Answer> single(Question question, Map<String, String> answers, TraversalResult result) {
        String computed = result.computedAnswers().get(question.key());
        String copied = result.prefilledAnswers().get(question.key());
        String typed = trimmed(answers.get(question.key()));

        String value = firstNonNull(computed, copied, typed);
        if (value == null) {
            return Optional.empty();   // unanswered questions are not part of the record
        }
        AnswerProvenance provenance = provenanceOf(computed != null, copied != null);
        return Optional.of(Answer.single(question.key(), question.label(), question.type().name(),
                value, optionLabel(question, value), provenance));
    }

    /**
     * A prefilled answer must NOT be recorded as COMPUTED. "The analyst answered this on the FED
     * form" and "the tree worked it out" are different facts, and the first is what gets asked
     * about when the two forms disagree.
     */
    private AnswerProvenance provenanceOf(boolean computed, boolean copied) {
        if (computed) {
            return AnswerProvenance.COMPUTED;
        }
        return copied ? AnswerProvenance.PREFILLED : AnswerProvenance.TYPED;
    }

    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ checklists

    /**
     * Every declared item is frozen, not only the ones touched: "the analyst left this blank and a
     * YES elsewhere settled it" is part of the record, and reconstructing it later from an absence
     * would be guesswork.
     *
     * <p>The NOT_APPLICABLE coercion is applied here so the snapshot matches what the rules say was
     * saved. When the ECB save use case is written it should coerce ONCE, before this, and pass the
     * settled answers in — at which point this becomes a straight copy.
     */
    private Optional<Answer> checklist(Question question, Map<String, String> answers) {
        Map<String, ItemAnswer> given = itemAnswers(question, answers);
        if (given.isEmpty()) {
            return Optional.empty();
        }
        boolean anyYes = given.containsValue(ItemAnswer.YES);
        List<SubAnswer> items = new ArrayList<>();
        for (ChecklistItem item : question.items()) {
            items.add(frozenItem(item, given.get(item.key()), anyYes));
        }
        String aggregate = anyYes ? Aggregate.ANY_YES.name() : Aggregate.ALL_NO.name();
        return Optional.of(Answer.checklist(question.key(), question.label(), aggregate, items));
    }

    private SubAnswer frozenItem(ChecklistItem item, ItemAnswer given, boolean anyYes) {
        if (given != null) {
            return SubAnswer.item(item.key(), item.label(), given.name(), null, AnswerProvenance.TYPED);
        }
        // Unanswered. On a saved form this can only happen when a YES settled the block, because
        // otherwise the save was refused — so NOT_APPLICABLE either way, and anyYes is what makes
        // that assertion checkable rather than assumed.
        assert anyYes : "an unanswered item with no YES should never have been saved";
        return SubAnswer.item(item.key(), item.label(), ItemAnswer.NOT_APPLICABLE.name(), null,
                AnswerProvenance.SYSTEM_ASSIGNED);
    }

    private Map<String, ItemAnswer> itemAnswers(Question question, Map<String, String> answers) {
        Map<String, ItemAnswer> given = new LinkedHashMap<>();
        for (ChecklistItem item : question.items()) {
            String raw = trimmed(answers.get(question.key() + '.' + item.key()));
            toItemAnswer(raw).ifPresent(answer -> given.put(item.key(), answer));
        }
        return given;
    }

    // ------------------------------------------------------------------ financial table

    /**
     * Boxes are frozen in the order the definition declares them, which is the order they were on
     * screen. A justification is stored WITH the figure it explains rather than beside it — it is
     * the audit trail for a number that moved the ECB leverage ratio.
     */
    private Optional<Answer> dataEntry(Question question, Map<String, String> answers) {
        List<SubAnswer> boxes = new ArrayList<>();
        for (DataField field : question.fields()) {
            frozenField(question.key(), field, answers).ifPresent(boxes::add);
        }
        return boxes.isEmpty() ? Optional.empty()
                : Optional.of(Answer.dataEntry(question.key(), question.label(), boxes));
    }

    private Optional<SubAnswer> frozenField(String questionKey, DataField field, Map<String, String> answers) {
        String prefix = questionKey + '.' + field.key();
        String value = trimmed(answers.get(prefix));
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(SubAnswer.field(field.key(), field.label(), value,
                fieldProvenance(field), justification(prefix, answers)));
    }

    private AnswerProvenance fieldProvenance(DataField field) {
        if (field.isCalculated()) {
            return AnswerProvenance.CALCULATED;
        }
        return field.derivedFrom() != null ? AnswerProvenance.PREFILLED : AnswerProvenance.TYPED;
    }

    /** {@code Q-F01.ebitda.wording} / {@code .comment}, written by the adjustment pop-in. */
    private Justification justification(String prefix, Map<String, String> answers) {
        String wording = trimmed(answers.get(prefix + ".wording"));
        String comment = trimmed(answers.get(prefix + ".comment"));
        return wording == null && comment == null ? null : new Justification(wording, comment);
    }

    // ------------------------------------------------------------------ helpers

    private Map<String, Question> index(DecisionTreeDefinition definition) {
        Map<String, Question> byKey = new LinkedHashMap<>();
        definition.questions().forEach(question -> byKey.put(question.key(), question));
        return byKey;
    }

    private LocalizedLabel optionLabel(Question question, String value) {
        return question.options().stream()
                .filter(option -> value.equals(option.value()))
                .map(Option::label)
                .findFirst()
                .orElse(null);
    }

    private static Optional<ItemAnswer> toItemAnswer(String raw) {
        if (raw == null) {
            return Optional.empty();
        }
        String token = raw.toUpperCase().replace(' ', '_');
        return Arrays.stream(ItemAnswer.values())
                .filter(candidate -> candidate.name().equals(token))
                .findFirst();
    }

    private static String trimmed(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
