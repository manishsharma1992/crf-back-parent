package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.Bullet;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.*;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;

import static com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.application.leverage.definitionimport.InMemoryWorkbookSource.row;
import static org.junit.jupiter.api.Assertions.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class QuestionSheetParserTest {

    private final ConditionExpressionParser conditions = new ConditionExpressionParser();
    private final LabelParser labels = new LabelParser();
    private final OptionsParser options = new OptionsParser();
    private final QuestionSheetParser parser = new QuestionSheetParser(
            labels, options,
            new BranchExpressionParser(conditions),
            new ValueRuleExpressionParser(conditions));
    private final FieldsSheetParser fieldsParser = new FieldsSheetParser();

    private ImportIssues issues;

    private ImportIssues fresh() {
        issues = new ImportIssues();
        return issues;
    }

    private boolean hasIssue(String code) {
        return issues.all().stream().anyMatch(i -> i.code().equals(code));
    }

    private static final List<String> HEADERS = row(
            "Question Key", "Type", "Mandatory", "Computed", "Editable", "Derived From", "Value Rules",
            "Prefill From", "Label EN", "Bullets EN", "Label FR", "Bullets FR", "Subtitle EN", "Subtitle FR",
            "Note EN", "Note FR", "Note Bullets EN", "Note Bullets FR", "Options", "Items", "Branches", "Fills Flag");

    /** Q-Q03 exactly as authored: options, a note, a Fills Flag and a two-line Branches cell. */
    private InMemoryWorkbookSource ecbSheet() {
        return new InMemoryWorkbookSource().sheet("ECB Q", List.of(
                row("ECB — Questions"),
                row("The real ECB tree."),
                HEADERS,
                row("Q01", "SINGLE_CHOICE", "Yes", "No", "Yes", "", "", "FED/Q01",
                        "Is the cumulative ultimate equity detention above 40%?", "",
                        "La detention ultime cumulee est-elle superieure a 40 % ?", "", "", "", "", "", "", "",
                        "YES|Yes|Oui ; NO|No|Non", "", "YES -> Q-B01A\nNO -> Q-B01B", "ecbLboFlag"),
                row("Q-B01A", "CHECKLIST", "Yes", "No", "Yes", "", "", "",
                        "Does the Borrower meet one of the below exclusion criteria?", "",
                        "L'emprunteur repond-il a l'un des criteres ?", "", "", "", "", "", "", "", "",
                        "sovereign|Sovereign or Public Sector Entities|Souverain ; "
                                + "financialSector|Financial sector entities|Entites du secteur financier",
                        "ANY_YES -> END, flags: ecbLeveragedFlag=ECB_NOT_LEVERAGED\nALL_NO -> Q-T01", ""),
                row("Q-Q03", "SINGLE_CHOICE", "Yes", "No", "Yes", "", "", "",
                        "Covenant structure of the borrower?", "",
                        "Structure des covenants de l'emprunteur ?", "", "", "",
                        "In order to assess the covenant structure, the most conservative covenants apply.",
                        "(a fournir)", "", "",
                        "LOOSE|Covenant \"Loose\"|Covenant Loose ; LITE|Covenant \"Lite\"|Covenant Lite ; "
                                + "NONE|No Covenant|Sans covenant ; FULL|Full Covenant|Full Covenant",
                        "",
                        "Q-C02 in [ORIGINATION, MATERIAL_MODIFICATION] AND field ecbLeverageRatio range [<0 | >6] -> Q-Q04\n"
                                + "* -> END",
                        "ecbCovenantStructure")));
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Questions {

        @Test
        void reads_every_row_in_sheet_order() {
            List<Question> qs = parser.parse(ecbSheet(), LeverageFormType.ECB, Map.of(), fresh());
            assertTrue(issues.isEmpty(), () -> issues.describeAll().toString());
            assertEquals(List.of("Q01", "Q-B01A", "Q-Q03"), qs.stream().map(Question::key).toList());
        }

        @Test
        void reads_flags_and_prefill() {
            Question q01 = parser.parse(ecbSheet(), LeverageFormType.ECB, Map.of(), fresh()).get(0);
            assertEquals("ecbLboFlag", q01.fillsFlag());
            assertEquals("FED/Q01", q01.prefillFrom());
            assertTrue(q01.mandatory());
            assertFalse(q01.computed());
            assertTrue(q01.editable());
        }

        @Test
        void reads_options_and_items() {
            List<Question> qs = parser.parse(ecbSheet(), LeverageFormType.ECB, Map.of(), fresh());
            assertEquals(List.of("YES", "NO"), qs.get(0).options().stream().map(Option::value).toList());
            assertEquals(List.of("sovereign", "financialSector"),
                    qs.get(1).items().stream().map(ChecklistItem::key).toList());
            assertEquals("Souverain", qs.get(1).items().get(0).label().fr());
        }

        @Test
        void reads_a_composite_branch_and_a_default() {
            Question q = parser.parse(ecbSheet(), LeverageFormType.ECB, Map.of(), fresh()).get(2);
            assertEquals(2, q.branches().size());
            assertTrue(q.branches().get(0).when().isComposite());
            assertEquals("Q-Q04", q.branches().get(0).goTo());
            assertTrue(q.branches().get(1).when().isDefault());
            assertEquals("ecbCovenantStructure", q.fillsFlag());
        }

        @Test
        void duplicate_question_key_is_reported() {
            InMemoryWorkbookSource wb = new InMemoryWorkbookSource().sheet("ECB Q", List.of(
                    HEADERS,
                    row("Q01", "SINGLE_CHOICE", "Yes", "No", "Yes", "", "", "", "A", "", "B", "", "", "", "", "",
                            "", "", "YES|Yes|Oui ; NO|No|Non", "", "* -> END", ""),
                    row("Q01", "SINGLE_CHOICE", "Yes", "No", "Yes", "", "", "", "A", "", "B", "", "", "", "", "",
                            "", "", "YES|Yes|Oui ; NO|No|Non", "", "* -> END", "")));
            parser.parse(wb, LeverageFormType.ECB, Map.of(), fresh());
            assertTrue(hasIssue("QUESTION_DUPLICATE"));
        }

        @Test
        void sheet_name_maps_from_form_type() {
            assertEquals("Preliminary Q", QuestionSheetParser.sheetNameFor(LeverageFormType.PRELIMINARY));
            assertEquals("ECB Q", QuestionSheetParser.sheetNameFor(LeverageFormType.ECB));
            assertEquals("FED Q", QuestionSheetParser.sheetNameFor(LeverageFormType.FED));
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Bullets {

        private List<Bullet> parse(String cell) {
            return labels.parseBullets(cell, SourceLocation.of("ECB Q", 13, "Note Bullets EN"), fresh());
        }

        /** The Q-S02 Support-Entity tooltip: four bullets, the last carrying two children. */
        @Test
        void sub_bullets_attach_to_the_bullet_above() {
            List<Bullet> bullets = parse("""
                    - Be rated with the Corporate or the Sovereign model
                    - Bear a valid and less than 12 months old rating
                    - Be considered to have an incentive to avoid the counterparty default, deriving from:
                    -- The strategic importance of the subsidiary for the Support Entity
                    -- The contribution of the Support Entity in the funding of the obligor""");
            assertTrue(issues.isEmpty(), () -> issues.describeAll().toString());
            assertEquals(3, bullets.size());
            assertTrue(bullets.get(0).children().isEmpty());
            assertEquals(2, bullets.get(2).children().size());
            assertTrue(bullets.get(2).children().get(0).text().startsWith("The strategic importance"));
        }

        @Test
        void an_orphan_sub_bullet_is_promoted_not_dropped() {
            List<Bullet> bullets = parse("-- orphan\n- proper");
            assertEquals(2, bullets.size());
            assertEquals("orphan", bullets.get(0).text());
            assertTrue(hasIssue("BULLET_ORPHAN_SUB"));
        }

        @Test
        void a_line_without_a_marker_still_becomes_a_bullet() {
            List<Bullet> bullets = parse("no marker here");
            assertEquals(1, bullets.size());
            assertTrue(hasIssue("BULLET_NO_MARKER"));
        }

        @Test
        void blank_cell_yields_no_bullets() {
            assertTrue(parse(null).isEmpty());
            assertTrue(parse("   ").isEmpty());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class OptionsAndItems {

        private final SourceLocation where = SourceLocation.of("ECB Q", 17, "Options");

        @Test
        void lookup_options_name_a_source_instead_of_values() {
            List<Option> parsed = options.parseOptions("LOOKUP/COUNTERPARTY", where, fresh());
            assertEquals(1, parsed.size());
            assertEquals("LOOKUP/COUNTERPARTY", parsed.get(0).value());
            assertTrue(issues.isEmpty());
        }

        @Test
        void a_missing_french_label_is_kept_as_null_not_blank() {
            List<Option> parsed = options.parseOptions("YES|Yes| ; NO|No|Non", where, fresh());
            assertNull(parsed.get(0).label().fr());
            assertEquals("Non", parsed.get(1).label().fr());
        }

        @Test
        void two_part_entry_is_reported() {
            options.parseOptions("YES|Yes", where, fresh());
            assertTrue(hasIssue("ENTRY_MALFORMED"));
        }

        @Test
        void duplicate_code_is_reported() {
            options.parseOptions("YES|Yes|Oui ; YES|Yes again|Oui", where, fresh());
            assertTrue(hasIssue("ENTRY_DUPLICATE"));
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class FieldsJoin {

        private InMemoryWorkbookSource fieldsSheet() {
            return new InMemoryWorkbookSource().sheet("Fields", List.of(
                    row("Fields — one row per box"),
                    row("Row order is screen order."),
                    row("Form", "Question Key", "Group", "Field Key", "Label EN", "Label FR", "Type",
                            "Mandatory", "Editable", "Derived From", "Note EN", "Note FR",
                            "Formula (documentation only)", "Fills Flag"),
                    row("ECB", "Q-F01", "ECB Leverage Ratio", "ecbLeverageRatio", "ECB Leverage Ratio",
                            "Ratio de levier BCE", "NUMERIC", "Yes", "No", "CALC/ecbLeverageRatio", "", "",
                            "= Total ECB Debt / Adjusted EBITDA", "ecbLeverageRatio"),
                    row("ECB", "Q-F01", "ECB Leverage Ratio", "ebitda", "EBITDA", "EBITDA", "NUMERIC",
                            "Yes", "Yes", "FINANCIALS/ebitda", "", "", "", ""),
                    row("ECB", "Q-F01", "ECB Leverage Ratio", "reportedLtmAdjustment", "Reported LTM adjustment",
                            "", "NUMERIC", "Yes", "Yes", "", "", "", "", "")));
        }

        @Test
        void groups_boxes_by_form_and_question_preserving_order() {
            Map<LeverageFormType, Map<String, List<DataField>>> byForm =
                    fieldsParser.parse(fieldsSheet(), fresh());
            assertTrue(issues.isEmpty(), () -> issues.describeAll().toString());
            List<DataField> boxes = byForm.get(LeverageFormType.ECB).get("Q-F01");
            assertEquals(List.of("ecbLeverageRatio", "ebitda", "reportedLtmAdjustment"),
                    boxes.stream().map(DataField::key).toList());
        }

        /** A FINANCIALS box is prefilled AND editable; a CALC box is neither. */
        @Test
        void distinguishes_calculated_from_prefilled_and_typed() {
            List<DataField> boxes = fieldsParser.parse(fieldsSheet(), fresh())
                    .get(LeverageFormType.ECB).get("Q-F01");
            assertTrue(boxes.get(0).isCalculated());
            assertFalse(boxes.get(0).isAnalystInput());
            assertFalse(boxes.get(1).isCalculated());
            assertTrue(boxes.get(1).isAnalystInput());
            assertNull(boxes.get(2).derivedFrom());
            assertTrue(boxes.get(2).isAnalystInput());
        }

        @Test
        void fills_flag_and_formula_are_carried_through() {
            DataField ratio = fieldsParser.parse(fieldsSheet(), fresh())
                    .get(LeverageFormType.ECB).get("Q-F01").get(0);
            assertEquals("ecbLeverageRatio", ratio.fillsFlag());
            assertTrue(ratio.formula().startsWith("= Total ECB Debt"));
        }

        @Test
        void boxes_are_joined_onto_their_question() {
            InMemoryWorkbookSource wb = new InMemoryWorkbookSource().sheet("ECB Q", List.of(
                    HEADERS,
                    row("Q-F01", "DATA_ENTRY", "Yes", "No", "Yes", "", "", "", "Financial Data", "",
                            "Donnees financieres", "", "", "", "", "", "", "", "", "", "* -> Q-Q01", "")));
            Map<String, List<DataField>> boxes =
                    fieldsParser.parse(fieldsSheet(), fresh()).get(LeverageFormType.ECB);
            Question q = parser.parse(wb, LeverageFormType.ECB, boxes, fresh()).get(0);
            assertEquals(3, q.fields().size());
            assertEquals(QuestionType.DATA_ENTRY, q.type());
        }

        /** A box pointing at a question that is not on the tab would vanish silently otherwise. */
        @Test
        void orphan_field_is_reported() {
            InMemoryWorkbookSource wb = new InMemoryWorkbookSource().sheet("ECB Q", List.of(
                    HEADERS,
                    row("Q01", "SINGLE_CHOICE", "Yes", "No", "Yes", "", "", "", "A", "", "B", "", "", "", "",
                            "", "", "", "YES|Yes|Oui ; NO|No|Non", "", "* -> END", "")));
            Map<String, List<DataField>> boxes =
                    fieldsParser.parse(fieldsSheet(), new ImportIssues()).get(LeverageFormType.ECB);
            parser.parse(wb, LeverageFormType.ECB, boxes, fresh());
            assertTrue(hasIssue("FIELDS_ORPHAN_QUESTION"));
        }

        @Test
        void boxes_on_a_non_data_entry_question_are_reported() {
            InMemoryWorkbookSource wb = new InMemoryWorkbookSource().sheet("ECB Q", List.of(
                    HEADERS,
                    row("Q-F01", "SINGLE_CHOICE", "Yes", "No", "Yes", "", "", "", "A", "", "B", "", "", "",
                            "", "", "", "", "YES|Yes|Oui ; NO|No|Non", "", "* -> END", "")));
            Map<String, List<DataField>> boxes =
                    fieldsParser.parse(fieldsSheet(), new ImportIssues()).get(LeverageFormType.ECB);
            parser.parse(wb, LeverageFormType.ECB, boxes, fresh());
            assertTrue(hasIssue("FIELDS_ON_NON_DATA_ENTRY"));
        }
    }
}
