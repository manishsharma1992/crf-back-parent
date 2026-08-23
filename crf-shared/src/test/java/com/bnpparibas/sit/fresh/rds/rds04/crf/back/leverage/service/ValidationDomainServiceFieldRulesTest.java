package com.bnpparibas.sit.fresh.rds.rds04.crf.back.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service.ValidationDomainService;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.*;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.TraversalResult;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.TraversalState;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.catalogue.Severity;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.catalogue.ValidationMessage;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.catalogue.ValidationRule;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.input.DataField;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.input.DataFieldType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.label.LabelDetails;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The four box-level rules of Q-F01 — pure, no Spring, no database.
 *
 * <p>Kept apart from {@code ValidationDomainServiceTest}, which owns MANDATORY and the checklist
 * scenarios. The interesting cases here are the near misses: a zero that must pass
 * {@code MUST_BE_POSITIVE}, an empty box that must NOT need a justification, and a derived box
 * that must stay quiet while the box feeding it is already complaining.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValidationDomainServiceFieldRulesTest {

    private ValidationDomainService validation;

    @BeforeAll
    void setUp() {
        validation = new ValidationDomainService();
    }

    // ------------------------------------------------------------------ fixtures

    private static final String Q = "Q-F01";

    private static LocalizedLabel ll(String text) {
        return new LocalizedLabel(text, text);
    }

    private static LocalizedQuestionLabel label(String text) {
        return new LocalizedQuestionLabel(LabelDetails.of(text), LabelDetails.of(text));
    }

    /** An analyst-typed adjustment: mandatory, editable, visible. */
    private static DataField editable(String key) {
        return new DataField(key, "G", ll(key), null, DataFieldType.NUMERIC,
                true, true, true, null, null, null);
    }

    /** A FINSTAR box: shown, read-only. */
    private static DataField source(String key) {
        return new DataField(key, "G", ll(key), null, DataFieldType.NUMERIC,
                true, false, true, "FINANCIALS/" + key, null, null);
    }

    /** A CALC box, judged from the computed figures rather than from what was posted. */
    private static DataField calculated(String key) {
        return new DataField(key, "G", ll(key), null, DataFieldType.NUMERIC,
                true, false, true, "CALC/" + key, null, null);
    }

    private static Question financialTable() {
        return new Question(Q, QuestionType.DATA_ENTRY, true, false, true, null, List.of(), null,
                label(Q), null, null, List.of(), List.of(),
                List.of(source("ebitda"), editable("reportedLtmAdjustment"),
                        editable("committedUndrawnDebt"), calculated("adjustedEbitda")),
                List.of(), null);
    }

    private static ValidationMessage message(ValidationRule rule, String fieldKey, String messageKey) {
        return new ValidationMessage(Q, fieldKey, rule, messageKey, Severity.ERROR, ll(messageKey));
    }

    private static DecisionTreeDefinition definition(List<ValidationMessage> messages) {
        return new DecisionTreeDefinition(LeverageFormType.ECB, 3, DefinitionStatus.PUBLISHED,
                "EN", List.of("EN", "FR"), Q,
                List.of(new Section("MAIN", 1, ll("M"), List.of(financialTable()))),
                Map.of(), Map.of(), Map.of(), messages, List.of());
    }

    private static TraversalResult reached(String... path) {
        return new TraversalResult(TraversalState.PENDING_INPUT, null,
                Map.of(), Map.of(), Map.of(), null, List.of(path));
    }

    private static ComputedFinancials adjustedEbitdaOf(String value) {
        BigDecimal amount = value == null ? null : new BigDecimal(value);
        return new ComputedFinancials(amount, new BigDecimal("100"), new BigDecimal("100"),
                Ratio.of(new BigDecimal("100"), amount), Ratio.of(new BigDecimal("100"), amount));
    }

    /** Builder for the flat dotted answer map the save path posts. */
    private static final class Posted {
        private final Map<String, String> values = new LinkedHashMap<>();

        static Posted nothing() {
            return new Posted();
        }

        Posted box(String fieldKey, String value) {
            values.put(Q + '.' + fieldKey, value);
            return this;
        }

        Posted justified(String fieldKey, String value) {
            return box(fieldKey, value)
                    .raw(Q + '.' + fieldKey + ".wording", "Perimeter change")
                    .raw(Q + '.' + fieldKey + ".comment", "Acquisition completed in March");
        }

        Posted raw(String key, String value) {
            values.put(key, value);
            return this;
        }

        Map<String, String> map() {
            return values;
        }
    }

    private List<String> firedKeys(List<ValidationMessage> messages, Posted posted,
                                   ComputedFinancials financials) {
        return validation.violations(definition(messages), posted.map(),
                        reached(Q), null, financials).stream()
                .map(ValidationMessage::messageKey)
                .toList();
    }

    private List<String> firedKeys(List<ValidationMessage> messages, Posted posted) {
        return firedKeys(messages, posted, adjustedEbitdaOf("500"));
    }

    // ================================================================== SOURCE_EMPTY

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class SourceEmpty {

        private final List<ValidationMessage> rows =
                List.of(message(ValidationRule.SOURCE_EMPTY, "ebitda", "ECB_EBITDA_EMPTY"));

        @Test
        void an_absent_source_fires() {
            assertEquals(List.of("ECB_EBITDA_EMPTY"), firedKeys(rows, Posted.nothing()));
        }

        @Test
        void a_present_source_is_silent() {
            assertTrue(firedKeys(rows, Posted.nothing().box("ebitda", "418.87")).isEmpty());
        }

        /** A negative EBITDA is ordinary — the legacy screen shows one. */
        @Test
        void a_negative_source_is_silent() {
            assertTrue(firedKeys(rows, Posted.nothing().box("ebitda", "-418.87")).isEmpty());
        }

        /**
         * Zero is present, so this rule stays quiet and MUST_NOT_BE_ZERO speaks instead. Sharing
         * one message key across both would put "the amount is empty" on screen for a zero.
         */
        @Test
        void a_zero_source_is_left_to_the_other_rule() {
            assertTrue(firedKeys(rows, Posted.nothing().box("ebitda", "0")).isEmpty());
        }

        /** A cleared input posts "" and must read as never delivered. */
        @Test
        void a_blank_source_counts_as_absent() {
            assertEquals(List.of("ECB_EBITDA_EMPTY"),
                    firedKeys(rows, Posted.nothing().box("ebitda", "   ")));
        }
    }

    // ================================================================== MUST_NOT_BE_ZERO

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class MustNotBeZero {

        private final List<ValidationMessage> rows =
                List.of(message(ValidationRule.MUST_NOT_BE_ZERO, "ebitda", "ECB_EBITDA_ZERO"));

        @Test
        void a_zero_fires() {
            assertEquals(List.of("ECB_EBITDA_ZERO"), firedKeys(rows, Posted.nothing().box("ebitda", "0")));
        }

        /** Trailing zeros must not defeat the test: 0.00 is zero. */
        @Test
        void a_zero_at_any_scale_fires() {
            assertEquals(List.of("ECB_EBITDA_ZERO"),
                    firedKeys(rows, Posted.nothing().box("ebitda", "0.0000")));
        }

        /** Absent is SOURCE_EMPTY's business; this rule needs a figure to judge. */
        @Test
        void an_absent_box_is_silent() {
            assertTrue(firedKeys(rows, Posted.nothing()).isEmpty());
        }

        @Test
        void a_non_zero_is_silent() {
            assertTrue(firedKeys(rows, Posted.nothing().box("ebitda", "-0.01")).isEmpty());
        }
    }

    // ================================================================== MUST_BE_POSITIVE

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class MustBePositive {

        private final List<ValidationMessage> rows = List.of(
                message(ValidationRule.MUST_BE_POSITIVE, "committedUndrawnDebt",
                        "ECB_POSITIVE_COMMITTED_UNDRAWN"));

        @Test
        void a_negative_fires() {
            assertEquals(List.of("ECB_POSITIVE_COMMITTED_UNDRAWN"),
                    firedKeys(rows, Posted.nothing().justified("committedUndrawnDebt", "-1")));
        }

        /**
         * Zero passes. The box sits at zero until the analyst touches it, so a strict test would
         * refuse a form nobody has edited.
         */
        @Test
        void a_zero_passes() {
            assertTrue(firedKeys(rows, Posted.nothing().justified("committedUndrawnDebt", "0")).isEmpty());
        }

        @Test
        void an_absent_box_is_silent() {
            assertTrue(firedKeys(rows, Posted.nothing()).isEmpty());
        }
    }

    // ================================================================== JUSTIFICATION_REQUIRED

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class JustificationRequired {

        private final List<ValidationMessage> rows = List.of(
                message(ValidationRule.JUSTIFICATION_REQUIRED, "reportedLtmAdjustment",
                        "ECB_JUSTIF_REPORTED_LTM"));

        @Test
        void a_figure_with_both_halves_passes() {
            assertTrue(firedKeys(rows, Posted.nothing().justified("reportedLtmAdjustment", "1000")).isEmpty());
        }

        @Test
        void a_figure_with_no_justification_fires() {
            assertEquals(List.of("ECB_JUSTIF_REPORTED_LTM"),
                    firedKeys(rows, Posted.nothing().box("reportedLtmAdjustment", "1000")));
        }

        /** A name without a reason is not an audit trail. */
        @Test
        void a_wording_without_a_comment_fires() {
            assertEquals(List.of("ECB_JUSTIF_REPORTED_LTM"),
                    firedKeys(rows, Posted.nothing()
                            .box("reportedLtmAdjustment", "1000")
                            .raw(Q + ".reportedLtmAdjustment.wording", "Perimeter change")));
        }

        @Test
        void a_comment_without_a_wording_fires() {
            assertEquals(List.of("ECB_JUSTIF_REPORTED_LTM"),
                    firedKeys(rows, Posted.nothing()
                            .box("reportedLtmAdjustment", "1000")
                            .raw(Q + ".reportedLtmAdjustment.comment", "Because")));
        }

        /**
         * DISMISS ADJUSTMENT clears all three keys together, and an empty box is a legal state —
         * so an untouched adjustment owes nothing.
         */
        @Test
        void an_absent_figure_needs_no_justification() {
            assertTrue(firedKeys(rows, Posted.nothing()).isEmpty());
        }

        /** A typed zero is still an override, per the BA: any edit must be explained. */
        @Test
        void a_typed_zero_still_needs_a_justification() {
            assertEquals(List.of("ECB_JUSTIF_REPORTED_LTM"),
                    firedKeys(rows, Posted.nothing().box("reportedLtmAdjustment", "0")));
        }

        /** Whitespace is not a reason. */
        @Test
        void a_blank_comment_does_not_satisfy_the_rule() {
            assertEquals(List.of("ECB_JUSTIF_REPORTED_LTM"),
                    firedKeys(rows, Posted.nothing()
                            .box("reportedLtmAdjustment", "1000")
                            .raw(Q + ".reportedLtmAdjustment.wording", "Perimeter")
                            .raw(Q + ".reportedLtmAdjustment.comment", "   ")));
        }
    }

    // ================================================================== calculated boxes

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class CalculatedBoxes {

        private final List<ValidationMessage> rows = List.of(
                message(ValidationRule.SOURCE_EMPTY, "ebitda", "ECB_EBITDA_EMPTY"),
                message(ValidationRule.MUST_NOT_BE_ZERO, "adjustedEbitda", "ECB_ADJUSTED_EBITDA_ZERO"));

        /** The case put to the BA: a healthy base cancelled to zero by the adjustments. */
        @Test
        void a_zero_adjusted_ebitda_fires_when_its_inputs_are_clean() {
            assertEquals(List.of("ECB_ADJUSTED_EBITDA_ZERO"),
                    firedKeys(rows, Posted.nothing().box("ebitda", "50000000"), adjustedEbitdaOf("0")));
        }

        /**
         * One cause, one message. Without the suppression the analyst reads that EBITDA is empty
         * AND that Adjusted EBITDA is zero, and only the first names something they can fix.
         */
        @Test
        void a_derived_box_stays_quiet_while_its_input_is_already_complaining() {
            assertEquals(List.of("ECB_EBITDA_EMPTY"),
                    firedKeys(rows, Posted.nothing(), adjustedEbitdaOf("0")));
        }

        /** The posted figure is ignored — only what the domain layer computed can block. */
        @Test
        void a_posted_value_cannot_override_the_computed_one() {
            assertEquals(List.of("ECB_ADJUSTED_EBITDA_ZERO"),
                    firedKeys(rows, Posted.nothing()
                            .box("ebitda", "50000000")
                            .box("adjustedEbitda", "999999"), adjustedEbitdaOf("0")));
        }

        /** No figures resolved at all — the PRELIMINARY path, or before Q-F01 is reached. */
        @Test
        void absent_financials_leave_the_derived_rules_quiet() {
            assertEquals(List.of("ECB_EBITDA_EMPTY"), firedKeys(rows, Posted.nothing(), null));
        }
    }

    // ================================================================== which rows apply

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WhichRowsApply {

        /** Deleting the line switches the check off — no code change. */
        @Test
        void no_authored_row_means_the_rule_is_off() {
            assertTrue(firedKeys(List.of(), Posted.nothing()).isEmpty());
        }

        /** A field rule with no Field Key has no box to judge; the validator rejects the row. */
        @Test
        void a_field_rule_with_no_field_key_is_ignored() {
            var orphan = new ValidationMessage(Q, null, ValidationRule.SOURCE_EMPTY,
                    "ECB_EBITDA_EMPTY", Severity.ERROR, ll("x"));
            assertTrue(firedKeys(List.of(orphan), Posted.nothing()).isEmpty());
        }

        @Test
        void a_row_naming_a_box_that_does_not_exist_is_ignored() {
            var ghost = message(ValidationRule.SOURCE_EMPTY, "ghostBox", "M1");
            assertTrue(firedKeys(List.of(ghost), Posted.nothing()).isEmpty());
        }

        /** Only questions the walk actually reached can be at fault. */
        @Test
        void a_box_on_a_question_off_the_path_is_not_examined() {
            var rows = List.of(message(ValidationRule.SOURCE_EMPTY, "ebitda", "ECB_EBITDA_EMPTY"));
            var fired = validation.violations(definition(rows), Map.of(),
                    reached("Q01"), null, adjustedEbitdaOf("500"));
            assertTrue(fired.isEmpty());
        }

        /** Rows fire in the order the BA authored them, so the alert list reads top to bottom. */
        @Test
        void messages_come_back_in_authored_order() {
            var rows = List.of(
                    message(ValidationRule.SOURCE_EMPTY, "ebitda", "ECB_EBITDA_EMPTY"),
                    message(ValidationRule.JUSTIFICATION_REQUIRED, "reportedLtmAdjustment",
                            "ECB_JUSTIF_REPORTED_LTM"));
            assertEquals(List.of("ECB_EBITDA_EMPTY", "ECB_JUSTIF_REPORTED_LTM"),
                    firedKeys(rows, Posted.nothing().box("reportedLtmAdjustment", "1000")));
        }

        /**
         * Defensive: a client can post anything. Unparseable text leaves the arithmetic rules
         * quiet rather than guessing at a sign, but the box still counts as filled in.
         */
        @Test
        void text_in_a_numeric_box_does_not_throw() {
            var rows = List.of(
                    message(ValidationRule.MUST_BE_POSITIVE, "committedUndrawnDebt", "M1"),
                    message(ValidationRule.JUSTIFICATION_REQUIRED, "committedUndrawnDebt", "M2"));
            assertEquals(List.of("M2"),
                    firedKeys(rows, Posted.nothing().box("committedUndrawnDebt", "not a number")));
        }

        /** The returned list is a record of a decision and must not be edited by its caller. */
        @Test
        void the_result_is_unmodifiable() {
            var rows = List.of(message(ValidationRule.SOURCE_EMPTY, "ebitda", "ECB_EBITDA_EMPTY"));
            var fired = validation.violations(definition(rows), Map.of(),
                    reached(Q), null, adjustedEbitdaOf("500"));
            assertThrows(UnsupportedOperationException.class, () -> fired.add(rows.get(0)));
        }
    }
}
