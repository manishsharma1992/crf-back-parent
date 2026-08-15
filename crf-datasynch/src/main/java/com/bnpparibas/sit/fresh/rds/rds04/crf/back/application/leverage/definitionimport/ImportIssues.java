package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.ReportLine;

import java.util.ArrayList;
import java.util.List;

/**
 * Problems found while reading the workbook, before any tree exists to validate.
 */
public final class ImportIssues {

    private final List<ImportIssue> issues = new ArrayList<>();

    public void add(SourceLocation where, String code, String message) {
        issues.add(new ImportIssue(where, code, message));
    }

    public boolean isEmpty() {
        return issues.isEmpty();
    }

    public List<ImportIssue> all() {
        return List.copyOf(issues);
    }

    /**
     * The same issues as table rows.
     *
     * <p>Nothing is reconstructed here — {@link ImportIssue} has held the location, the code and
     * the message separately all along, and {@code describe()} was only ever joining them. A parse
     * issue always carries a real cell, because it is found by failing to read one.
     */
    public List<ReportLine> lines() {
        return issues.stream()
                .map(issue -> ReportLine.parseIssue(issue.where().describe(), issue.code(), issue.message()))
                .toList();
    }

    public List<String> describeAll() {
        return issues.stream().map(ImportIssue::describe).toList();
    }
}
