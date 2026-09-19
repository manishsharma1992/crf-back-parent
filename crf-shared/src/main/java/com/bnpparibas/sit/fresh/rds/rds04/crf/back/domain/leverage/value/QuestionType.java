package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.input.DataField;

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
    TEXT,
    /**
     * A single calendar day, stored as ISO-8601 {@code yyyy-MM-dd}.
     *
     * <p>Added for the FED form: Project Finance, APLC and REITs all open with "Date of the latest
     * Fiscal Quarter End". Like {@link #TEXT} it carries no routing of its own — every DATE question
     * authored so far has one {@code * -> Qxx} branch — and no FED rule compares one date with
     * another, so {@code ConditionEvaluator} is untouched.
     *
     * <p><b>The stored form is the canonical one, never a locale rendering.</b> The form runs in EN
     * and FR and the snapshot has to mean the same day when it is replayed years later;
     * {@code 03/04/2026} does not. Normalise at the boundary with {@code IsoDate.normalise}, and
     * reject rather than store anything that will not parse — a bad string written into a frozen
     * answer is a record nobody can read back.
     */
    DATE
}
