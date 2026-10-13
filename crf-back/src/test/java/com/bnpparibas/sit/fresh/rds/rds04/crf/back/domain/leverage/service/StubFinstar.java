package com.bnpparibas.sit.fresh.rds.rds04.crf.back.leverage;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.AnalysisSubject;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.fed.FedFinancialSources;

import java.math.BigDecimal;
import java.util.Map;

/**
 * The reference data a FED walk reads, as a table of facts.
 *
 * <p>Stubs rather than mocks on purpose. A mock would record that
 * {@code findFedFiguresById(42L)} was called; what a reader of these tests needs to know is that
 * Total B/S Debt is 1000 and Book Value of Equity is 300, because those numbers are what make the
 * expected ratios checkable by hand.
 *
 * <p><b>The column tokens here are the contract with the workbook</b>, and must match
 * {@code FedFinancialSourcesResolverImpl.COLUMNS}. A mismatch does not throw — the figure is simply
 * absent and the box renders empty.
 */
final class StubFinstar {

    /** Arbitrary, but it must be non-null or the resolvers short-circuit before reading anything. */
    private static final Long FINANCIALS_ID = 42L;

    /**
     * FED prefills.
     *
     * <ul>
     *   <li>APLC: applicable funded debt 1000 and adjusted equity 300 before the analyst touches
     *       anything, so 1000/300 is the baseline every APLC case varies from.</li>
     *   <li>General Obligor / Utilities: EBITDA 200 and cash 100, and gross_debt 1000 serves
     *       Table 2's Total B/S debt. None of these equals what the GO walk TYPES (EBITDA 100,
     *       B/S debt 300, cash 0), so a test can always tell a prefilled figure from a typed one —
     *       and because those boxes are editable, the typed figures win in every other scenario.</li>
     * </ul>
     */
    static final FedFinancialSources FED_SOURCES = new FedFinancialSources(Map.of(
            "gross_debt", new BigDecimal("1000"),
            "total_equity", new BigDecimal("300"),
            "ebitda", new BigDecimal("200"),
            "cash_marketable_securities", new BigDecimal("100")));

    /** A counterparty FINSTAR holds nothing for — every prefilled box comes back empty. */
    static final FedFinancialSources NO_ROW = FedFinancialSources.NONE;

    private StubFinstar() {
    }

    static AnalysisSubject subject(String rmpmid) {
        return new AnalysisSubject(rmpmid, FINANCIALS_ID);
    }

    /**
     * A subject with no financials row at all.
     *
     * <p>Worth having: {@code FedFinancialSourcesResolverImpl} short-circuits on a null
     * {@code financialsId} without touching the database, and an analysis in that state is
     * ordinary rather than broken — the analyst types every figure themselves.
     */
    static AnalysisSubject subjectWithoutFinancials(String rmpmid) {
        return new AnalysisSubject(rmpmid, null);
    }
}
