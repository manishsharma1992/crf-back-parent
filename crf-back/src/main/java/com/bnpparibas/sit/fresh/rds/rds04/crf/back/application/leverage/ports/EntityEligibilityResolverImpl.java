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
        return dao.compare(selected.trim(), subject.rmpmid())
                .map(row -> toEligibility(selected.trim(), subject.rmpmid(), row))
                // A chosen entity reference data cannot find is treated as unchosen, not as
                // passing: the analyst is asked again rather than the rule quietly not firing.
                .orElse(EntityEligibility.UNANSWERED);
    }

    private EntityEligibility toEligibility(String selected, String analysed, EligibilityRow row) {
        boolean sameAsAnalysed = selected.equalsIgnoreCase(analysed);

        // Null group on either side is NOT a match. A counterparty with no business group cannot be
        // shown to belong to the parent's, and the rule's remedy — "contact RMPM to add this
        // counterparty to the Business Group" — is exactly right for that case.
        boolean sameGroup = row.getSelectedGroup() != null
                && Objects.equals(row.getSelectedGroup(), row.getParentGroup());

        boolean nameDiffers = !equalsIgnoringCase(row.getSelectedName(), row.getParentName());

        return new EntityEligibility(selected, sameAsAnalysed, sameGroup, nameDiffers);
    }

    private boolean equalsIgnoringCase(String left, String right) {
        return left == null ? right == null : left.equalsIgnoreCase(right);
    }
}
