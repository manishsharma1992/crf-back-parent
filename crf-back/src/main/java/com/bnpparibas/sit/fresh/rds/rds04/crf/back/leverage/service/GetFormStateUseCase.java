package com.bnpparibas.sit.fresh.rds.rds04.crf.back.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service.DecisionTreeTraversalService;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.TraversalResult;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeDefinition;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeResolver;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;
import org.springframework.transaction.annotation.Transactional;

/**
 * Renders a form's current state for the UI.
 *
 * <p>Orchestration only — resolve the definition, walk it, project the result. The single change
 * from the previous version is that the engine now takes a {@link com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service.TraversalAnswers}
 * rather than a raw map, so the request's answers are wrapped in {@link FormAnswers} here. The
 * REQUEST shape is untouched, so nothing the UI sends changes.
 *
 * <p>Stateless with respect to answers: the request carries them, so this is a pure read.
 */
@DomainDrivenDesign.ApplicationService
public class GetFormStateUseCase {

    private final DecisionTreeResolver resolver;
    private final DecisionTreeTraversalService traversal;
    private final FormStateAssembler assembler;

    public GetFormStateUseCase(DecisionTreeResolver resolver,
                               DecisionTreeTraversalService traversal,
                               FormStateAssembler assembler) {
        this.resolver = resolver;
        this.traversal = traversal;
        this.assembler = assembler;
    }

    @Transactional(readOnly = true)
    public FormState getFormState(GetFormStateRequest request) {
        DecisionTreeDefinition definition = request.version() == null
                ? resolver.resolveActive(request.formType())                     // first load
                : resolver.resolvePinned(request.formType(), request.version()); // pinned session

        FormAnswers answers = FormAnswers.of(definition, request.answers());
        TraversalResult result = traversal.resolve(definition, answers);

        return assembler.assemble(definition, request.answers(), result);
    }
}
