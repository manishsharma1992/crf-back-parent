package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.*;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.ItemAnswer;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.TraversalResult;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.catalogue.ValidationMessage;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.catalogue.ValidationRule;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.input.DataField;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.math.BigDecimal;
import java.util.*;

/**
 * Decides which authored validation messages currently fire.
 *
 * <p>Pure: definition, answers, the walk and two pre-resolved facts in, message keys out. No
 * repository, no locale — the rules are the same in both languages, and rendering happens where
 * the locale is known.
 *
 * <p><b>Only rules with an authored row can fire.</b> The Forms tab's presence of a row IS the
 * rule's activation, so a check with no message has nothing to say and is not run. That is what
 * lets a BA switch a rule off by deleting a line — and what lets {@code MUST_NOT_BE_ZERO} be
 * implemented here ahead of the decision to use it.
 *
 * <p><b>Facts, not lookups.</b> Two checks cannot be decided from answers alone: whether two
 * rmpmids share a business group, and what Adjusted EBITDA came to. Both are resolved upstream
 * and handed in already reduced — {@link EntityEligibility} to three booleans,
 * {@link ComputedFinancials} to five figures — which is what keeps this service pure.
 */
@DomainDrivenDesign.DomainService
public final class ValidationDomainService {

    /**
 * The rules a row may carry when it names a box.
 *
 * <p>MANDATORY is here as well as in addMandatoryViolations, and they do not overlap: a row with a
 * Field Key judges that box, a row with only a Question Key judges the question the walk stopped
 * on. ECB authors neither shape — its one MANDATORY row is form-wide — so this is additive.
 */
private static final Set<ValidationRule> FIELD_RULES = EnumSet.of(
        ValidationRule.MANDATORY,
        ValidationRule.SOURCE_EMPTY,
        ValidationRule.MUST_NOT_BE_ZERO,
        ValidationRule.MUST_BE_POSITIVE,
        ValidationRule.JUSTIFICATION_REQUIRED);


    public List<ValidationMessage> violations(DecisionTreeDefinition definition,
                                              Map<String, String> answers,
                                              TraversalResult result,
                                              EntityEligibility entity,
                                              Map<String, String> computed) {
        List<ValidationMessage> fired = new ArrayList<>();
        addMandatoryViolations(definition, answers, result, fired);
        addEntityViolations(definition, result, entity, fired);
        addAnswerViolations(definition, result, entity, fired);
        addFieldViolations(definition, answers, result, computed, fired);
        return List.copyOf(fired);
    }

    /**
     * For a walk that has reached neither the lookup nor the financial table — the PRELIMINARY
     * form, and the ECB form before Q-F01. Absent facts leave those rules quiet rather than
     * guessing at them.
     */
    public List<ValidationMessage> violations(DecisionTreeDefinition definition,
                                              Map<String, String> answers,
                                              TraversalResult result) {
        return violations(definition, answers, result, null, Map.of());
    }

    // ================================================================== MANDATORY

