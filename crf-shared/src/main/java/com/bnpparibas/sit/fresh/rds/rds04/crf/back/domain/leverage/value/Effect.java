package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.Map;

/**
 * What a branch does when it is taken.
 *
 * @param setOutcome  PRELIMINARY only — which forms the analysis opens; null on ECB / FED
 * @param flags       flag key -> value code, authored as {@code flags: ecbLeveragedFlag=INR}.
 *                    A flag NOT named here is simply left empty; there is no syntax for
 *                    "clear it", because a flag holds no value until something sets one.
 * @param terminal    true when the form stops here
 */
@DomainDrivenDesign.ValueObject
public record Effect(RecommendationOutcome setOutcome, Map<String, String> flags, boolean terminal) {

    public Effect {
        flags = flags == null ? Map.of() : Map.copyOf(flags);
    }
}
