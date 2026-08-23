package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.ItemAnswer;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

/**
 * Everything the walk may READ about what has been answered.
 *
 * <p>An interface rather than a map, for one reason that matters: {@link #crossFormAnswer} reaches
 * into ANOTHER form's saved answers (Q-S06 takes the FED answer when there is one), and the walk
 * must not know how that is stored. Everything else is a lookup the caller already has.
 *
 * <p><b>Absent means absent.</b> Every method returns empty for a question the analyst never
 * reached, and NO implementation may substitute a blank string or a zero. That single rule is what
 * lets Q-S04 carry one value rule per inbound path and keep the unused ones quiet, and lets
 * Q-Q02's "not displayed" rule sit above the arithmetic without the arithmetic firing anyway.
 */
public interface TraversalAnswers {

    /** The analyst's answer to a question, or empty. */
    Optional<String> answerOf(String questionKey);

    /** A numeric box inside a DATA_ENTRY question. Keys are unique per form. */
    Optional<BigDecimal> fieldValue(String fieldKey);

    /** Item key to answer for a CHECKLIST question; empty map when untouched. */
    Map<String, ItemAnswer> itemAnswers(String questionKey);

    /**
     * An answer from another form, addressed as it is authored: {@code FED/Q01}.
     *
     * @return empty when that form has not been filled, in which case the analyst answers here
     */
    Optional<String> crossFormAnswer(String formAndQuestionKey);
}
