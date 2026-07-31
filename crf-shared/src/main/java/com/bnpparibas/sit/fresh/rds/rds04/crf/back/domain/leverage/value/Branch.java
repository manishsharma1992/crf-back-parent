package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

/**
 * One line of the Branches cell: a condition, and either a next question or an end.
 *
 * <p>Terminal XOR onward — {@code goTo} and a terminal {@code effect} are mutually exclusive,
 * and one of them must be present.
 */
@DomainDrivenDesign.ValueObject
public record Branch(Condition when, String goTo, Effect effect) {

    public boolean isTerminal() {
        return effect != null && effect.terminal();
    }
}