  /*
 * ValidationDomainService — addMandatoryViolations replaced, two helpers added.
 * Nothing else in the class changes, and the workbook needs no edit: the authored row is already
 * form-wide (blank Question Key, blank Field Key), so it already speaks for every question type.
 */

/**
 * The form is not finished.
 *
 * <p><b>Read from where the WALK stopped, not by scanning the answers for blanks.</b> The engine
 * already answers this: traversal advances until it meets a question it cannot answer, and
 * {@link TraversalResult#pendingQuestion()} hands that question straight back.
 *
 * <p>Scanning the answer map instead would be wrong as well as slower. A prefilled question like
 * Q01 arrives through the cross-form channel and a derived one through
 * {@code prefilledAnswers} — neither is ever in the posted map, so both would look unanswered and
 * raise a message about a box the analyst cannot act on.
 *
 * <p>One message for the whole form, however many questions remain. The authored row names no
 * question, so the alert anchors to the section rather than to a fieldset — right, because "you
 * have not finished" is a statement about the form, not about one control.
 */
private void addMandatoryViolations(DecisionTreeDefinition definition,
                                    Map<String, String> answers,
                                    TraversalResult result,
                                    List<ValidationMessage> fired) {

    // Two different faults, and now two different messages. Stopping on an unanswered question
    // can name that question; an unsettled checklist still speaks for the form, because repeating
    // identical text per block tells the analyst nothing extra.
    if (stoppedAtUnanswered(result)) {
        addIfAuthored(pendingMandatoryMessage(definition, result), fired);
    }
    if (anyStartedButUnsettledChecklist(definition, answers, result)) {
        addIfAuthored(formWideMessage(definition, ValidationRule.MANDATORY), fired);
    }
}

/** No authored row means the rule is off. The same row twice is still one alert. */
private void addIfAuthored(ValidationMessage message, List<ValidationMessage> fired) {
    if (message != null && !fired.contains(message)) {
        fired.add(message);
    }
}

/**
 * The row for the question the walk stopped on, falling back to the form-wide one.
 *
 * <p>ECB authors no question-scoped MANDATORY row, so the lookup misses and it resolves to
 * exactly the message it uses today. FED authors one per question and gets the specific wording.
 */
private ValidationMessage pendingMandatoryMessage(DecisionTreeDefinition definition,
                                                  TraversalResult result) {
    ValidationMessage scoped = result.pendingQuestion()
            .map(Question::key)
            .map(key -> questionScopedMessage(definition, ValidationRule.MANDATORY, key))
            .orElse(null);

    return scoped != null ? scoped : formWideMessage(definition, ValidationRule.MANDATORY);
}


/**
 * True when the walk halted on a mandatory question still waiting for an answer.
 *
 * <p>Covers SINGLE_CHOICE, NUMERIC, TEXT and LOOKUP without naming any of them: whatever the type,
 * if the engine stopped there and the question is mandatory, it is waiting.
 *
 * <p><b>A non-mandatory question halting the walk is NOT a violation.</b> The analyst may leave it
 * and the form is still complete enough to record.
 *
 * <p><b>DATA_ENTRY is excluded.</b> Q-F01 is only ever unanswered because a blocking rule withheld
 * its calculated boxes, and that rule has already produced a message naming the real cause — a
 * missing EBITDA, a zero Gross Debt. Adding "the form is incomplete" underneath it tells the
 * analyst nothing they can act on and buries the message that does.
 *
 * <p><b>COMPUTED is excluded</b> for the same reason from the other direction: nobody can answer
 * one, so if the walk is sitting on it, something upstream is at fault and this message would
 * point at the wrong place.
 */
private boolean stoppedAtUnanswered(TraversalResult result) {
    return result.pendingQuestion()
            .filter(Question::mandatory)
            .filter(question -> question.type() != QuestionType.DATA_ENTRY)
            .filter(question -> question.type() != QuestionType.COMPUTED)
            .isPresent();
}

/**
 * A checklist that has been STARTED but not settled.
 *
 * <p>Unchanged, and still needed alongside the check above, because a checklist is the one type
 * that can be wrong BEHIND the walk. Answer two of three items with no YES and the engine cannot
 * tell whether the untouched one is inapplicable or was never reached — so it refuses to record
 * the block, even though the walk itself may have moved on.
 */
private boolean anyStartedButUnsettledChecklist(DecisionTreeDefinition definition,
                                                Map<String, String> answers,
                                                TraversalResult result) {
    for (String key : result.path()) {
        Question question = question(definition, key);
        if (question != null && question.type() == QuestionType.CHECKLIST
                && isStartedButUnsettled(question, answers)) {
            return true;
        }
    }
    return false;
}

    private boolean isStartedButUnsettled(Question question, Map<String, String> answers) {
        int answered = 0;
        boolean anyYes = false;

        for (ChecklistItem item : question.items()) {
            String raw = answers.get(question.key() + '.' + item.key());
            if (raw == null || raw.isBlank()) {
                continue;
            }
            answered++;
            anyYes |= ItemAnswer.YES.name().equalsIgnoreCase(raw.trim());
        }
        boolean untouched = answered == 0;
        boolean settled = anyYes || answered == question.items().size();
        return !untouched && !settled;
    }

    // ================================================================== Q-S06

    /**
     * Three rules on one question, mutually exclusive by construction.
     *
     * <p>Only evaluated when the walk actually reached Q-S06 — a rule about a question the analyst
     * has not been shown would be an error they cannot act on.
     */
    private void addEntityViolations(DecisionTreeDefinition definition, TraversalResult result,
                                     EntityEligibility entity, List<ValidationMessage> fired) {

       String lookupKey = definition.lookupQuestion().map(Question::key).orElse(null);

       if (entity == null || lookupKey == null || !result.path().contains(lookupKey) ) {
           return;
       }

        // Choosing the analysed company itself: it cannot be its own parent.
        if (entity.answered() && entity.sameAsAnalysed()) {
            addQuestionScoped(definition, ValidationRule.NOT_SELF, fired);
            return;
        }

        // Not chosen at all, or chosen from outside the business group. Both mean the same to the
        // analyst — the parent still has to be identified — and the sheet authors one message for
        // the rule rather than one per cause.
        if (!entity.answered() || !entity.inSameBusinessGroup()) {
            addQuestionScoped(definition, ValidationRule.PARENT_ENTITY_ELIGIBLE, fired);
            return;
        }

        // Eligible, but not the parent RMPM already holds. INFO, not ERROR: the analyst may
        // proceed, and the message tells them to correct RMPM rather than refusing the save.
        if (entity.nameDiffersFromParent()) {
            addQuestionScoped(definition, ValidationRule.PARENT_NAME_DIFFERS, fired);
        }
    }

