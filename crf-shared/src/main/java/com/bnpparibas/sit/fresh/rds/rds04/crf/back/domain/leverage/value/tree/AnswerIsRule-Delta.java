package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.catalogue;

/**
 * DELTA — the ANSWER_IS rule, in five small pieces.
 *
 * WHY A NEW COLUMN: every existing rule judges a box's STATE, so the rule name alone says
 * everything. ANSWER_IS judges a VALUE, so it needs to carry which one. ValidationRule is a bare
 * enum and cannot, and "ANSWER_IS OTHERS" in the rule cell does not parse — enumValue returns null
 * and FormsSheetParser drops the row with an issue.
 *
 * The column is appended as the NINTH on the validation table rather than inserted after Rule:
 * the Forms tab stacks five tables in the same columns, and inserting mid-sheet would put a hole
 * through metadata, outcomes, flags and panels. The parser reads by header name, so position does
 * not matter to it.
 *
 * All five pieces are additive. No existing row carries a Rule Value, and no existing rule reads
 * one, so ECB is untouched.
 */
public final class AnswerIsRuleDelta {

    // ================================================================ 1. the rule

    //  ValidationRule
    //      /**
    //       * The question named in Question Key was answered with the option code in Rule Value.
    //       *
    //       * <p>Not a fault: FED uses it to tell an analyst who picked "Others" on Main Industry to
    //       * complete the Excel worksheet and attach it. The only rule that judges an ANSWER rather
    //       * than the state of a box, which is why it is the only one carrying a value.
    //       */
    //      ANSWER_IS

    // ================================================================ 2. the row

    /*
     * ValidationMessage gains one component. Put it LAST so every existing construction site in
     * the tests fails to compile rather than silently binding a String to the wrong slot.
     *
     *   public record ValidationMessage(String questionKey,
     *                                   String fieldKey,
     *                                   ValidationRule rule,
     *                                   String messageKey,
     *                                   Severity severity,
     *                                   LocalizedLabel text,
     *                                   String ruleValue) { }
     *
     * Add a convenience constructor so the ~40 existing test fixtures do not all need editing:
     *
     *   public ValidationMessage(String questionKey, String fieldKey, ValidationRule rule,
     *                            String messageKey, Severity severity, LocalizedLabel text) {
     *       this(questionKey, fieldKey, rule, messageKey, severity, text, null);
     *   }
     */

    // ================================================================ 3. the parser

    /*
     * FormsSheetParser, beside the other column constants:
     *
     *   private static final String RULE_VALUE = "Rule Value";
     *
     * and in the ValidationMessage construction, one more argument:
     *
     *   row.get(RULE_VALUE).orElse(null)
     *
     * Do NOT add RULE_VALUE to the required-columns list passed to table(...): an older workbook
     * without the column must still import, or every ECB definition in the wild stops loading.
     */

    // ================================================================ 4. the rule itself

    /*
     * ValidationDomainService. ANSWER_IS is QUESTION-scoped, not box-scoped, so it does not belong
     * in FIELD_RULES or in fires(...) — it belongs beside addEntityViolations. Called from
     * violations(...) alongside the other add*Violations.
     */

    /**
     * Rows that fire because a question carries a particular answer.
     *
     * <p>Path-scoped like every other rule: a question the analyst never reached cannot be at
     * fault. Value-compared exactly — the option CODE, not its label, because a label is
     * locale-scoped and a code is not.
     */
    /*
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
    */

    // ================================================================ 5. the import check

    /*
     * DecisionTreeValidator, with the other validation-message checks. Two directions, because
     * both mistakes are silent at runtime: a rule that never fires, and a value nobody reads.
     *
     *   ANSWER_IS_NO_VALUE        rule is ANSWER_IS and Rule Value is blank
     *   ANSWER_IS_NO_QUESTION     rule is ANSWER_IS and Question Key is blank
     *   ANSWER_IS_UNKNOWN_VALUE   Rule Value is not an option code of that question
     *   RULE_VALUE_ON_OTHER_RULE  Rule Value set on a rule that does not read one
     *
     * ANSWER_IS_UNKNOWN_VALUE is the one that earns its keep: authoring "Other" instead of
     * "OTHERS" gives a row that imports clean and never fires, and the only symptom is an analyst
     * not being told to attach the worksheet.
     */
}
