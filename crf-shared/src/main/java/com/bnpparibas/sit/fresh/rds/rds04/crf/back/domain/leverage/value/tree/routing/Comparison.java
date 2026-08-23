package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.routing;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.math.BigDecimal;

/**
 * Compares one numeric field against a multiple of another:
 * {@code field totalEcbDebt > 4 x field adjustedEbitda}.
 *
 * <p>Deliberately a fixed four-token shape — left field, operator, multiplier, right field —
 * with no nesting and no precedence, so this stays a predicate and does not become an
 * expression language.
 *
 * <p>It exists because the BR is written against the DEBT MULTIPLE, not the ratio, and the two
 * are only equivalent while adjusted EBITDA is positive: dividing by a negative EBITDA flips
 * the inequality.
 */
@DomainDrivenDesign.ValueObject
public record Comparison(String leftFieldKey,
                         ComparisonOperator operator,
                         BigDecimal multiplier,
                         String rightFieldKey) {
}
