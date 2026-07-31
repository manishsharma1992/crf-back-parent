package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeValidator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.application.leverage.definitionimport.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.application.leverage.definitionimport
        .InMemoryWorkbookSource.row;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The use case end to end, without Spring or a database: a real workbook in, three stored
 * definitions out.
 *
 * <p>The workbook fixture is deliberately the same shape as {@code DecisionTreeAssemblerTest}'s —
 * small, but structurally complete enough that the real validator passes it. Anything less and the
 * "publish" path would never be exercised.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DecisionTreeImportServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-31T09:00:00Z");

    private InMemoryDecisionTreeDefinitionRepository repository;
    private DecisionTreeImportService service;

    @BeforeEach
    void setUp() {
        ConditionExpressionParser conditions = new ConditionExpressionParser();
        DecisionTreeAssembler assembler = new DecisionTreeAssembler(
                new FormsSheetParser(new FlagValuesSheetParser()),
                new FieldsSheetParser(),
                new QuestionSheetParser(new LabelParser(), new OptionsParser(),
                        new BranchExpressionParser(conditions),
                        new ValueRuleExpressionParser(conditions)));
        repository = new InMemoryDecisionTreeDefinitionRepository();
        service = new DecisionTreeImportService(assembler, new DecisionTreeValidator(), repository,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static final List<String> Q_HEADERS = row(
            "Question Key", "Type", "Mandatory", "Computed", "Editable", "Derived From", "Value Rules",
            "Prefill From", "Label EN", "Bullets EN", "Label FR", "Bullets FR", "Subtitle EN", "Subtitle FR",
            "Note EN", "Note FR", "Note Bullets EN", "Note Bullets FR", "Options", "Items", "Branches", "Fills Flag");

    private static List<String> q(String key, String branches) {
        return row(key, "SINGLE_CHOICE", "Yes", "No", "Yes", "", "", "",
                key + " EN", "", key + " FR", "", "", "", "", "", "", "",
                "YES|Yes|Oui ; NO|No|Non", "", branches, "");
    }

    private InMemoryWorkbookSource workbook() {
        return new InMemoryWorkbookSource()
                .sheet("Forms", List.of(
                        row("Form Type", "Default Locale", "Locales", "Entry Question"),
                        row("PRELIMINARY", "EN", "EN;FR", "P01"),
                        row("ECB", "EN", "EN;FR", "Q01"),
                        row("FED", "EN", "EN;FR", "F01"),
                        row(),
                        row("Outcome Code", "Display Value", "Forms To Show", "Forced Flags"),
                        row("ECB", "ECB only", "ECB", ""),
                        row("NOT_REQUIRED", "Not applicable", "", ""),
                        row(),
                        row("Form", "Flag Key", "Display EN", "Display FR", "Stored As", "Value Set"),
                        row("ECB", "ecbLeveragedFlag", "Leveraged Flag", "Flag Leveraged", "CODE", "LEVERAGED_FLAG"),
                        row("FED", "fedLeveragedFlag", "FED Leveraged Flag", "Flag FED", "CODE", "LEVERAGED_FLAG")))
                .sheet("Flag Values", List.of(
                        row("Value Set", "Code", "Stored Value", "Display EN", "Display FR", "Set By"),
                        row("LEVERAGED_FLAG", "ECB_NOT_LEVERAGED", "0", "ECB Not Leveraged", "", "ECB"),
                        row("LEVERAGED_FLAG", "INR", "2", "INR", "", "BOTH"),
                        row("LEVERAGED_FLAG", "FED_NOT_LEVERAGED", "3", "FED Not Leveraged", "", "FED")))
                .sheet("Preliminary Q", List.of(Q_HEADERS,
                        q("P01", "YES -> END, outcome=ECB\nNO -> END, outcome=NOT_REQUIRED")))
                .sheet("ECB Q", List.of(Q_HEADERS,
                        q("Q01", "YES -> END, flags: ecbLeveragedFlag=ECB_NOT_LEVERAGED\n"
                                + "NO -> END, flags: ecbLeveragedFlag=INR")))
                .sheet("FED Q", List.of(Q_HEADERS,
                        q("F01", "YES -> END, flags: fedLeveragedFlag=FED_NOT_LEVERAGED\n"
                                + "NO -> END, flags: fedLeveragedFlag=INR")))
                .sheet("Fields", List.of(
                        row("Form", "Question Key", "Group", "Field Key", "Label EN", "Label FR", "Type",
                                "Mandatory", "Editable", "Derived From", "Note EN", "Note FR",
                                "Formula (documentation only)", "Fills Flag")));
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class HappyPath {

        @Test
        void publishes_all_three_forms() {
            ImportOutcome outcome = service.importWorkbook(workbook(), ImportMode.PUBLISH);
            assertEquals(ImportStatus.PUBLISHED, outcome.status(), () -> outcome.report().toString());
            assertTrue(outcome.report().isEmpty());
            assertEquals(3, repository.rows().size());
            for (LeverageFormType form : LeverageFormType.values()) {
                assertTrue(repository.findInForce(form, NOW).isPresent(), form.toString());
            }
        }

        @Test
        void first_import_is_version_one_for_every_form() {
            ImportOutcome outcome = service.importWorkbook(workbook(), ImportMode.PUBLISH);
            assertEquals(1, outcome.publishedVersions().get(LeverageFormType.ECB));
            assertEquals(1, repository.currentVersion(LeverageFormType.FED));
        }

        /** The point of versioning: yesterday's analysis keeps yesterday's rules. */
        @Test
        void a_second_import_supersedes_without_deleting() {
            service.importWorkbook(workbook(), ImportMode.PUBLISH);
            ImportOutcome second = service.importWorkbook(workbook(), ImportMode.PUBLISH);

            assertEquals(2, second.publishedVersions().get(LeverageFormType.ECB));
            assertEquals(6, repository.rows().size(), "nothing is deleted; the old rows are closed");
            assertEquals(2, repository.findInForce(LeverageFormType.ECB, NOW).orElseThrow().version());
        }

        @Test
        void stamps_every_definition_with_the_same_instant() {
            service.importWorkbook(workbook(), ImportMode.PUBLISH);
            assertTrue(repository.rows().stream().allMatch(r -> r.validFrom().equals(NOW)));
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class DryRun {

        @Test
        void validates_without_writing_anything() {
            ImportOutcome outcome = service.importWorkbook(workbook(), ImportMode.DRY_RUN);
            assertEquals(ImportStatus.VALIDATED, outcome.status());
            assertTrue(repository.rows().isEmpty());
            assertTrue(outcome.publishedVersions().isEmpty());
        }

        /** A rehearsal must report exactly what a publish would, or it is worthless. */
        @Test
        void reports_the_same_problems_a_publish_would() {
            InMemoryWorkbookSource broken = workbook().sheet("FED Q", List.of(Q_HEADERS,
                    q("F01", "YES -> Q_GHOST\nNO -> END, flags: fedLeveragedFlag=INR")));
            List<String> dryRun = service.importWorkbook(broken, ImportMode.DRY_RUN).report();
            List<String> publish = service.importWorkbook(broken, ImportMode.PUBLISH).report();
            assertEquals(dryRun, publish);
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Rejection {

        /**
         * ECB and FED share a value set and reference each other's codes; publishing one without
         * the other is not a coherent state, so one bad cell rejects the workbook.
         */
        @Test
        void one_broken_form_stops_the_other_two_being_written() {
            InMemoryWorkbookSource broken = workbook().sheet("FED Q", List.of(Q_HEADERS,
                    q("F01", "YES -> Q_GHOST\nNO -> END, flags: fedLeveragedFlag=INR")));
            ImportOutcome outcome = service.importWorkbook(broken, ImportMode.PUBLISH);

            assertEquals(ImportStatus.REJECTED, outcome.status());
            assertTrue(repository.rows().isEmpty(), "nothing is written when anything is wrong");
            assertTrue(outcome.report().stream().anyMatch(line -> line.contains("UNKNOWN_GOTO")));
        }

        @Test
        void the_report_points_at_the_offending_cell() {
            InMemoryWorkbookSource broken = workbook().sheet("FED Q", List.of(Q_HEADERS,
                    q("F01", "YES -> Q_GHOST\nNO -> END, flags: fedLeveragedFlag=INR")));
            ImportOutcome outcome = service.importWorkbook(broken, ImportMode.PUBLISH);
            assertTrue(outcome.report().stream().anyMatch(line ->
                            line.startsWith("Sheet 'FED Q', row 2, column 'Branches' (line 1)")),
                    () -> outcome.report().toString());
        }

        @Test
        void a_parse_issue_alone_is_enough_to_reject() {
            InMemoryWorkbookSource broken = workbook().sheet("ECB Q", List.of(Q_HEADERS,
                    q("Q01", "YES END, flags: ecbLeveragedFlag=INR")));
            ImportOutcome outcome = service.importWorkbook(broken, ImportMode.PUBLISH);
            assertEquals(ImportStatus.REJECTED, outcome.status());
            assertTrue(outcome.report().stream().anyMatch(line -> line.contains("BRANCH_NO_ARROW")));
        }

        /** A form that produced nothing raises no validation errors, so it is called out by name. */
        @Test
        void a_missing_form_is_named_in_the_report() {
            InMemoryWorkbookSource broken = workbook().sheet("Forms", List.of(
                    row("Form Type", "Default Locale", "Locales", "Entry Question"),
                    row("PRELIMINARY", "EN", "EN;FR", "P01"),
                    row("ECB", "EN", "EN;FR", "Q01")));
            ImportOutcome outcome = service.importWorkbook(broken, ImportMode.PUBLISH);
            assertEquals(ImportStatus.REJECTED, outcome.status());
            assertTrue(outcome.report().stream().anyMatch(line -> line.contains("No definition could be built for FED")),
                    () -> outcome.report().toString());
        }

        @Test
        void a_rejected_second_import_leaves_the_first_in_force() {
            service.importWorkbook(workbook(), ImportMode.PUBLISH);
            InMemoryWorkbookSource broken = workbook().sheet("FED Q", List.of(Q_HEADERS,
                    q("F01", "YES -> Q_GHOST\nNO -> END, flags: fedLeveragedFlag=INR")));
            service.importWorkbook(broken, ImportMode.PUBLISH);

            assertEquals(3, repository.rows().size());
            assertEquals(1, repository.findInForce(LeverageFormType.ECB, NOW).orElseThrow().version());
        }
    }
}
