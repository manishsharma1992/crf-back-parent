package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.input;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

/**
 * The one place a DATE answer is read or written as text.
 *
 * <p>Answers travel as {@code Map<String, String>} from the request, through coercion, traversal
 * and validation, into the frozen snapshot — so a date is a String whether we like it or not. What
 * matters is that it is ALWAYS THE SAME String for the same day.
 *
 * <p><b>ISO-8601 {@code yyyy-MM-dd}, never a locale rendering.</b> The form runs in EN and FR, the
 * analyst's browser formats dates its own way, and a snapshot is read back years later by someone
 * who has no idea which locale wrote it. {@code 03/04/2026} is two different days depending on who
 * froze it; {@code 2026-04-03} is one day forever.
 *
 * <p><b>Reject at the boundary, not at render time.</b> The UI will be a date picker so malformed
 * values should not arrive, but the API is open and a bad string stored raw becomes a record that
 * cannot be displayed, audited or replayed. Better a refused save than an unreadable snapshot.
 *
 * <p>Deliberately not a value object on {@link Question}: the type stays a String end to end, the
 * same way {@code Answer.type} does, so an old snapshot never needs migrating when this changes.
 */
public final class IsoDate {

    private IsoDate() {
    }

    /**
     * Parses a stored or submitted date.
     *
     * @return the day, or empty when the text is null, blank or not ISO-8601. Empty is the same
     *         answer {@link TraversalAnswers} gives for anything absent — "no value", never a
     *         substituted default — so a caller that forgets to check cannot silently get today.
     */
    public static Optional<LocalDate> parse(String text) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(LocalDate.parse(text.trim()));
        } catch (DateTimeParseException ex) {
            return Optional.empty();
        }
    }

    /** True when the text is a date we can store. Blank counts as valid — unanswered is not malformed. */
    public static boolean isStorable(String text) {
        return text == null || text.isBlank() || parse(text).isPresent();
    }

    /**
     * Canonical form for storage: trimmed ISO-8601, or null when there is nothing to store.
     *
     * <p>Call this on the save path, before the answers reach the traversal, so that what routes,
     * what validates and what gets frozen are the same string. Anything that fails
     * {@link #isStorable} should have been refused before it reaches here.
     */
    public static String normalise(String text) {
        return parse(text).map(LocalDate::toString).orElse(null);
    }
}
