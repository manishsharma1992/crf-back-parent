package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.ValidationResult;

/**
 * One problem, in columns.
 *
 * <p><b>Why this exists alongside {@code report}.</b> The report is assembled as
 * {@code "ECB Q!C15 — [MISSING_LABEL_FR] EN label text is missing"} — a sentence built from four
 * facts the server already had. Rendering that as a TABLE means taking it apart again, and the
 * only place to do that would be TypeScript, splitting on an em-dash and a bracket that a message
 * is free to contain (several already mention ranges like {@code [0 .. <4]}). So the four facts are
 * sent as four fields and the screen decides how to arrange them.
 *
 * <p>{@code report} stays. It is what a log line and the existing tests use, and the two are the
 * same information in two shapes rather than a migration.
 *
 * @param formType which of the three trees, or null for a problem with the workbook as a whole
 * @param location where to look — a cell reference like {@code ECB Q!C15} when the importer could
 *                 map the error back to the sheet, otherwise the logical path
 *                 ({@code ECB / Q-F01 / ebitda})
 * @param cell     true when {@code location} is a real cell reference, so the screen can offer it
 *                 as something to go and find rather than as a description
 * @param code     the stable error code — what a BA searches for and what these tests assert on
 * @param message  the sentence, already in English; wording is free to improve, codes are not
 */
public record ReportLine(LeverageFormType formType,
                         String location,
                         boolean cell,
                         String code,
                         String message) {

    /** For a problem found before any tree could be built — a missing sheet, an unreadable row. */
    public static ReportLine parseIssue(String location, String code, String message) {
        return new ReportLine(null, location, false, code, message);
    }

    public static ReportLine from(LeverageFormType formType, ValidationResult.Error error,
                                  String location, boolean cell) {
        return new ReportLine(formType, location, cell, error.code(), error.message());
    }
}

/*
 * ============================================================ ValidationReportAssembler
 *
 * `describe` already computes exactly these parts. Split it so both shapes come from one place and
 * cannot drift:
 *
 *     public List<ReportLine> toLines(ImportIssues issues,
 *                                     Map<LeverageFormType, ValidationResult> resultsByForm) {
 *         List<ReportLine> lines = new ArrayList<>(issues.describeAllStructured());
 *         for (var entry : resultsByForm.entrySet()) {
 *             for (ValidationResult.Error error : entry.getValue().errors()) {
 *                 Optional<SourceLocation> physical = locator.locate(error);
 *                 lines.add(ReportLine.from(
 *                         entry.getKey(), error,
 *                         physical.map(SourceLocation::describe).orElseGet(() -> logical(error)),
 *                         physical.isPresent()));
 *             }
 *         }
 *         return List.copyOf(lines);
 *     }
 *
 * `ImportIssues.describeAllStructured()` is the one piece that does not exist yet — today
 * `describeAll()` returns finished strings. If that class already holds the code and the cell
 * separately it is a five-line addition; if it does not, wrap each string as
 * `ReportLine.parseIssue(null, null, text)` for now and the table still renders, just with two
 * empty columns for parse issues.
 *
 * ============================================================ ImportOutcome
 *
 *     List<ReportLine> lines,          // added; `report` unchanged
 *
 * ============================================================ DecisionTreeImportResponse
 *
 *     List<ReportLine> lines,          // added; `report` unchanged
 */
