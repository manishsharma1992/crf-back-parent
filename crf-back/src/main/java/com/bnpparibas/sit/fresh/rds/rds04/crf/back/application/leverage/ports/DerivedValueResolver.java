package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports;

import java.util.Map;
import java.util.Set;

/**
 * PORT. Resolves the reference-data values a form declares as {@code Derived From}.
 *
 * <p>Sources are authored as {@code AREA/ATTRIBUTE}. {@code COUNTERPARTY/PARENT} reads the parent
 * counterparty's RMPM id and name; {@code FINANCIALS/x} reads a figure off the analysed row.
 * {@code CALC/x} never arrives here — those are computed in the domain layer from other answers,
 * and a resolver that reads rows would be answering the wrong question.
 *
 * <p><b>An unknown source is omitted, not an error.</b> A BA publishing an area this build has
 * never met should leave the field blank and let the analyst carry on, exactly as a prefill with no
 * source form does. Failing the request would take the whole analysis down over one caption.
 *
 * <p>Values come back ALREADY RENDERED, for the same reason panel snapshots do: a coded value means
 * nothing without the value set that decoded it, and that set can be reworded by a later import.
 */
public interface DerivedValueResolver {

    /**
     * @param sources every {@code Derived From} the walked definition declares, CALC already
     *                filtered out; never null, often empty
     * @param subject the counterparty and financials row under analysis
     * @param locale  the language names and captions are rendered in
     * @return source to value, omitting anything reference data cannot answer
     */
    Map<String, String> resolve(Set<String> sources, AnalysisSubject subject, String locale);

    /** For forms that derive nothing, and for tests that do not care. */
    static DerivedValueResolver none() {
        return (sources, subject, locale) -> Map.of();
    }
}
