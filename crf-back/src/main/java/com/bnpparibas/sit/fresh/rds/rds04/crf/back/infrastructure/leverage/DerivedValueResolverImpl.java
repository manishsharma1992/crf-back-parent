package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Reads the reference data behind each authored {@code Derived From}.
 *
 * <p>Dispatch is a map from AREA to a handler rather than a sealed hierarchy. Sealing would buy
 * compile-time exhaustiveness, but it fights the behaviour the form actually needs: an area this
 * build has never met must leave a blank field, not fail to compile or fail the request. Where
 * exhaustiveness IS wanted, the place to get it is import validation — reject a workbook naming an
 * unknown source, and every published definition is then guaranteed resolvable.
 *
 * <p>Adding a source is a line in {@link #areas} and a method beside {@link #counterparty}.
 */
@Component
@RequiredArgsConstructor
public class DerivedValueResolverImpl implements DerivedValueResolver {

    private static final String SEPARATOR = "/";

    private final CounterpartyDerivationDao counterpartyDao;
    private final FinancialsDerivationDao financialsDao;

    private Map<String, DerivedArea> areas;

    @PostConstruct
    void registerAreas() {
        // Built after injection rather than inline, so the handlers can be method references on
        // instances Spring has already supplied.
        areas = Map.of(
                "COUNTERPARTY", this::counterparty,
                "FINANCIALS", this::financials);
    }

    @Override
    public Map<String, String> resolve(Set<String> sources, AnalysisSubject subject, String locale) {
        if (sources == null || sources.isEmpty() || subject == null) {
            return Map.of();
        }
        Map<String, String> resolved = new LinkedHashMap<>();
        for (String source : sources) {
            resolveOne(source, subject, locale).ifPresent(value -> resolved.put(source, value));
        }
        return Map.copyOf(resolved);
    }

    private Optional<String> resolveOne(String source, AnalysisSubject subject, String locale) {
        if (source == null || !source.contains(SEPARATOR)) {
            return Optional.empty();
        }
        String[] parts = source.split(SEPARATOR, 2);
        DerivedArea area = areas.get(parts[0].trim().toUpperCase());

        // Unknown area: the field stays blank and the analyst carries on. See the class note.
        return area == null ? Optional.empty() : area.resolve(parts[1].trim(), subject, locale);
    }

    // ------------------------------------------------------------------ COUNTERPARTY

    /**
     * {@code COUNTERPARTY/PARENT} renders as "12345678 - ACME HOLDING SA".
     *
     * <p>Both halves in one string because the business rule asks for both — "the RMPM ID and the
     * name of the parent counterparty" — and because the snapshot stores what was on screen. A
     * separate id and name would have to be recombined at read time, by which point the name may
     * have changed.
     */
    private Optional<String> counterparty(String attribute, AnalysisSubject subject, String locale) {
        if (!"PARENT".equals(attribute) || subject.rmpmid() == null) {
            return Optional.empty();
        }
        return counterpartyDao.findParentOf(subject.rmpmid())
                .map(parent -> parent.getRmpmid() + " - " + parent.getCompanyName());
    }

    // ------------------------------------------------------------------ FINANCIALS

    /**
     * {@code FINANCIALS/x} reads a figure off the analysed row.
     *
     * <p>Nothing on the ECB or FED tabs declares one yet — the instructions describe the area but
     * the sheet does not use it. Wired now so the financial table can author one without touching
     * the traversal, and returning empty until then.
     */
    private Optional<String> financials(String attribute, AnalysisSubject subject, String locale) {
        if (subject.financialsId() == null) {
            return Optional.empty();
        }
        return financialsDao.findAttribute(subject.financialsId(), attribute);
    }

    @FunctionalInterface
    private interface DerivedArea {
        Optional<String> resolve(String attribute, AnalysisSubject subject, String locale);
    }
}
