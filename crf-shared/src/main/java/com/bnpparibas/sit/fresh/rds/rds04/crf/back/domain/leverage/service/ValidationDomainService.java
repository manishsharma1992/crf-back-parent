package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.ItemAnswer;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.TraversalResult;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Decides which authored validation messages currently fire.
 *
 * <p>Pure: definition, answers and the walk in, message keys out. No repository, no locale — the
 * rules are the same in both languages, and rendering happens where the locale is known.
 *
 * <p><b>Only rules with an authored row can fire.</b> The Forms tab's presence of a row IS the
 * rule's activation, so a check with no message has nothing to say and is not run. That is what
 * lets a BA switch a rule off by deleting a line.
 *
 * <p>Starts with MANDATORY. The field-level rules — JUSTIFICATION_REQUIRED, MUST_BE_POSITIVE,
 * SOURCE_EMPTY — land with the financial table, and plug in as further private methods rather than
 * changing this shape.
 */
@DomainDrivenDesign.DomainService
public final class ValidationDomainService {

    public List<ValidationMessage> violations(DecisionTreeDefinition definition,
                                              Map<String, String> answers,
                                              TraversalResult result) {
        List<ValidationMessage> fired = new ArrayList<>();
        addMandatoryViolations(definition, answers, result, fired);
        return List.copyOf(fired);
    }

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

    /** A row with no Question Key and no Field Key speaks for the whole form. */
    private ValidationMessage formWideMessage(DecisionTreeDefinition definition, ValidationRule rule) {
        return definition.validationMessages().stream()
                .filter(message -> message.rule() == rule)
                .filter(message -> isBlank(message.questionKey()) && isBlank(message.fieldKey()))
                .findFirst()
                .orElse(null);
    }

    private Question question(DecisionTreeDefinition definition, String key) {
        return definition.questions().stream()
                .filter(candidate -> candidate.key().equals(key))
                .findFirst()
                .orElse(null);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
