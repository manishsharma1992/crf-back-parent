package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.validation;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;

import java.util.List;
import java.util.Optional;

/**
 * Outcome of the completeness evaluation. Drives both BR01 (render the Validate
 * button) and the BR02 guard, so the button and the transition cannot disagree.
 *
 * <p>Carries which form blocked, not just why. "3 mandatory fields missing" is not
 * actionable on an analysis that has both an ECB and a FED form open; "3 missing
 * in FED" is.
 *
 * @param blocker          first blocking reason, or {@link CompletenessBlocker#NONE}
 * @param blockingForm     form the blocker was found on; null when complete
 * @param missingFieldKeys populated only for MANDATORY_FIELDS_MISSING, in form
 *                         order so the UI can scroll to the first offender
 */
public record FormCompleteness(CompletenessBlocker blocker,
                               LeverageFormType blockingForm,
                               List<String> missingFieldKeys) {

    public FormCompleteness {
        missingFieldKeys = List.copyOf(missingFieldKeys);
    }

    public static FormCompleteness complete() {
        return new FormCompleteness(CompletenessBlocker.NONE, null, List.of());
    }

    public static FormCompleteness blockedBy(CompletenessBlocker blocker, LeverageFormType form) {
        return new FormCompleteness(blocker, form, List.of());
    }

    public static FormCompleteness notInDraft() {
        return new FormCompleteness(CompletenessBlocker.NOT_IN_DRAFT, null, List.of());
    }

    public static FormCompleteness missingFields(LeverageFormType form, List<String> missingFieldKeys) {
        return new FormCompleteness(CompletenessBlocker.MANDATORY_FIELDS_MISSING, form, missingFieldKeys);
    }

    public boolean canValidate() {
        return blocker == CompletenessBlocker.NONE;
    }

    public Optional<LeverageFormType> blockingFormType() {
        return Optional.ofNullable(blockingForm);
    }
}
