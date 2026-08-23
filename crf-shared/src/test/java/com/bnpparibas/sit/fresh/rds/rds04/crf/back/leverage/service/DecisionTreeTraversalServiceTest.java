package com.bnpparibas.sit.fresh.rds.rds04.crf.back.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.*;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.input.DataField;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.input.DataFieldType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.label.LabelDetails;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.routing.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Walks the real ECB shapes: the LBO split, both borrower checklists, the Status block whose
 * routing depends on how it was reached, the financial table and the qualitative questions.
 *
 * <p>The fixture is built in code rather than parsed, so a failure here means the WALK is wrong
 * rather than the parser.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DecisionTreeTraversalServiceTest {

    private DecisionTreeTraversalService service;

    @BeforeAll
    void setUp() {
        service = new DecisionTreeTraversalService(new ConditionEvaluator());
    }

    // ------------------------------------------------------------------ fixture helpers

    private static LocalizedQuestionLabel label(String text) {
        return new LocalizedQuestionLabel(LabelDetails.of(text), LabelDetails.of(text));
    }

    private static Option option(String value) {
        return new Option(value, new LocalizedLabel(value, value));
    }

    private static ChecklistItem item(String key) {
        return new ChecklistItem(key, new LocalizedLabel(key, key));
    }

    private static Condition own(String value) {
        return new Condition(false, null, null, value, null, null, null, null, null);
    }

    private static Condition other(String questionKey, String value) {
        return new Condition(false, questionKey, null, value, null, null, null, null, null);
    }

    private static Condition aggregate(Aggregate aggregate) {
        return new Condition(false, null, null, null, null, aggregate, null, null, null);
    }

    private static Condition fieldRange(String fieldKey, Range... ranges) {
        return new Condition(false, null, fieldKey, null, null, null, List.of(ranges), null, null);
    }

    private static Condition multiple(String left, ComparisonOperator op, String factor, String right) {
        return new Condition(false, null, null, null, null, null, null,
                new Comparison(left, op, new BigDecimal(factor), right), null);
    }

    private static Branch to(Condition when, String target) {
        return new Branch(when, target, null);
    }

    private static Branch end(Condition when, Map<String, String> flags) {
        return new Branch(when, null, new Effect(null, flags, true));
    }

    private static Question choice(String key, List<String> options, List<Branch> branches, String fillsFlag) {
        return new Question(key, QuestionType.SINGLE_CHOICE, true, false, true, null, List.of(), null,
                label(key), null, null, options.stream().map(DecisionTreeTraversalServiceTest::option).toList(),
                List.of(), List.of(), branches, fillsFlag);
    }

    private static Question checklist(String key, List<String> items, List<Branch> branches) {
        return new Question(key, QuestionType.CHECKLIST, true, false, true, null, List.of(), null,
                label(key), null, null, List.of(),
                items.stream().map(DecisionTreeTraversalServiceTest::item).toList(),
                List.of(), branches, null);
    }

    private static Question computed(String key, List<String> options,
                                     List<ValueRule> rules, List<Branch> branches) {
        return new Question(key, QuestionType.COMPUTED, false, true, false, null, rules, null,
                label(key), null, null, options.stream().map(DecisionTreeTraversalServiceTest::option).toList(),
                List.of(), List.of(), branches, null);
    }

    private static DataField numeric(String key, boolean mandatory, String fillsFlag) {
        return new DataField(key, "G", new LocalizedLabel(key, key), null, DataFieldType.NUMERIC,
                mandatory, true, null, null, fillsFlag);
    }

    private static Question dataEntry(String key, List<DataField> fields, List<Branch> branches) {
        return new Question(key, QuestionType.DATA_ENTRY, true, false, true, null, List.of(), null,
                label(key), null, null, List.of(), List.of(), fields, branches, null);
    }

    private static DecisionTreeDefinition definition(String entry, List<Question> questions) {
        return new DecisionTreeDefinition(LeverageFormType.ECB, 1, DefinitionStatus.PUBLISHED, "EN",
                List.of("EN", "FR"), entry,
                List.of(new Section("MAIN", 1, new LocalizedLabel("M", "M"), questions)),
                Map.of(), Map.of(), Map.of(), List.of(), List.of());
    }

    /** The ECB tree, trimmed to the shapes that matter for routing. */
    private DecisionTreeDefinition ecb() {
        return definition("Q01", List.of(
                choice("Q01", List.of("YES", "NO"),
                        List.of(to(own("YES"), "Q-B01A"), to(own("NO"), "Q-B01B")), "ecbLboFlag"),
                checklist("Q-B01A", List.of("sovereign", "financialSector", "investmentGrade"),
                        List.of(end(aggregate(Aggregate.ANY_YES), Map.of("ecbLeveragedFlag", "ECB_NOT_LEVERAGED")),
                                to(aggregate(Aggregate.ALL_NO), "Q-T01"))),
                checklist("Q-B01B", List.of("sme", "sovereign", "financialSector", "investmentGrade"),
                        List.of(end(aggregate(Aggregate.ANY_YES), Map.of("ecbLeveragedFlag", "ECB_NOT_LEVERAGED")),
                                to(aggregate(Aggregate.ALL_NO), "Q-S01"))),
                checklist("Q-T01", List.of("tradeFinance", "factoring"),
                        List.of(end(aggregate(Aggregate.ANY_YES), Map.of("ecbLeveragedFlag", "INR")),
                                to(other("Q01", "NO"), "Q-T02"),
                                to(aggregate(Aggregate.ALL_NO), "Q-C01"))),
                choice("Q-T02", List.of("YES", "NO"),
                        List.of(to(own("YES"), "Q-C01"), end(own("NO"), Map.of("ecbLeveragedFlag", "INR"))), null),
                choice("Q-C01", List.of("YES", "NO"),
                        List.of(to(own("YES"), "Q-F01"), end(own("NO"), Map.of("ecbLeveragedFlag", "INR"))), null),
                choice("Q-S01", List.of("UMC", "SUBSIDIARY"),
                        List.of(to(own("UMC"), "Q-S04"), to(own("SUBSIDIARY"), "Q-S02")), null),
                choice("Q-S02", List.of("YES", "NO"),
                        List.of(to(own("YES"), "Q-S03"), to(own("NO"), "Q-S04")), null),
                choice("Q-S03", List.of("YES", "NO"),
                        List.of(to(Condition.defaultBranch(), "Q-S04")), null),
                computed("Q-S04", List.of("BUSINESS_GROUP", "BORROWER"),
                        List.of(new ValueRule(other("Q-S01", "UMC"), "BUSINESS_GROUP"),
                                new ValueRule(other("Q-S02", "NO"), "BORROWER"),
                                new ValueRule(other("Q-S03", "YES"), "BORROWER"),
                                new ValueRule(other("Q-S03", "NO"), "BUSINESS_GROUP")),
                        List.of(end(other("Q-S03", "YES"), Map.of("ecbLeveragedFlag", "ECB_NOT_LEVERAGED")),
                                to(other("Q-S03", "NO"), "Q-T01"),
                                to(Condition.defaultBranch(), "Q-T01"))),
                dataEntry("Q-F01",
                        List.of(numeric("ecbLeverageRatio", true, "ecbLeverageRatio"),
                                numeric("totalEcbDebt", true, null),
                                numeric("adjustedEbitda", false, null)),
                        List.of(to(Condition.defaultBranch(), "Q-Q02"))),
                computed("Q-Q02", List.of("YES", "NO"),
                        List.of(new ValueRule(fieldRange("ecbLeverageRatio",
                                new Range(BigDecimal.ZERO, null, null, new BigDecimal("4"))), "NO"),
                                new ValueRule(multiple("totalEcbDebt", ComparisonOperator.GT, "6",
                                        "adjustedEbitda"), "YES"),
                                new ValueRule(Condition.defaultBranch(), "NO")),
                        List.of(end(fieldRange("ecbLeverageRatio",
                                        new Range(BigDecimal.ZERO, null, null, new BigDecimal("4"))), Map.of()),
                                to(Condition.defaultBranch(), "Q-Q03"))),
                choice("Q-Q03", List.of("NONE", "FULL"),
                        List.of(end(Condition.defaultBranch(), Map.of("ecbLeveragedFlag", "ECB_LEVERAGED"))),
                        "ecbCovenantStructure")));
    }

    private TraversalResult walk(TraversalAnswers answers) {
        return service.resolve(ecb(), answers);
    }

    // ------------------------------------------------------------------ tests

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class StoppingForInput {

        @Test
        void an_empty_form_stops_at_the_entry_question() {
            TraversalResult result = walk(FakeAnswers.empty());
            assertEquals(TraversalState.PENDING_INPUT, result.state());
            assertEquals("Q01", result.pendingQuestion().orElseThrow().key());
            assertEquals(List.of("Q01"), result.path());
        }

        @Test
        void the_lbo_answer_chooses_which_borrower_checklist_is_asked() {
            assertEquals("Q-B01A", walk(FakeAnswers.of("Q01", "YES")).pendingQuestion().orElseThrow().key());
            assertEquals("Q-B01B", walk(FakeAnswers.of("Q01", "NO")).pendingQuestion().orElseThrow().key());
        }

        /** A prefilled question is already answered, so the walk passes straight through it. */
        @Test
        void a_prefilled_question_is_not_asked_again() {
            DecisionTreeDefinition withPrefill = definition("QP", List.of(
                    new Question("QP", QuestionType.SINGLE_CHOICE, true, false, true, null, List.of(),
                            "FED/Q01", label("QP"), null, null,
                            List.of(option("YES"), option("NO")), List.of(), List.of(),
                            List.of(end(own("YES"), Map.of("ecbLboFlag", "YES")),
                                    end(own("NO"), Map.of())), null)));
            TraversalResult result = service.resolve(withPrefill,
                    FakeAnswers.empty().withCrossForm("FED/Q01", "YES"));
            assertEquals(TraversalState.TERMINAL, result.state());
            assertEquals("YES", result.flags().get("ecbLboFlag"));
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Checklists {

        /** A YES settles the block, so the unanswered siblings never hold the walk up. */
        @Test
        void one_yes_ends_the_form_without_the_other_items() {
            TraversalResult result = walk(FakeAnswers.of("Q01", "YES")
                    .withItem("Q-B01A", "sovereign", ItemAnswer.YES));
            assertEquals(TraversalState.TERMINAL, result.state());
            assertEquals("ECB_NOT_LEVERAGED", result.flags().get("ecbLeveragedFlag"));
        }

        @Test
        void a_partly_answered_checklist_still_waits() {
            TraversalResult result = walk(FakeAnswers.of("Q01", "YES")
                    .withItem("Q-B01A", "sovereign", ItemAnswer.NO));
            assertEquals(TraversalState.PENDING_INPUT, result.state());
            assertEquals("Q-B01A", result.pendingQuestion().orElseThrow().key());
        }

        @Test
        void all_no_continues_to_the_transaction_block() {
            TraversalResult result = walk(FakeAnswers.of("Q01", "YES")
                    .withItem("Q-B01A", "sovereign", ItemAnswer.NO)
                    .withItem("Q-B01A", "financialSector", ItemAnswer.NO)
                    .withItem("Q-B01A", "investmentGrade", ItemAnswer.NO));
            assertEquals("Q-T01", result.pendingQuestion().orElseThrow().key());
        }

        /** NOT_APPLICABLE is non-triggering, so a settled block still routes as ALL_NO. */
        @Test
        void a_not_applicable_item_does_not_trigger_any_yes() {
            TraversalResult result = walk(FakeAnswers.of("Q01", "YES")
                    .withItem("Q-B01A", "sovereign", ItemAnswer.NO)
                    .withItem("Q-B01A", "financialSector", ItemAnswer.NOT_APPLICABLE)
                    .withItem("Q-B01A", "investmentGrade", ItemAnswer.NO));
            assertEquals("Q-T01", result.pendingQuestion().orElseThrow().key());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class OrderSensitiveRouting {

        /**
         * ALL_NO is true on BOTH LBO paths, so the {@code Q01 is NO} line above it is what sends
         * the non-LBO walk to Q-T02. Reorder those two lines and this breaks — which is exactly
         * why the authored order is business logic.
         */
        @Test
        void the_lbo_test_wins_over_all_no_on_the_transaction_block() {
            FakeAnswers base = FakeAnswers.of("Q01", "NO")
                    .withItem("Q-B01B", "sme", ItemAnswer.NO)
                    .withItem("Q-B01B", "sovereign", ItemAnswer.NO)
                    .withItem("Q-B01B", "financialSector", ItemAnswer.NO)
                    .withItem("Q-B01B", "investmentGrade", ItemAnswer.NO)
                    .with("Q-S01", "UMC")
                    .withItem("Q-T01", "tradeFinance", ItemAnswer.NO)
                    .withItem("Q-T01", "factoring", ItemAnswer.NO);
            assertEquals("Q-T02", walk(base).pendingQuestion().orElseThrow().key());
        }

        @Test
        void the_lbo_path_reaches_the_credit_event_instead() {
            FakeAnswers base = FakeAnswers.of("Q01", "YES")
                    .withItem("Q-B01A", "sovereign", ItemAnswer.NO)
                    .withItem("Q-B01A", "financialSector", ItemAnswer.NO)
                    .withItem("Q-B01A", "investmentGrade", ItemAnswer.NO)
                    .withItem("Q-T01", "tradeFinance", ItemAnswer.NO)
                    .withItem("Q-T01", "factoring", ItemAnswer.NO);
            assertEquals("Q-C01", walk(base).pendingQuestion().orElseThrow().key());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ComputedValues {

        private FakeAnswers throughToStatus() {
            return FakeAnswers.of("Q01", "NO")
                    .withItem("Q-B01B", "sme", ItemAnswer.NO)
                    .withItem("Q-B01B", "sovereign", ItemAnswer.NO)
                    .withItem("Q-B01B", "financialSector", ItemAnswer.NO)
                    .withItem("Q-B01B", "investmentGrade", ItemAnswer.NO);
        }

        /** Reached via Ultimate Mother, Q-S04 fills itself and carries on. */
        @Test
        void q_s04_derives_business_group_from_the_path_taken() {
            TraversalResult result = walk(throughToStatus().with("Q-S01", "UMC"));
            assertEquals("BUSINESS_GROUP", result.computedAnswers().get("Q-S04"));
            assertEquals("Q-T01", result.pendingQuestion().orElseThrow().key());
        }

        /**
         * Reached via Q-S03 = Yes it derives BORROWER and STOPS; via Q-S02 = No it derives the same
         * value and continues. The value cannot decide the route — only the path can.
         */
        @Test
        void the_same_derived_value_routes_differently_by_path() {
            TraversalResult stops = walk(throughToStatus()
                    .with("Q-S01", "SUBSIDIARY").with("Q-S02", "YES").with("Q-S03", "YES"));
            assertEquals(TraversalState.TERMINAL, stops.state());
            assertEquals("BORROWER", stops.computedAnswers().get("Q-S04"));
            assertEquals("ECB_NOT_LEVERAGED", stops.flags().get("ecbLeveragedFlag"));

            TraversalResult continues = walk(throughToStatus()
                    .with("Q-S01", "SUBSIDIARY").with("Q-S02", "NO"));
            assertEquals("BORROWER", continues.computedAnswers().get("Q-S04"));
            assertEquals(TraversalState.PENDING_INPUT, continues.state());
        }

        /** An unanswered question matches nothing — that is what keeps the unused rules quiet. */
        @Test
        void rules_about_paths_not_taken_stay_silent() {
            TraversalResult result = walk(throughToStatus().with("Q-S01", "UMC"));
            assertEquals("BUSINESS_GROUP", result.computedAnswers().get("Q-S04"),
                    "Q-S02 and Q-S03 are blank, so their rules must not fire");
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class FinancialTableAndFlags {

        private FakeAnswers throughToFinancials() {
            return FakeAnswers.of("Q01", "YES")
                    .withItem("Q-B01A", "sovereign", ItemAnswer.NO)
                    .withItem("Q-B01A", "financialSector", ItemAnswer.NO)
                    .withItem("Q-B01A", "investmentGrade", ItemAnswer.NO)
                    .withItem("Q-T01", "tradeFinance", ItemAnswer.NO)
                    .withItem("Q-T01", "factoring", ItemAnswer.NO)
                    .with("Q-C01", "YES");
        }

        @Test
        void the_table_waits_until_its_mandatory_boxes_are_filled() {
            assertEquals("Q-F01", walk(throughToFinancials()).pendingQuestion().orElseThrow().key());
            assertEquals("Q-F01", walk(throughToFinancials().withField("ecbLeverageRatio", "5"))
                    .pendingQuestion().orElseThrow().key());
        }

        /** Ratio in [0,4) ends the form; the qualitative questions are never reached. */
        @Test
        void a_low_ratio_ends_the_form_at_q_q02() {
            TraversalResult result = walk(throughToFinancials()
                    .withField("ecbLeverageRatio", "2").withField("totalEcbDebt", "200")
                    .withField("adjustedEbitda", "100"));
            assertEquals(TraversalState.TERMINAL, result.state());
            assertEquals("NO", result.computedAnswers().get("Q-Q02"));
            assertFalse(result.path().contains("Q-Q03"));
        }

        @Test
        void a_high_ratio_continues_to_the_covenant_question() {
            TraversalResult result = walk(throughToFinancials()
                    .withField("ecbLeverageRatio", "7").withField("totalEcbDebt", "700")
                    .withField("adjustedEbitda", "100"));
            assertEquals("YES", result.computedAnswers().get("Q-Q02"));
            assertEquals("Q-Q03", result.pendingQuestion().orElseThrow().key());
        }

        /**
         * With a NEGATIVE adjusted EBITDA the debt multiple and the ratio disagree, which is why
         * the BR is written as a multiple and why the "not displayed" rule must come first.
         */
        @Test
        void the_low_ratio_rule_wins_over_the_debt_multiple() {
            TraversalResult result = walk(throughToFinancials()
                    .withField("ecbLeverageRatio", "2")
                    .withField("totalEcbDebt", "-200").withField("adjustedEbitda", "-100"));
            assertEquals("NO", result.computedAnswers().get("Q-Q02"),
                    "-200 > 6 x -100 is true, but the 0-4 band settles it first");
        }

        @Test
        void a_field_fills_its_flag_and_an_option_fills_another() {
            TraversalResult result = walk(throughToFinancials()
                    .withField("ecbLeverageRatio", "7").withField("totalEcbDebt", "700")
                    .withField("adjustedEbitda", "100").with("Q-Q03", "FULL"));
            assertEquals(TraversalState.TERMINAL, result.state());
            assertEquals("YES", result.flags().get("ecbLboFlag"), "Q01's answer became the LBO flag");
            assertEquals("7", result.flags().get("ecbLeverageRatio"), "the box became its flag");
            assertEquals("FULL", result.flags().get("ecbCovenantStructure"));
            assertEquals("ECB_LEVERAGED", result.flags().get("ecbLeveragedFlag"));
        }

        /** Nothing writes an empty: a flag no branch named is simply absent. */
        @Test
        void unset_flags_are_absent_rather_than_blank() {
            TraversalResult result = walk(FakeAnswers.of("Q01", "YES")
                    .withItem("Q-B01A", "sovereign", ItemAnswer.YES));
            assertFalse(result.flags().containsKey("ecbCovenantStructure"));
            assertFalse(result.flags().containsKey("escalatedTransactions"));
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Determinism {

        @Test
        void the_same_answers_always_give_the_same_result() {
            FakeAnswers answers = FakeAnswers.of("Q01", "YES")
                    .withItem("Q-B01A", "sovereign", ItemAnswer.YES);
            TraversalResult first = walk(answers);
            TraversalResult second = walk(answers);
            assertEquals(first.state(), second.state());
            assertEquals(first.flags(), second.flags());
            assertEquals(first.path(), second.path());
        }

        /** A definition published around the validator must fail visibly, not hang. */
        @Test
        void a_branch_pointing_nowhere_strands_the_walk() {
            DecisionTreeDefinition broken = definition("Q1", List.of(
                    choice("Q1", List.of("YES"), List.of(to(own("YES"), "Q_GHOST")), null)));
            TraversalResult result = service.resolve(broken, FakeAnswers.of("Q1", "YES"));
            assertEquals(TraversalState.STRANDED, result.state());
        }

        @Test
        void no_matching_branch_strands_rather_than_loops() {
            DecisionTreeDefinition broken = definition("Q1", List.of(
                    choice("Q1", List.of("YES", "NO"), List.of(to(own("YES"), "Q1")), null)));
            TraversalResult result = service.resolve(broken, FakeAnswers.of("Q1", "NO"));
            assertEquals(TraversalState.STRANDED, result.state());
        }
    }
}
