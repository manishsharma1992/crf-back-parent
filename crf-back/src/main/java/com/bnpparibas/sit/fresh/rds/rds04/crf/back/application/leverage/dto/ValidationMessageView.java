package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.Severity;

/**
 * One validation message that CURRENTLY FIRES, already rendered in the analyst's language.
 *
 * <p>Fired rather than catalogued on purpose. Most rules on the Forms tab cannot be evaluated in a
 * browser at all — PARENT_ENTITY_ELIGIBLE needs a business-group lookup, JUSTIFICATION_REQUIRED and
 * MUST_BE_POSITIVE are per-field checks on the financial table — so shipping the whole catalogue and
 * letting the client decide which applies would put the rules in two places and let them drift.
 *
 * <p>Localised here rather than on the client so a re-wording in the workbook reaches the screen
 * through an import, with no UI change and no message key duplicated in a translation file.
 *
 * @param questionKey the question at fault, or null when the rule is form-wide — the generic
 *                    checklist message is authored with a blank Question Key precisely because it
 *                    speaks for every checklist on the form
 * @param fieldKey    the data-entry box at fault, or null when the rule is not field-level
 */
public record ValidationMessageView(String messageKey,
                                    Severity severity,
                                    String questionKey,
                                    String fieldKey,
                                    String text) {
}
