package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.RecommendationOutcome;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.TraversalResult;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.TraversalState;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeDefinition;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.Outcome;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.Question;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Projects a pure {@link TraversalResult} into the UI-facing {@link FormState}.
 *
 * <p>Application layer, because it is a projection concern rather than a domain rule — which is
 * what keeps the use cases thin and the traversal engine ignorant of screens.
 *
 * <p><b>Visible questions come from the walk's path.</b> The path is exactly the questions the
 * answers led through, so a question on a road not taken is never rendered. It ends with either
 * the pending question or the terminal one, so the trail always finishes where the analyst is.
 */
@DomainDrivenDesign.ApplicationService
public final class FormStateAssembler {

    public FormState assemble(DecisionTreeDefinition definition,
                              Map<String, String> answers,
                              TraversalResult result) {

        if (result.state() == TraversalState.STRANDED) {
            throw new StrandedTraversalException(definition.formType(), definition.version());
        }
        Map<String, Question> byKey = index(definition);
        String currentKey = result.pendingQuestion().map(Question::key).orElse(null);

        List<QuestionView> views = new ArrayList<>();
        for (String key : result.path()) {
            Question question = byKey.get(key);
            if (question == null) {
                continue;   // defensive: a path key with no definition cannot survive validation
            }
            views.add(QuestionView.from(question, answers, result.computedAnswers(),
                    result.prefilledAnswers(), key.equals(currentKey)));
        }
        return state(definition, result, views, currentKey);
    }

    private FormState state(DecisionTreeDefinition definition,
                            TraversalResult result,
                            List<QuestionView> views,
                            String currentKey) {

        if (result.state() == TraversalState.TERMINAL) {
            return new FormState(definition.formType(), definition.version(), FormState.Status.COMPLETED,
                    views, null, result.flags(), outcomeView(definition, result));
        }
        // Flags travel even mid-form: the LBO flag is filled by the very first question, and the
        // UI shows the whole catalogue from the start with the unfilled ones blank.
        return new FormState(definition.formType(), definition.version(), FormState.Status.IN_PROGRESS,
                views, currentKey, result.flags(), null);
    }

    /**
     * PRELIMINARY only. ECB and FED express their result as flags, so a terminal walk there
     * carries no outcome and this returns null rather than an empty shell.
     */
    private FormState.OutcomeView outcomeView(DecisionTreeDefinition definition, TraversalResult result) {
        RecommendationOutcome code = result.outcome();
        if (code == null) {
            return null;
        }
        Outcome catalogue = definition.outcomes().get(code);
        return new FormState.OutcomeView(
                code.name(),
                catalogue == null ? null : catalogue.displayValue(),
                catalogue == null ? List.of() : catalogue.formsToShow(),
                mergedFlags(catalogue, result));
    }

    /**
     * The catalogue's forced flags first, then anything the walk set — so a branch that names a
     * flag explicitly wins over the outcome's default for it.
     */
    private Map<String, String> mergedFlags(Outcome catalogue, TraversalResult result) {
        Map<String, String> flags = new LinkedHashMap<>();
        if (catalogue != null) {
            flags.putAll(catalogue.forcedFlags());
        }
        flags.putAll(result.flags());
        return flags;
    }

    private Map<String, Question> index(DecisionTreeDefinition definition) {
        Map<String, Question> byKey = new LinkedHashMap<>();
        definition.questions().forEach(question -> byKey.put(question.key(), question));
        return byKey;
    }
}
