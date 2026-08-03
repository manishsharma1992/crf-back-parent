package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeDefinition;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.FlagDefinition;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.FlagStorage;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.FlagValue;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.InfoPanel;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Decides WHICH info panels a form should show, and how a stored code reads on screen.
 *
 * <p>Pure, and separate from the RMPM lookup on purpose. Two different things happen when a panel
 * appears: a rule decides it is triggered, and a database is read for its contents. Only the first
 * is domain logic, and keeping it here means it can be tested without a counterparty row.
 *
 * <p>A panel is triggered by a FLAG VALUE, never by a branch. That is why Q-T01, Q-C01 and Q-T03
 * all show the INR panel without one of them mentioning it: they set the same flag, and the rule
 * lives with the flag.
 */
@DomainDrivenDesign.DomainService
public final class InfoPanelSelector {

    /**
     * @param flags the flags the walk produced so far — a panel appears as soon as its trigger is
     *              filled, not only at the terminal
     */
    public List<InfoPanel> triggeredBy(DecisionTreeDefinition definition, Map<String, String> flags) {
        List<InfoPanel> triggered = new ArrayList<>();
        for (InfoPanel panel : definition.infoPanels()) {
            if (isTriggered(panel, flags)) {
                triggered.add(panel);
            }
        }
        return List.copyOf(triggered);
    }

    private boolean isTriggered(InfoPanel panel, Map<String, String> flags) {
        if (panel == null || panel.whenFlagKey() == null) {
            return false;
        }
        return panel.whenFlagValue().equals(flags.get(panel.whenFlagKey()));
    }

    /**
     * Renders a value RMPM stores as a number into the text the analyst reads — 2 becomes "INR",
     * 6 becomes "FED Leveraged Low Risk".
     *
     * <p>This is why a panel snapshot is frozen already-rendered: the number means nothing without
     * the value set that decoded it, and that set can be reworded by a later import.
     *
     * @return the display text, or the number as text when the flag is not coded or the code is
     *         unknown — showing a raw value beats showing nothing
     */
    public String display(DecisionTreeDefinition definition, String flagKey, int storedValue, String locale) {
        FlagDefinition flag = definition.flags().get(flagKey);
        if (flag == null || flag.storage() != FlagStorage.CODE) {
            return String.valueOf(storedValue);
        }
        return findByStoredValue(definition, flag.valueSet(), storedValue)
                .map(value -> localised(value, locale))
                .orElseGet(() -> String.valueOf(storedValue));
    }

    private Optional<FlagValue> findByStoredValue(DecisionTreeDefinition definition, String valueSet, int stored) {
        return definition.flagValueSets().getOrDefault(valueSet, List.of()).stream()
                .filter(value -> value.storedValue() == stored)
                .findFirst();
    }

    private String localised(FlagValue value, String locale) {
        if (value.display() == null) {
            return value.code();
        }
        String text = "FR".equalsIgnoreCase(locale) ? value.display().fr() : value.display().en();
        return text == null || text.isBlank() ? value.code() : text;
    }
}
