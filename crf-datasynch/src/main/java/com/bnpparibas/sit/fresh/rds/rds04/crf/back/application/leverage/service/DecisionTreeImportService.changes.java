/*
 * DecisionTreeImportService — two methods change. Nothing else in the class moves.
 *
 * The only difference is that `report` becomes `lines`: the assembler now produces ReportLine, and
 * ImportOutcome derives the sentences from them. Arity is unchanged at every call site.
 */

@Transactional
public ImportOutcome importWorkbook(WorkbookSource workbook, ImportMode mode) {
    Instant now = clock.instant();
    ImportIssues issues = new ImportIssues();

    Map<LeverageFormType, Integer> versions = nextVersions();
    AssembledWorkbook assembled = assembler.assemble(
            workbook, DefinitionStatus.PUBLISHED, versions::get, issues);

    Map<LeverageFormType, ValidationResult> results = validateAll(assembled);
    List<ReportLine> lines = new ValidationReportAssembler(assembled.locator()).toLines(issues, results);

    if (!isClean(assembled, issues, results)) {
        return new ImportOutcome(ImportStatus.REJECTED, withMissingFormsNoted(assembled, lines),
                Map.of(), now);
    }
    if (mode == ImportMode.DRY_RUN) {
        return new ImportOutcome(ImportStatus.VALIDATED, lines, Map.of(), now);
    }
    publish(assembled, now);
    return new ImportOutcome(ImportStatus.PUBLISHED, lines, versions, now);
}

/**
 * A form that produced no definition at all raises no validation errors — there is nothing to
 * validate — so without this the report could look thin next to a rejection.
 *
 * <p>These rows carry no code and no cell: nothing failed at a particular place, the form simply
 * never came into existence. The table shows a dash in both columns, which is honest.
 */
private List<ReportLine> withMissingFormsNoted(AssembledWorkbook assembled, List<ReportLine> lines) {
    if (assembled.isComplete()) {
        return lines;
    }
    List<ReportLine> full = new ArrayList<>(lines);
    assembled.missingForms().forEach(form ->
            full.add(ReportLine.missingForm(form, "No definition could be built for " + form
                    + " — see the issues above")));
    return List.copyOf(full);
}
