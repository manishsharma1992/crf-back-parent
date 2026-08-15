package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeDefinition;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.FlagDefinition;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.FlagStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A flag the walk has set, ready to render.
 *
 * <p><b>Why this exists.</b> {@code FormState.flags} is a raw {@code Map<String, String>} —
 * {@code ecbLeveragedFlag -> "INR"} — and neither half means anything to a reader. The label of the
 * flag and the label of its value both live in the workbook's catalogues, which the client does not
 * have. Every other view type on {@code FormState} arrives already rendered in the analyst's
 * language ({@code ValidationMessageView.text}, {@code PanelSnapshot.values}); flags were the
 * outlier, and a client-side lookup table would be the same duplication the alerts catalogue
 * already costs us.
 *
 * @param key          the flag key, for anything that needs to reason rather than render
 * @param label        the flag's own caption, localised
 * @param value        the stored code, e.g. {@code INR} — what a record or an export would carry
 * @param displayValue what the analyst reads, e.g. "Not Required". Equal to {@code value} for a
 *                     flag with no value set — a NUMBER flag stores the figure itself.
 */
public record FlagView(String key, String label, String value, String displayValue) {

    /**
     * Projects the flags the walk actually set.
     *
     * <p>A flag no branch named is ABSENT, not blank, so it is omitted rather than emitted with an
     * empty value — semantic 3, and the reason the snapshot can be read as "these are the
     * conclusions" rather than "here is every flag, some of which are empty".
     *
     * <p>Order follows the flags catalogue rather than the walk, so two analyses of the same form
     * present their conclusions in the same order regardless of the path taken through the tree.
     */
    public static List<FlagView> from(DecisionTreeDefinition definition,
                                      Map<String, String> setFlags,
                                      String locale) {
        List<FlagView> views = new ArrayList<>();
        for (FlagDefinition flag : definition.flags().values()) {
            String value = setFlags.get(flag.key());
            if (value == null || value.isBlank()) {
                continue;
            }
            views.add(new FlagView(
                    flag.key(),
                    localise(flag.label(), locale),
                    value,
                    displayValue(definition, flag, value, locale)));
        }
        return List.copyOf(views);
    }

    /**
     * A coded flag reads as its value's label; anything else reads as itself.
     *
     * <p>An unknown code falls back to the code rather than to blank. It should be impossible —
     * {@code FLAG_VALUE_UNKNOWN} rejects it at import — but a snapshot that silently drops a
     * conclusion is worse than one showing a raw code.
     */
    private static String displayValue(DecisionTreeDefinition definition, FlagDefinition flag,
                                       String value, String locale) {
        if (flag.storage() != FlagStorage.CODE) {
            return value;
        }
        return definition.flagValue(flag.valueSet(), value)
                .map(flagValue -> localise(flagValue.label(), locale))
                .orElse(value);
    }

    private static String localise(com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value
                                           .LocalizedLabel label, String locale) {
        if (label == null) {
            return "";
        }
        String text = "FR".equalsIgnoreCase(locale) ? label.fr() : label.en();
        // Fall back across locales rather than showing nothing: the workbook still has gaps in its
        // French column, and a blank caption reads as a defect in the screen.
        if (text == null || text.isBlank()) {
            text = "FR".equalsIgnoreCase(locale) ? label.en() : label.fr();
        }
        return text == null ? "" : text;
    }
}

/*
 * ============================================================ FormState
 *
 * Add one component, after `flags`:
 *
 *     List<FlagView> flagViews,
 *
 * `flags` stays. It is the machine-readable form and other code routes on it; `flagViews` is the
 * rendered form and only the screen reads it. Additive, so an older client ignores it.
 *
 * ============================================================ FormStateAssembler
 *
 * In assemble(), alongside the other projections:
 *
 *     FlagView.from(definition, result.flags(), locale)
 *
 * The assembler already holds the definition and the locale, so nothing new is injected.
 */
