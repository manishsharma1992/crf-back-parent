package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

/**
 * The single reason an analysis is not yet validatable. {@link #NONE} means it is.
 *
 * <p>Far shorter than an earlier draft of this enum, because most of what it used
 * to enumerate is already decided elsewhere:
 *
 * <ul>
 *   <li>Missing mandatory answers cannot reach this point. A DATA_ENTRY question
 *       is only answered once every mandatory box has a value, so an unfilled box
 *       stops the walk at PENDING_INPUT - it never gets to be a separate blocker.</li>
 *   <li>Missing justifications arrive as ERROR violations from
 *       ValidationDomainService, per the BA's answer, so they fold into
 *       BLOCKING_ERRORS rather than needing a category of their own.</li>
 * </ul>
 */
@DomainDrivenDesign.ValueObject
public enum CompletenessBlocker {

    /** Analysis is not in DRAFT, so there is nothing to validate. */
    NOT_IN_DRAFT,

    /** A form's walk stopped at PENDING_INPUT - questions remain unanswered. */
    FORM_INCOMPLETE,

    /**
     * A form's walk ended STRANDED. Not an analyst problem: the definition was
     * published with a gap. Kept separate from FORM_INCOMPLETE because the fix is
     * a re-import, not more typing, and telling the analyst to fill in a field
     * would send them looking for something that does not exist.
     */
    DEFINITION_STRANDED,

    /** Blocking ERROR violations stand on a form - mandatory or justification. */
    BLOCKING_ERRORS,

    /** Every applicable form is complete and clean; the transition may proceed. */
    NONE
}
