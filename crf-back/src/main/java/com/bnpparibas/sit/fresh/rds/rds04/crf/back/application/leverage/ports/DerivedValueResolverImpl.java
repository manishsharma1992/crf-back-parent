package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.CounterpartyRatings;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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
 * <p><b>Sources are grouped by area and each area is called ONCE.</b> This used to be a loop that
 * called a handler per source, which was fine while COUNTERPARTY had a single attribute. The REIT
 * path asks for three ratings off one row, so per-source dispatch would be three round trips per
 * traversal — and a traversal runs on every answer — with the worse problem that three reads can
 * disagree if the row is written between them, leaving a screen that contradicts the frozen
 * record. Batching is what lets a handler fetch its row once and answer every attribute from it.
 *
 * <p>Adding a source is an attribute case in an existing area, or a line in {@link #areas} and a
 * method beside {@link #counterparty}.
 *
 * <p><b>FINANCIALS is deliberately absent.</b> It was wired here speculatively and never reached:
 * this resolver is driven by {@code GetLeverageFormStateUseCase.derivedSources}, which collects
 * QUESTION-level {@code derivedFrom}, and the financial sources are authored on FIELDS. They are
 * owned by {@code FinancialsResolver} and {@code FedFinancialSourcesResolver}, which return
 * numbers rather than display strings and fetch a whole row in one read.
 */
@Component
@RequiredArgsConstructor
public class DerivedValueResolverImpl implements DerivedValueResolver {

    private static final String SEPARATOR = "/";
    private static final String PARENT = "PARENT";
    private static final String BNPP_RATING = "BNPP_COUNTERPARTY_RATING";
    private static final String SP_RATING = "SP_ISSUER_RATING";
    private static final String MOODYS_RATING = "MOODYS_ISSUER_RATING";

    private final CounterpartyDerivationRepository counterparties;

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
        Map<String, Set<String>> byArea = groupByArea(sources);

        Map<String, String> resolved = new LinkedHashMap<>();
        byArea.forEach((area, attributes) -> {
            DerivedArea handler = areas.get(area);
            if (handler == null) {
                return;   // unknown area: the fields stay blank and the analyst carries on
            }
            handler.resolve(attributes, subject, locale)
                    .forEach((attribute, value) -> resolved.put(area + SEPARATOR + attribute, value));
        });
        return Map.copyOf(resolved);
    }

    /** {@code COUNTERPARTY/PARENT} becomes {@code COUNTERPARTY -> {PARENT}}. */
    private Map<String, Set<String>> groupByArea(Set<String> sources) {
        Map<String, Set<String>> byArea = new LinkedHashMap<>();
        for (String source : sources) {
            if (source == null || !source.contains(SEPARATOR)) {
                continue;
            }
            String[] parts = source.split(SEPARATOR, 2);
            byArea.computeIfAbsent(parts[0].trim().toUpperCase(), a -> new LinkedHashSet<>())
                    .add(parts[1].trim());
        }
        return byArea;
    }

    // ------------------------------------------------------------------ COUNTERPARTY

    /**
     * Everything read off the analysed counterparty.
     *
     * <p>Two reads at most, and only the ones asked for: the parent lookup, and the ratings row.
     * ECB asks for PARENT alone and never touches the ratings; the REIT path asks for all three
     * ratings and gets them from one row.
     */
    private Map<String, String> counterparty(Set<String> attributes, AnalysisSubject subject, String locale) {
        Map<String, String> values = new LinkedHashMap<>();
        if (subject.rmpmid() == null) {
            return values;
        }
        if (attributes.contains(PARENT)) {
            parent(subject).ifPresent(value -> values.put(PARENT, value));
        }
        if (wantsRatings(attributes)) {
            addRatings(values, attributes, counterparties.findRatings(subject.rmpmid())
                    .orElse(CounterpartyRatings.NONE));
        }
        return values;
    }

    private boolean wantsRatings(Set<String> attributes) {
        return attributes.contains(BNPP_RATING)
                || attributes.contains(SP_RATING)
                || attributes.contains(MOODYS_RATING);
    }

    private void addRatings(Map<String, String> values, Set<String> attributes, CounterpartyRatings ratings) {
        put(values, attributes, BNPP_RATING, ratings.bnppRating(), ratings.bnppRatingDate());
        put(values, attributes, SP_RATING, ratings.spIssuerRating(), ratings.spIssuerRatingDate());
        put(values, attributes, MOODYS_RATING, ratings.moodysIssuerRating(), ratings.moodysIssuerRatingDate());
    }

    /**
     * {@code "A- (2026-03-31)"} — the rating and the date it was set, in one string.
     *
     * <p>Both halves together for the same reason PARENT renders as {@code "id - name"}: the
     * business rule asks for both, and the snapshot stores what was on screen rather than
     * recombining parts at read time.
     *
     * <p>ISO for the date, never a locale rendering. This string is frozen into the record and
     * read back years later by someone who has no idea which locale wrote it, and {@code 03/04/26}
     * is two different days depending on the answer.
     *
     * <p><b>No rating means no entry</b>, not a blank one — a counterparty with no Moody's rating
     * is ordinary. A rating with no date renders as the rating alone rather than trailing an empty
     * bracket.
     */
    private void put(Map<String, String> values, Set<String> attributes,
                     String attribute, String rating, LocalDate date) {
        if (!attributes.contains(attribute) || rating == null || rating.isBlank()) {
            return;
        }
        values.put(attribute, date == null ? rating.trim() : rating.trim() + " (" + date + ")");
    }

    /**
     * {@code COUNTERPARTY/PARENT} renders as "12345678 - ACME HOLDING SA".
     *
     * <p>Both halves in one string because the business rule asks for both — "the RMPM ID and the
     * name of the parent counterparty" — and because the snapshot stores what was on screen. A
     * separate id and name would have to be recombined at read time, by which point the name may
     * have changed.
     */
    private Optional<String> parent(AnalysisSubject subject) {
        return counterparties.findParentOf(subject.rmpmid())
                .map(p -> p.rmpmid() + " - " + p.companyName());
    }

    @FunctionalInterface
    private interface DerivedArea {
        /** @return attribute to rendered value, omitting anything reference data cannot answer */
        Map<String, String> resolve(Set<String> attributes, AnalysisSubject subject, String locale);
    }
}
