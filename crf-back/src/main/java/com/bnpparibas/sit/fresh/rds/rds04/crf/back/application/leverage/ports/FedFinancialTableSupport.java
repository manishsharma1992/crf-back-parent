package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports;

/**
 * One FED financial table: its arithmetic, plus FINSTAR read through the FED port.
 *
 * <p>FED declares TWO tables — APLC and REITs — so there are two of these, one per calculator.
 * They share the source resolver because they read the same FINSTAR row; they share nothing else.
 *
 * <p>Token resolution is delegated to {@link FedFinancialSources#of(String)}, so adding a prefill
 * is a column in the FED query, not a case in a switch here.
 *
 * <p>Registered as two beans rather than as one support that claims both tables: a support claims
 * questions through its calculator, and a calculator that claimed both would have to decide which
 * arithmetic to run — which is the traversal's business, not the resolver's.
 */
@Component
@RequiredArgsConstructor
public class FedFinancialTableSupport implements FinancialTableSupport {

    private final FedFinancialSourcesResolver sources;
    private final FinancialTableCalculator calculator;

    @Override
    public FinancialTableCalculator calculator() {
        return calculator;
    }

    @Override
    public Function<String, BigDecimal> prefills(AnalysisSubject subject) {
        FedFinancialSources finstar = sources.resolve(subject);
        return derivedFrom -> finstar.of(derivedFrom).orElse(null);
    }
}

