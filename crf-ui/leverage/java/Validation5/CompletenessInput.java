package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.List;

/**
 * Facts for every form the analysis is required to complete, in display order.
 *
 * <p>PRELIMINARY first, then whichever of ECB and FED its outcome asked for. BR01
 * gates one analysis-level Validate button, not one per form, so an analysis
 * routed to both is validatable only when both are done.
 */
@DomainDrivenDesign.ValueObject
public record CompletenessInput(List<FormCompletenessInput> forms) {

    public CompletenessInput {
        forms = List.copyOf(forms);
    }
}
