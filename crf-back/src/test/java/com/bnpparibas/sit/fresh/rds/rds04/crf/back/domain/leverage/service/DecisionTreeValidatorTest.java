package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.service;

package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.RecommendationOutcome.*;
import static com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.LeverageTreeFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure structural validation — fixtures built in code, no Excel, no Spring.
 *
 * <p>Asserts on stable ERROR CODES rather than messages: the codes are the contract the import
 * report and these tests share, and message wording should be free to improve.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DecisionTreeValidatorTest {

    private DecisionTreeValidator validator;

    @BeforeAll
    void setUp() {
        validator = new DecisionTreeValidator();
    }

    private ValidationResult validate(DecisionTreeDefinition definition) {
        return validator.validate(definition);
    }

    private static boolean has(ValidationResult result, String code) {
        return result.errors().stream().anyMatch(error -> error.code().equals(code));
    }

    private static String dump(ValidationResult result) {
        return "unexpected errors: " + result.errors();
    }

    // ============================================================ happy paths

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Valid {

        @Test
        void a_simple_two_option_tree_passes() {
            var result = validate(def("Q1", bool("Q1", List.of(end(eq("YES"), ECB), end(eq("NO"), NOT_REQUIRED)))));
            assertTrue(result.isValid(), () -> dump(result));
        }

        @Test
        void a_checklist_covering_both_aggregates_passes() {
            var checklist = checklist("EXCL", List.of(item("sme"), item("sov")),
                    List.of(endFlags(agg(Aggregate.ANY_YES), Map.of("ecbLeveragedFlag", "ECB_NOT_LEVERAGED")),
                            goTo(agg(Aggregate.ALL_NO), "Q2")));
            var q2 = bool("Q2", List.of(endFlags(dflt(), Map.of("ecbLeveragedFlag", "INR"))));
            var result = validate(ecbDef("EXCL", checklist, q2));
            assertTrue(result.isValid(), () -> dump(result));
        }

        /** The Q-T01 shape: a cross-question test between the two aggregates. */
        @Test
        void a_checklist_branch_may_test_another_question() {
            var lbo = sc("Q01", List.of(goTo(eq("YES"), "Q-T01"), goTo(eq("NO"), "Q-T01")));
            var checklist = checklist("Q-T01", List.of(item("tradeFinance")),
                    List.of(endFlags(agg(Aggregate.ANY_YES), Map.of("ecbLeveragedFlag", "INR")),
                            goTo(other("Q01", "NO"), "Q-T02"),
                            goTo(agg(Aggregate.ALL_NO), "Q-T02")));
            var next = bool("Q-T02", List.of(endFlags(dflt(), Map.of("ecbLeveragedFlag", "INR"))));
            var result = validate(ecbDef("Q01", lbo, checklist, next));
            assertTrue(result.isValid(), () -> dump(result));
        }

        /** The Q-F01 -> Q-Q02 shape: a field-scoped range with a default closing the gap. */
        @Test
        void a_data_entry_feeding_a_field_scoped_range_passes() {
            var table = dataEntry("Q-F01",
                    List.of(field("ecbLeverageRatio"), field("totalEcbDebt"), field("adjustedEbitda")),
                    List.of(goTo(dflt(), "Q-Q02")));
            var verdict = computedRuled("Q-Q02", List.of(opt("YES"), opt("NO")),
                    List.of(rule(fieldRanges("ecbLeverageRatio", halfOpen(0, 4)), "NO"),
                            rule(compare("totalEcbDebt", ComparisonOperator.GT, "6", "adjustedEbitda"), "YES"),
                            rule(dflt(), "NO")),
                    List.of(endFlags(fieldRanges("ecbLeverageRatio", halfOpen(0, 4)), Map.of()),
                            endFlags(dflt(), Map.of("ecbLeveragedFlag", "ECB_LEVERAGED"))));
            var result = validate(ecbDef("Q-F01", table, verdict));
            assertTrue(result.isValid(), () -> dump(result));
        }

        /** Q-S04: value from earlier answers, routing from how it was reached. */
        @Test
        void a_computed_question_with_value_rules_passes() {
            var status = scOpts("Q-S01", List.of(opt("UMC"), opt("SUBSIDIARY")),
                    List.of(goTo(eq("UMC"), "Q-S04"), goTo(eq("SUBSIDIARY"), "Q-S04")));
            var level = computedRuled("Q-S04", List.of(opt("BUSINESS_GROUP"), opt("BORROWER")),
                    List.of(rule(other("Q-S01", "UMC"), "BUSINESS_GROUP"), rule(dflt(), "BORROWER")),
                    List.of(endFlags(dflt(), Map.of("ecbLeveragedFlag", "ECB_NOT_LEVERAGED"))));
            var result = validate(ecbDef("Q-S01", status, level));
            assertTrue(result.isValid(), () -> dump(result));
        }

        /** Displayed, never walked — must not be reported unreachable. */
        @Test
        void a_display_only_computed_output_is_exempt_from_reachability() {
            var q1 = bool("Q1", List.of(end(eq("YES"), ECB), end(eq("NO"), NOT_REQUIRED)));
            var result = validate(def("Q1", q1, computedOutput("Q6")));
            assertFalse(has(result, "UNREACHABLE"));
            assertFalse(has(result, "NO_BRANCH"));
            assertTrue(result.isValid(), () -> dump(result));
        }

        @Test
        void a_lookup_naming_its_source_passes() {
            var result = validate(ecbDef("Q-S06",
                    lookup("Q-S06", "LOOKUP/COUNTERPARTY",
                            List.of(endFlags(dflt(), Map.of("ecbLeveragedFlag", "INR"))))));
            assertTrue(result.isValid(), () -> dump(result));
        }
    }

    // ============================================================ structure

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Structure {

        @Test
        void a_missing_entry_question_fails() {
            assertTrue(has(validate(def("NOPE", bool("Q1", List.of(end(dflt(), ECB))))), "MISSING_ENTRY"));
        }

        @Test
        void a_branch_to_an_unknown_question_fails() {
            assertTrue(has(validate(def("Q1",
                    bool("Q1", List.of(end(eq("YES"), ECB), goTo(eq("NO"), "GHOST"))))), "UNKNOWN_GOTO"));
        }

        @Test
        void a_duplicate_question_key_fails() {
            var a = bool("Q1", List.of(end(eq("YES"), ECB), end(eq("NO"), NOT_REQUIRED)));
            var b = bool("Q1", List.of(end(dflt(), ECB)));
            assertTrue(has(validate(def("Q1", a, b)), "DUPLICATE_KEY"));
        }

        @Test
        void a_missing_french_label_fails() {
            var q = question("Q1", QuestionType.BOOLEAN, false, true, null, List.of(), null,
                    List.of(opt("YES"), opt("NO")), List.of(), List.of(),
                    List.of(end(dflt(), ECB)), null);
            var noFrench = new Question(q.key(), q.type(), true, false, true, null, List.of(), null,
                    ql("English only", null), null, null, q.options(), List.of(), List.of(),
                    q.branches(), null);
            assertTrue(has(validate(def("Q1", noFrench)), "MISSING_LABEL_FR"));
        }

        @Test
        void an_uncovered_option_fails() {
            assertTrue(has(validate(def("Q1", bool("Q1", List.of(end(eq("YES"), ECB))))), "OPTION_UNCOVERED"));
        }

        /**
         * The ECB shape: the same rows serve both LBO orderings, so the graph really does contain
         * Q-T01 -> Q-C01 -> Q-S01 -> Q-S04 -> Q-T01. No analyst can walk it, because going round
         * needs Q01 to be both YES and NO — and at Q-S04 the "Q01 is YES" branch always fires
         * before the catch-all below it.
         */
        @Test
        void a_loop_that_no_answer_can_walk_is_not_a_cycle() {
            var lbo = sc("Q01", List.of(goTo(eq("YES"), "Q-T01"), goTo(eq("NO"), "Q-S01")));
            var transaction = checklist("Q-T01", List.of(item("tradeFinance")),
                    List.of(endFlags(agg(Aggregate.ANY_YES), Map.of("ecbLeveragedFlag", "INR")),
                            goTo(agg(Aggregate.ALL_NO), "Q-C01")));
            var creditEvent = sc("Q-C01", List.of(goTo(other("Q01", "YES"), "Q-S01"),
                    goTo(dflt(), "Q-F01")));
            var status = sc("Q-S01", List.of(goTo(dflt(), "Q-S04")));
            var level = computedRuled("Q-S04", List.of(opt("BUSINESS_GROUP")),
                    List.of(rule(dflt(), "BUSINESS_GROUP")),
                    List.of(goTo(other("Q01", "YES"), "Q-F01"), goTo(dflt(), "Q-T01")));
            var table = dataEntry("Q-F01", List.of(field("ebitda")), List.of(goTo(dflt(), "Q-END")));
            var end = sc("Q-END", List.of(endFlags(dflt(), Map.of("ecbLeveragedFlag", "ECB_LEVERAGED"))));

            var result = validate(ecbDef("Q01", lbo, transaction, creditEvent, status, level, table, end));
            assertFalse(has(result, "CYCLE"), () -> dump(result));
        }

        /**
         * The other half of the same bug: with the LBO flag pinned to YES the walk never takes
         * Q-T01's non-LBO branch, so questions only that path reaches must still be explored when
         * the walk comes round with Q01 = NO.
         */
        @Test
        void a_question_only_one_branch_of_a_flag_reaches_is_still_reachable() {
            var lbo = sc("Q01", List.of(goTo(eq("YES"), "Q-T01"), goTo(eq("NO"), "Q-T01")));
            var transaction = sc("Q-T01", List.of(goTo(other("Q01", "NO"), "Q-T02"),
                    goTo(dflt(), "Q-END")));
            var nonLboOnly = sc("Q-T02", List.of(goTo(dflt(), "Q-END")));
            var end = sc("Q-END", List.of(endFlags(dflt(), Map.of("ecbLeveragedFlag", "INR"))));

            var result = validate(ecbDef("Q01", lbo, transaction, nonLboOnly, end));
            assertFalse(has(result, "UNREACHABLE"), () -> dump(result));
        }

        /** The report has to say which edge to change, not merely that a loop exists. */
        @Test
        void a_cycle_message_names_the_whole_loop() {
            var q1 = sc("Q1", List.of(goTo(dflt(), "Q2")));
            var q2 = sc("Q2", List.of(goTo(dflt(), "Q3")));
            var q3 = sc("Q3", List.of(goTo(dflt(), "Q1")));

            var result = validate(ecbDef("Q1", q1, q2, q3));
            String message = result.errors().stream()
                    .filter(error -> error.code().equals("CYCLE"))
                    .map(ValidationResult.Error::message)
                    .findFirst()
                    .orElseThrow();
            assertTrue(message.contains("Q1 -> Q2 -> Q3"), message);
            assertTrue(message.contains("back to Q1"), message);
        }

        /** A loop with nothing to rule it out is still reported. */
        @Test
        void a_cycle_fails() {
            var q1 = bool("Q1", List.of(goTo(eq("YES"), "Q2"), end(eq("NO"), NOT_REQUIRED)));
            var q2 = bool("Q2", List.of(goTo(eq("YES"), "Q1"), end(eq("NO"), ECB)));
            assertTrue(has(validate(def("Q1", q1, q2)), "CYCLE"));
        }

        @Test
        void an_outcome_outside_the_catalogue_fails() {
            var def = defWithCatalogues(LeverageFormType.PRELIMINARY, "Q1",
                    List.of(bool("Q1", List.of(end(dflt(), ECB_AND_FED)))),
                    standardFlags(), standardFlagValues(), List.of(), List.of());
            var narrowed = new DecisionTreeDefinition(def.formType(), def.version(), def.status(),
                    def.defaultLocale(), def.locales(), def.entryQuestion(), def.sections(),
                    Map.of(ECB, new Outcome("ECB", List.of(LeverageFormType.ECB), Map.of())),
                    def.flags(), def.flagValueSets(), List.of(), List.of());
            assertTrue(has(validate(narrowed), "OUTCOME_NOT_DECLARED"));
        }
    }

    // ============================================================ replaces the EXTERNAL rules

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class ComputedSources {

        /** Replaces EXTERNAL_NO_DERIVED_FROM: a computed question needs exactly one source. */
        @Test
        void a_computed_question_with_neither_source_fails() {
            var orphan = question("C1", QuestionType.COMPUTED, true, false, null, List.of(), null,
                    List.of(opt("A")), List.of(), List.of(), List.of(end(dflt(), ECB)), null);
            assertTrue(has(validate(def("C1", orphan)), "COMPUTED_NO_SOURCE"));
        }

        @Test
        void a_computed_question_with_both_sources_fails() {
            var both = question("C1", QuestionType.COMPUTED, true, false, "OUTCOME",
                    List.of(rule(dflt(), "A")), null, List.of(opt("A")), List.of(), List.of(),
                    List.of(end(dflt(), ECB)), null);
            assertTrue(has(validate(def("C1", both)), "COMPUTED_BOTH_SOURCES"));
        }

        @Test
        void a_computed_question_that_is_editable_fails() {
            var editable = question("C1", QuestionType.COMPUTED, true, true, "OUTCOME", List.of(), null,
                    List.of(opt("A")), List.of(), List.of(), List.of(end(dflt(), ECB)), null);
            assertTrue(has(validate(def("C1", editable)), "COMPUTED_EDITABLE"));
        }

        @Test
        void a_value_rule_assigning_an_undeclared_option_fails() {
            var bad = computedRuled("C1", List.of(opt("A"), opt("B")),
                    List.of(rule(dflt(), "NOT_AN_OPTION")), List.of(end(dflt(), ECB)));
            assertTrue(has(validate(def("C1", bad)), "VALUE_RULE_UNKNOWN_VALUE"));
        }

        @Test
        void value_rules_on_a_non_computed_question_fail() {
            var bad = question("Q1", QuestionType.SINGLE_CHOICE, false, true, null,
                    List.of(rule(dflt(), "YES")), null, List.of(opt("YES"), opt("NO")),
                    List.of(), List.of(), List.of(end(dflt(), ECB)), null);
            assertTrue(has(validate(def("Q1", bad)), "VALUE_RULES_ON_NON_COMPUTED"));
        }
    }

    // ============================================================ numeric

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Numeric {

        @Test
        void range_routing_without_a_default_fails() {
            var table = dataEntry("FIN", List.of(field("ratio")), List.of(goTo(dflt(), "Q2")));
            var router = bool("Q2", List.of(endFlags(fieldRanges("ratio", band(4, 6)), Map.of())));
            assertTrue(has(validate(ecbDef("FIN", table, router)), "RANGE_ROUTING_NO_DEFAULT"));
        }

        @Test
        void an_impossible_range_fails() {
            var table = dataEntry("FIN", List.of(field("ratio")), List.of(goTo(dflt(), "Q2")));
            var router = bool("Q2", List.of(endFlags(fieldRanges("ratio", range(6.0, null, 4.0, null)), Map.of()),
                    endFlags(dflt(), Map.of())));
            assertTrue(has(validate(ecbDef("FIN", table, router)), "RANGE_IMPOSSIBLE"));
        }

        @Test
        void a_condition_on_an_unknown_field_fails() {
            var router = bool("Q1", List.of(endFlags(fieldRanges("ghost", band(4, 6)), Map.of()),
                    endFlags(dflt(), Map.of())));
            assertTrue(has(validate(ecbDef("Q1", router)), "COND_UNKNOWN_FIELD"));
        }

        @Test
        void a_comparison_against_an_unknown_field_fails() {
            var table = dataEntry("FIN", List.of(field("debt")), List.of(goTo(dflt(), "Q2")));
            var router = bool("Q2", List.of(
                    endFlags(compare("debt", ComparisonOperator.GT, "6", "ghost"), Map.of()),
                    endFlags(dflt(), Map.of())));
            assertTrue(has(validate(ecbDef("FIN", table, router)), "COMPARISON_UNKNOWN_FIELD"));
        }

        /** Conditions name a field bare, so a key reused across questions would be ambiguous. */
        @Test
        void the_same_field_key_in_two_questions_fails() {
            var first = dataEntry("F1", List.of(field("ebitda")), List.of(goTo(dflt(), "F2")));
            var second = dataEntry("F2", List.of(field("ebitda")), List.of(endFlags(dflt(), Map.of())));
            assertTrue(has(validate(ecbDef("F1", first, second)), "DATA_FIELD_DUPLICATE_IN_FORM"));
        }
    }

    // ============================================================ types

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class TypeRules {

        @Test
        void a_checklist_missing_an_aggregate_fails() {
            var checklist = checklist("EXCL", List.of(item("sme")),
                    List.of(endFlags(agg(Aggregate.ANY_YES), Map.of())));
            assertTrue(has(validate(ecbDef("EXCL", checklist)), "CHECKLIST_AGG_INCOMPLETE"));
        }

        @Test
        void an_aggregate_outside_a_checklist_fails() {
            var q1 = bool("Q1", List.of(end(agg(Aggregate.ANY_YES), ECB), end(dflt(), NOT_REQUIRED)));
            assertTrue(has(validate(def("Q1", q1)), "AGGREGATE_MISUSE"));
        }

        /** The financial table feeds the qualitative block — it must continue. */
        @Test
        void a_data_entry_that_terminates_fails() {
            var table = dataEntry("FIN", List.of(field("ebitda")), List.of(endFlags(dflt(), Map.of())));
            assertTrue(has(validate(ecbDef("FIN", table)), "DATA_ENTRY_TERMINAL"));
        }

        @Test
        void a_data_entry_with_no_typeable_box_fails() {
            var table = dataEntry("FIN", List.of(calcField("ratio")), List.of(goTo(dflt(), "Q2")));
            var next = bool("Q2", List.of(endFlags(dflt(), Map.of())));
            assertTrue(has(validate(ecbDef("FIN", table, next)), "DATA_NO_INPUT"));
        }

        @Test
        void a_calculated_box_that_is_also_editable_fails() {
            var editableCalc = new DataField("ratio", "G", ll("r", "r"), null, DataFieldType.NUMERIC,
                    true, true, "CALC/ratio", null, null);
            var table = dataEntry("FIN", List.of(editableCalc), List.of(goTo(dflt(), "Q2")));
            var next = bool("Q2", List.of(endFlags(dflt(), Map.of())));
            assertTrue(has(validate(ecbDef("FIN", table, next)), "DATA_FIELD_CALC_EDITABLE"));
        }

        @Test
        void a_lookup_without_a_source_fails() {
            var bad = question("Q-S06", QuestionType.LOOKUP, false, true, null, List.of(), null,
                    List.of(opt("A"), opt("B")), List.of(), List.of(),
                    List.of(endFlags(dflt(), Map.of())), null);
            assertTrue(has(validate(ecbDef("Q-S06", bad)), "LOOKUP_NO_SOURCE"));
        }

        @Test
        void a_cross_question_condition_naming_nothing_fails() {
            var q1 = bool("Q1", List.of(goTo(allOf(eq("YES"), in("GHOST", List.of("X"))), "Q2"),
                    end(dflt(), NOT_REQUIRED)));
            var q2 = bool("Q2", List.of(end(dflt(), ECB)));
            assertTrue(has(validate(def("Q1", q1, q2)), "COND_UNKNOWN_QUESTION"));
        }
    }

    // ============================================================ catalogues

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Catalogues {

        @Test
        void a_branch_setting_an_uncatalogued_flag_fails() {
            var q1 = bool("Q1", List.of(endFlags(dflt(), Map.of("madeUpFlag", "X"))));
            assertTrue(has(validate(ecbDef("Q1", q1)), "FLAG_UNKNOWN"));
        }

        @Test
        void a_branch_setting_a_value_outside_the_set_fails() {
            var q1 = bool("Q1", List.of(endFlags(dflt(), Map.of("ecbLeveragedFlag", "NOT_A_CODE"))));
            assertTrue(has(validate(ecbDef("Q1", q1)), "FLAG_VALUE_UNKNOWN"));
        }

        /** Set By is what stops ECB writing a FED-only code. */
        @Test
        void a_form_setting_another_forms_code_fails() {
            var q1 = bool("Q1", List.of(endFlags(dflt(), Map.of("ecbLeveragedFlag", "FED_NOT_LEVERAGED"))));
            assertTrue(has(validate(ecbDef("Q1", q1)), "FLAG_VALUE_FORM_NOT_ALLOWED"));
        }

        /** Q-Q03: every option must be a code of the flag it fills. */
        @Test
        void a_fills_flag_option_outside_the_value_set_fails() {
            var q = scFillingFlag("Q-Q03", List.of(opt("NONE"), opt("WRONG")),
                    List.of(endFlags(dflt(), Map.of())), "ecbCovenantStructure");
            assertTrue(has(validate(ecbDef("Q-Q03", q)), "FILLS_FLAG_OPTION_MISMATCH"));
        }

        @Test
        void a_fills_flag_naming_an_uncatalogued_flag_fails() {
            var q = scFillingFlag("Q1", List.of(opt("YES"), opt("NO")),
                    List.of(endFlags(dflt(), Map.of())), "ghostFlag");
            assertTrue(has(validate(ecbDef("Q1", q)), "FLAG_UNKNOWN"));
        }

        @Test
        void a_coded_flag_with_no_value_set_fails() {
            Map<String, FlagDefinition> flags = Map.of("orphan",
                    new FlagDefinition("orphan", ll("O", "O"), FlagStorage.CODE, null));
            var def = defWithCatalogues(LeverageFormType.ECB, "Q1",
                    List.of(bool("Q1", List.of(endFlags(dflt(), Map.of())))),
                    flags, standardFlagValues(), List.of(), List.of());
            assertTrue(has(validate(def), "FLAG_NO_VALUE_SET"));
        }

        @Test
        void a_validation_message_targeting_an_unknown_question_fails() {
            var message = new ValidationMessage("GHOST", null, ValidationRule.MANDATORY, "M1",
                    Severity.ERROR, ll("en", "fr"));
            var def = defWithCatalogues(LeverageFormType.ECB, "Q1",
                    List.of(bool("Q1", List.of(endFlags(dflt(), Map.of())))),
                    standardFlags(), standardFlagValues(), List.of(message), List.of());
            assertTrue(has(validate(def), "MESSAGE_UNKNOWN_QUESTION"));
        }

        @Test
        void a_validation_message_targeting_an_unknown_field_fails() {
            var message = new ValidationMessage(null, "ghostField", ValidationRule.MUST_BE_POSITIVE, "M1",
                    Severity.ERROR, ll("en", "fr"));
            var def = defWithCatalogues(LeverageFormType.ECB, "Q1",
                    List.of(bool("Q1", List.of(endFlags(dflt(), Map.of())))),
                    standardFlags(), standardFlagValues(), List.of(message), List.of());
            assertTrue(has(validate(def), "MESSAGE_UNKNOWN_FIELD"));
        }

        @Test
        void an_info_panel_on_an_unknown_flag_fails() {
            var panel = new InfoPanel("P1", ll("T", "T"), "COUNTERPARTY_CHARACTERISTICS",
                    List.of("leveragedFlag"), "ghostFlag", "INR");
            var def = defWithCatalogues(LeverageFormType.ECB, "Q1",
                    List.of(bool("Q1", List.of(endFlags(dflt(), Map.of())))),
                    standardFlags(), standardFlagValues(), List.of(), List.of(panel));
            assertTrue(has(validate(def), "PANEL_UNKNOWN_FLAG"));
        }

        @Test
        void an_info_panel_on_an_unknown_flag_value_fails() {
            var panel = new InfoPanel("P1", ll("T", "T"), "COUNTERPARTY_CHARACTERISTICS",
                    List.of("leveragedFlag"), "ecbLeveragedFlag", "NOT_A_CODE");
            var def = defWithCatalogues(LeverageFormType.ECB, "Q1",
                    List.of(bool("Q1", List.of(endFlags(dflt(), Map.of())))),
                    standardFlags(), standardFlagValues(), List.of(), List.of(panel));
            assertTrue(has(validate(def), "PANEL_UNKNOWN_FLAG_VALUE"));
        }

        @Test
        void two_codes_may_share_a_set_across_forms() {
            var inrSetter = bool("Q1", List.of(endFlags(dflt(), Map.of("ecbLeveragedFlag", "INR"))));
            assertTrue(validate(ecbDef("Q1", inrSetter)).isValid(),
                    "INR is Set By BOTH, so ECB may write it");
        }
    }

    // ============================================================ prefill and robustness

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Robustness {

        /** Shape only — whether FED/Q01 exists is another definition's business. */
        @Test
        void a_malformed_prefill_reference_fails() {
            var bad = prefilled("Q01", "not-a-reference", List.of(end(dflt(), ECB)));
            assertTrue(has(validate(def("Q01", bad)), "PREFILL_BAD_FORMAT"));
        }

        @Test
        void a_well_formed_prefill_reference_passes() {
            var good = prefilled("Q01", "FED/Q01", List.of(end(dflt(), ECB)));
            assertTrue(validate(def("Q01", good)).isValid());
        }

        @Test
        void a_null_definition_is_reported_not_thrown() {
            assertTrue(has(validate(null), "NULL_DEFINITION"));
        }

        /** One malformed question must not abort validation of the rest. */
        @Test
        void a_null_question_does_not_stop_the_pass() {
            var def = new DecisionTreeDefinition(LeverageFormType.ECB, 1, DefinitionStatus.PUBLISHED, "EN",
                    List.of("EN", "FR"), "Q1",
                    List.of(new Section("s", 1, ll("S", "S"),
                            java.util.Arrays.asList(bool("Q1", List.of(end(eq("YES"), ECB))), null))),
                    standardOutcomes(), standardFlags(), standardFlagValues(), List.of(), List.of());
            var result = validate(def);
            assertFalse(result.isValid());
            assertTrue(has(result, "OPTION_UNCOVERED"), "the surviving question was still checked");
        }

        @Test
        void every_error_carries_a_form_and_a_code() {
            var result = validate(def("NOPE", bool("Q1", List.of(end(dflt(), ECB)))));
            assertFalse(result.errors().isEmpty());
            result.errors().forEach(error -> {
                assertNotNull(error.formType());
                assertNotNull(error.code());
                assertNotNull(error.aspect());
            });
        }
    }
}