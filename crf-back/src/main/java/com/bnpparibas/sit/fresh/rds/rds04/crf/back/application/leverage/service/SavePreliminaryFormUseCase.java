package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

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
@DomainDrivenDesign.ApplicationService
public class SavePreliminaryFormUseCase {

    private final LeverageAnalysisRepository analyses;
    private final DecisionTreeResolver resolver;
    private final DecisionTreeTraversalService traversal;
    private final PreliminaryResponseAssembler responseAssembler;
    private final FormStateAssembler formStateAssembler;

    public SavePreliminaryFormUseCase(LeverageAnalysisRepository analyses,
                                      DecisionTreeResolver resolver,
                                      DecisionTreeTraversalService traversal,
                                      PreliminaryResponseAssembler responseAssembler,
                                      FormStateAssembler formStateAssembler) {
        this.analyses = analyses;
        this.resolver = resolver;
        this.traversal = traversal;
        this.responseAssembler = responseAssembler;
        this.formStateAssembler = formStateAssembler;
    }

    @Transactional
    public FormState save(String analysisUid, SavePreliminaryFormRequest request) {
        LeverageAnalysis analysis = analyses.findByUid(analysisUid)
                .orElseThrow(() -> new AnalysisNotFoundException(analysisUid));

        // The aggregate owns the pinned version — the session cannot drift onto a newer one.
        DecisionTreeDefinition definition =
                resolver.resolvePinned(LeverageFormType.PRELIMINARY, analysis.preliminaryVersion());

        FormAnswers answers = FormAnswers.of(definition, request.answers());
        TraversalResult result = traversal.resolve(definition, answers);

        String locale = request.locale() != null ? request.locale() : definition.defaultLocale();
        FormResponses snapshot = responseAssembler.assemble(definition, request.answers(), result, locale);

        // Outcome is only known once the section is terminal; null while still in progress.
        RecommendationOutcome outcome = result.isComplete() ? result.outcome() : null;

        analysis.recordPreliminary(snapshot, outcome);
        analyses.save(analysis);

        return formStateAssembler.assemble(definition, request.answers(), result);
    }
}
