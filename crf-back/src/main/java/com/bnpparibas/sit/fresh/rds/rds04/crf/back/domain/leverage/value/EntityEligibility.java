package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

/**
 * What reference data says about the entity chosen at Q-S06, reduced to the three comparisons the
 * rules turn on.
 *
 * <p>Booleans rather than the rows themselves, so {@link ValidationDomainService} stays pure. The
 * comparisons need a business-group join; deciding what to do about them does not.
 *
 * @param selected             the chosen rmpmid, or null when the analyst has not picked yet
 * @param sameAsAnalysed       the chosen entity IS the one under analysis
 * @param inSameBusinessGroup  chosen and parent share a business group
 * @param nameDiffersFromParent the chosen company name is not the parent's name
 */
@DomainDrivenDesign.ValueObject
public record EntityEligibility(String selected,
                                boolean sameAsAnalysed,
                                boolean inSameBusinessGroup,
                                boolean nameDiffersFromParent) {

    /** Nothing chosen yet — the state every analysis starts in. */
    public static final EntityEligibility UNANSWERED =
            new EntityEligibility(null, false, false, false);

    public boolean answered() {
        return selected != null && !selected.isBlank();
    }
}
