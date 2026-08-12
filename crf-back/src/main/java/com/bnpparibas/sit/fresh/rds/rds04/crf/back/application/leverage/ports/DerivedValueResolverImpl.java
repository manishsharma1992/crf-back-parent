package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports;

import jakarta.annotation.PostConstruct;
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
 *
 * <p><b>FINANCIALS is deliberately absent.</b> It was wired here speculatively and never reached:
 * this resolver is driven by {@code GetLeverageFormStateUseCase.derivedSources}, which collects
 * QUESTION-level {@code derivedFrom}, and the financial sources are authored on FIELDS. They are
 * now owned by {@code FinancialsResolver}, which returns numbers rather than display strings and
 * fetches all three in one read. Two paths to the same table could disagree about what EBITDA was
 * — the screen would then contradict the frozen record — so there is only one.
 */
@Component
@RequiredArgsConstructor
public class DerivedValueResolverImpl implements DerivedValueResolver {

    private static final String SEPARATOR = "/";

    private final CounterpartyDerivationDao counterpartyDao;

    private Map<String, DerivedArea> areas;

    @PostConstruct
    void registerAreas() {
        // Built after injection rather than inline, so the handlers can be method references on
        // instances Spring has already supplied.
        areas = Map.of("COUNTERPARTY", this::counterparty);
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

    @FunctionalInterface
    private interface DerivedArea {
        Optional<String> resolve(String attribute, AnalysisSubject subject, String locale);
    }
}
