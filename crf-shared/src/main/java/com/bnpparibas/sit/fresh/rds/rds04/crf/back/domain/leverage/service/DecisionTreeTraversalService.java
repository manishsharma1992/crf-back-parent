package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.ItemAnswer;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.RecommendationOutcome;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.TraversalResult;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.TraversalState;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.math.BigDecimal;
import java.util.*;

/**
 * Walks a published decision tree with the answers given so far and reports where it stopped.
 *
 * <p><b>Stateless.</b> Nothing about a walk is stored: answers are the only state, and the result
 * is recomputed from the entry question on every call. A refresh, a re-open a week later and a
 * replay for audit therefore all produce the same answer — which for a regulatory form is the
 * whole point.
 *
 * <p><b>Two stopping states.</b> {@code PENDING_INPUT} and {@code TERMINAL}. There is no waiting
 * state any more: every calculation happens in this layer, so the walk never pauses on another
 * service. {@code STRANDED} exists only to make a mis-published definition visible rather than
 * silent.
 *
 * <p><b>Structure.</b> The walk is a {@link Walk} object holding the little state a single run
 * needs, so each step is a short method instead of one long loop with accumulating locals. That
 * keeps every method's cognitive complexity in low single figures, and — more usefully — means
 * each rule of the traversal can be read on its own.
 */
@DomainDrivenDesign.DomainService
public final class DecisionTreeTraversalService {

    /**
     * The validator rejects cycles before publication, so this can only trip on a definition that
     * was published around it. Bounded to fail as STRANDED rather than to hang a request thread.
     */
    private static final int MAX_STEPS = 200;

    private final ConditionEvaluator evaluator;

    public DecisionTreeTraversalService(ConditionEvaluator evaluator) {
        this.evaluator = evaluator;
    }

    public TraversalResult resolve(DecisionTreeDefinition definition, TraversalAnswers answers) {
        return new Walk(definition, answers).run();
    }

    /** One step's verdict: stop here, end here, or go there. */
    private record Step(TraversalState stop, Branch terminal, String next) {

        static Step pending() {
            return new Step(TraversalState.PENDING_INPUT, null, null);
        }

        static Step stranded() {
            return new Step(TraversalState.STRANDED, null, null);
        }

        static Step end(Branch branch) {
            return new Step(TraversalState.TERMINAL, branch, null);
        }

        static Step goTo(String questionKey) {
            return new Step(null, null, questionKey);
        }

        boolean stops() {
            return stop != null;
        }
    }

    /**
     * One run. Holds the answers accumulated along the way, so that a value the system computes at
     * Q-S04 is visible to a condition read at Q-Q02 — the walk builds up knowledge as it goes.
     */
    private final class Walk implements TraversalAnswers {

        private final DecisionTreeDefinition definition;
        private final TraversalAnswers given;
        private final Map<String, String> computed = new LinkedHashMap<>();
        private final Map<String, String> prefilledAnswers = new LinkedHashMap<>();
        private final Map<String, String> flags = new LinkedHashMap<>();
        private final List<String> path = new ArrayList<>();

        private Question current;
        private RecommendationOutcome outcome;

        private Walk(DecisionTreeDefinition definition, TraversalAnswers given) {
            this.definition = definition;
            this.given = given;
        }

        private TraversalResult run() {
            String key = definition.entryQuestion();
            for (int guard = 0; guard < MAX_STEPS; guard++) {
                current = question(key);
                if (current == null) {
                    return result(TraversalState.STRANDED);
                }
                path.add(key);
                Step step = step(current);
                if (step.stops()) {
                    return stopped(step);
                }
                key = step.next();
            }
            return result(TraversalState.STRANDED);
        }

        private Step step(Question question) {
            if (awaitsAnswer(question)) {
                return Step.pending();
            }
            recordPrefilled(question);
            fillComputedValue(question);
            collectFlagsFilledBy(question);
            return follow(question);
        }

        /** First match wins — the order the BA authored the lines IS the logic. */
        private Step follow(Question question) {
            for (Branch branch : question.branches()) {
                if (!evaluator.matches(branch.when(), question, this)) {
                    continue;
                }
                applyEffect(branch.effect());
                return branch.isTerminal() ? Step.end(branch) : Step.goTo(branch.goTo());
            }
            return Step.stranded();
        }

        // -------------------------------------------------------------- what needs an answer

        private boolean awaitsAnswer(Question question) {
            return !question.computed() && resolvedAnswer(question).isEmpty();
        }

        /**
         * A DATA_ENTRY question is answered once every mandatory box has a value. The per-field
         * checks that refuse a save — justifications, positivity, business-group lookups — are the
         * save path's business, not the walk's.
         */
        private Optional<String> resolvedAnswer(Question question) {
            if (question.type() == QuestionType.DATA_ENTRY) {
                return dataEntryAnswer(question);
            }
            if (question.type() == QuestionType.CHECKLIST) {
                return checklistAnswer(question);
            }
            return given.answerOf(question.key()).or(() -> prefilled(question));
        }

        private Optional<String> dataEntryAnswer(Question question) {
            boolean complete = question.fields().stream()
                    .filter(DataField::mandatory)
                    .allMatch(field -> given.fieldValue(field.key()).isPresent());
            return complete ? Optional.of(question.key()) : Optional.empty();
        }

