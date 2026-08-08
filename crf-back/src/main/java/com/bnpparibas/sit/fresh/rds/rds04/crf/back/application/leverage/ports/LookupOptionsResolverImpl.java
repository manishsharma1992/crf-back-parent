package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Searches the reference data behind each authored {@code LOOKUP/AREA}.
 *
 * <p>Dispatch is a map from AREA to a handler, for the same reason {@code DerivedValueResolverImpl}
 * uses one: an area this build has never met must leave an empty dropdown, not fail to compile or
 * fail the request. Where exhaustiveness is wanted, import validation is the place to get it —
 * reject a workbook naming an unknown source and every published definition is resolvable.
 *
 * <p>Adding a source is a line in {@link #areas} and a method beside {@link #counterparty}.
 */
@Component
@RequiredArgsConstructor
public class LookupOptionsResolverImpl implements LookupOptionsResolver {

    private static final String PREFIX = "LOOKUP/";

    /** An autocomplete is scanned, not read — beyond a handful the analyst stops looking. */
    private static final int MAX_RESULTS = 5;

    private final CounterpartyLookupDao counterpartyDao;

    private Map<String, LookupArea> areas;

    @PostConstruct
    void registerAreas() {
        areas = Map.of("COUNTERPARTY", this::counterparty);
    }

    @Override
    public List<LookupOption> resolve(String source, AnalysisSubject subject,
                                      String query, String locale) {
        if (source == null || !source.startsWith(PREFIX) || subject == null) {
            return List.of();
        }
        LookupArea area = areas.get(source.substring(PREFIX.length()).trim().toUpperCase());

        // Unknown area: an empty dropdown, and the analyst carries on. See the class note.
        return area == null ? List.of() : area.search(subject, query, locale);
    }

    /**
     * {@code LOOKUP/COUNTERPARTY} — a trigram search over the counterparty's fuzzy text.
     *
     * <p>The database has already ordered by relevance, so nothing is re-sorted here; doing so
     * would discard the score it computed.
     */
    private List<LookupOption> counterparty(AnalysisSubject subject, String query, String locale) {
        return counterpartyDao.search(query, MAX_RESULTS).stream()
                .map(row -> new LookupOption(row.getValue(), label(row)))
                .toList();
    }

    /** A row with no company name still has an id worth offering — showing it beats showing blank. */
    private String label(CounterpartyLookupDao.LookupRow row) {
        return row.getLabel() == null || row.getLabel().isBlank()
                ? row.getValue()
                : row.getValue() + " - " + row.getLabel();
    }

    @FunctionalInterface
    private interface LookupArea {
        List<LookupOption> search(AnalysisSubject subject, String query, String locale);
    }
}
