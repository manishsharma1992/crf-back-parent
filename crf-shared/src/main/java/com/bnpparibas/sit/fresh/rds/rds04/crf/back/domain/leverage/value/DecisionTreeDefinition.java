package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

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
        outcomes = outcomes == null ? Map.of() : Map.copyOf(outcomes);
        flags = flags == null ? Map.of() : Map.copyOf(flags);
        flagValueSets = flagValueSets == null ? Map.of() : Map.copyOf(flagValueSets);
        validationMessages = validationMessages == null ? List.of() : List.copyOf(validationMessages);
        infoPanels = infoPanels == null ? List.of() : List.copyOf(infoPanels);
    }

    /** Every question of every section, in authored order. */
    public List<Question> questions() {
        return sections.stream().flatMap(s -> s.questions().stream()).filter(java.util.Objects::nonNull).toList();
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