        /**
         * A checklist is answerable once ANY item is YES — the YES settles the block and the
         * remaining items stop being required — or once every item has been answered.
         */
        private Optional<String> checklistAnswer(Question question) {
            Map<String, ItemAnswer> items = given.itemAnswers(question.key());
            if (items.values().stream().anyMatch(answer -> answer == ItemAnswer.YES)) {
                return Optional.of(Aggregate.ANY_YES.name());
            }
            boolean allAnswered = !question.items().isEmpty()
                    && items.size() == question.items().size();
            return allAnswered ? Optional.of(Aggregate.ALL_NO.name()) : Optional.empty();
        }

        /** {@code FED/Q01}: taken from the other form when it has been answered there. */
        private Optional<String> prefilled(Question question) {
            return Optional.ofNullable(question.prefillFrom()).flatMap(given::crossFormAnswer);
        }

        // -------------------------------------------------------------- computed values

        /**
         * A prefilled answer is recorded separately from a computed one. Both are values the
         * analyst did not type here, but the provenance differs — "copied from the FED form" is
         * not "the tree worked it out" — and the snapshot has to tell them apart.
         */
        private void recordPrefilled(Question question) {
            if (question.prefillFrom() == null || given.answerOf(question.key()).isPresent()) {
                return;
            }
            prefilled(question).ifPresent(value -> prefilledAnswers.put(question.key(), value));
        }

        private void fillComputedValue(Question question) {
            if (!question.computed() || computed.containsKey(question.key())) {
                return;
            }
            firstMatchingRule(question).ifPresent(value -> computed.put(question.key(), value));
        }

        private Optional<String> firstMatchingRule(Question question) {
            return question.valueRules().stream()
                    .filter(rule -> evaluator.matches(rule.when(), question, this))
                    .map(ValueRule::value)
                    .findFirst();
        }

        // -------------------------------------------------------------- flags

        /**
         * A question's chosen option, or a computed box's value, BECOMES a flag. Declaring it on
         * the row means a flag can be filled on a path that continues, where a terminal
         * {@code flags:} clause could never reach.
         */
        private void collectFlagsFilledBy(Question question) {
            if (question.fillsFlag() != null) {
                resolvedAnswer(question).ifPresent(value -> flags.put(question.fillsFlag(), value));
            }
            question.fields().stream()
                    .filter(field -> field.fillsFlag() != null)
                    .forEach(this::collectFieldFlag);
        }

        private void collectFieldFlag(DataField field) {
            given.fieldValue(field.key())
                    .ifPresent(value -> flags.put(field.fillsFlag(), value.toPlainString()));
        }

        /** A flag named nowhere stays absent — that is all "remains empty" ever meant. */
        private void applyEffect(Effect effect) {
            if (effect == null) {
                return;
            }
            flags.putAll(effect.flags());
            if (effect.setOutcome() != null) {
                outcome = effect.setOutcome();
            }
        }

        // -------------------------------------------------------------- results

        private TraversalResult stopped(Step step) {
            if (step.stop() == TraversalState.PENDING_INPUT) {
                return result(TraversalState.PENDING_INPUT);
            }
            return result(step.stop());
        }

        private TraversalResult result(TraversalState state) {
            Question pending = state == TraversalState.PENDING_INPUT ? current : null;
            return new TraversalResult(state, pending, computed, prefilledAnswers, flags, outcome, path);
        }

        private Question question(String key) {
            return definition.questions().stream()
                    .filter(candidate -> candidate.key().equals(key))
                    .findFirst()
                    .orElse(null);
        }

        // -------------------------------------------------------------- answers seen by conditions

        /**
         * The walk answers condition lookups itself, layering values it has computed over the ones
         * the analyst gave. Without this, a condition on Q-S04 could not see the level Q-S04 just
         * derived.
         */
        @Override
        public Optional<String> answerOf(String questionKey) {
            String derived = computed.get(questionKey);
            if (derived != null) {
                return Optional.of(derived);
            }
            return given.answerOf(questionKey).or(() -> implied(questionKey));
        }

        /**
         * An answer nobody typed into THIS form but which the question nonetheless has: a
         * checklist's aggregate, or a value prefilled from another form.
         *
         * <p>This has to agree with {@link #resolvedAnswer}. When it did not, a prefilled question
         * was correctly not ASKED but was invisible to the conditions that route on it, so
         * {@code YES -> …} fell through to the default and the branch's flags were never set.
         */
        private Optional<String> implied(String questionKey) {
            Question question = question(questionKey);
            if (question == null) {
                return Optional.empty();
            }
            if (question.type() == QuestionType.CHECKLIST) {
                return checklistAnswer(question);
            }
            // Deliberately not DATA_ENTRY: its sentinel answer means "every mandatory box is
            // filled" and is not a value any condition should compare against — those name a field.
            return prefilled(question);
        }

        @Override
        public Optional<BigDecimal> fieldValue(String fieldKey) {
            return given.fieldValue(fieldKey);
        }

        @Override
        public Map<String, ItemAnswer> itemAnswers(String questionKey) {
            return given.itemAnswers(questionKey);
        }

        @Override
        public Optional<String> crossFormAnswer(String formAndQuestionKey) {
            return given.crossFormAnswer(formAndQuestionKey);
        }
    }
}
