package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.*;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses the left-hand side of a branch or value-rule line into a {@link Condition}.
 *
 * <p><b>Grammar</b> (atoms joined by {@code AND}):
 * <pre>
 *   *                                          the catch-all
 *   ANY_YES | ALL_NO                           this CHECKLIST's own items
 *   VALUE                                      this question's own answer, e.g. YES, UMC
 *   QKEY is VALUE                              another question's answer
 *   QKEY in [A, B, C]                          another question's answer, one of
 *   field FKEY range [ ... ]                   a numeric box inside a DATA_ENTRY question
 *   field FKEY &gt; 4 x field FKEY2              a box against a multiple of another box
 *   range [ ... ]                              this question's own numeric answer
 * </pre>
 *
 * <p><b>Ranges</b> are a {@code |}-separated list of terms inside {@code [ ]}. A term is either a
 * single bound ({@code &lt;0}, {@code &gt;6}, {@code &gt;=4}) or a band ({@code 4..6},
 * {@code 0 .. &lt;4}). A bare bound on the left of {@code ..} is inclusive, as is a bare bound on
 * the right — so {@code 4..6} is 4 &le; r &le; 6 and {@code 0 .. &lt;4} is 0 &le; r &lt; 4.
 *
 * <p><b>Never throws.</b> An unparseable atom records an issue and yields null, so the BA sees
 * every bad line at once instead of the first one.
 */
@DomainDrivenDesign.ApplicationService
public final class ConditionExpressionParser {

    static final String DEFAULT_TOKEN = "*";

