package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.response;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LocalizedLabel;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

/**
 * One part of a multi-part answer: a checklist item, or a box in the financial table.
 *
 * <p><b>This is the gap the preliminary form never exposed.</b> Every preliminary question has a
 * single value, so {@link Answer} needed only {@code value}. The ECB form is mostly multi-part —
 * Q-T01 alone has ten items, Q-F01 seventeen boxes — and none of it has anywhere to live today.
 *
 * <p>One shape serves both, rather than two near-identical records, because the snapshot only ever
 * reads them back for display and audit: a key, what it was called, what it held, and where the
 * value came from. {@code justification} is simply null for a checklist item.
 *
 * @param subKey     item or field key, e.g. {@code sovereign}, {@code adjustedEbitda}
 * @param label      frozen text as the analyst saw it
 * @param value      canonical value: {@code YES} / {@code NO} / {@code NOT_APPLICABLE} for an
 *                   item, the figure as entered for a box
 * @param valueLabel frozen display of that value; null for a figure, which needs no translation
 */
@DomainDrivenDesign.ValueObject
public record SubAnswer(String subKey,
                        LocalizedLabel label,
                        String value,
                        LocalizedLabel valueLabel,
                        AnswerProvenance provenance,
                        Justification justification) {

    public static SubAnswer item(String subKey, LocalizedLabel label, String value,
                                 LocalizedLabel valueLabel, AnswerProvenance provenance) {
        return new SubAnswer(subKey, label, value, valueLabel, provenance, null);
    }

    public static SubAnswer field(String subKey, LocalizedLabel label, String value,
                                  AnswerProvenance provenance, Justification justification) {
        return new SubAnswer(subKey, label, value, null, provenance, justification);
    }
}
