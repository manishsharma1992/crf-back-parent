package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports;

/**
 * One form's financial table, as the resolver sees it: the arithmetic, plus where its prefilled
 * figures come from.
 *
 * <p><b>Why the two travel together.</b> ECB reads FINSTAR through {@code FinancialsResolver} and
 * addresses the columns as {@code FINANCIALS/ebitda}, {@code FINANCIALS/grossDebt}; FED reads it
 * through {@code FedFinancialSourcesResolver} and addresses them as
 * {@code FINANCIALS/gross_debt}, {@code FINANCIALS/total_equity}. Different ports, different token
 * spellings, and nothing sensible to gain from merging them. Registering one object per form keeps
 * a calculator and its source of figures from drifting apart — the resolver never has to guess
 * which port goes with which arithmetic.
 *
 * <p>Implementations live in the application layer because they navigate reference data. The
 * arithmetic they carry stays pure.
 */
public interface FinancialTableSupport {

    /** The arithmetic. Also what decides which questions this support claims. */
    FinancialTableCalculator calculator();

    /**
     * The prefilled figures for this request, addressed by a field's {@code Derived From}.
     *
     * <p>Resolved ONCE per request and reused for every box, so traversal, validation and the
     * frozen snapshot all judge figures that coexisted. Two reads could disagree if FINSTAR moved
     * between them, and the screen would then contradict the record.
     *
     * @return a function returning null for a source this form does not read and for a column
     *         FINSTAR holds no value in. Null is ABSENT and must never be read as zero.
     */
    Function<String, BigDecimal> prefills(AnalysisSubject subject);
}

