package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

/**
 * The save path, with the same three additions as the read path.
 *
 * <p><b>The figures are resolved here too, not carried over from the last read.</b> A request may
 * arrive minutes after the screen was drawn, and what gets FROZEN has to be what was true at the
 * moment of the write. Resolving twice is the price of a snapshot that can be defended.
 *
 * <p><b>The overlay is what gets persisted.</b> {@code PreliminaryResponseAssembler} freezes a box
 * from the answer map, and stamps CALCULATED provenance from {@code field.isCalculated()} — so
 * merging the computed figures into the map before assembling is all that is needed for the ratio
 * to appear in the record with the right provenance. Nothing in the assembler changes.
 */
@DomainDrivenDesign.ApplicationService
public class SaveLeverageFormUseCase {

    private final ValidationDomainService validation;
    private final FinancialTableResolver financialTable;   // new collaborator
    // ... existing collaborators unchanged ...

    @Transactional
    public FormState save(String analysisUid, LeverageFormType formType, SaveLeverageFormRequest request) {

        // ... load the aggregate and resolve the pinned definition, unchanged ...

        Map<String, String> settled = coercion.coerce(definition, request.answers());
        AnalysisSubject subject = AnalysisSubject.of(analysis);

        FinancialTable financials = financialTable.resolve(definition, settled, subject);
        Map<String, String> resolved = financials.applyTo(settled);

        FormAnswers answers = FormAnswers.of(definition, resolved,
                crossFormAnswers(analysis, formType),
                derivedValues.resolve(derivedSources(definition), subject, language));

        TraversalResult result = traversal.resolve(definition, answers);

        EntityEligibility entity = entityEligibility.resolve(resolved.get(LOOKUP_QUESTION), subject);

        List<ValidationMessage> violations =
                validation.violations(definition, resolved, result, entity, financials.computed());

        List<PanelSnapshot> panels = infoPanelResolver.resolve(definition,
                panelSelector.triggeredBy(definition, result.flags()), subject, language);

        // Autosave records progress even while errors stand — the analyst's typing is not lost
        // because a box is still empty. What an ERROR must prevent is VALIDATION of the analysis;
        // whether that is enforced here or on the validate endpoint is the open question below.
        FormResponses snapshot = responseAssembler.assemble(definition, resolved, result, language, panels);

        analysis.recordSection(formType, snapshot);
        analyses.save(analysis);

        // Audit read AFTER the save: lastModifiedTimestamp has to describe the write that just
        // happened, not the one before it, or the screen shows a stamp older than the data.
        return formStateAssembler.assemble(definition, resolved, result, violations,
                panels, language, FormAudit.of(analysis));
    }
}
