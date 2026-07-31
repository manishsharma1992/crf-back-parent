package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.List;

/**
 * The {@code when} of a {@link Branch} or a {@link ValueRule}. Branches are tried in the order
 * they were authored and the FIRST match wins, so a condition never has to exclude the ones
 * below it.
 *
 * <p>Exactly one shape is used per instance:
 * <ul>
 *   <li>{@code isDefault} — the {@code *} catch-all; carries no predicate.</li>
 *   <li>{@code allOf} — every child must hold ({@code AND} in the sheet).</li>
 *   <li>a leaf predicate: {@code equals}, {@code in}, {@code aggregate}, {@code ranges}
 *       or {@code comparison}.</li>
 * </ul>
 *
 * <p><b>What the predicate is applied to</b> is chosen by the two reference fields:
 * <ul>
 *   <li>both null — the owning question's own answer, e.g. {@code YES -> Q-C02};</li>
 *   <li>{@code questionKey} — another question's answer, e.g. {@code Q01 is YES};</li>
 *   <li>{@code fieldKey} — a box inside a DATA_ENTRY question, e.g.
 *       {@code field ecbLeverageRatio range [<0 | >6]}. Field keys are unique within a form,
 *       so the question does not have to be named.</li>
 * </ul>
 *
 * <p>A question the analyst never reached has NO answer and matches NOTHING. That is what lets
 * Q-S04 list a rule per inbound path and keep the unused ones quiet.
 */
@DomainDrivenDesign.ValueObject
public record Condition(
        boolean isDefault,
        String questionKey,
        String fieldKey,
        String equals,
        List<String> in,
        Aggregate aggregate,
        List<Range> ranges,
        Comparison comparison,
        List<Condition> allOf) {

    public Condition {
        in = in == null ? null : List.copyOf(in);
        ranges = ranges == null ? null : List.copyOf(ranges);
        allOf = allOf == null ? null : List.copyOf(allOf);
    }

    public boolean isComposite() {
        return allOf != null && !allOf.isEmpty();
    }

    /** True when this leaf carries something to evaluate. */
    public boolean hasPredicate() {
        return (equals != null && !equals.isBlank())
                || (in != null && !in.isEmpty())
                || (ranges != null && !ranges.isEmpty())
                || comparison != null
                || aggregate != null;
    }

    public static Condition defaultBranch() {
        return new Condition(true, null, null, null, null, null, null, null, null);
    }
}
