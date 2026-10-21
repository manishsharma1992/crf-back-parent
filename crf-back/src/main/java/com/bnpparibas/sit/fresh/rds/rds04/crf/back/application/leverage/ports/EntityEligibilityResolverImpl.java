package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * Reduces the chosen entity, the analysed one and the parent to the three comparisons the rules
 * turn on.
 */
@Component
@RequiredArgsConstructor
public class EntityEligibilityResolverImpl implements EntityEligibilityResolver {

    private final EntityEligibilityDao dao;

    @Override
    public EntityEligibility resolve(String selected, AnalysisSubject subject) {
        if (selected == null || selected.isBlank() || subject == null || subject.rmpmid() == null) {
            return EntityEligibility.UNANSWERED;
        }
        String chosen = selected.trim();
        return dao.compare(chosen, subject.rmpmid())
                .map(row -> toEligibility(chosen, subject.rmpmid(), row))
                // Empty only when the ANALYSED counterparty is missing from reference data — a chosen
                // rmpmid that matches nothing still returns a row, with its columns null. With no
                // analysed row there is nothing to compare against, so the answer is treated as
                // unresolved and the eligibility rule fires rather than quietly passing.
                .orElse(EntityEligibility.UNANSWERED);
    }

    private EntityEligibility toEligibility(String selected, String analysed, EligibilityRow row) {
        boolean sameAsAnalysed = selected.equalsIgnoreCase(analysed);
        boolean nameDiffers = !equalsIgnoringCase(row.getSelectedName(), row.getParentName());
        return new EntityEligibility(selected, sameAsAnalysed, sharesGroup(row), nameDiffers);
    }

    /**
     * Same business group as the counterparty UNDER ANALYSIS.
     *
     * <p>Was compared with the parent's group, which is null whenever the analysed company has no
     * parent row — and then no choice at all could pass. The analysed company always has a row
     * (it is the one the query starts from), and by definition it is in the same group as its
     * parent, so this is the same rule without that hole.
     *
     * <p>Null on either side is NOT a match: a counterparty with no business group cannot be shown
     * to belong to it, and the rule's remedy — "contact RMPM to add this counterparty to the
     * Business Group" — is exactly right for that case. An unknown rmpmid has a null group too, so
     * it fails here as well.
     */
    private boolean sharesGroup(EligibilityRow row) {
        Long chosenGroup = row.getSelectedGroup();
        return chosenGroup != null && chosenGroup.equals(row.getAnalysedGroup());
    }

    private boolean equalsIgnoringCase(String left, String right) {
        return left == null ? right == null : left.equalsIgnoreCase(right);
    }
}
