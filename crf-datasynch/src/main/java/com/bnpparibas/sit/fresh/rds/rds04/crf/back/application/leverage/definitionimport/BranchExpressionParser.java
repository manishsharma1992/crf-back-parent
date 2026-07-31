package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.*;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.*;

/**
 * Parses a whole <b>Branches</b> cell into an ordered {@link Branch} list.
 *
 * <p>One line per branch, order preserved because FIRST MATCH WINS — that ordering is load-bearing
 * business logic, not formatting. Q-T01 relies on {@code Q01 is NO} sitting ahead of {@code ALL_NO}
 * because ALL_NO is true on both LBO paths and would otherwise swallow the non-LBO route.
 *
 * <pre>
 *   &lt;condition&gt; -&gt; END
 *   &lt;condition&gt; -&gt; END, flags: ecbLeveragedFlag=INR; escalatedTransactions=YES
 *   &lt;condition&gt; -&gt; END, outcome=ECB            (PRELIMINARY only)
 *   &lt;condition&gt; -&gt; Q-Q03
 * </pre>
 *
 * <p>An empty right-hand side ({@code ecbCovenantStructure=}) is rejected rather than treated as
 * "clear it": a flag holds no value until something sets one, so there is nothing to clear.
 */
@DomainDrivenDesign.ApplicationService
public final class BranchExpressionParser {

    static final String ARROW = "->";
    static final String END = "END";
    private static final String FLAGS_PREFIX = "flags:";
    private static final String OUTCOME_PREFIX = "outcome=";

    private final ConditionExpressionParser conditionParser;

    public BranchExpressionParser(ConditionExpressionParser conditionParser) {
        this.conditionParser = conditionParser;
    }

    public List<Branch> parse(String cell, SourceLocation where, ImportIssues issues) {
        List<Branch> branches = new ArrayList<>();
        List<String> lines = Cells.lines(cell);
        for (int i = 0; i < lines.size(); i++) {
            SourceLocation lineWhere = new SourceLocation(where.sheet(), where.row(), where.columnHeader(), i + 1);
            Branch branch = parseLine(lines.get(i), lineWhere, issues);
            if (branch != null) branches.add(branch);
        }
        return branches;
    }

    private Branch parseLine(String line, SourceLocation where, ImportIssues issues) {
        String[] halves = Expressions.splitOnceTopLevel(line, ARROW);
        if (halves == null) {
            issues.add(where, "BRANCH_NO_ARROW", "Branch line has no '->': " + line);
            return null;
        }
        Condition when = conditionParser.parse(halves[0], where, issues);
        if (when == null) return null;

        List<String> rightParts = Expressions.splitTopLevel(halves[1], ',');
        if (rightParts.isEmpty()) {
            issues.add(where, "BRANCH_NO_TARGET", "Branch line has nothing after '->': " + line);
            return null;
        }
        String target = rightParts.get(0).trim();
        Map<String, String> flags = new LinkedHashMap<>();
        RecommendationOutcome outcome = null;

        for (String part : rightParts.subList(1, rightParts.size())) {
            if (part.regionMatches(true, 0, FLAGS_PREFIX, 0, FLAGS_PREFIX.length())) {
                flags.putAll(parseFlagAssignments(part.substring(FLAGS_PREFIX.length()), where, issues));
            } else if (part.regionMatches(true, 0, OUTCOME_PREFIX, 0, OUTCOME_PREFIX.length())) {
                outcome = parseOutcome(part.substring(OUTCOME_PREFIX.length()).trim(), where, issues);
            } else {
                issues.add(where, "BRANCH_UNKNOWN_CLAUSE",
                        "Expected 'flags: ...' or 'outcome=...' after the target; got '" + part + "'");
            }
        }

        boolean terminal = END.equalsIgnoreCase(target);
        if (terminal) {
            return new Branch(when, null, new Effect(outcome, flags, true));
        }
        if (outcome != null) {
            issues.add(where, "BRANCH_OUTCOME_NOT_TERMINAL", "'outcome=' is only valid on a branch that ends the form");
        }
        // Flags on a CONTINUING branch are allowed: the form has not stopped, but the value is known.
        Effect effect = flags.isEmpty() ? null : new Effect(null, flags, false);
        return new Branch(when, target, effect);
    }

    private RecommendationOutcome parseOutcome(String token, SourceLocation where, ImportIssues issues) {
        for (RecommendationOutcome candidate : RecommendationOutcome.values()) {
            if (candidate.name().equalsIgnoreCase(token)) return candidate;
        }
        issues.add(where, "BRANCH_UNKNOWN_OUTCOME", "'" + token + "' is not a recommendation outcome");
        return null;
    }

    private Map<String, String> parseFlagAssignments(String text, SourceLocation where, ImportIssues issues) {
        Map<String, String> flags = new LinkedHashMap<>();
        for (String token : Expressions.splitTopLevel(text, ';')) {
            int eq = token.indexOf('=');
            if (eq <= 0 || eq == token.length() - 1) {
                issues.add(where, "FLAG_ASSIGNMENT_MALFORMED",
                        "Expected 'flagKey=VALUE'; got '" + token + "'. Never write an empty right-hand side — "
                                + "a flag nothing sets is already empty.");
                continue;
            }
            String key = token.substring(0, eq).trim();
            if (flags.put(key, token.substring(eq + 1).trim()) != null) {
                issues.add(where, "FLAG_ASSIGNED_TWICE", "Flag '" + key + "' is set twice on the same branch");
            }
        }
        return flags;
    }
}
