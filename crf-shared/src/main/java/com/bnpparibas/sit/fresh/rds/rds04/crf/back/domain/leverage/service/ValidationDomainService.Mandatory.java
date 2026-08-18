/*
 * ValidationDomainService — addMandatoryViolations replaced, plus one helper.
 * Nothing else in the class changes. No workbook change: the authored row is already form-wide.
 */

/**
 * The form is not finished.
 *
 * <p><b>Read from where the WALK stopped, not by scanning for blanks.</b> The engine already
 * answers this: traversal advances until it meets a question it cannot answer, so a walk that has
 * not reached a terminal branch has stopped AT the unanswered question. Scanning the answer map
 * instead would be wrong as well as slower — a prefilled question like Q01 arrives through the
 * cross-form channel and never appears in the posted answers, so it would look unanswered and
 * raise a message about a box the analyst cannot even see.
 *
 * <p>One message for the whole form, however many questions remain. The authored row has no
 * question key, so the alert anchors to the section rather than to a fieldset — which is right,
 * because "you have not finished" is a statement about the form, not about one control.
 *
 * <p><b>DATA_ENTRY is excluded.</b> Q-F01 is only ever unanswered because a blocking rule withheld
 * its calculated boxes, and that rule has already produced a message naming the actual cause —
 * a missing EBITDA, a zero Gross Debt. Adding "the form is incomplete" underneath it says nothing
 * the analyst can act on and buries the message that does.
 *
 * <p><b>CHECKLIST keeps its own treatment</b> below, unchanged: a checklist can be half-filled
 * while the walk has already moved past it, which no other question type can do.
 */
private void addMandatoryViolations(DecisionTreeDefinition definition,
                                    Map<String, String> answers,
                                    TraversalResult result,
                                    List<ValidationMessage> fired) {

    ValidationMessage message = formWideMessage(definition, ValidationRule.MANDATORY);
    if (message == null) {
        return;   // no authored row, so the rule is off
    }
    if (stoppedAtUnanswered(definition, result) || anyStartedButUnsettledChecklist(definition, answers, result)) {
        fired.add(message);
    }
}

/**
 * True when the walk halted on a mandatory question the analyst still has to answer.
 *
 * <p>Covers SINGLE_CHOICE, NUMERIC, TEXT and LOOKUP without naming any of them: whatever the type,
 * if the engine stopped there and the question is mandatory, it is waiting for an answer.
 *
 * <p>A non-mandatory question can also halt the walk, and that is deliberately NOT a violation —
 * the analyst may leave it and the form is still complete enough to record.
 */
private boolean stoppedAtUnanswered(DecisionTreeDefinition definition, TraversalResult result) {
    if (result.isComplete()) {
        return false;
    }
    Question stoppedAt = question(definition, result.nextQuestionKey());
    return stoppedAt != null
            && stoppedAt.mandatory()
            && stoppedAt.type() != QuestionType.DATA_ENTRY
            && stoppedAt.type() != QuestionType.COMPUTED;
}

/**
 * A checklist that has been STARTED but not settled.
 *
 * <p>Unchanged, and still separate from the check above, because a checklist is the one type that
 * can be wrong BEHIND the walk. Answer two of three items with no YES and the engine cannot tell
 * whether the untouched one is inapplicable or simply not reached — so it refuses to record the
 * block, even though the walk may have moved on past it.
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
