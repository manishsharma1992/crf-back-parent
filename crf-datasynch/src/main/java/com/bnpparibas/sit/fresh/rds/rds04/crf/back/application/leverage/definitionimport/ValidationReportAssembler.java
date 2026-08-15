package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.ReportLine;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.ValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Turns what the importer found into what a BA reads.
 *
 * <p><b>One shape now, not two.</b> This used to build finished sentences; it now builds
 * {@link ReportLine}s, and the sentence is {@link ReportLine#describe()}. That inversion matters:
 * a table needs the parts, a log needs the sentence, and deriving the sentence from the parts is
 * the only arrangement where the two cannot drift.
 *
 * <p>Both {@code toReport} overloads survive unchanged in behaviour, so existing callers and tests
 * see exactly the strings they saw before.
 */
public final class ValidationReportAssembler {

    private final SourceLocator locator;

    public ValidationReportAssembler(SourceLocator locator) {
        this.locator = locator == null ? SourceLocator.none() : locator;
    }

    // ------------------------------------------------------------------ structured

    /**
     * Parse issues first, then validation errors form by form.
     *
     * <p>The order is causal and deliberately not sorted: a sheet that could not be read produces
     * errors in every tree below it, and sorting by code or by location would scatter the cause
     * among its consequences.
     */
    public List<ReportLine> toLines(ImportIssues issues,
                                    Map<LeverageFormType, ValidationResult> resultsByForm) {
        List<ReportLine> lines = new ArrayList<>(issues.lines());
        for (Map.Entry<LeverageFormType, ValidationResult> entry : resultsByForm.entrySet()) {
            for (ValidationResult.Error error : entry.getValue().errors()) {
                lines.add(toLine(entry.getKey(), error));
            }
        }
        return List.copyOf(lines);
    }

    public List<ReportLine> toLines(LeverageFormType formType, ValidationResult result) {
        return result.errors().stream().map(error -> toLine(formType, error)).toList();
    }

    /**
     * A physical location when the importer could map the error back to a cell, otherwise the
     * logical path.
     *
     * <p>The distinction reaches the screen as {@code cell}: "row 15 of the ECB Q sheet" is
     * somewhere to go, whereas "ECB / Q-F01 / ebitda" only says where in the tree the problem sits.
     * A validator rule about the graph as a whole has no cell to blame.
     */
    private ReportLine toLine(LeverageFormType formType, ValidationResult.Error error) {
        Optional<SourceLocation> physical = locator.locate(error);
        return new ReportLine(
                formType,
                physical.map(SourceLocation::describe).orElseGet(() -> logical(error)),
                physical.isPresent(),
                error.code(),
                error.message());
    }

    // ------------------------------------------------------------------ sentences

    /** Unchanged for every existing caller — the strings are the same strings. */
    public List<String> toReport(ImportIssues issues,
                                 Map<LeverageFormType, ValidationResult> resultsByForm) {
        return toLines(issues, resultsByForm).stream().map(ReportLine::describe).toList();
    }

    public List<String> toReport(LeverageFormType formType, ValidationResult result) {
        return toLines(formType, result).stream().map(ReportLine::describe).toList();
    }

    private String logical(ValidationResult.Error error) {
        StringBuilder sb = new StringBuilder(String.valueOf(error.formType()));
        if (error.questionKey() != null) sb.append(" / ").append(error.questionKey());
        if (error.subKey() != null) sb.append(" / ").append(error.subKey());
        if (error.branchIndex() != null) sb.append(" / branch ").append(error.branchIndex() + 1);
        return sb.toString();
    }
}
