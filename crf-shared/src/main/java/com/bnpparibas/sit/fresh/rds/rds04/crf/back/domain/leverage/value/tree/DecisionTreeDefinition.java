package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.Question;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.QuestionType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.catalogue.*;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.input.DataField;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A whole form's decision tree, as published. This is the aggregate root that gets serialised
 * into {@code leverage_decision_tree_definition.definition} and is IMMUTABLE once published:
 * an analysis in flight keeps walking the version it started on, which is also what lets us
 * answer "which rules were applied to this file in March".
 *
 * <p>The four catalogues are part of the definition rather than of any question, because each is
 * declared ONCE and referenced from many places.
 *
 * <p><b>The map copies preserve insertion order deliberately.</b> {@code Map.copyOf} would not:
 * its iteration order is unspecified AND salted per JVM run, so the same workbook would serialise
 * to different JSON on every start — a committed definition would churn in git, a drift check in
 * CI would flake, and the flags catalogue would render in a different order each deploy. The order
 * a BA authored the flags in IS the order the form shows them.
 *
 * @param outcomes           PRELIMINARY only — which forms each recommendation opens
 * @param flags              key -> declaration; the full set the form always displays
 * @param flagValueSets      set name -> codes, for {@link FlagStorage#CODE} flags
 * @param validationMessages what the analyst reads when a save is refused
 * @param infoPanels         read-only RMPM blocks shown on a flag value
 */
@DomainDrivenDesign.AggregateRoot
public record DecisionTreeDefinition(
        LeverageFormType formType,
        int version,
        DefinitionStatus status,
        String defaultLocale,
        List<String> locales,
        String entryQuestion,
        List<Section> sections,
        Map<RecommendationOutcome, Outcome> outcomes,
        Map<String, FlagDefinition> flags,
        Map<String, List<FlagValue>> flagValueSets,
        List<ValidationMessage> validationMessages,
        List<InfoPanel> infoPanels) {

    public DecisionTreeDefinition {
        locales = locales == null ? List.of() : List.copyOf(locales);
        sections = sections == null ? List.of() : List.copyOf(sections);
        outcomes = outcomes == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(outcomes));
        flags = flags == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(flags));
        flagValueSets = flagValueSets == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(flagValueSets));
        validationMessages = validationMessages == null ? List.of() : List.copyOf(validationMessages);
        infoPanels = infoPanels == null ? List.of() : List.copyOf(infoPanels);
    }

    /** Every question of every section, in authored order. */
    public List<Question> questions() {
        return sections.stream().flatMap(s -> s.questions().stream()).filter(java.util.Objects::nonNull).toList();
    }

    /**
     * BUG-01. Locates one question by key.
     *
     * <p>The aggregate already offered {@link #field(String)} but nothing for a question, so every
     * caller that wanted one either built its own index or reached for a {@code question(...)} that
     * was never here. Symmetry with {@code field} is the fix: one lookup, one place, Optional so an
     * absent key is a value rather than a null the caller forgets to check.
     */
    public Optional<Question> question(String questionKey) {
        if (questionKey == null) {
            return Optional.empty();
        }
        return questions().stream()
                .filter(q -> questionKey.equals(q.key()))
                .findFirst();
    }

    /**
     * BUG-02. The question whose answer names the counterparty the ratio is calculated on.
     *
     * <p>Was {@code LOOKUP_QUESTION = "Q-S06"}, hardcoded in both leverage use cases. A literal key
     * in application code is the one thing this whole design exists to avoid: keys are version
     * scoped and per form, so a tree that renumbered — or simply had no such question — sent a null
     * through {@code entityEligibility.resolve} and on into the violations list.
     *
     * <p>Derived rather than declared: a tree that asks for a counterparty says so by having a
     * LOOKUP question, and there is no second thing to keep in step. The import's structural check
     * should assert at most one per form (see the note in the accompanying analysis); until it
     * does, the first authored one wins, which is the same answer for every tree we have.
     */
    public Optional<Question> lookupQuestion() {
        return questions().stream()
                .filter(q -> q.type() == QuestionType.LOOKUP)
                .findFirst();
    }


        /**
         * Locates a DATA_ENTRY box by key. Field keys are unique within a form, which is what lets a
         * condition write {@code field ecbLeverageRatio} without naming the question.
         */
    public Optional<DataField> field(String fieldKey) {
        return questions().stream()
                .flatMap(q -> q.fields().stream())
                .filter(f -> f != null && fieldKey.equals(f.key()))
                .findFirst();
    }

    public Optional<FlagValue> flagValue(String valueSet, String code) {
        return flagValueSets.getOrDefault(valueSet, List.of()).stream()
                .filter(v -> v.code().equals(code))
                .findFirst();
    }
}
