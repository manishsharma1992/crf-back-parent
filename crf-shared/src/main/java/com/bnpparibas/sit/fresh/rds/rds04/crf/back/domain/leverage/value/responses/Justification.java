package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

/**
 * Why an analyst overrode a figure in the financial table.
 *
 * <p>Both parts are entered in the adjustment pop-in and the save is refused without them, so this
 * is not decoration — it is the audit trail for a number that moved the ECB leverage ratio. It
 * must be frozen with the value it explains, not stored beside it.
 *
 * @param wording what the analyst called the adjustment ("How do you name this adjustment?")
 * @param comment why the value changed ("How do you justify this change in value?")
 */
@DomainDrivenDesign.ValueObject
public record Justification(String wording, String comment) {
}
