package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import java.util.ArrayList;
import java.util.List;

/**
 * Mutable collector for {@link ImportIssue}s.
 *
 * <p>The importer NEVER throws on bad data: a BA who mistypes one cell should get the whole list
 * of problems in one pass, not the first one and a stack trace. Parsers therefore record an issue,
 * substitute a null or a default, and carry on.
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

    public List<String> describeAll() {
        return issues.stream().map(ImportIssue::describe).toList();
    }
}
