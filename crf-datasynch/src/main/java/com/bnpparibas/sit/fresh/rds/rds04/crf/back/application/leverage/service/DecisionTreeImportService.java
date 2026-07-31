package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.DefinitionStatus;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeDefinition;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeValidator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.ValidationResult;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.repository.LeverageDecisionTreeDefinitionRepository;
import com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.application.leverage.definitionimport.*;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Imports an authoring workbook: read it, assemble three trees, validate them, and either publish
 * all three or publish none.
 *
 * <p>This is the use case. Everything it needs already exists and is individually testable — the
 * parsers, the assembler, the pure validator, the repository port — and this class contributes
 * only the ORDER they run in and the decision to commit.
 *
 * <h2>All three, or nothing</h2>
 * The forms are not independent. ECB and FED share the {@code LEVERAGED_FLAG} value set, and an
 * ECB info panel renders a code FED wrote. Publishing ECB while FED stays on last month's
 * definition is not a coherent state, so one bad cell anywhere rejects the whole workbook. That is
 * also why the transaction wraps all six writes rather than one form at a time.
 *
 * <h2>Nothing is written before everything is checked</h2>
 * Parse issues and validation errors are both collected first; the writes happen last and only on
 * a clean run. The transaction is therefore a belt-and-braces guarantee rather than the mechanism
 * — a rejected import performs no writes to roll back.
 *
 * <h2>The repository is the shared one</h2>
 * {@code LeverageDecisionTreeDefinitionRepository} lives in crf-shared and is the same port
 * crf-back reads through at traversal time. Importing and traversing therefore agree on what "in
 * force" means by construction, and its cache — keyed by (form, version), which is immutable — is
 * warmed by the same publish that created the version.
 *
 * <h2>Versions are decided up front</h2>
 * The version is stamped INTO the definition, so it has to be known before assembly. Each form
 * gets {@code current + 1} independently: forms move at their own pace, and a workbook that
 * changes only the ECB tab still bumps all three, which is the honest record of what was published
 * together.
 */
@DomainDrivenDesign.ApplicationService
public class DecisionTreeImportService {

    private final DecisionTreeAssembler assembler;
    private final DecisionTreeValidator validator;
    private final LeverageDecisionTreeDefinitionRepository repository;
    private final Clock clock;

    public DecisionTreeImportService(DecisionTreeAssembler assembler,
                                     DecisionTreeValidator validator,
                                     LeverageDecisionTreeDefinitionRepository repository,
                                     Clock clock) {
        this.assembler = assembler;
        this.validator = validator;
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * @param workbook already opened by the caller, which also closes it — this service never
     *                 learns what a file is, so it can be driven from an upload, a batch job or a
     *                 test with equal ease
     */
    @Transactional
    public ImportOutcome importWorkbook(WorkbookSource workbook, ImportMode mode) {
        Instant now = clock.instant();
        ImportIssues issues = new ImportIssues();

        Map<LeverageFormType, Integer> versions = nextVersions();
        AssembledWorkbook assembled = assembler.assemble(
                workbook, DefinitionStatus.PUBLISHED, versions::get, issues);

        Map<LeverageFormType, ValidationResult> results = validateAll(assembled);
        List<String> report = new ValidationReportAssembler(assembled.locator()).toReport(issues, results);

        if (!isClean(assembled, issues, results)) {
            return new ImportOutcome(ImportStatus.REJECTED, withMissingFormsNoted(assembled, report),
                    Map.of(), now);
        }
        if (mode == ImportMode.DRY_RUN) {
            return new ImportOutcome(ImportStatus.VALIDATED, report, Map.of(), now);
        }
        publish(assembled, now);
        return new ImportOutcome(ImportStatus.PUBLISHED, report, versions, now);
    }

    private Map<LeverageFormType, Integer> nextVersions() {
        Map<LeverageFormType, Integer> versions = new EnumMap<>(LeverageFormType.class);
        for (LeverageFormType form : LeverageFormType.values()) {
            versions.put(form, repository.currentVersion(form) + 1);
        }
        return versions;
    }

    private Map<LeverageFormType, ValidationResult> validateAll(AssembledWorkbook assembled) {
        Map<LeverageFormType, ValidationResult> results = new EnumMap<>(LeverageFormType.class);
        assembled.definitions().forEach((form, definition) -> results.put(form, validator.validate(definition)));
        return results;
    }

    private boolean isClean(AssembledWorkbook assembled,
                            ImportIssues issues,
                            Map<LeverageFormType, ValidationResult> results) {
        return issues.isEmpty()
                && assembled.isComplete()
                && results.values().stream().allMatch(ValidationResult::isValid);
    }

    /**
     * A form that produced no definition at all raises no validation errors — there is nothing to
     * validate — so without this the report could look thin next to a rejection.
     */
    private List<String> withMissingFormsNoted(AssembledWorkbook assembled, List<String> report) {
        if (assembled.isComplete()) return report;
        List<String> full = new java.util.ArrayList<>(report);
        assembled.missingForms().forEach(form ->
                full.add("No definition could be built for " + form + " — see the issues above"));
        return List.copyOf(full);
    }

    /**
     * Close the open version, then append the new one, form by form. Ordering matters within a
     * form and not between them, so this reads in declaration order for reproducibility.
     */
    private void publish(AssembledWorkbook assembled, Instant now) {
        for (LeverageFormType form : LeverageFormType.values()) {
            DecisionTreeDefinition definition = assembled.definitions().get(form);
            repository.supersede(form, now);
            repository.save(definition, now);
        }
    }
}
