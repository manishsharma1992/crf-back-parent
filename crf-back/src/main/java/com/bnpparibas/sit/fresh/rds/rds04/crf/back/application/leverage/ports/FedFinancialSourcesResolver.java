package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports;

/**
 * Reads the FINSTAR figures the FED financial tables prefill from.
 *
 * <p><b>A sibling of {@code FinancialsResolver}, not an extension of it.</b> That port returns
 * ECB's {@code FinancialInputs.Sources} and its javadoc describes ECB behaviour; widening it for
 * FED would make every ECB caller carry a figure it never reads, and put the two forms one
 * signature change away from each other.
 *
 * <p>Same contract otherwise, because it is a good one: resolved ONCE per request and passed to
 * everything that needs it, so traversal, validation and the frozen snapshot all judge figures
 * that coexisted. Two reads could disagree if FINSTAR moved between them.
 */
public interface FedFinancialSourcesResolver {

    /** Never null. Absent entries mean FINSTAR holds no value; the caller must not read that as zero. */
    FedFinancialSources resolve(AnalysisSubject subject);

    /** For tests that do not care, and for any context with no analysed financials. */
    static FedFinancialSourcesResolver none() {
        return subject -> FedFinancialSources.NONE;
    }
}
