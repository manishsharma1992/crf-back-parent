package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisStatus;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.CompletenessBlocker;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.FormCompleteness;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageAnalysis;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;

/**
 * Analysis-level validation state, replacing the narrower ValidationAvailability.
 *
 * <p>Widened because two places need the same answer and were about to derive it
 * two different ways: the validation panel needs canValidate, and the snapshot
 * header needs the status. Both are properties of the ANALYSIS, so one read
 * serves both and they cannot drift.
 *
 * <p><b>This is what the snapshot header should bind to, not FormState.status.</b>
 * FormState.status is IN_PROGRESS / COMPLETED for ONE form. Reading
 * {@code ecbFormState()?.status || fedFormState()?.status} answers "is the ECB
 * form finished, or failing that the FED one" - which is neither form's status
 * nor the analysis's, and on an analysis routed to both it reports whichever
 * happens to be non-null first.
 *
 * @param status              DRAFT or VALIDATED - the headline
 * @param validatedBy         null while DRAFT
 * @param validatedTimestamp  null while DRAFT
 * @param canValidate         drives the Validate button
 * @param blocker             why not; NONE when canValidate is true
 * @param blockingForm        which form the blocker sits on; null when complete
 * @param blockingMessageCodes ERROR keys, for highlighting rather than display
 */
public record AnalysisValidationState(AnalysisStatus status,
                                      String validatedBy,
                                      Instant validatedTimestamp,
                                      boolean canValidate,
                                      CompletenessBlocker blocker,
                                      LeverageFormType blockingForm,
                                      List<String> blockingMessageCodes) {

    public AnalysisValidationState {
        blockingMessageCodes = blockingMessageCodes == null
                ? List.of()
                : Collections.unmodifiableList(List.copyOf(blockingMessageCodes));
    }

    public static AnalysisValidationState of(LeverageAnalysis analysis, FormCompleteness completeness) {
        return new AnalysisValidationState(
                analysis.getStatus(),
                analysis.getValidatedBy(),
                analysis.getValidatedTimestamp(),
                completeness.canValidate(),
                completeness.blocker(),
                completeness.blockingForm(),
                completeness.blockingMessageCodes());
    }
}