    private void addQuestionScoped(DecisionTreeDefinition definition, ValidationRule rule,
                                   String questionKey, List<ValidationMessage> fired) {
        ValidationMessage message = questionScopedMessage(definition, rule, questionKey);
        if (message != null) {
            fired.add(message);
        }
    }

    /**
     * Rows that fire because a question carries a particular answer.
     *
     * <p>Path-scoped like every other rule: a question the analyst never reached cannot be at
     * fault. Value-compared exactly — the option CODE, not its label, because a label is
     * locale-scoped and a code is not.
     */
    private void addAnswerViolations(DecisionTreeDefinition definition,
                                     Map<String, String> answers,
                                     TraversalResult result,
                                     List<ValidationMessage> fired) {

        for (ValidationMessage message : definition.validationMessages()) {
            if (message.rule() != ValidationRule.ANSWER_IS
                    || isBlank(message.questionKey())
                    || isBlank(message.ruleValue())) {
                continue;   // malformed rows are reported by DecisionTreeValidator at import
            }
            if (!result.path().contains(message.questionKey())) {
                continue;
            }
            String given = answers.get(message.questionKey());
            if (given != null && message.ruleValue().equals(given.trim())) {
                fired.add(message);
            }
        }
    }
    

    // ================================================================== Q-F01

    /**
     * The four box-level rules of the financial table.
     *
     * <p>Driven from the authored rows rather than from the fields, so a box with no row is simply
     * unchecked, and a rule retired on the Forms tab stops running here with no other change.
     *
     * <p><b>A derived box is only judged when its inputs are clean.</b> A zero EBITDA from FINSTAR
     * with no adjustments would otherwise fire {@code ECB_EBITDA_ZERO} AND
     * {@code ECB_ADJUSTED_EBITDA_ZERO} — two messages, one cause, and only one of them naming
     * something the analyst can act on. So messages on calculated boxes are held back while any
     * message stands against a box that feeds them. The suppression lives here rather than in the
     * client, because deciding which of two true statements is useful is a rule, not a rendering
     * choice.
     */
    private void addFieldViolations(DecisionTreeDefinition definition,
                                    Map<String, String> answers,
                                    TraversalResult result,
                                    Map<String, String> computed,
                                    List<ValidationMessage> fired) {

        Map<String, Box> boxes = indexBoxes(definition);
        List<ValidationMessage> onInputs = new ArrayList<>();
        List<ValidationMessage> onCalculated = new ArrayList<>();

        for (ValidationMessage message : definition.validationMessages()) {
            if (!FIELD_RULES.contains(message.rule()) || isBlank(message.fieldKey())) {
                continue;
            }
            Box box = boxes.get(message.fieldKey());
            if (box == null || !result.path().contains(box.questionKey())) {
                continue;   // no such box, or the analyst was never shown it
            }
            if (!fires(message.rule(), box, value(box, answers, computed), answers)) {
                continue;
            }
            (box.field().isCalculated() ? onCalculated : onInputs).add(message);
        }

        fired.addAll(onInputs);
        if (onInputs.isEmpty()) {
            fired.addAll(onCalculated);
        }
    }

