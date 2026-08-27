package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.time.Instant;
import java.util.List;

/**
 * BR03 - the snapshot line rendered for a validated analysis.
 *
 * <p><b>The archive id comes from the frozen responses, not from a join to
 * financials.</b> SpreadsheetSelection already holds the archive the analyst
 * actually chose, so reading it live would answer a different question - what the
 * counterparty's financials are NOW, rather than which statement this conclusion
 * was drawn from. Same reason FormResponses freezes its panels.
 *
 * <p><b>There is no formType field.</b> Which forms apply is read from which
 * FormResponses are present: preliminary always, ECB and FED independently, and an
 * analysis routed to both carries both. A single discriminator could not express
 * that case.
 *
 * @param recommendedOutcome the PRELIMINARY conclusion. ECB and FED have no
 *                           outcome - their entire result is in their flags.
 */
@DomainDrivenDesign.ValueObject
public record AnalysisSnapshotView(String analysisUid,
                                   String archiveId,
                                   String companyName,
                                   String recommendedOutcome,
                                   List<FormSnapshot> forms,
                                   String validatedBy,
                                   Instant validatedTimestamp,
                                   String changedBy,
                                   Instant changedTimestamp,
                                   AnalysisStatus fromStatus,
                                   AnalysisStatus toStatus) {

    public AnalysisSnapshotView {
        forms = forms == null ? List.of() : List.copyOf(forms);
    }
}
