/**
 * Additions to FormStateAssemblerTest for the widened signature.
 *
 * <p>Add the overload and the two factories to the FIXTURES section, and the two nested classes at
 * the end. Every existing assemble(def, answers, result) call then compiles unchanged — the three
 * new arguments are exactly the ones those tests have no opinion about.
 */

// ------------------------------------------------------------------ fixtures (add these)

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.FormAudit;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.FormState;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.ValidationMessageView;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import javax.print.attribute.standard.Severity;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The old three-argument shape, for the tests that are about projection rather than about
 * validation or audit. Defaults are the "nothing to say" values: no violations, the form's own
 * language, no analysis behind the call.
 */
private FormState assemble(DecisionTreeDefinition definition,
                           Map<String, String> answers,
                           TraversalResult result) {
    return assembler.assemble(definition, answers, result, List.of(), "EN", FormAudit.NONE);
}

/** One place to correct if the record's component order differs from the Forms tab's columns. */
private static ValidationMessage message(String messageKey, ValidationRule rule,
                                         String questionKey, String fieldKey,
                                         String textEn, String textFr) {
    return new ValidationMessage(LeverageFormType.ECB, questionKey, fieldKey, rule,
            messageKey, Severity.ERROR, textEn, textFr);
}

private static FormAudit audit(String modified, String validated, String validatedBy) {
    return new FormAudit(modified == null ? null : Instant.parse(modified),
            validated == null ? null : Instant.parse(validated), validatedBy);
}

// ------------------------------------------------------------------ new nested classes

@Nested
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValidationMessages {

    private DecisionTreeDefinition ecb() {
        return definition(LeverageFormType.ECB, List.of(choice("Q01", null, "YES", "NO")), Map.of());
    }

    @Test
    void nothing_firing_is_an_empty_list_rather_than_null() {
        FormState state = assemble(ecb(), Map.of("Q01", "NO"),
                pending(null, List.of("Q01"), Map.of(), Map.of()));

        assertNotNull(state.validationMessages(), "the client iterates without a guard");
        assertTrue(state.validationMessages().isEmpty());
    }

    @Test
    void a_fired_message_is_rendered_in_the_requested_language() {
        ValidationMessage mandatory = message("ECB_CHECKLIST_MANDATORY", ValidationRule.MANDATORY,
                null, null, "ECB Form - Please answer the mandatory questions.",
                "Formulaire BCE - Veuillez répondre aux questions obligatoires.");

        FormState state = assembler.assemble(ecb(), Map.of(),
                pending(null, List.of("Q01"), Map.of(), Map.of()),
                List.of(mandatory), "FR", FormAudit.NONE);

        ValidationMessageView view = state.validationMessages().get(0);
        assertEquals("ECB_CHECKLIST_MANDATORY", view.messageKey());
        assertEquals(Severity.ERROR, view.severity());
        assertEquals("Formulaire BCE - Veuillez répondre aux questions obligatoires.", view.text());
    }

    /**
     * The French column is still "(à fournir)" for most rows, so a French analyst has to read
     * the English wording rather than an empty alert.
     */
    @Test
    void a_missing_french_wording_falls_back_to_english() {
        ValidationMessage mandatory = message("ECB_CHECKLIST_MANDATORY", ValidationRule.MANDATORY,
                null, null, "Please answer the mandatory questions.", null);

        FormState state = assembler.assemble(ecb(), Map.of(),
                pending(null, List.of("Q01"), Map.of(), Map.of()),
                List.of(mandatory), "FR", FormAudit.NONE);

        assertEquals("Please answer the mandatory questions.", state.validationMessages().get(0).text());
    }

    /** A form-wide rule is authored with no keys, and the view has to keep them null. */
    @Test
    void a_form_wide_message_carries_no_question_or_field() {
        ValidationMessage mandatory = message("ECB_CHECKLIST_MANDATORY", ValidationRule.MANDATORY,
                null, null, "Please answer the mandatory questions.", null);

        FormState state = assembler.assemble(ecb(), Map.of(),
                pending(null, List.of("Q01"), Map.of(), Map.of()),
                List.of(mandatory), "EN", FormAudit.NONE);

        assertNull(state.validationMessages().get(0).questionKey());
        assertNull(state.validationMessages().get(0).fieldKey());
    }

    /** A field rule has to reach the box it is about, or the alert cannot anchor to it. */
    @Test
    void a_field_message_keeps_the_question_and_field_it_names() {
        ValidationMessage positive = message("ECB_EBITDA_POSITIVE", ValidationRule.MUST_BE_POSITIVE,
                "Q-F01", "ebitda", "EBITDA must be positive.", null);

        FormState state = assembler.assemble(ecb(), Map.of(),
                pending(null, List.of("Q01"), Map.of(), Map.of()),
                List.of(positive), "EN", FormAudit.NONE);

        assertEquals("Q-F01", state.validationMessages().get(0).questionKey());
        assertEquals("ebitda", state.validationMessages().get(0).fieldKey());
    }
}

@Nested
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AuditStamps {

    private DecisionTreeDefinition ecb() {
        return definition(LeverageFormType.ECB, List.of(choice("Q01", null, "YES", "NO")), Map.of());
    }

    @Test
    void stamps_travel_on_an_in_progress_state() {
        FormState state = assembler.assemble(ecb(), Map.of(),
                pending(null, List.of("Q01"), Map.of(), Map.of()), List.of(), "EN",
                audit("2026-08-06T09:15:00Z", null, null));

        assertEquals(Instant.parse("2026-08-06T09:15:00Z"), state.lastModifiedTimestamp());
        assertNull(state.validatedAt(), "a draft has not been validated");
        assertNull(state.validatedBy());
    }

    /** The terminal branch builds a different FormState, so it needs its own cover. */
    @Test
    void stamps_travel_on_a_completed_state_too() {
        FormState state = assembler.assemble(ecb(), Map.of("Q01", "YES"),
                terminal(List.of("Q01"), null, Map.of()), List.of(), "EN",
                audit("2026-08-06T09:15:00Z", "2026-08-06T11:00:00Z", "F93328"));

        assertEquals(FormState.Status.COMPLETED, state.status());
        assertEquals(Instant.parse("2026-08-06T11:00:00Z"), state.validatedAt());
        assertEquals("F93328", state.validatedBy());
    }

    /** The stateless path has no analysis row, so there is nothing to stamp. */
    @Test
    void the_stateless_path_carries_no_stamps() {
        FormState state = assemble(ecb(), Map.of(),
                pending(null, List.of("Q01"), Map.of(), Map.of()));

        assertNull(state.lastModifiedTimestamp());
        assertNull(state.validatedAt());
    }
}