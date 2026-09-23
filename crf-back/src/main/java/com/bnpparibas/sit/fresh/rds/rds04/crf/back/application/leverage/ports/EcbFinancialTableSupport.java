package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports;

/**
 * The ECB financial table: the wrapped ECB arithmetic, plus FINSTAR read through the ECB port.
 *
 * <p><b>The three token spellings below are the contract with the workbook</b> and are ECB's own —
 * camelCase, matching {@code FINANCIALS/ebitda}, {@code FINANCIALS/grossDebt},
 * {@code FINANCIALS/netDebt} on the Fields tab. FED spells its columns snake_case. Neither form
 * has to know the other's convention, which is the point of one support per form.
 *
 * <p>A mismatch here does not fail loudly — the figure is simply absent and the box renders empty
 * — so these strings deserve a test of their own.
 */
@Component
@RequiredArgsConstructor
public class EcbFinancialTableSupport implements FinancialTableSupport {

    private static final String PREFIX = "FINANCIALS/";

    private final FinancialsResolver sources;
    private final EcbFinancialCalculator calculator;

    @Override
    public FinancialTableCalculator calculator() {
        return calculator;
    }

    @Override
    public Function<String, BigDecimal> prefills(AnalysisSubject subject) {
        FinancialInputs.Sources finstar = sources.resolve(subject);
        return derivedFrom -> {
            if (derivedFrom == null || !derivedFrom.startsWith(PREFIX)) {
                return null;
            }
            return switch (derivedFrom.substring(PREFIX.length())) {
                case FinancialInputs.EBITDA -> finstar.ebitda();
                case FinancialInputs.GROSS_DEBT -> finstar.grossDebt();
                case FinancialInputs.NET_DEBT -> finstar.netDebt();
                default -> null;
            };
        };
    }
}

