package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.ValidationResult;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.ValidationResult.Error;
import static com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.ValidationResult.Error.Aspect;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SourceLocatorTest {

    private SourceIndex index;
    private ExcelSourceLocator locator;

    @BeforeAll
    void setUp() {
        index = SourceIndex.recording();
        index.form(LeverageFormType.ECB, 6);
        index.question(LeverageFormType.ECB, "Q-S04", 15);
        index.question(LeverageFormType.ECB, "Q-F01", 18);
        index.question(LeverageFormType.FED, "F01", 4);
        index.field(LeverageFormType.ECB, "Q-F01", "adjustedEbitda", 11);
        index.flag(LeverageFormType.ECB, "ecbLeveragedFlag", 17);
        index.flagValue("LEVERAGED_FLAG", "INR", 6);
        index.validationMessage(LeverageFormType.ECB, "ECB_EBITDA_EMPTY", 30);
        index.infoPanel("CURRENT_LEVERAGE_TX_FLAGS", 47);
        locator = new ExcelSourceLocator(index);
    }

    private Optional<SourceLocation> locate(Error error) {
        return locator.locate(error);
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class QuestionTab {

        @Test
        void resolves_a_question_error_to_sheet_row_and_column() {
            SourceLocation loc = locate(Error.question(
                    LeverageFormType.ECB, "Q-S04", Aspect.VALUE_RULES, "X", "msg")).orElseThrow();
            assertEquals("ECB Q", loc.sheet());
            assertEquals(15, loc.row());
            assertEquals("Value Rules", loc.columnHeader());
            assertNull(loc.line());
        }

        /** A branch index becomes the LINE inside the multi-line cell — where the grammar is densest. */
        @Test
        void a_branch_index_becomes_a_line_number() {
            SourceLocation loc = locate(Error.branch(
                    LeverageFormType.ECB, "Q-S04", 2, "X", "msg")).orElseThrow();
            assertEquals("Branches", loc.columnHeader());
            assertEquals(3, loc.line(), "branchIndex is 0-based, the BA counts from 1");
            assertEquals("Sheet 'ECB Q', row 15, column 'Branches' (line 3)", loc.describe());
        }

        @Test
        void each_form_resolves_to_its_own_tab() {
            assertEquals("FED Q", locate(Error.question(
                    LeverageFormType.FED, "F01", Aspect.OPTIONS, "X", "m")).orElseThrow().sheet());
        }

        @Test
        void a_reachability_error_gives_the_row_but_no_column() {
            SourceLocation loc = locate(Error.question(
                    LeverageFormType.ECB, "Q-S04", Aspect.REACHABILITY, "UNREACHABLE", "m")).orElseThrow();
            assertEquals(15, loc.row());
            assertNull(loc.columnHeader(), "no single column owns reachability");
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class OtherTabs {

        @Test
        void a_field_error_points_at_the_fields_tab() {
            SourceLocation loc = locate(Error.field(
                    LeverageFormType.ECB, "Q-F01", "adjustedEbitda", "X", "m")).orElseThrow();
            assertEquals("Fields", loc.sheet());
            assertEquals(11, loc.row());
        }

        @Test
        void catalogue_errors_point_at_forms_or_flag_values() {
            assertEquals("Forms", locate(Error.catalogue(LeverageFormType.ECB, Aspect.FLAGS_CATALOGUE,
                    "ecbLeveragedFlag", "X", "m")).orElseThrow().sheet());
            assertEquals(30, locate(Error.catalogue(LeverageFormType.ECB, Aspect.VALIDATION_MESSAGES,
                    "ECB_EBITDA_EMPTY", "X", "m")).orElseThrow().row());
            assertEquals(47, locate(Error.catalogue(LeverageFormType.ECB, Aspect.INFO_PANELS,
                    "CURRENT_LEVERAGE_TX_FLAGS", "X", "m")).orElseThrow().row());
            assertEquals("Flag Values", locate(Error.catalogue(LeverageFormType.ECB, Aspect.FLAG_VALUES,
                    "LEVERAGED_FLAG/INR", "X", "m")).orElseThrow().sheet());
        }

        @Test
        void a_form_level_error_falls_back_to_the_metadata_row() {
            SourceLocation loc = locate(Error.form(LeverageFormType.ECB, "MISSING_ENTRY", "m")).orElseThrow();
            assertEquals("Forms", loc.sheet());
            assertEquals(6, loc.row());
            assertEquals("Form Type", loc.columnHeader());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class WhenNothingIsKnown {

        /** Returning empty is deliberate: a half-right cell sends the BA to the wrong row. */
        @Test
        void an_unrecorded_question_yields_no_location() {
            assertTrue(locate(Error.question(
                    LeverageFormType.ECB, "Q-NEVER-PARSED", Aspect.BRANCHES, "X", "m")).isEmpty());
        }

        @Test
        void a_discarding_index_never_locates_anything() {
            SourceLocator none = new ExcelSourceLocator(SourceIndex.discarding());
            assertTrue(none.locate(Error.question(
                    LeverageFormType.ECB, "Q-S04", Aspect.BRANCHES, "X", "m")).isEmpty());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Report {

        @Test
        void a_located_error_reads_as_a_cell_reference() {
            var report = new ValidationReportAssembler(locator).toReport(new ValidationResult(List.of(
                    Error.branch(LeverageFormType.ECB, "Q-S04", 1, "UNKNOWN_GOTO",
                            "Branch points to unknown question 'Q-GHOST'"))));
            assertEquals("Sheet 'ECB Q', row 15, column 'Branches' (line 2) — "
                    + "[UNKNOWN_GOTO] Branch points to unknown question 'Q-GHOST'", report.get(0));
        }

        @Test
        void an_unlocatable_error_still_names_form_question_and_branch() {
            var report = new ValidationReportAssembler(SourceLocator.none()).toReport(new ValidationResult(
                    List.of(Error.branch(LeverageFormType.ECB, "Q-S04", 1, "UNKNOWN_GOTO", "boom"))));
            assertEquals("ECB / Q-S04 / branch 2 — [UNKNOWN_GOTO] boom", report.get(0));
        }

        /** Parse issues come first: an unreadable cell usually explains the errors after it. */
        @Test
        void parse_issues_precede_validation_errors() {
            ImportIssues issues = new ImportIssues();
            issues.add(SourceLocation.of("ECB Q", 15, "Branches"), "BRANCH_NO_ARROW", "no arrow");
            var report = new ValidationReportAssembler(locator).toReport(issues, Map.of(
                    LeverageFormType.ECB, new ValidationResult(List.of(
                            Error.question(LeverageFormType.ECB, "Q-S04", Aspect.BRANCHES, "NO_BRANCH", "dead end")))));
            assertEquals(2, report.size());
            assertTrue(report.get(0).contains("BRANCH_NO_ARROW"));
            assertTrue(report.get(1).contains("NO_BRANCH"));
        }
    }
}
