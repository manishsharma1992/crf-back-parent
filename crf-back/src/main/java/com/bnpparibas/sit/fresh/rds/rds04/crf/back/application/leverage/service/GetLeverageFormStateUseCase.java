package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.FormAnswers;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.FormAudit;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.FormState;

import java.util.Map;

public class GetLeverageFormStateUseCase {

    private final ValidationDomainService validation; // new collaborator

    /** Reload: replays what is stored. Locale comes from the caller, defaulting to the form's. */
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
     * Shared tail of both reads. Cross-form is taken from the aggregate either way — that is what
     * neither of these can borrow from the stateless use case.
     */
    private FormState project(LeverageAnalysis analysis, DecisionTreeDefinition definition,
                              LeverageFormType formType, Map<String, String> settled, String locale) {

        TraversalResult result = traversal.resolve(definition,
                FormAnswers.of(definition, settled, crossFormAnswers(analysis, formType)));

        return formStateAssembler.assemble(definition, settled, result,
                validation.violations(definition, settled, result),
                locale == null ? definition.defaultLocale() : locale,
                FormAudit.of(analysis));
    }
}
