package com.bnpparibas.crf.shared.domain.leverage.model;

import java.util.List;

/**
 * Outcome of the completeness evaluation. Drives both BR01 (render the Validate
 * button) and the BR02 guard, so the button and the transition cannot disagree.
 *
 * @param blocker          first blocking reason, or {@link CompletenessBlocker#NONE}
 * @param missingFieldKeys populated only for
 *                         {@link CompletenessBlocker#MANDATORY_FIELDS_MISSING};
 *                         kept in form order so the UI can scroll to the first
 *                         offender
 */
public record FormCompleteness(CompletenessBlocker blocker, List<String> missingFieldKeys) {

    public FormCompleteness {
        missingFieldKeys = List.copyOf(missingFieldKeys);
    }

    public static FormCompleteness complete() {
        return new FormCompleteness(CompletenessBlocker.NONE, List.of());
    }

    public static FormCompleteness blockedBy(CompletenessBlocker blocker) {
        return new FormCompleteness(blocker, List.of());
    }

    public static FormCompleteness missingFields(List<String> missingFieldKeys) {
        return new FormCompleteness(CompletenessBlocker.MANDATORY_FIELDS_MISSING, missingFieldKeys);
    }

    public boolean canValidate() {
        return blocker == CompletenessBlocker.NONE;
    }
}
