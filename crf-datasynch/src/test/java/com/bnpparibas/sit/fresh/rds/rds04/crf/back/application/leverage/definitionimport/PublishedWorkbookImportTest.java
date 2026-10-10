package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

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

    /** Clara, feedback #5: the leverage test is still evaluated, just not shown. */
    @Test
    void the_reit_leverage_test_is_hidden_and_nothing_else_is() {
        DecisionTreeDefinition fed = assembled.definition(LeverageFormType.FED).orElseThrow();

        assertTrue(question(fed, "Q-RT20").hidden());
        assertEquals(1, fed.questions().stream().filter(Question::hidden).count(),
                "only Q-RT20 is authored Visible = No");
    }

    private static Question question(DecisionTreeDefinition definition, String key) {
        return definition.questions().stream()
                .filter(q -> q.key().equals(key))
                .findFirst()
                .orElseThrow(() -> new AssertionError(key + " is not in the " + definition.formType() + " definition"));
    }
}