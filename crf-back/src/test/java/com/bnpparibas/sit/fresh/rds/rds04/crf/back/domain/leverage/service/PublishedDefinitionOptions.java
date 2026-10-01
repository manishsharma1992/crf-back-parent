package com.bnpparibas.sit.fresh.rds.rds04.crf.back.leverage;

/**
 * SEAM 1 — where {@code publishedFedDefinition()} comes from. Three options, best first.
 *
 * <p>Do NOT paste the JSONB inline in Java. It would be a two-thousand-line string literal that
 * nobody can read, nobody can diff, and nobody will update when the workbook changes — so the test
 * would keep passing against a definition the application no longer uses. That is worse than
 * having no test, because it looks like coverage.
 */
public final class PublishedDefinitionOptions {

    // ================================================================ A. import the workbook

    /*
     * BEST, and it decides where this test class lives.
     *
     * crf-datasynch owns the importer and already depends on crf-back for the domain types, so a
     * test in crf-datasynch's test sources can see BOTH the importer and the traversal, validation
     * and financial services. crf-back cannot see the importer. So put FedFormWalkTest in
     * crf-datasynch, not crf-back.
     *
     * This is the only option that tests what the BA actually authored. Both of the workbook
     * defects that reached us — TEXTAREA is not a QuestionType, SINGLE_CHOICE is not a
     * DataFieldType — were in the sheet, not the code, and no fixture would have found either.
     *
     *   private DecisionTreeDefinition publishedFedDefinition() {
     *       ImportResult result = importService.importWorkbook(
     *               new ClassPathResource("leverage/leverage-decision-tree-authoring-template_14.xlsx"));
     *
     *       // Assert it imported clean BEFORE walking it. A walk over a definition that failed
     *       // validation tells you nothing: every assertion below would be about a tree the
     *       // application would have refused to publish.
     *       assertThat(result.issues()).isEmpty();
     *       return result.definition(LeverageFormType.FED);
     *   }
     *
     * Copy the workbook into src/test/resources/leverage/ and keep it there. It is a test fixture
     * from that point on — when v15 adds General Obligor / Utilities, this copy is updated
     * deliberately and the diff shows what changed.
     */

    // ================================================================ B. the published JSON

    /*
     * If the module boundary makes A impossible, export the definition the application published
     * and read it back. Still the real artifact, still regenerable, no module dependency — and it
     * exercises the Jackson round trip we just fixed, so a derived is-getter creeping back in
     * fails here too.
     *
     *   psql> \copy (select definition from leverage_decision_tree_definition
     *                where form_type = 'FED' and version = 1)
     *         to 'src/test/resources/leverage/fed-v14.json'
     *
     *   private DecisionTreeDefinition publishedFedDefinition() throws IOException {
     *       try (InputStream json = getClass().getResourceAsStream("/leverage/fed-v14.json")) {
     *           return objectMapper.readValue(json, DecisionTreeDefinition.class);
     *       }
     *   }
     *
     * Use the SAME mapper configuration the application uses, or the test proves something the
     * application does not do. The quickest way to be sure is to inject the @Primary bean rather
     * than building a fresh ObjectMapper here.
     *
     * The weakness is drift: re-author the workbook and this file is stale until someone
     * remembers. Worth a line in the v15 checklist.
     */

    // ================================================================ C. a Java fixture

    /*
     * NOT for these tests. Thirty-one questions, thirty-three fields and thirty-two validation
     * rows hand-built in Java is both unreadable and circular — it tests the engine against what
     * the test author believed the workbook says. Keep Java fixtures for the unit tests, where a
     * three-question definition is the point.
     */

    private PublishedDefinitionOptions() {
    }
}
