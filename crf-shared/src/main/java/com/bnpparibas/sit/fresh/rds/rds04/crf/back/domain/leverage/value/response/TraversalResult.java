package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.response;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.Question;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The outcome of walking a form with the answers given so far.
 *
 * <p>Recomputed from scratch on every call — nothing about the walk is stored. Answers are the
 * only state, so a refresh, a re-open a week later and a replay for audit all produce the same
 * result.
 *
 * @param state           where the walk stopped
 * @param question        the question awaiting an answer; empty unless {@code PENDING_INPUT}
 * @param computedAnswers values the system filled along the way, by question key — the analyst
 *                        never typed these, but they are part of the record
 * @param flags           output flags accumulated over the whole walk
 * @param outcome         PRELIMINARY only: which forms this recommendation opens
 * @param path            question keys visited, in order — what the UI renders as the trail
 */
@DomainDrivenDesign.ValueObject
public record TraversalResult(TraversalState state,
                              Question question,
                              Map<String, String> computedAnswers,
                              Map<String, String> flags,
                              RecommendationOutcome outcome,
                              List<String> path) {

    public TraversalResult {
        computedAnswers = computedAnswers == null ? Map.of() : Map.copyOf(computedAnswers);
        flags = flags == null ? Map.of() : Map.copyOf(flags);
        path = path == null ? List.of() : List.copyOf(path);
    }

    public Optional<Question> pendingQuestion() {
        return Optional.ofNullable(question);
    }

    public boolean isComplete() {
        return state == TraversalState.TERMINAL;
    }
}
