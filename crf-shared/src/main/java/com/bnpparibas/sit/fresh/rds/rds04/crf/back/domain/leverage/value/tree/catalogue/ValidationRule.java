package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.catalogue;

/**
 * A check the application runs against an answer. The rule is NAMED here and IMPLEMENTED in
 * code; the workbook only says which question or field it applies to and what the analyst
 * reads. Anything needing a join — does this entity share the counterparty's business group —
 * could never have been a spreadsheet expression, which is why these are names, not formulas.
 */
public enum ValidationRule {
    MANDATORY,
    JUSTIFICATION_REQUIRED,
    MUST_BE_POSITIVE,
    SOURCE_EMPTY,
    NOT_SELF,
    PARENT_ENTITY_ELIGIBLE,
    PARENT_NAME_DIFFERS
}
