package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.AnalysisSubject;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.EntityEligibilityResolverImpl;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.EntityEligibility;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage.EntityEligibilityDao;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage.EntityEligibilityDao.EligibilityRow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The three comparisons, with every column the query can return as null.
 *
 * <p>AnalysisSubject is mocked for its rmpmid only; adjust if it is a record you would rather build.
 */
class EntityEligibilityResolverImplTest {

    private static final String ANALYSED = "4AAQ000001";
    private static final String CHOSEN = "4AAQ241036";
    private static final Long GROUP = 700L;
    private static final Long OTHER_GROUP = 800L;

    private final EntityEligibilityDao dao = mock(EntityEligibilityDao.class);
    private final EntityEligibilityResolverImpl resolver = new EntityEligibilityResolverImpl(dao);

    @Test
    @DisplayName("Same group as the analysed company: eligible")
    void sameGroup() {
        assertThat(resolve(row(CHOSEN, "Acme Holding", GROUP, GROUP, "Acme Holding")).inSameBusinessGroup())
                .isTrue();
    }

    @Test
    @DisplayName("Clara 21/10: another group is not eligible")
    void otherGroup() {
        assertThat(resolve(row(CHOSEN, "Other Co", OTHER_GROUP, GROUP, "Acme Holding")).inSameBusinessGroup())
                .isFalse();
    }

    @Test
    @DisplayName("No parent row: a correct choice still passes (was compared with the parent's null group)")
    void noParentRow() {
        EntityEligibility eligibility = resolve(row(CHOSEN, "Acme Holding", GROUP, GROUP, null));

        assertThat(eligibility.inSameBusinessGroup()).isTrue();
    }

    @Test
    @DisplayName("Chosen company has no group: not eligible")
    void chosenWithoutGroup() {
        assertThat(resolve(row(CHOSEN, "Acme Holding", null, GROUP, "Acme Holding")).inSameBusinessGroup())
                .isFalse();
    }

    @Test
    @DisplayName("Neither has a group: null = null is NOT a match")
    void bothWithoutGroup() {
        assertThat(resolve(row(CHOSEN, "Acme Holding", null, null, "Acme Holding")).inSameBusinessGroup())
                .isFalse();
    }

    @Test
    @DisplayName("Unknown rmpmid: the row comes back with the chosen columns null, and it fails")
    void unknownRmpmid() {
        EntityEligibility eligibility = resolve(row(null, null, null, GROUP, "Acme Holding"));

        assertThat(eligibility.answered()).isTrue();
        assertThat(eligibility.inSameBusinessGroup()).isFalse();
    }

    @Test
    @DisplayName("Analysed company missing from reference data: unresolved, so the rule fires")
    void analysedMissing() {
        when(dao.compare(CHOSEN, ANALYSED)).thenReturn(Optional.empty());

        assertThat(resolver.resolve(CHOSEN, subject())).isEqualTo(EntityEligibility.UNANSWERED);
    }

    @Test
    @DisplayName("Choosing the analysed company itself is flagged, whatever the case")
    void sameAsAnalysed() {
        when(dao.compare(ANALYSED.toLowerCase(), ANALYSED))
                .thenReturn(Optional.of(row(ANALYSED, "Acme", GROUP, GROUP, "Acme Holding")));

        assertThat(resolver.resolve(ANALYSED.toLowerCase(), subject()).sameAsAnalysed()).isTrue();
    }

    @Test
    @DisplayName("Name differs from the parent's: INFO case")
    void nameDiffers() {
        assertThat(resolve(row(CHOSEN, "Acme Intermediate", GROUP, GROUP, "Acme Holding")).nameDiffersFromParent())
                .isTrue();
        assertThat(resolve(row(CHOSEN, "ACME HOLDING", GROUP, GROUP, "Acme Holding")).nameDiffersFromParent())
                .isFalse();
    }

    @Test
    @DisplayName("Nothing chosen: unanswered, and the database is not asked")
    void blankChoice() {
        assertThat(resolver.resolve("  ", subject())).isEqualTo(EntityEligibility.UNANSWERED);
    }

    // ------------------------------------------------------------------ helpers

    private EntityEligibility resolve(EligibilityRow row) {
        when(dao.compare(CHOSEN, ANALYSED)).thenReturn(Optional.of(row));
        return resolver.resolve(" " + CHOSEN + " ", subject());   // trimmed before the query
    }

    private static AnalysisSubject subject() {
        AnalysisSubject subject = mock(AnalysisSubject.class);
        when(subject.rmpmid()).thenReturn(ANALYSED);
        return subject;
    }

    private static EligibilityRow row(String selectedRmpmid, String selectedName, Long selectedGroup,
                                      Long analysedGroup, String parentName) {
        return new Row(selectedRmpmid, selectedName, selectedGroup, analysedGroup,
                parentName == null ? null : "4AAQ999999", parentName);
    }

    private record Row(String getSelectedRmpmid, String getSelectedName, Long getSelectedGroup,
                       Long getAnalysedGroup, String getParentRmpmid, String getParentName)
            implements EligibilityRow {
    }
}
