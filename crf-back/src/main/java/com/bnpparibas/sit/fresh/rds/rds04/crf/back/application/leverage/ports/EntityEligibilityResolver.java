package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.EntityEligibility;

/**
 * PORT. Compares the entity chosen at Q-S06 against the analysed counterparty and its parent.
 *
 * <p>Narrow like the other resolvers: the joins live here, the rules do not. What "eligible" MEANS
 * is business logic and belongs in the domain; whether two rmpmids share a business group is a
 * question only the database can answer.
 */
public interface EntityEligibilityResolver {

    /**
     * @param selected the rmpmid the analyst chose, or null/blank when they have not chosen
     * @param subject  the counterparty under analysis
     * @return the three comparisons, or {@link EntityEligibility#UNANSWERED} when nothing was
     *         chosen or reference data cannot answer — an unresolvable entity is treated as
     *         unchosen rather than as passing, so the analyst is asked again
     */
    EntityEligibility resolve(String selected, AnalysisSubject subject);

    static EntityEligibilityResolver none() {
        return (selected, subject) -> EntityEligibility.UNANSWERED;
    }
}
