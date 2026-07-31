package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Grammar tests. Every case below is a REAL cell from the authoring workbook, so a failure here
 * means a form stopped importing, not that a fixture drifted.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ExpressionParserTest {

    private final ConditionExpressionParser conditions = new ConditionExpressionParser();
    private final BranchExpressionParser branches = new BranchExpressionParser(conditions);
    private final ValueRuleExpressionParser valueRules = new ValueRuleExpressionParser(conditions);

    private static final SourceLocation WHERE = SourceLocation.of("ECB Q", 21, "Branches");

    private ImportIssues issues;

    private ImportIssues fresh() {
        issues = new ImportIssues();
        return issues;
    }

    private Condition condition(String text) {
        Condition c = conditions.parse(text, WHERE, fresh());
        assertTrue(issues.isEmpty(), () -> issues.describeAll().toString());
        return c;
    }

    private boolean hasIssue(String code) {
        return issues.all().stream().anyMatch(i -> i.code().equals(code));
    }

    // ============================================================ conditions

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Atoms {

        @Test
        void star_is_the_default_branch() {
            assertTrue(condition("*").isDefault());
        }

        @Test
        void bare_token_is_this_questions_own_answer() {
            Condition c = condition("JUST_BELOW_CCDG");
            assertEquals("JUST_BELOW_CCDG", c.equals());
            assertNull(c.questionKey());
            assertNull(c.fieldKey());
        }

        @Test
        void aggregates_are_recognised() {
            assertEquals(Aggregate.ANY_YES, condition("ANY_YES").aggregate());
            assertEquals(Aggregate.ALL_NO, condition("ALL_NO").aggregate());
        }

        @Test
        void cross_question_equality() {
            Condition c = condition("Q01 is NO");
            assertEquals("Q01", c.questionKey());
            assertEquals("NO", c.equals());
        }

        @Test
        void cross_question_membership() {
            Condition c = condition("Q-C02 in [ORIGINATION, MATERIAL_MODIFICATION, REFINANCING]");
            assertEquals("Q-C02", c.questionKey());
            assertEquals(List.of("ORIGINATION", "MATERIAL_MODIFICATION", "REFINANCING"), c.in());
        }

        @Test
        void field_scoped_range() {
            Condition c = condition("field ecbLeverageRatio range [<0 | >6]");
            assertEquals("ecbLeverageRatio", c.fieldKey());
            assertEquals(2, c.ranges().size());
            assertEquals(BigDecimal.ZERO, c.ranges().get(0).lt());
            assertEquals(new BigDecimal("6"), c.ranges().get(1).gt());
        }

        /** {@code 0 .. <4} is 0 <= r < 4 — the band that ends the ECB form. */
        @Test
        void half_open_band() {
            Condition c = condition("field ecbLeverageRatio range [0 .. <4]");
            Range r = c.ranges().get(0);
            assertEquals(BigDecimal.ZERO, r.gte());
            assertEquals(new BigDecimal("4"), r.lt());
            assertNull(r.lte());
            assertTrue(r.contains(new BigDecimal("3.99")));
            assertFalse(r.contains(new BigDecimal("4")));
        }

        @Test
        void closed_band_is_inclusive_at_both_ends() {
            Range r = condition("range [4..6]").ranges().get(0);
            assertTrue(r.contains(new BigDecimal("4")));
            assertTrue(r.contains(new BigDecimal("6")));
            assertFalse(r.contains(new BigDecimal("6.01")));
        }

        /**
         * The BR is written against the DEBT MULTIPLE, not the ratio, because dividing by a
         * negative adjusted EBITDA flips the inequality.
         */
        @Test
        void debt_multiple_comparison() {
            Condition c = condition("field totalEcbDebt > 4 x field adjustedEbitda");
            Comparison cmp = c.comparison();
            assertEquals("totalEcbDebt", cmp.leftFieldKey());
            assertEquals(ComparisonOperator.GT, cmp.operator());
            assertEquals(new BigDecimal("4"), cmp.multiplier());
            assertEquals("adjustedEbitda", cmp.rightFieldKey());
        }

        @Test
        void negative_single_bound() {
            Range r = condition("field totalEcbDebt range [<0]").ranges().get(0);
            assertEquals(BigDecimal.ZERO, r.lt());
            assertTrue(r.contains(new BigDecimal("-1")));
            assertFalse(r.contains(BigDecimal.ZERO));
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Composites {

        /** Splitting on AND must not tear apart the comma list or the pipe range. */
        @Test
        void and_splits_only_outside_brackets() {
            Condition c = condition(
                    "Q-C02 in [ORIGINATION, MATERIAL_MODIFICATION, REFINANCING] "
                            + "AND field ecbLeverageRatio range [<0 | >6]");
            assertTrue(c.isComposite());
            assertEquals(2, c.allOf().size());
            assertEquals(3, c.allOf().get(0).in().size());
            assertEquals(2, c.allOf().get(1).ranges().size());
        }

        @Test
        void own_answer_combined_with_another_question() {
            Condition c = condition("YES AND Q-Q02 is YES");
            assertEquals("YES", c.allOf().get(0).equals());
            assertEquals("Q-Q02", c.allOf().get(1).questionKey());
        }

        @Test
        void lower_case_and_is_accepted() {
            assertTrue(condition("YES and Q-Q02 is YES").isComposite());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class BadConditions {

        @Test
        void unparseable_atom_is_reported_not_thrown() {
            assertNull(conditions.parse("some free text here", WHERE, fresh()));
            assertTrue(hasIssue("COND_UNPARSEABLE"));
        }

        @Test
        void range_without_brackets_is_reported() {
            assertNull(conditions.parse("field r range 4..6", WHERE, fresh()));
            assertTrue(hasIssue("RANGE_NO_BRACKETS"));
        }

        @Test
        void nonsense_bound_is_reported() {
            assertNull(conditions.parse("range [abc]", WHERE, fresh()));
            assertTrue(hasIssue("RANGE_BOUND_UNPARSEABLE"));
        }

        @Test
        void star_inside_an_and_is_rejected() {
            conditions.parse("* AND Q01 is YES", WHERE, fresh());
            assertTrue(hasIssue("COND_DEFAULT_IN_AND"));
        }
    }

    // ============================================================ branches

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Branches {

        @Test
        void terminal_with_flags() {
            List<Branch> parsed = branches.parse(
                    "ANY_YES -> END, flags: ecbLeveragedFlag=INR", WHERE, fresh());
            assertTrue(issues.isEmpty(), () -> issues.describeAll().toString());
            Branch b = parsed.get(0);
            assertTrue(b.isTerminal());
            assertNull(b.goTo());
            assertEquals("INR", b.effect().flags().get("ecbLeveragedFlag"));
        }

        @Test
        void terminal_with_two_flags() {
            Branch b = branches.parse(
                    "YES AND Q-Q02 is YES -> END, flags: escalatedTransactions=YES; ecbLeveragedFlag=ECB_LEVERAGED",
                    WHERE, fresh()).get(0);
            assertEquals(2, b.effect().flags().size());
        }

        /** Order is business logic: Q-T01 needs the LBO test ahead of ALL_NO. */
        @Test
        void line_order_is_preserved() {
            List<Branch> parsed = branches.parse("""
                    ANY_YES -> END, flags: ecbLeveragedFlag=INR
                    Q01 is NO -> Q-T02
                    ALL_NO -> Q-C01""", WHERE, fresh());
            assertEquals(3, parsed.size());
            assertEquals(Aggregate.ANY_YES, parsed.get(0).when().aggregate());
            assertEquals("Q-T02", parsed.get(1).goTo());
            assertEquals(Aggregate.ALL_NO, parsed.get(2).when().aggregate());
        }

        @Test
        void preliminary_outcome_clause() {
            Branch b = branches.parse("YES -> END, outcome=ECB_AND_FED", WHERE, fresh()).get(0);
            assertEquals(RecommendationOutcome.ECB_AND_FED, b.effect().setOutcome());
        }

        @Test
        void continuing_branch_has_no_effect_when_it_sets_nothing() {
            Branch b = branches.parse("UMC -> Q-S04", WHERE, fresh()).get(0);
            assertEquals("Q-S04", b.goTo());
            assertNull(b.effect());
            assertFalse(b.isTerminal());
        }

        @Test
        void empty_right_hand_side_is_rejected() {
            branches.parse("* -> END, flags: ecbCovenantStructure=", WHERE, fresh());
            assertTrue(hasIssue("FLAG_ASSIGNMENT_MALFORMED"));
        }

        @Test
        void missing_arrow_is_reported_and_other_lines_still_parse() {
            List<Branch> parsed = branches.parse("""
                    YES Q-B01A
                    NO -> Q-B01B""", WHERE, fresh());
            assertTrue(hasIssue("BRANCH_NO_ARROW"));
            assertEquals(1, parsed.size());
        }

        @Test
        void issue_records_the_offending_line_number() {
            branches.parse("""
                    YES -> Q-B01A
                    NO Q-B01B""", WHERE, fresh());
            assertEquals(2, issues.all().get(0).where().line());
        }
    }

    // ============================================================ value rules

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ValueRules {

        /** Q-Q02: the "not displayed" rule must stay first, ahead of the debt multiple. */
        @Test
        void parses_q_q02_in_order() {
            List<ValueRule> rules = valueRules.parse("""
                    field ecbLeverageRatio range [0 .. <4] -> NO
                    field totalEcbDebt > 6 x field adjustedEbitda -> YES
                    field totalEcbDebt range [<0] -> YES
                    * -> NO""", WHERE, fresh());
            assertTrue(issues.isEmpty(), () -> issues.describeAll().toString());
            assertEquals(4, rules.size());
            assertEquals("NO", rules.get(0).value());
            assertEquals("ecbLeverageRatio", rules.get(0).when().fieldKey());
            assertNotNull(rules.get(1).when().comparison());
            assertTrue(rules.get(3).when().isDefault());
        }

        @Test
        void parses_q_s04_path_rules() {
            List<ValueRule> rules = valueRules.parse("""
                    Q-S01 is UMC -> BUSINESS_GROUP
                    Q-S02 is NO -> BORROWER
                    Q-S03 is YES -> BORROWER
                    Q-S03 is NO -> BUSINESS_GROUP""", WHERE, fresh());
            assertEquals(4, rules.size());
            assertEquals("Q-S01", rules.get(0).when().questionKey());
            assertEquals("BUSINESS_GROUP", rules.get(0).value());
        }

        @Test
        void rule_without_a_value_is_reported() {
            valueRules.parse("Q-S01 is UMC ->", WHERE, fresh());
            assertTrue(hasIssue("VALUE_RULE_NO_VALUE"));
        }
    }
}
