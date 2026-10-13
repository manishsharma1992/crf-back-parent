package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports;

import java.util.Set;

/**
 * Reads FED's prefilled figures off the analysed FINSTAR row.
 *
 * <p>Same shape as {@code FinancialsResolverImpl}: the port is declared in the application layer,
 * the adapter lives here, and Spring supplies it.
 *
 * <p><b>The column tokens here are the published contract with the workbook.</b> They must match
 * what the Fields tab writes after {@code FINANCIALS/}, character for character. A mismatch does
 * not fail loudly — the figure is simply absent and the box renders empty — which is how General
 * Obligor's Accounting EBITDA went missing. {@link #COLUMNS} is what turns that into a red build:
 * {@code GeneralObligorWalkTest.everyFinstarTokenIsServed} reads every token from the PUBLISHED
 * workbook and requires it to be in that set.
 *
 * <p><b>Who reads what:</b>
 * <ul>
 *   <li>{@code gross_debt} — APLC Total B/S Debt, and General Obligor / Utilities Table 2 Total B/S debt</li>
 *   <li>{@code total_equity} — APLC Book Value of Equity</li>
 *   <li>{@code ebitda} — General Obligor / Utilities Accounting EBITDA, both tables</li>
 *   <li>{@code cash_marketable_securities} — General Obligor / Utilities Table 2 Cash</li>
 * </ul>
 * Every one of them is {@code Editable = Yes}, so a typed figure always wins over the prefill.
 */
@Component
@RequiredArgsConstructor
public class FedFinancialSourcesResolverImpl implements FedFinancialSourcesResolver {

    static final String GROSS_DEBT = "gross_debt";
    static final String TOTAL_EQUITY = "total_equity";
    static final String EBITDA = "ebitda";
    static final String CASH_MARKETABLE_SECURITIES = "cash_marketable_securities";

    /**
     * Every column this adapter serves after {@code FINANCIALS/}. Public so a test can hold the
     * published workbook against it. Add a column here and in {@link #toSources} together.
     */
    public static final Set<String> COLUMNS = Set.of(GROSS_DEBT, TOTAL_EQUITY, EBITDA, CASH_MARKETABLE_SECURITIES);

    private final FedFinancialsDao dao;

    @Override
    public FedFinancialSources resolve(AnalysisSubject subject) {
        if (subject == null || subject.financialsId() == null) {
            return FedFinancialSources.NONE;
        }
        return dao.findFedFiguresById(subject.financialsId())
                .map(FedFinancialSourcesResolverImpl::toSources)
                .orElse(FedFinancialSources.NONE);
    }

    /** Null columns are dropped by {@link FedFinancialSources} itself; nothing here substitutes zero. */
    private static FedFinancialSources toSources(FedFinancialsDao.FedFinancialFigures figures) {
        Map<String, BigDecimal> byColumn = new LinkedHashMap<>();
        byColumn.put(GROSS_DEBT, figures.getGrossDebt());
        byColumn.put(TOTAL_EQUITY, figures.getTotalEquity());
        byColumn.put(EBITDA, figures.getEbitda());
        byColumn.put(CASH_MARKETABLE_SECURITIES, figures.getCashMarketableSecurities());
        return new FedFinancialSources(byColumn);
    }
}