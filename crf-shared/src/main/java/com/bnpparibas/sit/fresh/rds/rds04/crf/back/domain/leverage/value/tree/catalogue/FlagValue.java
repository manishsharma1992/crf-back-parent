package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.catalogue;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LocalizedLabel;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.Set;

/**
 * A row of the Flag Values tab: one code of a coded flag and the number stored for it.
 *
 * @param setBy which forms may WRITE this value; the validator rejects an ECB branch that sets a
 *              FED-only code. Both forms may still READ it — that is how an INFO panel renders a
 *              stored number.
 */
@DomainDrivenDesign.ValueObject
public record FlagValue(String valueSet,
                        String code,
                        int storedValue,
                        LocalizedLabel display,
                        Set<LeverageFormType> setBy) {

    public FlagValue {
        setBy = setBy == null ? Set.of() : Set.copyOf(setBy);
    }
}
