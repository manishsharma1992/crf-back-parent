package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.application.leverage.definitionimport.InMemoryWorkbookSource.row;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Builds a small but STRUCTURALLY COMPLETE three-form workbook in memory and asserts the
 * assembler produces three definitions — then runs the real {@code DecisionTreeValidator} over
 * them, so this test also proves the parser emits a shape the validator accepts.
 *
 * <p>That last part is the point. Parser and validator were written against the same template but
 * from opposite ends; nothing else in the suite would catch them disagreeing.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DecisionTreeAssemblerTest {

    private DecisionTreeAssembler assembler;
    private DecisionTreeValidator validator;

    @BeforeAll
    void setUp() {
        ConditionExpressionParser conditions = new ConditionExpressionParser();
        assembler = new DecisionTreeAssembler(
                new FormsSheetParser(new FlagValuesSheetParser()),
                new FieldsSheetParser(),
                new QuestionSheetParser(
                        new LabelParser(),
                        new OptionsParser(),
                        new BranchExpressionParser(conditions),
                        new ValueRuleExpressionParser(conditions)));
        validator = new DecisionTreeValidator();
    }

    private static final List<String> Q_HEADERS = row(
            "Question Key", "Type", "Mandatory", "Computed", "Editable", "Derived From", "Value Rules",
            "Prefill From", "Label EN", "Bullets EN", "Label FR", "Bullets FR", "Subtitle EN", "Subtitle FR",
            "Note EN", "Note FR", "Note Bullets EN", "Note Bullets FR", "Options", "Items", "Branches", "Fills Flag");

    private static List<String> q(String key, String type, String options, String items,
                                 String branches, String fillsFlag, String derivedFrom, String valueRules,
                                 String computed, String editable) {
        return row(key, type, "Yes", computed, editable, derivedFrom, valueRules, "",
                key + " EN", "", key + " FR", "", "", "", "", "", "", "",
                options, items, branches, fillsFlag);
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
                        row("ECB", "ecbCovenantStructure", "Covenant Structure", "Structure", "CODE",
                                "COVENANT_STRUCTURE"),
                        row("ECB", "ecbLeverageRatio", "ECB Leverage Ratio", "Ratio BCE", "NUMBER", ""),
                        row("FED", "fedLeveragedFlag", "FED Leveraged Flag", "Flag FED", "CODE", "LEVERAGED_FLAG"),
                        row(),
                        row("Form", "Question Key", "Field Key", "Rule", "Message Key", "Severity",
                                "Text EN", "Text FR"),
                        row("ECB", "", "", "MANDATORY", "ECB_CHECKLIST_MANDATORY", "ERROR", "Answer them", "Repondez"),
                        row("ECB", "Q-F01", "ebitda", "SOURCE_EMPTY", "ECB_EBITDA_EMPTY", "ERROR",
                                "EBITDA is empty", "EBITDA vide"),
                        row(),
                        row("Panel Key", "Title EN", "Title FR", "Source", "Fields", "Shown When"),
                        row("CURRENT_LEVERAGE_TX_FLAGS", "Current Flags", "Flags courants",
                                "COUNTERPARTY_CHARACTERISTICS", "leveragedFlag ; leverageDate",
                                "ecbLeveragedFlag is INR")))
                .sheet("Flag Values", List.of(
                        row("Value Set", "Code", "Stored Value", "Display EN", "Display FR", "Set By"),
                        row("LEVERAGED_FLAG", "ECB_NOT_LEVERAGED", "0", "ECB Not Leveraged", "", "ECB"),
                        row("LEVERAGED_FLAG", "ECB_LEVERAGED", "1", "ECB Leveraged", "", "ECB"),
                        row("LEVERAGED_FLAG", "INR", "2", "INR", "", "BOTH"),
                        row("LEVERAGED_FLAG", "FED_NOT_LEVERAGED", "3", "FED Not Leveraged", "", "FED"),
                        row("COVENANT_STRUCTURE", "NONE", "0", "No Covenant", "Sans covenant", "ECB"),
                        row("COVENANT_STRUCTURE", "FULL", "1", "Full Covenant", "Full Covenant", "ECB")))
                .sheet("Preliminary Q", List.of(
                        Q_HEADERS,
                        q("P01", "SINGLE_CHOICE", "YES|Yes|Oui ; NO|No|Non", "",
                                "YES -> END, outcome=ECB\nNO -> END, outcome=NOT_REQUIRED", "", "", "", "No", "Yes")))
                .sheet("ECB Q", List.of(
                        Q_HEADERS,
                        q("Q01", "CHECKLIST", "", "sovereign|Sovereign|Souverain ; igb|Investment Grade|IG",
                                "ANY_YES -> END, flags: ecbLeveragedFlag=ECB_NOT_LEVERAGED\nALL_NO -> Q-F01",
                                "", "", "", "No", "Yes"),
                        q("Q-F01", "DATA_ENTRY", "", "", "* -> Q-Q02", "", "", "", "No", "Yes"),
                        q("Q-Q02", "COMPUTED", "YES|Yes|Oui ; NO|No|Non", "",
                                "field ecbLeverageRatio range [0 .. <4] -> END\n* -> Q-Q03", "", "",
                                "field ecbLeverageRatio range [0 .. <4] -> NO\n"
                                        + "field totalEcbDebt > 6 x field adjustedEbitda -> YES\n* -> NO",
                                "Yes", "No"),
                        q("Q-Q03", "SINGLE_CHOICE", "NONE|No Covenant|Sans covenant ; FULL|Full Covenant|Full", "",
                                "* -> END, flags: ecbLeveragedFlag=ECB_LEVERAGED", "ecbCovenantStructure",
                                "", "", "No", "Yes")))
                .sheet("FED Q", List.of(
                        Q_HEADERS,
                        q("F01", "SINGLE_CHOICE", "YES|Yes|Oui ; NO|No|Non", "",
                                "YES -> END, flags: fedLeveragedFlag=FED_NOT_LEVERAGED\n"
                                        + "NO -> END, flags: fedLeveragedFlag=INR", "", "", "", "No", "Yes")))
                .sheet("Fields", List.of(
                        row("Form", "Question Key", "Group", "Field Key", "Label EN", "Label FR", "Type",
                                "Mandatory", "Editable", "Derived From", "Note EN", "Note FR",
                                "Formula (documentation only)", "Fills Flag"),
                        row("ECB", "Q-F01", "ECB Leverage Ratio", "ecbLeverageRatio", "ECB Leverage Ratio",
                                "Ratio de levier BCE", "NUMERIC", "Yes", "No", "CALC/ecbLeverageRatio", "", "",
                                "= Total ECB Debt / Adjusted EBITDA", "ecbLeverageRatio"),
                        row("ECB", "Q-F01", "ECB Leverage Ratio", "ebitda", "EBITDA", "EBITDA", "NUMERIC",
                                "Yes", "Yes", "FINANCIALS/ebitda", "", "", "", ""),
                        row("ECB", "Q-F01", "ECB Leverage Ratio", "adjustedEbitda", "Adjusted EBITDA",
                                "EBITDA ajuste", "NUMERIC", "No", "No", "CALC/adjustedEbitda", "", "", "", ""),
                        row("ECB", "Q-F01", "ECB Leverage Ratio", "totalEcbDebt", "Total ECB Debt",
                                "Dette ECB totale", "NUMERIC", "Yes", "No", "CALC/totalEcbDebt", "", "", "", "")));
    }

    private AssembledWorkbook assemble(ImportIssues issues) {
        return assembler.assemble(workbook(), DefinitionStatus.PUBLISHED, form -> 7, issues);
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Assembly {

        @Test
        void produces_three_definitions_from_one_workbook() {
            ImportIssues issues = new ImportIssues();
            AssembledWorkbook assembled = assemble(issues);
            assertTrue(issues.isEmpty(), () -> issues.describeAll().toString());
            assertTrue(assembled.isComplete());
            assertEquals(List.of(), assembled.missingForms());
        }

        @Test
        void carries_version_and_status_from_the_caller() {
            DecisionTreeDefinition ecb = assemble(new ImportIssues()).definitions()
                    .get(LeverageFormType.ECB);
            assertEquals(7, ecb.version());
            assertEquals(DefinitionStatus.PUBLISHED, ecb.status());
            assertEquals("Q01", ecb.entryQuestion());
            assertEquals(List.of("EN", "FR"), ecb.locales());
        }

        @Test
        void each_form_gets_only_its_own_flags() {
            AssembledWorkbook a = assemble(new ImportIssues());
            assertEquals(List.of("ecbLeveragedFlag", "ecbCovenantStructure", "ecbLeverageRatio"),
                    List.copyOf(a.definitions().get(LeverageFormType.ECB).flags().keySet()));
            assertEquals(List.of("fedLeveragedFlag"),
                    List.copyOf(a.definitions().get(LeverageFormType.FED).flags().keySet()));
        }

        /** ECB must be able to READ a FED-written code, so the value sets are shared whole. */
        @Test
        void every_form_gets_the_whole_shared_value_sets() {
            AssembledWorkbook a = assemble(new ImportIssues());
            for (LeverageFormType form : LeverageFormType.values()) {
                assertEquals(4, a.definitions().get(form).flagValueSets().get("LEVERAGED_FLAG").size(),
                        "value sets are global, not per form: " + form);
            }
        }

        /** An outcome authored on ECB should fail validation, not import silently. */
        @Test
        void outcomes_belong_to_the_preliminary_form_only() {
            AssembledWorkbook a = assemble(new ImportIssues());
            assertEquals(2, a.definitions().get(LeverageFormType.PRELIMINARY).outcomes().size());
            assertTrue(a.definitions().get(LeverageFormType.ECB).outcomes().isEmpty());
        }

        @Test
        void messages_and_panels_land_on_their_form() {
            DecisionTreeDefinition ecb = assemble(new ImportIssues()).definitions().get(LeverageFormType.ECB);
            assertEquals(2, ecb.validationMessages().size());
            assertEquals(1, ecb.infoPanels().size());
            assertEquals("ecbLeveragedFlag", ecb.infoPanels().get(0).whenFlagKey());
            assertEquals("INR", ecb.infoPanels().get(0).whenFlagValue());
            assertTrue(assemble(new ImportIssues()).definitions()
                    .get(LeverageFormType.FED).infoPanels().isEmpty());
        }

        @Test
        void data_entry_boxes_are_joined_and_findable_by_key_across_the_form() {
            DecisionTreeDefinition ecb = assemble(new ImportIssues()).definitions().get(LeverageFormType.ECB);
            assertEquals(4, ecb.questions().stream().mapToInt(qn -> qn.fields().size()).sum());
            assertTrue(ecb.field("ecbLeverageRatio").isPresent());
            assertTrue(ecb.field("adjustedEbitda").isPresent());
            assertTrue(ecb.field("nonsense").isEmpty());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class AgreesWithTheValidator {

        /** The contract test: what the parser emits, the validator accepts. */
        @Test
        void all_three_assembled_definitions_are_structurally_valid() {
            AssembledWorkbook assembled = assemble(new ImportIssues());
            for (LeverageFormType form : LeverageFormType.values()) {
                ValidationResult result = validator.validate(assembled.definitions().get(form));
                assertTrue(result.isValid(), () -> form + " failed validation: " + result.errors());
            }
        }

        @Test
        void a_branch_to_a_missing_question_is_caught_downstream() {
            InMemoryWorkbookSource broken = workbook().sheet("FED Q", List.of(
                    Q_HEADERS,
                    q("F01", "SINGLE_CHOICE", "YES|Yes|Oui ; NO|No|Non", "",
                            "YES -> Q_GHOST\nNO -> END, flags: fedLeveragedFlag=INR", "", "", "", "No", "Yes")));
            ImportIssues issues = new ImportIssues();
            AssembledWorkbook assembled =
                    assembler.assemble(broken, DefinitionStatus.PUBLISHED, form -> 1, issues);
            assertTrue(issues.isEmpty(), "a dangling target parses fine; only the validator can see it");
            ValidationResult result = validator.validate(assembled.definitions().get(LeverageFormType.FED));
            assertTrue(result.errors().stream().anyMatch(e -> e.code().equals("UNKNOWN_GOTO")));
        }

        /** ECB may not write a FED-only code — that is what Set By is for. */
        @Test
        void a_form_setting_another_forms_code_is_rejected() {
            InMemoryWorkbookSource broken = workbook().sheet("ECB Q", List.of(
                    Q_HEADERS,
                    q("Q01", "CHECKLIST", "", "sovereign|Sovereign|Souverain",
                            "ANY_YES -> END, flags: ecbLeveragedFlag=FED_NOT_LEVERAGED\nALL_NO -> END",
                            "", "", "", "No", "Yes")));
            AssembledWorkbook assembled =
                    assembler.assemble(broken, DefinitionStatus.PUBLISHED, form -> 1, new ImportIssues());
            ValidationResult result = validator.validate(assembled.definitions().get(LeverageFormType.ECB));
            assertTrue(result.errors().stream().anyMatch(e -> e.code().equals("FLAG_VALUE_FORM_NOT_ALLOWED")),
                    () -> result.errors().toString());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class IncompleteWorkbooks {

        @Test
        void a_missing_form_is_reported_and_the_others_still_assemble() {
            InMemoryWorkbookSource wb = workbook().sheet("FED Q", List.of(Q_HEADERS));
            ImportIssues issues = new ImportIssues();
            AssembledWorkbook assembled =
                    assembler.assemble(wb, DefinitionStatus.PUBLISHED, form -> 1, issues);
            assertTrue(issues.all().stream().anyMatch(i -> i.code().equals("FORM_NO_QUESTIONS")));
            assertTrue(assembled.definition(LeverageFormType.ECB).isPresent());
        }

        @Test
        void a_form_with_no_metadata_row_is_skipped_not_half_built() {
            InMemoryWorkbookSource wb = workbook().sheet("Forms", List.of(
                    row("Form Type", "Default Locale", "Locales", "Entry Question"),
                    row("PRELIMINARY", "EN", "EN;FR", "P01"),
                    row("ECB", "EN", "EN;FR", "Q01")));
            ImportIssues issues = new ImportIssues();
            AssembledWorkbook assembled =
                    assembler.assemble(wb, DefinitionStatus.PUBLISHED, form -> 1, issues);
            assertFalse(assembled.isComplete());
            assertEquals(List.of(LeverageFormType.FED), assembled.missingForms());
            assertTrue(issues.all().stream().anyMatch(i -> i.code().equals("FORM_MISSING")));
        }
    }
}
