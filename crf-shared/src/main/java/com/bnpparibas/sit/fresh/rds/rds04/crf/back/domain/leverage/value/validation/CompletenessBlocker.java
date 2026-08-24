package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.validation;

/**
 * The single reason a form is not yet validatable. {@link #NONE} means complete.
 *
 * <p>Declared in evaluation-precedence order: the first blocker encountered wins,
 * so the UI never reports "3 mandatory fields missing" for a walk that has not
 * even reached a terminal node.
 */
public enum CompletenessBlocker {

    /** Analysis is not in DRAFT, so there is nothing to validate. */
    NOT_IN_DRAFT,

    /** Traversal returned PENDING_INPUT or AWAITING_EXTERNAL. */
    TRAVERSAL_NOT_TERMINAL,

    /** One or more visible mandatory fields (justifications included) are empty. */
    MANDATORY_FIELDS_MISSING,

    /** At least one blocking validation message is present on the form. */
    BLOCKING_VALIDATION_MESSAGES,

    /** Form is complete and the transition may proceed. */
    NONE
}
