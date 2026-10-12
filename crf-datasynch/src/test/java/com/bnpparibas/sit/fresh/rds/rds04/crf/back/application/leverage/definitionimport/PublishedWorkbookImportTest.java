package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport.*;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.DefinitionStatus;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.*;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage.definitionimport.PoiWorkbookSourceFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.IOException;
import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Imports the PUBLISHED workbook through the real POI adapter and the real parsers.
 *
 * <p>Every other test in this module reads an in-memory sheet that the test author typed. Both
 * workbook defects that reached the application ({@code TEXTAREA} is not a question type,
 * {@code SINGLE_CHOICE} is not a field type) were in the sheet itself, and no in-memory fixture
 * would have contained them. This is the one test that reads what the BA actually authored.
 *
 * <p>When a new version is published, change {@link #WORKBOOK} and keep the old file: a pinned
 * analysis can still be reopened against it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublishedWorkbookImportTest {

    private static final String WORKBOOK = "/leverage-decision-tree-authoring-template_15.xlsx";

    private ImportIssues issues;
    private AssembledWorkbook assembled;

    @BeforeAll
    void importIt() throws IOException {
        WorkbookSource workbook;
        try (InputStream in = getClass().getResourceAsStream(WORKBOOK)) {
            assertNotNull(in, WORKBOOK + " is not on the test classpath");
            workbook = new PoiWorkbookSourceFactory().open(in);
        }
        ConditionExpressionParser conditions = new ConditionExpressionParser();
        DecisionTreeAssembler assembler = new DecisionTreeAssembler(
                new FormsSheetParser(new FlagValuesSheetParser()),
                new FieldsSheetParser(),
                new QuestionSheetParser(new LabelParser(), new OptionsParser(),
                        new BranchExpressionParser(conditions), new ValueRuleExpressionParser(conditions)));

        issues = new ImportIssues();
        assembled = assembler.assemble(workbook, DefinitionStatus.PUBLISHED, form -> 15, issues);
    }

    @Test
    void reads_without_a_single_parse_issue() {
        assertTrue(issues.isEmpty(), () -> String.join("\n", issues.describeAll()));
        assertTrue(assembled.isComplete(), () -> "missing forms: " + assembled.missingForms());
    }

    @Test
    void every_form_passes_the_validator_the_upload_runs() {
        DecisionTreeValidator validator = new DecisionTreeValidator();
        assembled.definitions().forEach((form, definition) -> {
            ValidationResult result = validator.validate(definition);
            assertTrue(result.isValid(), () -> form + ": " + result);
        });
    }

    /**
     * Exactly the three intermediate tests are hidden: Q-RT20 (Clara, feedback #5) and the two
     * General Obligor / Utilities tests that replace the BR's cascade. They are evaluated and
     * frozen, just not shown. A fourth hidden question would be an authoring slip, so the list is
     * pinned rather than counted.
     */
    @Test
    void only_the_intermediate_tests_are_hidden() {
        DecisionTreeDefinition fed = assembled.definition(LeverageFormType.FED).orElseThrow();

        assertEquals(java.util.Set.of("Q-RT20", "Q-GO20", "Q-GO21"),
                fed.questions().stream().filter(Question::hidden).map(Question::key)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    /** v15's two section headers, in both languages, on the escalation questions only. */
    @Test
    void the_escalation_questions_carry_their_section_headers() {
        DecisionTreeDefinition fed = assembled.definition(LeverageFormType.FED).orElseThrow();

        assertEquals("Escalation tests for Highly Leveraged Transactions",
                question(fed, "Q-E01").section().en().text());
        assertEquals("Rule for escalation to CCDG", question(fed, "Q-E02").section().fr().text());
        assertEquals(2, fed.questions().stream().filter(q -> q.section() != null).count());
    }

    private static Question question(DecisionTreeDefinition definition, String key) {
        return definition.questions().stream()
                .filter(q -> q.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new AssertionError(key + " is not in the " + definition.formType() + " definition"));
    }
}
