package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

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

    // ... get(), load(), resolve() unchanged ...

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

    // ... crossFormAnswers(), derivedSources(), definitionFor() unchanged ...
}
