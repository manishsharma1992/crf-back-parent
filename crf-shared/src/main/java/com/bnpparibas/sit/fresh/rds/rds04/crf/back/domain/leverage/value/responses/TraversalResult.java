package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.Question;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.Collections;
import java.util.LinkedHashMap;
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
 * @param computedAnswers values the tree DERIVED along the way, by question key
 * @param prefilledAnswers values COPIED from another form, by question key. Kept apart from
 *                        {@code computedAnswers} because the provenance differs: "the analyst
 *                        answered this on the FED form" is not "the tree worked it out", and the
 *                        snapshot has to label them differently.
 * @param flags           output flags accumulated over the whole walk
 * @param outcome         PRELIMINARY only: which forms this recommendation opens
 * @param path            question keys visited, in order — what the UI renders as the trail
 */
@DomainDrivenDesign.ValueObject
public record TraversalResult(TraversalState state,
                              Question question,
                              Map<String, String> computedAnswers,
                              Map<String, String> prefilledAnswers,
                              Map<String, String> flags,
                              RecommendationOutcome outcome,
                              List<String> path) {

    public TraversalResult {
        computedAnswers = computedAnswers == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(computedAnswers));
        prefilledAnswers = prefilledAnswers == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(prefilledAnswers));
        flags = flags == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(flags));
        path = path == null ? List.of() : List.copyOf(path);
    }

    /**
     * Every value the walk resolved that the analyst did not type here — derived first, since a
     * computed value overrides a prefilled one on the same key.
     */
    public Map<String, String> resolvedAnswers() {
        Map<String, String> all = new java.util.LinkedHashMap<>(prefilledAnswers);
        all.putAll(computedAnswers);
        return Collections.unmodifiableMap(all);
    }

    public Optional<Question> pendingQuestion() {
        return Optional.ofNullable(question);
    }

    public boolean isComplete() {
        return state == TraversalState.TERMINAL;
    }
}
