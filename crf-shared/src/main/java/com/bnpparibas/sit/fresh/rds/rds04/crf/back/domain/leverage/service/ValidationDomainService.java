package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.*;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.response.ItemAnswer;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.response.TraversalResult;
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

    private static final String LOOKUP_QUESTION = "Q-S06";

    /** Rules that check ONE box, and therefore need a Field Key on their row. */
    private static final Set<ValidationRule> FIELD_RULES = EnumSet.of(
            ValidationRule.SOURCE_EMPTY,
            ValidationRule.MUST_NOT_BE_ZERO,
            ValidationRule.MUST_BE_POSITIVE,
            ValidationRule.JUSTIFICATION_REQUIRED);

    public List<ValidationMessage> violations(DecisionTreeDefinition definition,
                                              Map<String, String> answers,
                                              TraversalResult result,
                                              EntityEligibility entity,
                                              ComputedFinancials financials) {
        List<ValidationMessage> fired = new ArrayList<>();
        addMandatoryViolations(definition, answers, result, fired);
        addEntityViolations(definition, result, entity, fired);
        addFieldViolations(definition, answers, result, financials, fired);
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
        return violations(definition, answers, result, null, null);
    }

    // ================================================================== MANDATORY

    /**
     * A checklist that has been STARTED but not settled.
     *
     * <p>Not "the form is incomplete": a section may be saved part-finished, and firing on every
     * unanswered question would put an error on screen from the first click. What cannot be
     * recorded is a half-settled checklist — some items answered, none YES — because the snapshot
     * would then have to guess whether the untouched ones were inapplicable or simply not reached.
     * That is scenario three, and this is the message the analyst reads when it refuses.
     */
    private void addMandatoryViolations(DecisionTreeDefinition definition,
                                        Map<String, String> answers,
                                        TraversalResult result,
                                        List<ValidationMessage> fired) {

        ValidationMessage message = formWideMessage(definition, ValidationRule.MANDATORY);
        if (message == null) {
            return;   // no authored row, so the rule is off
        }
        for (String key : result.path()) {
            Question question = question(definition, key);
            if (question != null && question.type() == QuestionType.CHECKLIST
                    && isStartedButUnsettled(question, answers)) {
                fired.add(message);
                return;   // one form-wide message, however many checklists are unsettled
            }
        }
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

        if (entity == null || !result.path().contains(LOOKUP_QUESTION)) {
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
                                   List<ValidationMessage> fired) {
        ValidationMessage message = questionScopedMessage(definition, rule, LOOKUP_QUESTION);
        if (message != null) {
            fired.add(message);
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
                                    ComputedFinancials financials,
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
            if (!fires(message.rule(), box, value(box, answers, financials), answers)) {
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
     */
    private Optional<String> value(Box box, Map<String, String> answers, ComputedFinancials financials) {
        if (box.field().isCalculated()) {
            return financials == null
                    ? Optional.empty()
                    : financials.valueOf(box.field().key()).map(BigDecimal::toPlainString);
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
        return definition.questions().stream()
                .filter(candidate -> candidate.key().equals(key))
                .findFirst()
                .orElse(null);
    }

    private static Optional<String> trimmed(String value) {
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value.trim());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
