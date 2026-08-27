package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto;

import java.util.Collections;
import java.util.List;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.CompletenessBlocker;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.FormCompleteness;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;

/**
 * BR01 payload fragment - the server's answer to "should the Validate button be
 * enabled, and if not, why".
 *
 * <p>Analysis-level, not form-level. FormState.status already says whether THIS
 * form is finished; this says whether the ANALYSIS is, which is a different
 * question the moment a preliminary outcome asks for both ECB and FED. The
 * button is enabled from this, never from status == COMPLETED.
 *
 * <p>The reason travels with the flag deliberately. A greyed-out button with no
 * explanation is what generates support tickets; "FED still incomplete" is what
 * the analyst can act on.
 */
public record ValidationAvailability(boolean canValidate,
                                     CompletenessBlocker blocker,
                                     LeverageFormType blockingForm,
                                     List<String> blockingMessageCodes) {

    public ValidationAvailability {
        blockingMessageCodes = blockingMessageCodes == null
                ? List.of()
                : Collections.unmodifiableList(List.copyOf(blockingMessageCodes));
    }

    public static ValidationAvailability from(FormCompleteness completeness) {
        return new ValidationAvailability(
                completeness.canValidate(),
                completeness.blocker(),
                completeness.blockingForm(),
                completeness.blockingMessageCodes());
    }
}
