package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service.TraversalAnswers;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.ItemAnswer;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Decides whether one {@link Condition} holds. Pure, stateless, and the single place the routing
 * language is interpreted — branches and value rules both come through here.
 *
 * <p><b>An unanswered question matches nothing.</b> Not the default branch, not an exception —
 * simply false. Every path below returns false on an absent value, and that is deliberate: it is
 * what makes first-match-wins safe to author, because a rule about a path the analyst did not take
 * stays silent instead of firing on a blank.
 *
 * <p>Each method here does one thing so that no method exceeds a cognitive complexity of a few
 * points; the dispatch chains are flat by design rather than nested.
 */
@DomainDrivenDesign.DomainService
public final class ConditionEvaluator {

    /**
     * @param owner the question whose branch or value rule this is — the subject of any condition
     *              that names neither a question nor a field
     */
    public boolean matches(Condition condition, Question owner, TraversalAnswers answers) {
        if (condition == null) {
            return false;
        }
        if (condition.isDefault()) {
            return true;
        }
        if (condition.isComposite()) {
            return allMatch(condition.allOf(), owner, answers);
        }
        return matchesLeaf(condition, owner, answers);
    }

    private boolean allMatch(List<Condition> children, Question owner, TraversalAnswers answers) {
        return children.stream().allMatch(child -> matches(child, owner, answers));
    }

    private boolean matchesLeaf(Condition condition, Question owner, TraversalAnswers answers) {
        if (condition.aggregate() != null) {
            return matchesAggregate(condition.aggregate(), owner, answers);
        }
        if (condition.comparison() != null) {
            return matchesComparison(condition.comparison(), answers);
        }
        if (hasRanges(condition)) {
            return matchesRanges(condition, answers);
        }
        return matchesValue(condition, owner, answers);
    }

    // ------------------------------------------------------------------ checklist aggregates

    /**
     * ANY_YES and ALL_NO are strict complements over "is any item YES", so exactly one always
     * holds and a checklist can never strand the walk. NOT_APPLICABLE counts as non-triggering,
     * which is what lets a block that a YES already settled still route cleanly.
     */
    private boolean matchesAggregate(Aggregate aggregate, Question owner, TraversalAnswers answers) {
        boolean anyYes = answers.itemAnswers(owner.key()).values().stream()
                .anyMatch(answer -> answer == ItemAnswer.YES);
        return aggregate == Aggregate.ANY_YES ? anyYes : !anyYes;
    }

    // ------------------------------------------------------------------ numeric

    /**
     * {@code field totalEcbDebt > 4 x field adjustedEbitda}. Written as a multiple rather than a
     * ratio on purpose: dividing by a negative adjusted EBITDA would flip the inequality, so the
     * two are not interchangeable.
     */
    private boolean matchesComparison(Comparison comparison, TraversalAnswers answers) {
        Optional<BigDecimal> left = answers.fieldValue(comparison.leftFieldKey());
        Optional<BigDecimal> right = answers.fieldValue(comparison.rightFieldKey());
        if (left.isEmpty() || right.isEmpty()) {
            return false;
        }
        BigDecimal threshold = right.get().multiply(comparison.multiplier());
        return satisfies(left.get().compareTo(threshold), comparison.operator());
    }

    private boolean satisfies(int comparison, ComparisonOperator operator) {
        return switch (operator) {
            case GT -> comparison > 0;
            case GTE -> comparison >= 0;
            case LT -> comparison < 0;
            case LTE -> comparison <= 0;
        };
    }

    /** Terms inside one {@code range [...]} are alternatives: any one of them matching is enough. */
    private boolean matchesRanges(Condition condition, TraversalAnswers answers) {
        return numericSubject(condition, answers)
                .filter(value -> condition.ranges().stream().anyMatch(range -> range.contains(value)))
                .isPresent();
    }

    private Optional<BigDecimal> numericSubject(Condition condition, TraversalAnswers answers) {
        if (condition.fieldKey() != null) {
            return answers.fieldValue(condition.fieldKey());
        }
        return answers.answerOf(condition.questionKey()).flatMap(ConditionEvaluator::toNumber);
    }

    private static Optional<BigDecimal> toNumber(String raw) {
        try {
            return Optional.of(new BigDecimal(raw));
        } catch (NumberFormatException ex) {
            // A non-numeric answer simply does not fall in any band. The validator has already
            // rejected ranges against non-numeric targets, so this is defence, not a code path.
            return Optional.empty();
        }
    }

    // ------------------------------------------------------------------ values

    private boolean matchesValue(Condition condition, Question owner, TraversalAnswers answers) {
        Optional<String> actual = subject(condition, owner, answers);
        if (actual.isEmpty()) {
            return false;
        }
        String value = actual.get();
        if (condition.in() != null) {
            return condition.in().contains(value);
        }
        return value.equals(condition.equals());
    }

    /**
     * What the predicate is applied to: a named field, a named question, or — when neither is
     * named — the owning question's own answer.
     */
    private Optional<String> subject(Condition condition, Question owner, TraversalAnswers answers) {
        if (condition.fieldKey() != null) {
            return answers.fieldValue(condition.fieldKey()).map(BigDecimal::toPlainString);
        }
        String questionKey = condition.questionKey() != null ? condition.questionKey() : owner.key();
        return answers.answerOf(questionKey);
    }

    private boolean hasRanges(Condition condition) {
        return condition.ranges() != null && !condition.ranges().isEmpty();
    }
}
