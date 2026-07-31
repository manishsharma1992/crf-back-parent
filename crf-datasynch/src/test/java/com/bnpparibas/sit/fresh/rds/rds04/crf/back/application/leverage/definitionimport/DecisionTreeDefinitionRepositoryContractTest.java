package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.DefinitionStatus;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LocalizedLabel;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The versioning rules every {@link DecisionTreeDefinitionRepository} must obey, written once.
 *
 * <p>The in-memory fake extends this here; the JPA adapter extends it in the integration suite
 * with a real PostgreSQL. Same assertions, both sides — which is the only way the fake stays
 * honest enough to test the import service against.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class DecisionTreeDefinitionRepositoryContractTest {

    protected abstract DecisionTreeDefinitionRepository repository();

    private static final Instant MARCH = Instant.parse("2026-03-01T00:00:00Z");
    private static final Instant JULY = Instant.parse("2026-07-01T00:00:00Z");

    /** A minimal but real definition — enough to prove the JSON round-trips, not to validate. */
    protected static DecisionTreeDefinition definition(LeverageFormType form, int version, String entry) {
        Question question = new Question(
                entry, QuestionType.SINGLE_CHOICE, true, false, true, null, List.of(), null,
                new LocalizedQuestionLabel(LabelDetails.of("EN"), LabelDetails.of("FR")),
                null,
                new LocalizedQuestionLabel(
                        new LabelDetails("note", List.of(new Bullet("top", List.of(Bullet.of("child"))))),
                        LabelDetails.of("note fr")),
                List.of(new Option("YES", new LocalizedLabel("Yes", "Oui")),
                        new Option("NO", new LocalizedLabel("No", "Non"))),
                List.of(), List.of(),
                List.of(new Branch(Condition.defaultBranch(), null,
                        new Effect(null, Map.of("ecbLeveragedFlag", "INR"), true))),
                "ecbLboFlag");

        return new DecisionTreeDefinition(
                form, version, DefinitionStatus.PUBLISHED, "EN", List.of("EN", "FR"), entry,
                List.of(new Section("MAIN", 1, new LocalizedLabel("Main", "Principal"), List.of(question))),
                Map.of(),
                Map.of("ecbLboFlag", new FlagDefinition("ecbLboFlag",
                        new LocalizedLabel("LBO", "LBO"), FlagStorage.BOOLEAN, null)),
                Map.of("LEVERAGED_FLAG", List.of(new FlagValue("LEVERAGED_FLAG", "INR", 2,
                        new LocalizedLabel("INR", "INR"), java.util.Set.of(LeverageFormType.ECB)))),
                List.of(new ValidationMessage(entry, null, ValidationRule.MANDATORY, "M1",
                        Severity.ERROR, new LocalizedLabel("Answer", "Repondez"))),
                List.of(new InfoPanel("P1", new LocalizedLabel("T", "T"), "COUNTERPARTY_CHARACTERISTICS",
                        List.of("leveragedFlag"), "ecbLboFlag", "true")));
    }

    @Test
    void an_unimported_form_has_version_zero() {
        assertEquals(0, repository().currentVersion(LeverageFormType.FED));
    }

    @Test
    void saving_makes_the_definition_current_and_findable() {
        repository().save(definition(LeverageFormType.ECB, 1, "Q01"), MARCH);
        assertEquals(1, repository().currentVersion(LeverageFormType.ECB));
        assertTrue(repository().findInForce(LeverageFormType.ECB, MARCH.plus(1, ChronoUnit.DAYS)).isPresent());
    }

    /**
     * The reason definitions are versioned at all: an analysis started in March must keep walking
     * the March rules after July's import lands.
     */
    @Test
    void superseding_closes_the_old_version_without_deleting_it() {
        repository().save(definition(LeverageFormType.ECB, 1, "Q01"), MARCH);
        repository().supersede(LeverageFormType.ECB, JULY);
        repository().save(definition(LeverageFormType.ECB, 2, "Q01"), JULY);

        assertEquals(2, repository().currentVersion(LeverageFormType.ECB));
        assertEquals(1, repository().findInForce(LeverageFormType.ECB, MARCH.plus(1, ChronoUnit.DAYS))
                .orElseThrow().version(), "March still resolves to the March rules");
        assertEquals(2, repository().findInForce(LeverageFormType.ECB, JULY.plus(1, ChronoUnit.DAYS))
                .orElseThrow().version());
    }

    @Test
    void superseding_a_form_with_nothing_open_is_harmless() {
        assertDoesNotThrow(() -> repository().supersede(LeverageFormType.PRELIMINARY, JULY));
    }

    @Test
    void forms_are_versioned_independently() {
        repository().save(definition(LeverageFormType.ECB, 1, "Q01"), MARCH);
        repository().save(definition(LeverageFormType.ECB, 2, "Q01"), JULY);
        repository().save(definition(LeverageFormType.FED, 1, "F01"), MARCH);
        assertEquals(2, repository().currentVersion(LeverageFormType.ECB));
        assertEquals(1, repository().currentVersion(LeverageFormType.FED));
    }

    @Test
    void nothing_is_in_force_before_its_valid_from() {
        repository().save(definition(LeverageFormType.ECB, 1, "Q01"), JULY);
        assertTrue(repository().findInForce(LeverageFormType.ECB, MARCH).isEmpty());
    }

    /**
     * The JSONB round-trip, asserted through the port so it holds for Hibernate too: nested
     * bullets, an enum-keyed effect map and a shared value set must all come back intact.
     */
    @Test
    void the_stored_definition_round_trips_whole() {
        repository().save(definition(LeverageFormType.ECB, 1, "Q01"), MARCH);
        DecisionTreeDefinition read = repository().findInForce(LeverageFormType.ECB, MARCH).orElseThrow();

        Question question = read.questions().get(0);
        assertEquals("ecbLboFlag", question.fillsFlag());
        assertEquals("child", question.note().en().bullets().get(0).children().get(0).text());
        assertEquals("INR", question.branches().get(0).effect().flags().get("ecbLeveragedFlag"));
        assertEquals(2, read.flagValue("LEVERAGED_FLAG", "INR").orElseThrow().storedValue());
        assertEquals(Severity.ERROR, read.validationMessages().get(0).severity());
        assertEquals("ecbLboFlag", read.infoPanels().get(0).whenFlagKey());
    }
}
