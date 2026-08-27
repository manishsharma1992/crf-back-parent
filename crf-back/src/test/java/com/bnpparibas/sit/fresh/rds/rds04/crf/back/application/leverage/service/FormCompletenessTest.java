package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

class FormCompletenessTest {

    @Test
    void onlyNoneCountsAsValidatable() {
        assertThat(FormCompleteness.complete().canValidate()).isTrue();
        assertThat(FormCompleteness.notInDraft().canValidate()).isFalse();
        assertThat(FormCompleteness
                .blockedBy(CompletenessBlocker.FORM_INCOMPLETE, LeverageFormType.ECB)
                .canValidate()).isFalse();
    }

    @Test
    void exposesTheBlockingFormOnlyWhenThereIsOne() {
        assertThat(FormCompleteness.complete().blockingFormType()).isEmpty();
        assertThat(FormCompleteness.notInDraft().blockingFormType()).isEmpty();
        assertThat(FormCompleteness.blockingErrors(LeverageFormType.FED, List.of("X")).blockingFormType())
                .contains(LeverageFormType.FED);
    }

    /**
     * The codes travel to the client inside a 422 body, so a caller mutating the
     * list it passed in must not be able to change what was reported.
     */
    @Test
    void defensivelyCopiesTheCodes() {
        List<String> codes = new ArrayList<>(List.of("JUSTIFICATION_REQUIRED"));
        FormCompleteness completeness = FormCompleteness.blockingErrors(LeverageFormType.ECB, codes);

        codes.add("ADDED_LATER");

        assertThat(completeness.blockingMessageCodes()).containsExactly("JUSTIFICATION_REQUIRED");
        assertThatThrownBy(() -> completeness.blockingMessageCodes().add("X"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
