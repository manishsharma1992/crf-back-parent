package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.FinancialInputs;

/**
 * Reads the three prefilled figures of the financial table from FINSTAR.
 *
 * <p>A port of its own rather than a case inside {@link DerivedValueResolver}, for one reason:
 * that resolver answers {@code Map<String, String>} keyed by a {@code FORM/KEY} source and is
 * driven by QUESTION-level {@code derivedFrom}. The financial sources hang off FIELDS, they are
 * numbers rather than display strings, and one of them ({@code netDebt}) is never rendered at all.
 * Squeezing them through the string channel would mean parsing back what we just formatted.
 *
 * <p>Resolved ONCE per request and passed to everything that needs it — traversal, validation and
 * the frozen snapshot all judge the same figures. Two reads could disagree if FINSTAR moved
 * between them, and the screen would then contradict the record.
 */
public interface FinancialsResolver {

    /** Absent components mean FINSTAR holds no value; the caller must not read that as zero. */
    FinancialInputs.Sources resolve(AnalysisSubject subject);

    /** For PRELIMINARY and FED, which have no financial table, and for tests that do not care. */
    static FinancialsResolver none() {
        return subject -> new FinancialInputs.Sources(null, null, null);
    }
}
