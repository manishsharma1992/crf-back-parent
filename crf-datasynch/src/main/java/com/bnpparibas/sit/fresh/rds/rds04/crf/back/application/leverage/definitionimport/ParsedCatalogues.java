package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.*;

import java.util.List;
import java.util.Map;

/**
 * Everything the Forms and Flag Values tabs declare, parsed once and shared by all three
 * definitions.
 *
 * <p>Read the workbook ONCE and build all three forms in one transaction: the catalogues are
 * genuinely shared — the LEVERAGED_FLAG value set is written by both ECB and FED — so parsing
 * per form would either duplicate them or leave each form unable to see the other's codes.
 *
 * @param flagValueSets NOT keyed by form: a value set is global, and {@link FlagValue#setBy()}
 *                      says which form may write each code
 */
public record ParsedCatalogues(
        Map<LeverageFormType, FormMetadata> metadata,
        Map<RecommendationOutcome, Outcome> outcomes,
        Map<LeverageFormType, Map<String, FlagDefinition>> flags,
        Map<String, List<FlagValue>> flagValueSets,
        Map<LeverageFormType, List<ValidationMessage>> validationMessages,
        Map<LeverageFormType, List<InfoPanel>> infoPanels) {

    public Map<String, FlagDefinition> flagsFor(LeverageFormType form) {
        return flags.getOrDefault(form, Map.of());
    }

    public List<ValidationMessage> messagesFor(LeverageFormType form) {
        return validationMessages.getOrDefault(form, List.of());
    }

    public List<InfoPanel> panelsFor(LeverageFormType form) {
        return infoPanels.getOrDefault(form, List.of());
    }
}
