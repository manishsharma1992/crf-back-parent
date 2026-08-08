package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LookupOption;

import java.util.List;

/**
 * PORT. Searches the reference data behind a LOOKUP question.
 *
 * <p>Deliberately narrow, like {@link InfoPanelResolver} and {@link DerivedValueResolver}: WHICH
 * source a question uses is decided by the definition, and all that is left here is the search —
 * the only part that needs a database.
 *
 * <p>Sources are authored as {@code LOOKUP/AREA}, and an area this build has never met returns an
 * empty list rather than raising. A definition published against a newer authoring template should
 * leave a dropdown empty, not take the form down.
 *
 * <p>Results come back ALREADY RENDERED and ALREADY ORDERED — the ordering is a relevance score the
 * database computed, and re-sorting it anywhere else would throw that away.
 */
public interface LookupOptionsResolver {

    /**
     * @param source  the authored source, e.g. {@code LOOKUP/COUNTERPARTY}
     * @param subject the counterparty and financials row under analysis, which scopes the search
     * @param query   what the analyst has typed; never null by the time it reaches here
     * @param locale  the language the labels are rendered in
     */
    List<LookupOption> resolve(String source, AnalysisSubject subject, String query, String locale);

    /** For forms that look nothing up, and for tests that do not care. */
    static LookupOptionsResolver none() {
        return (source, subject, query, locale) -> List.of();
    }
}