    private boolean fires(ValidationRule rule, Box box, Optional<String> raw,
                          Map<String, String> answers) {
        return switch (rule) {
            // A box the analyst must fill — or, for a calculated box, a figure that could not be
            // worked out at all. Was missing: MANDATORY sat in FIELD_RULES with no case here, so it
            // fell to default and every Field-Key MANDATORY row on the Forms tab was silent —
            // NOI, Market Capitalization, Origination Date, Highly Secured, Book Value of Equity,
            // and Clara's two Total Committed Debt rows. Same test as SOURCE_EMPTY; the two differ
            // in what they mean to the BA, not in what they check.
            case MANDATORY -> raw.isEmpty();
            // Absent, not zero. Zero is MUST_NOT_BE_ZERO's business and carries its own wording.
            case SOURCE_EMPTY -> raw.isEmpty();
            case MUST_NOT_BE_ZERO -> signum(raw).filter(sign -> sign == 0).isPresent();
            // Zero passes: the three debt boxes carrying this rule sit at zero until touched.
            case MUST_BE_POSITIVE -> signum(raw).filter(sign -> sign < 0).isPresent();
            // Absent is a legal state — DISMISS ADJUSTMENT clears value, wording and comment
            // together. What is refused is a figure standing without the reason for it.
            case JUSTIFICATION_REQUIRED -> raw.isPresent() && !isJustified(box, answers);
            default -> false;
        };
    }

    /**
     * What the box holds, or empty when it holds nothing.
     *
     * <p>A calculated box is read from the freshly computed figures, never from the posted
     * answers: a client can post anything for {@code adjustedEbitda}, and the rule that blocks the
     * analysis must judge what the domain layer actually worked out.
     *
     * <p>The figures are keyed by bare field key and are COMPLETE — a calculator that withholds a
     * figure from the screen still reports it here, which is how {@code ECB_ADJUSTED_EBITDA_ZERO}
     * fires on a box the analyst never sees.
     */
    private Optional<String> value(Box box, Map<String, String> answers, Map<String, String> computed) {
        if (box.field().isCalculated()) {
            return computed == null ? Optional.empty() : trimmed(computed.get(box.field().key()));
        }
        return trimmed(answers.get(box.questionKey() + '.' + box.field().key()));
    }


    /**
     * Sign of the figure, or empty when the box is blank OR holds something that is not a number.
     *
     * <p>Unparseable text leaves the arithmetic rules quiet rather than guessing at a sign. The
     * box is still PRESENT for {@code JUSTIFICATION_REQUIRED}, because the analyst did type
     * something and owes an explanation for it.
     */
    private Optional<Integer> signum(Optional<String> raw) {
        return raw.flatMap(text -> {
            try {
                return Optional.of(new BigDecimal(text).signum());
            } catch (NumberFormatException ex) {
                return Optional.empty();
            }
        });
    }

    /** Both halves of the pop-in, or neither. A wording with no comment is not a justification. */
    private boolean isJustified(Box box, Map<String, String> answers) {
        String prefix = box.questionKey() + '.' + box.field().key();
        return trimmed(answers.get(prefix + ".wording")).isPresent()
                && trimmed(answers.get(prefix + ".comment")).isPresent();
    }

    /**
     * Field keys are unique across the FORM — {@code DATA_FIELD_DUPLICATE_IN_FORM} enforces it —
     * so a row naming a bare field key resolves to exactly one box and its owning question.
     */
    private Map<String, Box> indexBoxes(DecisionTreeDefinition definition) {
        Map<String, Box> byKey = new LinkedHashMap<>();
        for (Question question : definition.questions()) {
            for (DataField field : question.fields()) {
                if (field != null && !isBlank(field.key())) {
                    byKey.putIfAbsent(field.key(), new Box(question.key(), field));
                }
            }
        }
        return byKey;
    }

    /** A box and the question that owns it, so the dotted answer key can be rebuilt. */
    private record Box(String questionKey, DataField field) {
    }

    // ================================================================== shared

    /** A row with no Question Key and no Field Key speaks for the whole form. */
    private ValidationMessage formWideMessage(DecisionTreeDefinition definition, ValidationRule rule) {
        return definition.validationMessages().stream()
                .filter(message -> message.rule() == rule)
                .filter(message -> isBlank(message.questionKey()) && isBlank(message.fieldKey()))
                .findFirst()
                .orElse(null);
    }

    /**
     * A row naming a question speaks for that question. Distinct from the form-wide lookup, which
     * requires BLANK keys — the Q-S06 rows carry a question key, so that one would never find them.
     */
    private ValidationMessage questionScopedMessage(DecisionTreeDefinition definition,
                                                    ValidationRule rule, String questionKey) {
        return definition.validationMessages().stream()
                .filter(message -> message.rule() == rule)
                .filter(message -> questionKey.equals(message.questionKey()))
                .filter(message -> isBlank(message.fieldKey()))
                .findFirst()
                .orElse(null);
    }

    private Question question(DecisionTreeDefinition definition, String key) {
        return definition.question(key).orElse(null);
    }

    private static Optional<String> trimmed(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
