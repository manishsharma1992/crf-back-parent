package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Turns a frozen snapshot back into the flat answer map the engine reads.
 *
 * <p>The snapshot is a RECORD — self-describing, with labels, provenance and audit trail attached
 * to each value. The answer map is a WORKING SET: bare keys and strings, the same shape the client
 * posts. Replaying is the conversion between them, and its rule is that only what the ANALYST
 * supplied comes back. Anything the system worked out is recomputed from scratch against today's
 * reference data, because a stored derivation is a statement about the moment it was made.
 */
public final class SnapshotAnswers {

    private SnapshotAnswers() {
    }

    public static Map<String, String> flattenForReplay(FormResponses responses) {

        Map<String, String> flat = new LinkedHashMap<>();
        if (responses == null || responses.answers() == null) {
            return Collections.unmodifiableMap(flat);
        }

        for (Answer answer : responses.answers()) {
            if (isReDerived(answer.provenance())) {
                continue;
            }
            if (answer.isMultiPart()) {
                expandParts(flat, answer);
            } else if (isPresent(answer.value())) {
                flat.put(answer.questionKey(), answer.value());
            }
        }
        return Collections.unmodifiableMap(flat);
    }

    public static Map<String, String> flattenForCrossForm(LeverageFormType form, FormResponses responses) {
        Map<String, String> flat = new LinkedHashMap<>();
        if (responses == null || responses.answers() == null) {
            return Collections.unmodifiableMap(flat);
        }

        for (Answer answer : responses.answers()) {
            if (!answer.isMultiPart() && isPresent(answer.value())) {
                flat.put(form.name() + '/' + answer.questionKey(), answer.value());
            }
        }
        return Collections.unmodifiableMap(flat);
    }

    /**
     * Checklist items and data-entry boxes, back to their dotted keys.
     *
     * <p><b>Provenance is checked per PART, not only per answer.</b> A DATA_ENTRY answer as a whole
     * was typed, but most of its boxes were not: EBITDA and Gross Debt come from FINSTAR, and the
     * two ratios and three totals are computed. Replaying those would put yesterday's figures into
     * today's working set — and the financial overlay only ADDS keys, never removes them, so on a
     * blocked analysis (where the calculated figures are deliberately withheld) the stale ratio
     * would survive and the walk would route on it. The screen would show a leverage ratio that no
     * longer follows from the numbers beside it.
     *
     * <p>SYSTEM_ASSIGNED is skipped for the older reason: {@code NOT_APPLICABLE} was written by the
     * engine when a YES settled a checklist, and posting it back would make a system-assigned value
     * indistinguishable from an analyst's answer.
     */
    private static void expandParts(Map<String, String> flat, Answer answer) {
        String prefix = answer.questionKey() + '.';
        for (SubAnswer part : answer.subAnswers()) {
            if (part.provenance() == AnswerProvenance.SYSTEM_ASSIGNED || isReDerived(part.provenance())) {
                continue;
            }
            if (isPresent(part.value())) {
                flat.put(prefix + part.subkey(), part.value());
            }
            expandJustification(flat, prefix + part.subkey(), part.justification());
        }
    }

    /**
     * The audit trail for an adjustment, back to the two keys the rules read.
     *
     * <p>The snapshot NESTS a justification inside the box it explains, which is right — it is the
     * reason a particular figure moved the ECB leverage ratio, not a fact standing on its own. The
     * answer map is flat, and {@code JUSTIFICATION_REQUIRED} looks for
     * {@code Q-F01.ebitda.wording} and {@code .comment}.
     *
     * <p>Without this the loss is invisible until a reload: the figure replays, its explanation
     * does not, and the rule fires on a box that was justified yesterday — telling the analyst to
     * explain an adjustment they already explained, with no way to see what they had written.
     *
     * <p>Written even when the value itself is absent. The two move together everywhere else, so
     * if a snapshot ever holds one without the other, the rules should see that rather than have it
     * quietly tidied away here.
     */
    private static void expandJustification(Map<String, String> flat, String prefix,
                                            Justification justification) {
        if (justification == null) {
            return;
        }
        if (isPresent(justification.wording())) {
            flat.put(prefix + ".wording", justification.wording());
        }
        if (isPresent(justification.comment())) {
            flat.put(prefix + ".comment", justification.comment());
        }
    }

    private static boolean isReDerived(AnswerProvenance provenance) {
        return provenance == AnswerProvenance.COMPUTED
                || provenance == AnswerProvenance.PREFILLED
                || provenance == AnswerProvenance.CALCULATED;
    }

    /** Blank counts as absent: a control cleared to whitespace is not an answer. */
    private static boolean isPresent(String value) {
        return value != null && !value.isBlank();
    }
}