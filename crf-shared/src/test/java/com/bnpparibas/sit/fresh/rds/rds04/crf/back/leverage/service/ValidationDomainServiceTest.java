package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.*;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.catalogue.Severity;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.catalogue.ValidationMessage;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.catalogue.ValidationRule;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.label.LabelDetails;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rule evaluation — pure, no Spring, no database.
 *
 * <p>These are the checks that refuse a save, so the interesting cases are the near misses: a
 * checklist nobody has touched must NOT complain, and one settled by a YES must not either. The
 * only thing that fires is a block left half-answered, which is scenario three.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValidationDomainServiceTest {

    private ValidationDomainService validation;

    @BeforeAll
    void setUp() {
        validation = new ValidationDomainService();
    }

    // ------------------------------------------------------------------ fixtures

    private static final String MANDATORY_KEY = "ECB_CHECKLIST_MANDATORY";

    private static LocalizedQuestionLabel label(String text) {
        return new LocalizedQuestionLabel(LabelDetails.of(text), LabelDetails.of(text));
    }

    private static ChecklistItem item(String key) {
        return new ChecklistItem(key, new LocalizedLabel(key, key));
    }

    /** A CHECKLIST with the given item keys and no routing of its own. */
    private static Question checklist(String key, String... itemKeys) {
        return new Question(key, QuestionType.CHECKLIST, true, false, true, null, List.of(), null,
                label(key), null, null, List.of(),
                List.of(itemKeys).stream().map(ValidationDomainServiceTest::item).toList(),
                List.of(), List.of(), null);
    }

    private static Question choice(String key) {
        return new Question(key, QuestionType.SINGLE_CHOICE, true, false, true, null, List.of(), null,
                label(key), null, null,
                List.of(new Option("YES", new LocalizedLabel("Yes", "Oui")),
                        new Option("NO", new LocalizedLabel("No", "Non"))),
                List.of(), List.of(), List.of(), null);
    }

    /** One place to correct if the record's components ever move. */
    private static ValidationMessage message(ValidationRule rule, String questionKey, String fieldKey) {
        return new ValidationMessage(questionKey, fieldKey, rule, MANDATORY_KEY, Severity.ERROR,
                new LocalizedLabel("Please answer the mandatory questions.", null));
    }

    private static ValidationMessage formWideMandatory() {
        return message(ValidationRule.MANDATORY, null, null);
    }

    private static DecisionTreeDefinition definition(List<Question> questions,
                                                     List<ValidationMessage> messages) {
        return new DecisionTreeDefinition(LeverageFormType.ECB, 3, DefinitionStatus.PUBLISHED,
                "EN", List.of("EN", "FR"), questions.get(0).key(),
                List.of(new Section("MAIN", 1, new LocalizedLabel("M", "M"), questions)),
                Map.of(), Map.of(), Map.of(), messages, List.of());
    }

    private static TraversalResult walked(List<String> path) {
        return new TraversalResult(TraversalState.PENDING_INPUT, null,
                Map.of(), Map.of(), Map.of(), null, path);
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class TheThreeScenarios {

        private final Question borrower = checklist("Q-B01A", "sovereign", "financialSector", "sme");

        private List<ValidationMessage> evaluate(Map<String, String> answers) {
            return validation.violations(definition(List.of(borrower), List.of(formWideMandatory())),
                    answers, walked(List.of("Q-B01A")));
        }

        /** Scenario one: a YES settles the block, so the untouched items stop being required. */
        @Test
        void any_yes_settles_the_block() {
            assertTrue(evaluate(Map.of("Q-B01A.sovereign", "YES")).isEmpty());
        }

        /** Scenario two: every item answered, none YES. Nothing left to ask. */
        @Test
        void all_no_is_complete() {
            assertTrue(evaluate(Map.of(
                    "Q-B01A.sovereign", "NO",
                    "Q-B01A.financialSector", "NO",
                    "Q-B01A.sme", "NO")).isEmpty());
        }

        /** Scenario three: started and left hanging. The only case that fires. */
        @Test
        void some_answered_with_no_yes_fires() {
            List<ValidationMessage> fired = evaluate(Map.of(
                    "Q-B01A.sovereign", "NO",
                    "Q-B01A.financialSector", "NO"));

            assertEquals(1, fired.size());
            assertEquals(MANDATORY_KEY, fired.get(0).messageKey());
            assertEquals(Severity.ERROR, fired.get(0).severity());
        }

        /**
         * An untouched block is a question not yet reached, not a question answered wrongly. The
         * analyst would otherwise meet an error before their first click.
         */
        @Test
        void an_untouched_checklist_is_silent() {
            assertTrue(evaluate(Map.of()).isEmpty());
        }

        /** A cleared radio posts "" and must read as never answered rather than as an answer. */
        @Test
        void blank_values_count_as_unanswered() {
            assertTrue(evaluate(Map.of(
                    "Q-B01A.sovereign", "  ",
                    "Q-B01A.financialSector", "",
                    "Q-B01A.sme", "")).isEmpty(), "nothing was actually answered");
        }

        /** Mixed blanks and answers is still scenario three. */
        @Test
        void a_blank_among_answers_still_fires() {
            assertEquals(1, evaluate(Map.of(
                    "Q-B01A.sovereign", "NO",
                    "Q-B01A.financialSector", "",
                    "Q-B01A.sme", "NO")).size());
        }

        /** The wire value has been lower-cased and padded before now; the rule tolerates both. */
        @Test
        void yes_is_matched_case_insensitively_and_trimmed() {
            assertTrue(evaluate(Map.of("Q-B01A.sovereign", " yes ")).isEmpty());
        }

        /** A single-item block behaves like any other: one NO answers it completely. */
        @Test
        void a_one_item_block_is_settled_by_its_only_answer() {
            Question single = checklist("Q-T01", "onlyItem");
            List<ValidationMessage> fired = validation.violations(
                    definition(List.of(single), List.of(formWideMandatory())),
                    Map.of("Q-T01.onlyItem", "NO"), walked(List.of("Q-T01")));

            assertTrue(fired.isEmpty());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WhichRowsApply {

        private final Question borrower = checklist("Q-B01A", "a", "b");
        private final Map<String, String> halfAnswered = Map.of("Q-B01A.a", "NO");

        private List<ValidationMessage> evaluateWith(List<ValidationMessage> messages) {
            return validation.violations(definition(List.of(borrower), messages),
                    halfAnswered, walked(List.of("Q-B01A")));
        }

        /**
         * The presence of a row IS the rule's activation, so deleting the line switches the check
         * off. That is what lets a BA retire a rule without a code change.
         */
        @Test
        void no_authored_row_means_the_rule_is_off() {
            assertTrue(evaluateWith(List.of()).isEmpty());
        }

        @Test
        void a_row_for_another_rule_does_not_stand_in() {
            assertTrue(evaluateWith(List.of(
                    message(ValidationRule.MUST_BE_POSITIVE, null, null))).isEmpty());
        }

        /** A row naming a question speaks for that question, not for every checklist. */
        @Test
        void a_question_scoped_row_is_not_the_form_wide_message() {
            assertTrue(evaluateWith(List.of(
                    message(ValidationRule.MANDATORY, "Q-F01", null))).isEmpty());
        }

        /** Likewise a row naming a box. */
        @Test
        void a_field_scoped_row_is_not_the_form_wide_message() {
            assertTrue(evaluateWith(List.of(
                    message(ValidationRule.MANDATORY, null, "ebitda"))).isEmpty());
        }

        /** A blank cell reads as absent, the same as an empty one. */
        @Test
        void whitespace_in_the_key_columns_still_counts_as_form_wide() {
            assertEquals(1, evaluateWith(List.of(
                    message(ValidationRule.MANDATORY, "  ", "  "))).size());
        }

        /** The form-wide row is picked out from among scoped ones. */
        @Test
        void the_form_wide_row_is_found_among_others() {
            assertEquals(1, evaluateWith(List.of(
                    message(ValidationRule.MANDATORY, "Q-F01", "ebitda"),
                    message(ValidationRule.MUST_BE_POSITIVE, null, null),
                    formWideMandatory())).size());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WhatIsExamined {

        /** Only questions the walk actually reached can be at fault. */
        @Test
        void a_checklist_off_the_path_is_not_examined() {
            Question reached = choice("Q01");
            Question notReached = checklist("Q-T01", "a", "b");

            List<ValidationMessage> fired = validation.violations(
                    definition(List.of(reached, notReached), List.of(formWideMandatory())),
                    Map.of("Q-T01.a", "NO"), walked(List.of("Q01")));

            assertTrue(fired.isEmpty());
        }

        @Test
        void a_non_checklist_question_is_never_at_fault_for_this_rule() {
            Question single = choice("Q01");

            List<ValidationMessage> fired = validation.violations(
                    definition(List.of(single), List.of(formWideMandatory())),
                    Map.of(), walked(List.of("Q01")));

            assertTrue(fired.isEmpty(), "an unanswered radio is simply the next question");
        }

        /** Defensive: a path key with no definition behind it is skipped, not dereferenced. */
        @Test
        void a_path_key_with_no_question_is_skipped() {
            Question borrower = checklist("Q-B01A", "a", "b");

            assertDoesNotThrow(() -> validation.violations(
                    definition(List.of(borrower), List.of(formWideMandatory())),
                    Map.of("Q-B01A.a", "NO"), walked(List.of("Q-GHOST", "Q-B01A"))));
        }

        /**
         * The message speaks for the form, so two unsettled blocks are still one alert — repeating
         * identical text per block would tell the analyst nothing extra.
         */
        @Test
        void two_unsettled_checklists_produce_one_message() {
            Question first = checklist("Q-B01A", "a", "b");
            Question second = checklist("Q-T01", "c", "d");

            List<ValidationMessage> fired = validation.violations(
                    definition(List.of(first, second), List.of(formWideMandatory())),
                    Map.of("Q-B01A.a", "NO", "Q-T01.c", "NO"),
                    walked(List.of("Q-B01A", "Q-T01")));

            assertEquals(1, fired.size());
        }

        /** A later block being unsettled still fires when the earlier one is clean. */
        @Test
        void a_settled_block_does_not_mask_an_unsettled_one_later() {
            Question first = checklist("Q-B01A", "a", "b");
            Question second = checklist("Q-T01", "c", "d");

            List<ValidationMessage> fired = validation.violations(
                    definition(List.of(first, second), List.of(formWideMandatory())),
                    Map.of("Q-B01A.a", "YES", "Q-T01.c", "NO"),
                    walked(List.of("Q-B01A", "Q-T01")));

            assertEquals(1, fired.size());
        }

        @Test
        void an_empty_path_has_nothing_to_examine() {
            Question borrower = checklist("Q-B01A", "a", "b");

            assertTrue(validation.violations(
                    definition(List.of(borrower), List.of(formWideMandatory())),
                    Map.of("Q-B01A.a", "NO"), walked(List.of())).isEmpty());
        }

        /** The returned list is a record of a decision and must not be edited by its caller. */
        @Test
        void the_result_is_unmodifiable() {
            Question borrower = checklist("Q-B01A", "a", "b");
            List<ValidationMessage> fired = validation.violations(
                    definition(List.of(borrower), List.of(formWideMandatory())),
                    Map.of("Q-B01A.a", "NO"), walked(List.of("Q-B01A")));

            assertThrows(UnsupportedOperationException.class, () -> fired.add(formWideMandatory()));
        }
    }
}