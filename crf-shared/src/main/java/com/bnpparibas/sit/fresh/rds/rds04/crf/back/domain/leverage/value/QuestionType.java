package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

/** The kinds of node the authoring template can declare. */
public enum QuestionType {
    /** Two fixed options, Yes / No. */
    BOOLEAN,
    /** A closed list of options declared in the Options column. */
    SINGLE_CHOICE,
    /** Sub-items each answered Yes / No; routes on ANY_YES / ALL_NO. */
    CHECKLIST,
    /** A table of boxes (the financial data); the boxes are {@link DataField}s. */
    DATA_ENTRY,
    /** Filled by the system, from {@code derivedFrom} or from {@code valueRules}. */
    COMPUTED,
    /** Autocomplete over a runtime list, e.g. {@code LOOKUP/COUNTERPARTY}. */
    LOOKUP,
    /** Free numeric entry. */
    NUMERIC,
    /** Free text entry. */
    TEXT
}