    /** {@code totalEcbDebt > 4 x field adjustedEbitda} — the leading {@code field } already eaten. */
    private static final Pattern COMPARISON = Pattern.compile(
            "^(\\S+)\\s*(>=|<=|>|<)\\s*(-?\\d+(?:\\.\\d+)?)\\s*[x*\u00D7]\\s*field\\s+(\\S+)$",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern BOUND = Pattern.compile("^(>=|<=|>|<)?\\s*(-?\\d+(?:\\.\\d+)?)$");

    public Condition parse(String text, SourceLocation where, ImportIssues issues) {
        if (text == null || text.isBlank()) {
            issues.add(where, "COND_BLANK", "Condition is blank");
            return null;
        }
        String trimmed = text.trim();
        if (DEFAULT_TOKEN.equals(trimmed)) {
            return Condition.defaultBranch();
        }
        List<String> atoms = Expressions.splitOnKeyword(trimmed, "AND");
        if (atoms.size() > 1) {
            List<Condition> children = new ArrayList<>();
            for (String atom : atoms) {
                Condition child = parseAtom(atom, where, issues);
                if (child != null) children.add(child);
            }
            if (children.isEmpty()) return null;
            return new Condition(false, null, null, null, null, null, null, null, children);
        }
        return parseAtom(trimmed, where, issues);
    }

    // ------------------------------------------------------------------ atoms

    private Condition parseAtom(String atom, SourceLocation where, ImportIssues issues) {
        if (DEFAULT_TOKEN.equals(atom)) {
            issues.add(where, "COND_DEFAULT_IN_AND", "'*' cannot be combined with AND");
            return null;
        }
        for (Aggregate aggregate : Aggregate.values()) {
            if (aggregate.name().equalsIgnoreCase(atom)) {
                return new Condition(false, null, null, null, null, aggregate, null, null, null);
            }
        }
        if (startsWithWord(atom, "field")) {
            return parseFieldAtom(atom.substring("field".length()).trim(), where, issues);
        }
        if (startsWithWord(atom, "range")) {
            List<Range> ranges = parseRanges(atom, where, issues);
            return ranges == null ? null : new Condition(false, null, null, null, null, null, ranges, null, null);
        }
        String[] in = Expressions.splitOnceTopLevel(atom, " in ");
        if (in != null) {
            String content = Expressions.bracketContent(in[1]);
            if (content == null) {
                issues.add(where, "COND_IN_NO_LIST", "'in' must be followed by a bracketed list: " + atom);
                return null;
            }
            List<String> values = Expressions.splitTopLevel(content, ',');
            if (values.isEmpty()) {
                issues.add(where, "COND_IN_EMPTY", "'in' list is empty: " + atom);
                return null;
            }
            return new Condition(false, in[0], null, null, values, null, null, null, null);
        }
        String[] is = Expressions.splitOnceTopLevel(atom, " is ");
        if (is != null) {
            return new Condition(false, is[0], null, is[1], null, null, null, null, null);
        }
        // A bare token is this question's own answer — YES, UMC, JUST_BELOW_CCDG.
        if (atom.contains(" ")) {
            issues.add(where, "COND_UNPARSEABLE",
                    "Cannot read condition '" + atom + "'. Expected a value, 'QKEY is VALUE', "
                            + "'QKEY in [..]', 'field KEY range [..]', or 'field A > n x field B'.");
            return null;
        }
        return new Condition(false, null, null, atom, null, null, null, null, null);
    }

    /** Everything after the {@code field} keyword: a range test or a multiple comparison. */
    private Condition parseFieldAtom(String rest, SourceLocation where, ImportIssues issues) {
        Matcher comparison = COMPARISON.matcher(rest);
        if (comparison.matches()) {
            ComparisonOperator operator = switch (comparison.group(2)) {
                case ">" -> ComparisonOperator.GT;
                case ">=" -> ComparisonOperator.GTE;
                case "<" -> ComparisonOperator.LT;
                default -> ComparisonOperator.LTE;
            };
            Comparison c = new Comparison(comparison.group(1), operator,
                    new BigDecimal(comparison.group(3)), comparison.group(4));
            return new Condition(false, null, null, null, null, null, null, c, null);
        }
        String[] range = Expressions.splitOnceTopLevel(rest, " range ");
        if (range != null) {
            List<Range> ranges = parseRangeContent(Expressions.bracketContent(range[1]), rest, where, issues);
            return ranges == null ? null
                    : new Condition(false, null, range[0], null, null, null, ranges, null, null);
        }
        String[] is = Expressions.splitOnceTopLevel(rest, " is ");
        if (is != null) {
            return new Condition(false, null, is[0], is[1], null, null, null, null, null);
        }
        issues.add(where, "COND_FIELD_UNPARSEABLE",
                "Cannot read field condition 'field " + rest + "'. Expected 'field KEY range [..]', "
                        + "'field KEY is VALUE', or 'field A > n x field B'.");
        return null;
    }

    // ------------------------------------------------------------------ ranges

    private List<Range> parseRanges(String atom, SourceLocation where, ImportIssues issues) {
        return parseRangeContent(Expressions.bracketContent(atom), atom, where, issues);
    }

    private List<Range> parseRangeContent(String content, String original, SourceLocation where, ImportIssues issues) {
        if (content == null || content.isBlank()) {
            issues.add(where, "RANGE_NO_BRACKETS", "Expected a bracketed range in '" + original + "'");
            return null;
        }
        List<Range> ranges = new ArrayList<>();
        for (String term : Expressions.splitTopLevel(content, '|')) {
            Range range = parseRangeTerm(term, where, issues);
            if (range != null) ranges.add(range);
        }
        return ranges.isEmpty() ? null : ranges;
    }

    private Range parseRangeTerm(String term, SourceLocation where, ImportIssues issues) {
        String[] band = Expressions.splitOnceTopLevel(term, "..");
        if (band == null) {
            return singleBound(term.trim(), where, issues);
        }
        Bound low = bound(band[0].trim(), where, issues);
        Bound high = bound(band[1].trim(), where, issues);
        if (low == null || high == null) return null;
        // Bare bounds in a band are inclusive: "4..6" is 4 <= r <= 6.
        BigDecimal gte = low.operator == null || low.operator.equals(">=") ? low.value : null;
        BigDecimal gt = ">".equals(low.operator) ? low.value : null;
        BigDecimal lte = high.operator == null || high.operator.equals("<=") ? high.value : null;
        BigDecimal lt = "<".equals(high.operator) ? high.value : null;
        return new Range(gte, gt, lte, lt);
    }

    private Range singleBound(String term, SourceLocation where, ImportIssues issues) {
        Bound b = bound(term, where, issues);
        if (b == null) return null;
        if (b.operator == null) {
            // A bare number on its own means exactly that value.
            return new Range(b.value, null, b.value, null);
        }
        return switch (b.operator) {
            case ">=" -> new Range(b.value, null, null, null);
            case ">" -> new Range(null, b.value, null, null);
            case "<=" -> new Range(null, null, b.value, null);
            default -> new Range(null, null, null, b.value);
        };
    }

    private Bound bound(String text, SourceLocation where, ImportIssues issues) {
        Matcher m = BOUND.matcher(text.replace(" ", ""));
        if (!m.matches()) {
            issues.add(where, "RANGE_BOUND_UNPARSEABLE",
                    "Cannot read range bound '" + text + "'. Expected a number, optionally prefixed by <, <=, > or >=.");
            return null;
        }
        return new Bound(m.group(1), new BigDecimal(m.group(2)));
    }

    private record Bound(String operator, BigDecimal value) {
    }

    private static boolean startsWithWord(String text, String word) {
        return text.regionMatches(true, 0, word, 0, word.length())
                && text.length() > word.length()
                && Character.isWhitespace(text.charAt(word.length()));
    }
}
