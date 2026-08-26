package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import java.util.ArrayList;
import java.util.List;

import com.bnpparibas.crf.shared.domain.leverage.model.CompletenessInput;
import com.bnpparibas.crf.shared.domain.leverage.model.FormCompletenessInput;
import com.bnpparibas.crf.shared.domain.leverage.model.LeverageAnalysis;
import com.bnpparibas.crf.shared.domain.leverage.model.LeverageFormType;
import com.bnpparibas.crf.shared.domain.leverage.model.RequiredField;
import com.bnpparibas.crf.shared.domain.leverage.port.LeverageTraversalPort;
import com.bnpparibas.crf.shared.domain.leverage.port.RequiredJustificationsProvider;
import com.bnpparibas.crf.shared.domain.leverage.port.ValidationAlertCounter;

/**
 * Projects an analysis into the facts the completeness rule needs.
 *
 * <p>Applicable forms are derived from the definition-id columns, exactly as the
 * BR03 snapshot does: preliminary is always present, ECB and FED independently,
 * and an analysis routed to both yields two entries. Deriving it the same way in
 * both places is intentional - two different notions of "which forms apply" would
 * eventually disagree.
 *
 * <p>Sits in the application layer because it orchestrates collaborators (the
 * traversal engine, the justification source, the alert store). The domain
 * service downstream stays a pure function of what this produces, which is what
 * makes the rule testable without a database.
 */
public class CompletenessInputAssemblerImpl implements CompletenessInputAssembler {

    private final LeverageTraversalPort traversalPort;
    private final RequiredJustificationsProvider justificationsProvider;
    private final ValidationAlertCounter alertCounter;

    public CompletenessInputAssemblerImpl(LeverageTraversalPort traversalPort,
                                          RequiredJustificationsProvider justificationsProvider,
                                          ValidationAlertCounter alertCounter) {
        this.traversalPort = traversalPort;
        this.justificationsProvider = justificationsProvider;
        this.alertCounter = alertCounter;
    }

    @Override
    public CompletenessInput assemble(LeverageAnalysis analysis) {
        List<FormCompletenessInput> forms = new ArrayList<>();
        addIfApplicable(forms, analysis, LeverageFormType.PRELIMINARY,
                analysis.getPreliminaryDefinitionId());
        addIfApplicable(forms, analysis, LeverageFormType.ECB,
                analysis.getEcbDefinitionId());
        addIfApplicable(forms, analysis, LeverageFormType.FED,
                analysis.getFedDefinitionId());
        return new CompletenessInput(forms);
    }

    private void addIfApplicable(List<FormCompletenessInput> forms,
                                 LeverageAnalysis analysis,
                                 LeverageFormType formType,
                                 Long definitionId) {
        if (definitionId == null) {
            return;
        }
        forms.add(buildFormInput(analysis.getAnalysisUid(), formType, definitionId));
    }

    /**
     * Required fields are the union of two sources, kept in this order so the UI
     * scrolls to the earliest unanswered item: the mandatory questions actually
     * reached on the walk, then the justifications attached to financial rows.
     *
     * <p>Justifications are not part of the tree, so omitting them would report a
     * form complete while its justification dialog sat empty.
     */
    private FormCompletenessInput buildFormInput(String analysisUid,
                                                 LeverageFormType formType,
                                                 Long definitionId) {
        List<RequiredField> requiredFields = new ArrayList<>(
                traversalPort.visibleMandatoryQuestions(analysisUid, formType, definitionId));
        requiredFields.addAll(
                justificationsProvider.requiredJustifications(analysisUid, formType));

        return new FormCompletenessInput(
                formType,
                traversalPort.traversalState(analysisUid, formType, definitionId),
                requiredFields,
                alertCounter.countBlockingAlerts(analysisUid, formType));
    }
}
