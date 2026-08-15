package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;

/**
 * One problem, in columns.
 *
 * <p><b>Why not just the sentence.</b> The report reads
 * {@code "Sheet 'ECB Q', row 15 — [MISSING_LABEL_FR] EN label text is missing"} — four facts the
 * server already had, joined. Rendering that as a TABLE means taking it apart again, and the only
 * place left to do that is TypeScript, splitting on an em-dash and a bracket that a message is free
 * to contain: several already mention ranges like {@code [0 .. <4]}.
 *
 * <p><b>The sentence is derived from this, not stored beside it.</b> {@link #describe()} is the
 * only place the format lives, so the string a log line prints and the row a BA reads cannot
 * disagree.
 *
 * @param formType which tree, or null for a problem with the workbook itself — a missing sheet
 *                 belongs to no form
 * @param location where to look: a sheet-and-row reference when the importer could map the problem
 *                 back to a cell, otherwise the logical path ({@code ECB / Q-F01 / ebitda})
 * @param cell     true when {@code location} is a real reference, so the screen can present it as
 *                 somewhere to go rather than as a description
 * @param code     the stable code — what a BA searches for and what the tests assert on
 * @param message  the sentence. Wording is free to improve; codes are not.
 */
public record ReportLine(LeverageFormType formType,
                         String location,
                         boolean cell,
                         String code,
                         String message) {

    /** A problem found before any tree could be built. */
    public static ReportLine parseIssue(String location, String code, String message) {
        return new ReportLine(null, location, true, code, message);
    }

    /** A form that produced no definition at all — no cell to point at, no code to look up. */
    public static ReportLine missingForm(LeverageFormType formType, String message) {
        return new ReportLine(formType, String.valueOf(formType), false, null, message);
    }

    /**
     * The one-line form, byte-for-byte what {@code ValidationReportAssembler} used to build.
     *
     * <p>Kept identical on purpose: {@code DecisionTreeImportControllerTest} and the service log
     * both read it, and a formatting change would be an invisible break in both.
     */
    public String describe() {
        return code == null
                ? location + " — " + message
                : location + " — [" + code + "] " + message;
    }
}
