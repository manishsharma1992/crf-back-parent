package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.Condition;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.ValueRule;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.ArrayList;
import java.util.List;

/**
 * Parses a <b>Value Rules</b> cell: {@code CONDITION -> VALUE}, one per line, first match wins.
 *
 * <p>Same condition grammar as a branch, but the arrow points at one of the question's own option
 * values instead of a next question. Q-Q02 is the reference case, and its FIRST line is the
 * "not displayed" rule — which must stay first, because with a negative adjusted EBITDA the debt
 * multiple below it would otherwise fire on a path that is meant to read NO.
 */
@DomainDrivenDesign.ApplicationService
public final class ValueRuleExpressionParser {

    private final ConditionExpressionParser conditionParser;

    public ValueRuleExpressionParser(ConditionExpressionParser conditionParser) {
        this.conditionParser = conditionParser;
    }

    public List<ValueRule> parse(String cell, SourceLocation where, ImportIssues issues) {
        List<ValueRule> rules = new ArrayList<>();
        List<String> lines = Cells.lines(cell);
        for (int i = 0; i < lines.size(); i++) {
            SourceLocation lineWhere = new SourceLocation(where.sheet(), where.row(), where.columnHeader(), i + 1);
            String line = lines.get(i);
            String[] halves = Expressions.splitOnceTopLevel(line, BranchExpressionParser.ARROW);
            if (halves == null) {
                issues.add(lineWhere, "VALUE_RULE_NO_ARROW", "Value rule has no '->': " + line);
                continue;
            }
            if (halves[1].isBlank()) {
                issues.add(lineWhere, "VALUE_RULE_NO_VALUE", "Value rule assigns nothing: " + line);
                continue;
            }
            Condition when = conditionParser.parse(halves[0], lineWhere, issues);
            if (when != null) rules.add(new ValueRule(when, halves[1].trim()));
        }
        return rules;
    }
}
