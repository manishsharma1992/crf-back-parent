package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.CompletenessBlocker;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.FormCompleteness;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import org.junit.jupiter.api.Test;

class ValidationAvailabilityTest {

    @Test
    void carriesTheReasonAlongsideTheFlag() {
        ValidationAvailability availability = ValidationAvailability.from(
                FormCompleteness.blockingErrors(LeverageFormType.FED, List.of("MUST_BE_POSITIVE")));

        assertThat(availability.canValidate()).isFalse();
        assertThat(availability.blocker()).isEqualTo(CompletenessBlocker.BLOCKING_ERRORS);
        assertThat(availability.blockingForm()).isEqualTo(LeverageFormType.FED);
        assertThat(availability.blockingMessageCodes()).containsExactly("MUST_BE_POSITIVE");
    }

    @Test
    void reportsNoBlockingFormWhenComplete() {
        ValidationAvailability availability = ValidationAvailability.from(FormCompleteness.complete());

        assertThat(availability.canValidate()).isTrue();
        assertThat(availability.blockingForm()).isNull();
        assertThat(availability.blockingMessageCodes()).isEmpty();
    }
}
