package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports;

/**
 * Reads FED's prefilled figures off the analysed FINSTAR row.
 *
 * <p>Same shape as {@code FinancialsResolverImpl}: the port is declared in the application layer,
 * the adapter lives here, and Spring supplies it.
 *
 * <p><b>The column tokens here are the published contract with the workbook.</b> They must match
 * what the Fields tab writes after {@code FINANCIALS/}, character for character. A mismatch does
 * not fail loudly — the figure is simply absent and the box renders empty — so the
 * accompanying test pins both tokens.
 */
@Component
@RequiredArgsConstructor
public class FedFinancialSourcesResolverImpl implements FedFinancialSourcesResolver {

    static final String GROSS_DEBT = "gross_debt";
    static final String TOTAL_EQUITY = "total_equity";

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
        return new FedFinancialSources(byColumn);
    }
}

