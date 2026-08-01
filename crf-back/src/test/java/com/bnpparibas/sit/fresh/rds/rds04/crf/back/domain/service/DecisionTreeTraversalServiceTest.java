package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.ItemAnswer;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.TraversalResult;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.TraversalState;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;

import static com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.LeverageTreeFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Walks the real ECB shapes: the LBO split, both borrower checklists, the Status block whose
 * routing depends on how it was reached, the financial table, and the qualitative questions.
 *
 * <p>The fixture is built in code rather than parsed, so a failure here means the WALK is wrong,
 * not the parser. Every case is a path an analyst can actually take.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DecisionTreeTraversalServiceTest {

    private DecisionTreeTraversalService service;

    @BeforeAll
    void setUp() {
        service = new DecisionTreeTraversalService(new ConditionEvaluator());
    }

    /** The ECB tree, trimmed to the shapes that decide routing. */
    private DecisionTreeDefinition ecb() {
        return ecbDef("Q01",
                scFillingFlag("Q01", List.of(opt("YES"), opt("NO")),
                        List.of(goTo(eq("YES"), "Q-B01A"), goTo(eq("NO"), "Q-B01B")), "ecbLboFlag"),

                checklist("Q-B01A", List.of(item("sovereign"), item("financialSector"), item("investmentGrade")),
                        List.of(endFlags(agg(Aggregate.ANY_YES), Map.of("ecbLeveragedFlag", "ECB_NOT_LEVERAGED")),
                                goTo(agg(Aggregate.ALL_NO), "Q-T01"))),

                checklist("Q-B01B", List.of(item("sme"), item("sovereign"), item("financialSector"),
                                item("investmentGrade")),
                        List.of(endFlags(agg(Aggregate.ANY_YES), Map.of("ecbLeveragedFlag", "ECB_NOT_LEVERAGED")),
                                goTo(agg(Aggregate.ALL_NO), "Q-S01"))),

                // ALL_NO is true on BOTH LBO paths, so the Q01 test MUST sit above it.
                checklist("Q-T01", List.of(item("tradeFinance"), item("factoring")),
                        List.of(endFlags(agg(Aggregate.ANY_YES), Map.of("ecbLeveragedFlag", "INR")),
                                goTo(other("Q01", "NO"), "Q-T02"),
                                goTo(agg(Aggregate.ALL_NO), "Q-C01"))),

                sc("Q-T02", List.of(goTo(eq("YES"), "Q-C01"),
                        endFlags(eq("NO"), Map.of("ecbLeveragedFlag", "INR")))),

                sc("Q-C01", List.of(goTo(eq("YES"), "Q-F01"),
                        endFlags(eq("NO"), Map.of("ecbLeveragedFlag", "INR")))),

                scOpts("Q-S01", List.of(opt("UMC"), opt("SUBSIDIARY")),
                        List.of(goTo(eq("UMC"), "Q-S04"), goTo(eq("SUBSIDIARY"), "Q-S02"))),
                sc("Q-S02", List.of(goTo(eq("YES"), "Q-S03"), goTo(eq("NO"), "Q-S04"))),
                sc("Q-S03", List.of(goTo(dflt(), "Q-S04"))),

                // Its own value cannot decide the route: BORROWER occurs on a terminal AND a
                // continuing path, so the branches test how it was reached.
                computedRuled("Q-S04", List.of(opt("BUSINESS_GROUP"), opt("BORROWER")),
                        List.of(rule(other("Q-S01", "UMC"), "BUSINESS_GROUP"),
                                rule(other("Q-S02", "NO"), "BORROWER"),
                                rule(other("Q-S03", "YES"), "BORROWER"),
                                rule(other("Q-S03", "NO"), "BUSINESS_GROUP")),
                        List.of(endFlags(other("Q-S03", "YES"), Map.of("ecbLeveragedFlag", "ECB_NOT_LEVERAGED")),
                                goTo(other("Q-S03", "NO"), "Q-T01"),
                                goTo(dflt(), "Q-T01"))),

                dataEntry("Q-F01",
                        List.of(fieldFillingFlag("ecbLeverageRatio", "ecbLeverageRatio"),
                                field("totalEcbDebt"), field("adjustedEbitda")),
                        List.of(goTo(dflt(), "Q-Q02"))),

                computedRuled("Q-Q02", List.of(opt("YES"), opt("NO")),
                        List.of(rule(fieldRanges("ecbLeverageRatio", halfOpen(0, 4)), "NO"),
                                rule(compare("totalEcbDebt", ComparisonOperator.GT, "6", "adjustedEbitda"), "YES"),
                                rule(dflt(), "NO")),
                        List.of(endFlags(fieldRanges("ecbLeverageRatio", halfOpen(0, 4)), Map.of()),
                                goTo(dflt(), "Q-Q03"))),

                scFillingFlag("Q-Q03", List.of(opt("NONE"), opt("FULL")),
                        List.of(endFlags(dflt(), Map.of("ecbLeveragedFlag", "ECB_LEVERAGED"))),
                        "ecbCovenantStructure"));
    }

    private TraversalResult walk(FakeAnswers answers) {
        return service.resolve(ecb(), answers);
    }

    private String pendingKey(TraversalResult result) {
        return result.pendingQuestion().orElseThrow().key();
    }

    /** Answers the LBO question and marks every named item NO. */
    private FakeAnswers borrowerAllNo(String checklistKey, String lbo, String... itemKeys) {
        FakeAnswers answers = FakeAnswers.of("Q01", lbo);
        for (String itemKey : itemKeys) {
            answers = answers.withItem(checklistKey, itemKey, ItemAnswer.NO);
        }
        return answers;
    }

    // ------------------------------------------------------------------ stopping for input

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class StoppingForInput {

        @Test
        void an_empty_form_stops_at_the_entry_question() {
            TraversalResult result = walk(FakeAnswers.empty());
            assertEquals(TraversalState.PENDING_INPUT, result.state());
            assertEquals("Q01", pendingKey(result));
            assertEquals(List.of("Q01"), result.path());
        }

        @Test
        void the_lbo_answer_chooses_which_borrower_checklist_is_asked() {
            assertEquals("Q-B01A", pendingKey(walk(FakeAnswers.of("Q01", "YES"))));
            assertEquals("Q-B01B", pendingKey(walk(FakeAnswers.of("Q01", "NO"))));
        }

        /** A prefilled question is already answered, so the walk passes straight through it. */
        @Test
        void a_prefilled_question_is_not_asked_again() {
            DecisionTreeDefinition withPrefill = ecbDef("QP",
                    prefilled("QP", "FED/Q01",
                            List.of(endFlags(eq("YES"), Map.of("ecbLeveragedFlag", "INR")),
                                    endFlags(dflt(), Map.of()))));

            TraversalResult result = service.resolve(withPrefill,
                    FakeAnswers.empty().withCrossForm("FED/Q01", "YES"));

            assertEquals(TraversalState.TERMINAL, result.state());
            assertEquals("INR", result.flags().get("ecbLeveragedFlag"));
        }

        @Test
        void a_prefill_source_with_no_answer_still_asks_the_question() {
            DecisionTreeDefinition withPrefill = ecbDef("QP",
                    prefilled("QP", "FED/Q01", List.of(endFlags(dflt(), Map.of()))));
            assertEquals(TraversalState.PENDING_INPUT,
                    service.resolve(withPrefill, FakeAnswers.empty()).state());
        }
    }

    // ------------------------------------------------------------------ checklists

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
            assertEquals("Q-B01A", pendingKey(result));
        }

        @Test
        void all_no_continues_to_the_transaction_block() {
            assertEquals("Q-T01", pendingKey(walk(borrowerAllNo("Q-B01A", "YES",
                    "sovereign", "financialSector", "investmentGrade"))));
        }

        /** NOT_APPLICABLE is non-triggering, so a settled block still routes as ALL_NO. */
        @Test
        void a_not_applicable_item_does_not_trigger_any_yes() {
            TraversalResult result = walk(FakeAnswers.of("Q01", "YES")
                    .withItem("Q-B01A", "sovereign", ItemAnswer.NO)
                    .withItem("Q-B01A", "financialSector", ItemAnswer.NOT_APPLICABLE)
                    .withItem("Q-B01A", "investmentGrade", ItemAnswer.NO));
            assertEquals("Q-T01", pendingKey(result));
        }
    }

    // ------------------------------------------------------------------ order-sensitive routing

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class OrderSensitiveRouting {

        /**
         * ALL_NO is true on BOTH LBO paths, so the {@code Q01 is NO} line above it is what sends
         * the non-LBO walk to Q-T02. Swap those two lines and this breaks — which is exactly why
         * the authored order is business logic, not formatting.
         */
        @Test
        void the_lbo_test_wins_over_all_no_on_the_transaction_block() {
            FakeAnswers nonLbo = borrowerAllNo("Q-B01B", "NO",
                    "sme", "sovereign", "financialSector", "investmentGrade")
                    .with("Q-S01", "UMC")
                    .withItem("Q-T01", "tradeFinance", ItemAnswer.NO)
                    .withItem("Q-T01", "factoring", ItemAnswer.NO);
            assertEquals("Q-T02", pendingKey(walk(nonLbo)));
        }

        @Test
        void the_lbo_path_reaches_the_credit_event_instead() {
            FakeAnswers lbo = borrowerAllNo("Q-B01A", "YES",
                    "sovereign", "financialSector", "investmentGrade")
                    .withItem("Q-T01", "tradeFinance", ItemAnswer.NO)
                    .withItem("Q-T01", "factoring", ItemAnswer.NO);
            assertEquals("Q-C01", pendingKey(walk(lbo)));
        }
    }

    // ------------------------------------------------------------------ computed values

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ComputedValues {

        private FakeAnswers throughToStatus() {
            return borrowerAllNo("Q-B01B", "NO", "sme", "sovereign", "financialSector", "investmentGrade");
        }

        @Test
        void q_s04_derives_business_group_from_the_path_taken() {
            TraversalResult result = walk(throughToStatus().with("Q-S01", "UMC"));
            assertEquals("BUSINESS_GROUP", result.computedAnswers().get("Q-S04"));
            assertEquals("Q-T01", pendingKey(result));
        }

        /**
         * Via Q-S03 = Yes it derives BORROWER and STOPS; via Q-S02 = No it derives the SAME value
         * and continues. The value cannot decide the route — only the path can.
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

    // ------------------------------------------------------------------ financial table

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class FinancialTableAndFlags {

        private FakeAnswers throughToFinancials() {
            return borrowerAllNo("Q-B01A", "YES", "sovereign", "financialSector", "investmentGrade")
                    .withItem("Q-T01", "tradeFinance", ItemAnswer.NO)
                    .withItem("Q-T01", "factoring", ItemAnswer.NO)
                    .with("Q-C01", "YES");
        }

        @Test
        void the_table_waits_until_its_mandatory_boxes_are_filled() {
            assertEquals("Q-F01", pendingKey(walk(throughToFinancials())));
            assertEquals("Q-F01", pendingKey(walk(throughToFinancials().withField("totalEcbDebt", "500"))));
        }

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
            assertEquals("Q-Q03", pendingKey(result));
        }

        /**
         * With a NEGATIVE adjusted EBITDA the debt multiple and the ratio disagree — which is why
         * the BR is written as a multiple, and why the 0-4 rule must come FIRST.
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

    // ------------------------------------------------------------------ determinism

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Determinism {

        /** Answers are the only state, so a refresh must produce exactly the same screen. */
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
            DecisionTreeDefinition broken = ecbDef("Q1",
                    sc("Q1", List.of(goTo(eq("YES"), "GHOST"), endFlags(dflt(), Map.of()))));
            assertEquals(TraversalState.STRANDED,
                    service.resolve(broken, FakeAnswers.of("Q1", "YES")).state());
        }

        @Test
        void no_matching_branch_strands_rather_than_looping() {
            DecisionTreeDefinition broken = ecbDef("Q1", sc("Q1", List.of(goTo(eq("YES"), "Q1"))));
            assertEquals(TraversalState.STRANDED,
                    service.resolve(broken, FakeAnswers.of("Q1", "NO")).state());
        }
    }
}
