package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.FormAudit;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.formstate.FormAnswers;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.formstate.FormState;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.formstate.FormStateAssembler;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.analysis.AnalysisNotFoundException;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.analysis.LeverageAnalysis;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.analysis.LeverageAnalysisRepository;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.analysis.LeverageResponses.FormResponses;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service.DecisionTreeTraversalService;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.RecommendationOutcome;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.TraversalResult;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeDefinition;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeResolver;

import java.util.List;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists the preliminary section AND returns the resulting form state in one round-trip.
 *
 * <p>Flow: load the aggregate -> resolve its PINNED definition (reproducibility) -> traverse
 * against the submitted answers -> freeze the answered questions into a self-describing snapshot
 * -> record on the aggregate (with the deduced outcome if terminal) -> save -> return the new
 * {@link FormState} so the UI reveals the next question.
 *
 * <p>Two changes only, both mechanical: the engine takes {@link FormAnswers} rather than a raw
 * map, and completion is read as {@code result.isComplete()} rather than a status enum. The
 * request, the snapshot and the aggregate are untouched.
 */
@Service
@RequiredArgsConstructor
@DomainDrivenDesign.ApplicationService
public class SavePreliminaryFormUseCase {

    private final LeverageAnalysisRepository analyses;
    private final DecisionTreeResolver resolver;
    private final DecisionTreeTraversalService traversal;
    private final PreliminaryResponseAssembler responseAssembler;
    private final FormStateAssembler formStateAssembler;
    private final ValidationDomainService validation;   // new collaborator

    @Transactional
    public FormState save(String analysisUid, SavePreliminaryFormRequest request) {
        LeverageAnalysis analysis = analyses.findByAnalysisUid(analysisUid)
                .orElseThrow(() -> new AnalysisNotFoundException(analysisUid));

        // Was missing here, unlike SaveLeverageFormUseCase: a preliminary save can now delete FED
        // and ECB answers, so it must not run on a validated analysis.
        analysis.assertModifiable();

        // The aggregate owns the pinned version — the session cannot drift onto a newer one.
        DecisionTreeDefinition definition =
                resolver.resolvePinned(LeverageFormType.PRELIMINARY, analysis.getPreliminaryDecisionTree().getVersion());

        FormAnswers answers = FormAnswers.of(definition, request.answers());
        TraversalResult result = traversal.resolve(definition, answers);

        String locale = request.locale() != null ? request.locale() : definition.defaultLocale();
        // The preliminary form declares no info panels, so there is nothing to resolve from RMPM.
        // The ECB use case will pass InfoPanelResolver's output here instead.
        FormResponses snapshot =
                responseAssembler.assemble(definition, request.answers(), result, locale, List.of());

        // Outcome is only known once the section is terminal; null while still in progress.
        RecommendationOutcome outcome = result.isComplete() ? result.outcome() : null;

        analysis.recordPreliminary(snapshot, outcome);

        // A route that no longer names FED (or ECB) takes that form's answers with it — see
        // LeverageAnalysis#retireSectionsNotIn. The forms come from the Forms tab's catalogue,
        // the same list FormStateAssembler hands the screen, so backend and UI agree on the route.
        // Only on a terminal outcome: mid-edit there is no route yet, and clearing then would
        // wipe both forms on every re-picked answer. An outcome missing from the catalogue (the
        // import gate refuses that) retires nothing rather than everything.
        Outcome routed = outcome == null ? null : definition.outcomes().get(outcome);
        if (routed != null) {
            analysis.retireSectionsNotIn(routed.formsToShow());
        }
        analyses.save(analysis);

        return formStateAssembler.assemble(definition, request.answers(), result,
                validation.violations(definition, request.answers(), result),
                locale, FormAudit.of(analysis));
    }
}
