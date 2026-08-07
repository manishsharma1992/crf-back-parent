package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports;

/**
 * Who an analysis is about, in the terms reference data is addressed by.
 *
 * <p>Exists so the application layer stops navigating reference data on the resolvers' behalf.
 * Answering {@code COUNTERPARTY/PARENT} takes a self-join and answering a FINANCIALS attribute
 * takes a different one entirely; how many hops each needs is the adapter's business, and a use
 * case that pre-fetched an id would be guessing which one they wanted.
 *
 * @param rmpmid       the counterparty under analysis, as reference data addresses it
 * @param financialsId the row the analysis hangs off, for derivations that read the figures
 */
public record AnalysisSubject(String rmpmid, Long financialsId) {

    public static AnalysisSubject of(LeverageAnalysis analysis) {
        Financials financials = analysis.getFinancials();
        return new AnalysisSubject(
                financials.getCounterparty().getRmpmid(),   // TODO confirm accessor
                financials.getId());
    }
}
