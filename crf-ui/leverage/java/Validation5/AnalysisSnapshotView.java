package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.time.Instant;
import java.util.List;

/**
 * BR03 - the snapshot line rendered for a validated analysis.
 *
 * <p>There is no single formType. Which forms apply is derived from the three
 * definition-id columns on leverage_analysis: preliminary is always present, and
 * ECB and FED are present independently, so an analysis may legitimately carry
 * both. {@link #forms()} holds one entry per applicable form, in display order
 * (PRELIMINARY, ECB, FED).
 *
 * @param analysisUid        business identifier
 * @param financialArchiveId FINSTAR archive id, joined from financials
 * @param recommendedOutcome preliminary outcome that routed to ECB / FED / both
 * @param forms              one entry per applicable form
 * @param validatedBy        from leverage_analysis
 * @param validatedTimestamp from leverage_analysis
 * @param changedBy          from leverage_analysis_history
 * @param changedTimestamp   from leverage_analysis_history
 * @param fromStatus         from leverage_analysis_history
 * @param toStatus           from leverage_analysis_history
 */
@DomainDrivenDesign.ValueObject
public record AnalysisSnapshotView(String analysisUid,
                                   String financialArchiveId,
                                   String recommendedOutcome,
                                   List<FormSnapshot> forms,
                                   String validatedBy,
                                   Instant validatedTimestamp,
                                   String changedBy,
                                   Instant changedTimestamp,
                                   AnalysisStatus fromStatus,
                                   AnalysisStatus toStatus) {

    public AnalysisSnapshotView {
        forms = List.copyOf(forms);
    }

    public boolean appliesTo(LeverageFormType formType) {
        return forms.stream().anyMatch(form -> form.formType() == formType);
    }
}
