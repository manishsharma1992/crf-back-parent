package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.FormAnswers;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.repository.LeverageAnalysisRepository;

import java.util.LinkedHashMap;
import java.util.stream.Collectors;

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

    private static final String LOOKUP_QUESTION = "Q-S06";

    private final LeverageAnalysisRepository analyses;
    private final DecisionTreeResolver resolver;
    private final DecisionTreeTraversalService traversal;
    private final PreliminaryResponseAssembler responseAssembler;
    private final FormStateAssembler formStateAssembler;
    private final ChecklistCoercionDomainService coercion;
    private final ValidationDomainService validation;
    private final DervidedValueResolver derivedValues;
    private final InfoPanelSelector panelSelector;
    private final InfoPanelResolver infoPanels;
    private final EntityEligibilityResolver entityEligibility;
    private final FinancialTableResolver financialTable;

    @Transactional
    public FormState save(String analysisUid, String formType, SaveLeverageFormRequest request) {
        if(LeverageFormType.valueOf(formType).equals(LeverageFormType.PRELIMINARY)) {
            throw new IllegalArgumentException("preliminary is saved via SavePreliminaryFormUseCase.");
        }

        LeverageAnalysis analysis = analyses.findByAnalysisUid(analysisUid)
                .orElseThrow(() -> new AnalysisNotFoundException(analysisUid));

        analysis.assertModifiable();

        DecisionTreeDefinition definition = pinnedDefinition(analysis, LeverageFormType.valueOf(formType));
        String language = request.locale() != null ? request.locale() : definition.defaultLocale();

        Map<String, String> settled = coercion.coerce(definition, request.anwsers());
        AnalysisSubject subject = AnalysisSubject.of(analysis);

        FinancialTable financials = financialTable.resolve(definition, settled, subject);
        Map<String, String> resolved = financials.applyTo(settled);

        FormAnswers answers = FormAnswers.of(definition, resolved, crossFormAnswers(analysis, LeverageFormType.valueOf(formType)),
                derviedValues.resolve(derivedSources(definition), subject, language));

        TraversalResult result = traversal.resolve(definition, answers);

        EntityEligibility entity = entityEligibility(definition, resolved, subject);

        List<ValidationMessage> violations = validation.violations(definition, resolved, result, entity, financials.computed());

        List<PanelSnapshot> panels = infoPanels.resolve(definition, panelSelector.triggeredBy(definition, result.flags()), subject, language);

        // Autosave records progress even while errors stand - the analyst's typing is not lost
        // because a box is still empty. What an ERROR must prevent is VIOLATION of the analysis;
        // whether that is enforced here or on the validate endpoint is the open question below.
        FormResponses snapshot = responseAssembler.assemble(definition, resolved, result, language, panels);

        analysis.recordSection(LeverageFormType.valueOf(formType), snapshot);
        analyses.save(analysis);

        // Audit read AFTER the save: lastModifiedTimestamp has to describe the write that just
        // happened, not the one before it, or the screen shows a stamp older than the data.
        return formStateAssembler.assemble(definition, resolved, result, violations, panels, language, FormAudit.of(analysis));
    }

    /**
     * Identical to the read path's. Worth extracting to a small collaborator shared by both use
     * cases rather than copied — two copies of a rule about when a check applies is exactly how the
     * read and the save drift apart. A package-private {@code EntityEligibilityLookup} with this
     * one method would do; I have kept it inline here so the diff stays reviewable, but I would
     * pull it out before merge.
     */
    private EntityEligibility entityEligibility(DecisionTreeDefinition definition,
                                                Map<String, String> resolved,
                                                AnalysisSubject subject) {
        String answer = definition.lookupQuestion()
                .map(Question::key)
                .map(resolved::get)
                .orElse(null);

        return answer == null || answer.isBlank()
                ? EntityEligibility.notApplicable()
                : entityEligibility.resolve(answer, subject);
    }

    /**
     * The reference-data sources this definition declares.
     *
     * <p>CALC/x is filtered out on purpose: those are computed in the domain layer from other
     *  answers, so sending them to a resolver that reads rows would ask the wrong question.
     * </p>
     */
    state Set<String> derivedSources(DecisionTreeDefinition definition) {
        return definition.questions().stream()
                .map(Question::derivedFrom)
                .filter(Objects::nonNull)
                .filter(source -> !source.startsWith("CALC/"))
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * {@code start()} pins only the preliminary definition, so ECB and FED are null until the
     * analyst first opens them. Pinning on first save freezes the session onto whatever was active
     * then; {@code pinDecisionTree} is a no-op once set, so later saves cannot drift.
     */
    private DecisionTreeDefinition pinnedDefinition(LeverageAnalysis analysis, LeverageFormType formType) {
        if(analysis.decisionTreeFor(formType) == null) {
            analysis.pinDecisionTree(formType, resolver.resolveActiveEntity(formType));
        }
        return resolver.resolvePinned(formType, analysis.decisionTreeFor(formType).getVersion());
    }

    private Map<String, String> crossFormAnswers(LeverageAnalysis analysis, LeverageFormType target) {
        Map<String, String> crossForm = new LinkedHashMap<>();
        for(LeverageFormType source: LeverageFormType.values()) {
            if(source != target) {
                crossForm.putAll(SnapshotAnswers.flattenForCrossForm(source, analysis.responsesFor(source)));
            }
        }
        return crossForm;
    }
}
