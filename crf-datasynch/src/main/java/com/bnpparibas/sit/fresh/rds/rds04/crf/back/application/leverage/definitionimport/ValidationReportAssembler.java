package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.ValidationResult;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Produces the BA-facing report: every parse issue and every validation error, each pointed at a
 * cell.
 *
 * <p>This is the ONLY place Excel coordinates and domain errors meet, which is what lets the
 * validator stay pure and the message stay precise.
 *
 * <p>Parse issues come FIRST and are worth reading first: an unreadable cell usually explains the
 * validation errors that follow it. A question whose Branches cell failed to parse has no
 * branches, so it will also be reported as a dead end — one cause, two symptoms.
 */
@DomainDrivenDesign.ApplicationService
public final class ValidationReportAssembler {

    private final SourceLocator locator;

    public ValidationReportAssembler(SourceLocator locator) {
        this.locator = locator == null ? SourceLocator.none() : locator;
    }

    /** Parse issues and validation errors for every form, in reading order. */
    public List<String> toReport(ImportIssues issues,
                                 Map<LeverageFormType, ValidationResult> resultsByForm) {
        List<String> report = new ArrayList<>(issues.describeAll());
        for (Map.Entry<LeverageFormType, ValidationResult> e : resultsByForm.entrySet()) {
            for (ValidationResult.Error error : e.getValue().errors()) {
                report.add(describe(error));
            }
        }
        return List.copyOf(report);
    }

    public List<String> toReport(ValidationResult result) {
        return result.errors().stream().map(this::describe).toList();
    }

    private String describe(ValidationResult.Error error) {
        Optional<SourceLocation> physical = locator.locate(error);
        String where = physical.map(SourceLocation::describe).orElseGet(() -> logical(error));
        return where + " — [" + error.code() + "] " + error.message();
    }

    /** Fallback when nothing physical is known: still names the form, question and branch. */
    private String logical(ValidationResult.Error error) {
        StringBuilder sb = new StringBuilder(String.valueOf(error.formType()));
        if (error.questionKey() != null) sb.append(" / ").append(error.questionKey());
        if (error.subKey() != null) sb.append(" / ").append(error.subKey());
        if (error.branchIndex() != null) sb.append(" / branch ").append(error.branchIndex() + 1);
        return sb.toString();
    }
}
