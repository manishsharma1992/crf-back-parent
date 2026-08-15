/*
 * DecisionTreeImportControllerTest — replace the Rejection nest with this one.
 * The Success and BadUpload nests are unchanged: their List.of() is already an empty
 * List<ReportLine>, which infers fine.
 *
 * Add the import:
 *     import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.ReportLine;
 */

@Nested
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class Rejection {

    private ReportLine line(String location, String code, String message) {
        return new ReportLine(LeverageFormType.ECB, location, true, code, message);
    }

    /**
     * 422, not 400: the request was well formed and understood, and the server refused it. The
     * distinction is what tells a BA "row 15 has a typo" apart from "wrong file".
     */
    @Test
    void a_bad_workbook_returns_422_with_the_whole_report() throws Exception {
        factoryReturnsAWorkbook();
        serviceAnswers(new ImportOutcome(ImportStatus.REJECTED, List.of(
                line("Sheet 'ECB Q', row 15, column 'Branches' (line 2)", "UNKNOWN_GOTO",
                        "no such question"),
                line("Sheet 'Fields', row 7, column 'Field Key'", "DATA_FIELD_DUPLICATE_IN_FORM",
                        "duplicate")),
                Map.of(), NOW));

        mockMvc.perform(multipart("/api/leverage/decision-trees/import")
                        .file(upload()).param("mode", "PUBLISH"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.report.length()").value(2))
                .andExpect(jsonPath("$.report[0]").value(
                        org.hamcrest.Matchers.containsString("row 15")))
                .andExpect(jsonPath("$.publishedVersions").isEmpty());
    }

    /**
     * The sentence is DERIVED from the columns, so the two cannot disagree — this pins that they
     * are still both present and still describe the same problem. A caller rendering a table reads
     * `lines`; a caller pasting into a ticket reads `report`.
     */
    @Test
    void the_report_and_the_columns_carry_the_same_problem() throws Exception {
        factoryReturnsAWorkbook();
        serviceAnswers(new ImportOutcome(ImportStatus.REJECTED, List.of(
                line("Sheet 'ECB Q', row 15, column 'Branches' (line 2)", "UNKNOWN_GOTO",
                        "no such question")),
                Map.of(), NOW));

        mockMvc.perform(multipart("/api/leverage/decision-trees/import")
                        .file(upload()).param("mode", "PUBLISH"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.lines[0].code").value("UNKNOWN_GOTO"))
                .andExpect(jsonPath("$.lines[0].formType").value("ECB"))
                .andExpect(jsonPath("$.lines[0].cell").value(true))
                .andExpect(jsonPath("$.lines[0].location").value(
                        org.hamcrest.Matchers.containsString("row 15")))
                .andExpect(jsonPath("$.report[0]").value(
                        org.hamcrest.Matchers.containsString("[UNKNOWN_GOTO]")));
    }

    @Test
    void the_summary_counts_the_problems() throws Exception {
        factoryReturnsAWorkbook();
        serviceAnswers(new ImportOutcome(ImportStatus.REJECTED, List.of(
                line("a", "CODE_A", "first"),
                line("b", "CODE_B", "second"),
                line("c", "CODE_C", "third")),
                Map.of(), NOW));

        mockMvc.perform(multipart("/api/leverage/decision-trees/import").file(upload()))
                .andExpect(jsonPath("$.summary").value(
                        org.hamcrest.Matchers.containsString("3 problem(s)")));
    }
}
