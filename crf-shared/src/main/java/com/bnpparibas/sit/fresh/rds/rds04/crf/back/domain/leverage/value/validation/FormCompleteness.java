package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.validation;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.List;
import java.util.Optional;

/**
 * Outcome of the completeness evaluation. Drives both BR01 (enable the Validate
 * button) and the BR02 guard, so the button and the transition cannot disagree.
 *
 * <p>Carries which form blocked, not just why. "2 errors outstanding" is not
 * actionable on an analysis with both an ECB and a FED form open.
 *
 * @param blocker              first blocking reason, or {@link CompletenessBlocker#NONE}
 * @param blockingForm         form the blocker sits on; null when complete or NOT_IN_DRAFT
 * @param blockingMessageCodes ERROR codes to surface, empty unless BLOCKING_ERRORS
 */
@DomainDrivenDesign.ValueObject
public record FormCompleteness(CompletenessBlocker blocker,
                               LeverageFormType blockingForm,
                               List<String> blockingMessageCodes) {

    public FormCompleteness {
        blockingMessageCodes = List.copyOf(blockingMessageCodes);
    }

    public static FormCompleteness complete() {
        return new FormCompleteness(CompletenessBlocker.NONE, null, List.of());
    }

    public static FormCompleteness notInDraft() {
        return new FormCompleteness(CompletenessBlocker.NOT_IN_DRAFT, null, List.of());
    }

    public static FormCompleteness blockedBy(CompletenessBlocker blocker, LeverageFormType form) {
        return new FormCompleteness(blocker, form, List.of());
    }

    public static FormCompleteness blockingErrors(LeverageFormType form, List<String> codes) {
        return new FormCompleteness(CompletenessBlocker.BLOCKING_ERRORS, form, codes);
    }

    public boolean canValidate() {
        return blocker == CompletenessBlocker.NONE;
    }

    public Optional<LeverageFormType> blockingFormType() {
        return Optional.ofNullable(blockingForm);
    }
}
