package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.validation;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

/**
 * One answer read back out of a validated analysis, with enough context that the
 * caller knows what it is holding.
 *
 * <p><b>Self-describing on purpose.</b> A bare String would let the rating record
 * "Q-S06 was YES" without recording which form or which workbook version said so.
 * A year later, when the tree has moved on, that String cannot be interpreted -
 * and for a regulatory input, an uninterpretable value is worse than none.
 *
 * @param formType          which form the answer was given on
 * @param definitionVersion the workbook version that defined the question
 * @param questionKey       the key as authored in that version
 * @param value             the answer, exactly as frozen
 * @param provenance        typed by the analyst, computed by the tree, or prefilled
 *                          from another form - the rating may well care which
 */
@DomainDrivenDesign.ValueObject
public record ValidatedAnswer(LeverageFormType formType,
                              int definitionVersion,
                              String questionKey,
                              String value,
                              AnswerProvenance provenance) {
}
