package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.*;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.AnalysisSubject;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.DerivedValueResolver;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.EntityEligibilityResolver;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.FinancialTableResolver;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service.InfoPanelSelector;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.EntityEligibility;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Only project() changes, plus one new collaborator. Everything above it is untouched.
 */
@Service
@DomainDrivenDesign.ApplicationService
@RequiredArgsConstructor
public class GetLeverageFormStateUseCase {

    private static final String LOOKUP_QUESTION = "Q-S06";

    private final LeverageAnalysisRepository analyses;
    private final DecisionTreeResolver resolver;
    private final DecisionTreeTraversalService traversal;
    private final FormStateAssembler formStateAssembler;
    private final ChecklistCoercionDomainService coercion;
    private final ValidationDomainService validation;
    private final DerivedValueResolver derivedValues;
    private final InfoPanelSelector panelSelector;
    private final InfoPanelResolver infoPanelResolver;
    private final EntityEligibilityResolver entityEligibility;
    private final FinancialTableResolver financialTable;   // new collaborator

    @Transactional(readOnly = true)
    public FormState get(String analysisUid, LeverageFormType formType, String locale) {
        LeverageAnalysis analysis = analyses.findByAnalysisUid(analysisUid)
                .orElseThrow(() -> new AnalysisNotFoundException(analysisUid));

        DecisionTreeDefinition definition = definitionFor(analysis, formType);
        Map<String, String> settled = coercion.coerce(definition,
                SnapshotAnswers.flattenForReplay(analysis.responsesFor(formType)));

        return project(analysis, definition, formType, settled, locale);
    }

    /** Answer-as-you-type: answers come from the request, nothing is stored yet. */
    @Transactional(readOnly = true)
    public FormState resolve(String analysisUid, LeverageFormType formType,
                             Integer version, Map<String, String> answers, String locale) {
        LeverageAnalysis analysis = analyses.findByAnalysisUid(analysisUid)
                .orElseThrow(() -> new AnalysisNotFoundException(analysisUid));

        DecisionTreeDefinition definition = version == null
                ? definitionFor(analysis, formType)
                : resolver.resolvePinned(formType, version);

        return project(analysis, definition, formType, coercion.coerce(definition, answers), locale);
    }

    /**
     * Shared tail of both reads.
     *
     * <p><b>Order matters.</b> Coercion settles the checklists, then the financial table is
     * resolved against those settled answers, then its figures are written OVER them. Everything
     * downstream — traversal, validation, the projection — reads the same merged map, so the
     * ratio the analyst sees is the ratio that routed.
     */
    private FormState project(LeverageAnalysis analysis, DecisionTreeDefinition definition,
                              LeverageFormType formType, Map<String, String> settled, String locale) {

        String language = locale == null ? definition.defaultLocale() : locale;
        AnalysisSubject subject = AnalysisSubject.of(analysis);

        // Resolved before traversal, because Q-Q01 and Q-Q02 compare boxes it fills in.
        FinancialTable financials = financialTable.resolve(definition, settled, subject);
        Map<String, String> resolved = financials.applyTo(settled);

        FormAnswers answers = FormAnswers.of(definition, resolved,
                crossFormAnswers(analysis, formType),
                derivedValues.resolve(derivedSources(definition), subject, language));

        TraversalResult result = traversal.resolve(definition, answers);

        EntityEligibility entity = entityEligibility.resolve(resolved.get(LOOKUP_QUESTION), subject);

        List<PanelSnapshot> panels = infoPanelResolver.resolve(definition,
                panelSelector.triggeredBy(definition, result.flags()), subject, language);

        return formStateAssembler.assemble(definition, resolved, result,
                validation.violations(definition, resolved, result, entity, financials.computed()),
                panels, language, FormAudit.of(analysis));
    }

    private DecisionTreeDefinition definitionFor(LeverageAnalysis analysis, LeverageFormType formType) {
        LeverageDecisionTreeDefinition pinned = analysis.decisionTreeFor(formType);
        return pinned == null
                ? resolver.resolveActive(formType)
                : resolver.resolvePinned(formType, pinned.getVersion());
    }

    private Map<String, String> crossFormAnswers(LeverageAnalysis analysis, LeverageFormType formType) {
        Map<String, String> crossForm = new LinkedHashMap<>();
        for(LeverageFormType source: LeverageFormType.values()) {
            if(source != target) {
                crossForm.putAll(SnapshotAnswers.flattenForCrossForm(source, analysis.responsesFor(source)))
            }
        }
        return crossForm;
    }

    static Set<String> derivedSources(DecisionTreeDefinition definition) {
        return definition.questions().stream()
                .map(Question::derivedFrom)
                .filter(source -> !source.startWith("CALC/"))
                .collect(Collectors.toUnmodifiableSet());
    }

    private LeverageAnalysis load(String analysisUid) {
        return analyses.findByAnalysisUid(analysisUid)
                .orElseThrow(() -> new AnalysisNotFoundException(analysisUid));
    }
}
