package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.response;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;
import lombok.Builder;

/**
 * Every form's frozen answers for one analysis.
 *
 * <p>Unchanged in shape — {@code ecbForm} and {@code fedForm} were already here — but the two
 * missing {@code with…} methods are added so the ECB and FED use cases can record their sections
 * the way {@link #withPreliminary} already does.
 */
@DomainDrivenDesign.ValueObject
@Builder
public record LeverageResponses(SpreadsheetSelection spreadsheetSelection,
                                FormResponses preliminary,
                                FormResponses ecbForm,
                                FormResponses fedForm) {

    public static LeverageResponses initial(SpreadsheetSelection spreadsheetSelection) {
        return new LeverageResponses(spreadsheetSelection, null, null, null);
    }

    public LeverageResponses withPreliminary(FormResponses preliminary) {
        return new LeverageResponses(spreadsheetSelection, preliminary, ecbForm, fedForm);
    }

    public LeverageResponses withEcbForm(FormResponses ecbForm) {
        return new LeverageResponses(spreadsheetSelection, preliminary, ecbForm, fedForm);
    }

    public LeverageResponses withFedForm(FormResponses fedForm) {
        return new LeverageResponses(spreadsheetSelection, preliminary, ecbForm, fedForm);
    }
}
