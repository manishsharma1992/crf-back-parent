package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

/**
 * One line of the Value Rules cell of a COMPUTED question: {@code CONDITION -> VALUE}, where
 * VALUE is one of the question's declared option values. First match wins.
 *
 * <p>Used where the value has no single source but depends on HOW the question was reached —
 * Q-S04's level of calculation — or on arithmetic over the financial fields, as in Q-Q01 and
 * Q-Q02. Mutually exclusive with {@code derivedFrom}.
 */
@DomainDrivenDesign.ValueObject
public record ValueRule(Condition when, String value) {
}
