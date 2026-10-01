package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses;

/**
 * The two methods to add. NOT complete classes, because the copies you sent me are behind your
 * working tree — both have since gained {@code derivedAnswer}, and FormAnswers a four-argument
 * factory. Pasting mine would delete that.
 *
 * Each is a few lines and goes beside the existing fieldValue in each file.
 */
public final class FieldTextAddition {

    // ================================================================ TraversalAnswers

    /*
     * Beside fieldValue(String).
     */

    /**
     * The raw contents of a box, whatever its type.
     *
     * <p><b>Different question from {@link #fieldValue}, deliberately.</b> That one asks "what
     * NUMBER is in this box" and is what conditions read, so a value that will not parse correctly
     * matches nothing and a {@code range [...]} stays honest. This one asks "does this box hold
     * anything", which is what COMPLETENESS means — a DATE or a code is filled in whether or not
     * it is a figure.
     *
     * <p>Having one accessor serve both is what stopped every REIT analysis: {@code Q-F-REIT} has
     * two mandatory boxes that are not numeric, both read as absent, and the walk waited at the
     * table forever with no message to say why.
     *
     * @return empty for a box with no value and for one that is blank; NEVER empty merely because
     *         the value is not a number
     */
    // Optional<String> fieldText(String fieldKey);

    // ================================================================ FormAnswers

    /*
     * Beside the existing fieldValue implementation. Same lookup, without the parse — note that
     * fieldValue can now be written in terms of it, which keeps the dotted-key construction and
     * the blank-is-absent rule in one place.
     *
     *   @Override
     *   public Optional<BigDecimal> fieldValue(String fieldKey) {
     *       return fieldText(fieldKey).flatMap(FormAnswers::toNumber);
     *   }
     *
     *   @Override
     *   public Optional<String> fieldText(String fieldKey) {
     *       String owner = fieldOwners.get(fieldKey);
     *       if (owner == null) {
     *           return Optional.empty();
     *       }
     *       return value(owner + '.' + fieldKey);
     *   }
     */

    // ================================================================ worth adding at import time

    /*
     * DecisionTreeValidator would have caught this years before an analyst did, had anything been
     * looking. A mandatory non-NUMERIC box was unsatisfiable by construction:
     *
     *   MANDATORY_FIELD_NOT_SATISFIABLE   a mandatory field whose type the completeness check
     *                                     cannot read
     *
     * It is no longer true after this change, so the check is not needed for the reason it would
     * have been. But the general shape — "assert at publication that every mandatory box CAN be
     * satisfied" — is the kind of rule that pays for itself, and this is the second time a
     * silent stall has cost us a day.
     */

    private FieldTextAddition() {
    }
}
