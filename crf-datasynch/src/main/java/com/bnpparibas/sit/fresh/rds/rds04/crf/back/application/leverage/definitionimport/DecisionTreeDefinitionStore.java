package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeDefinition;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;

import java.time.Instant;
import java.util.Optional;

/**
 * PORT. Where published decision trees live.
 *
 * <p>Expressed in DOMAIN terms — a {@link DecisionTreeDefinition}, a form, an instant. Nothing
 * about JSON, columns or Hibernate crosses this line, so the import service can be tested against
 * an in-memory implementation and the storage technology can change without touching it.
 *
 * <p>Definitions are IMMUTABLE once published. A new import never edits a row; it closes the open
 * one and inserts the next version. That is what keeps an analysis started in March walking the
 * March rules, and what lets us answer "which rules were applied to this file" a year later.
 */
public interface DecisionTreeDefinitionStore {

    /** Highest version stored for a form, or 0 when the form has never been imported. */
    int currentVersion(LeverageFormType form);

    /**
     * Closes the currently open published row for a form by stamping {@code valid_to}.
     * Idempotent: closing an already-closed form does nothing.
     */
    void supersede(LeverageFormType form, Instant at, String author);

    /** Inserts a new version, open-ended ({@code valid_to} null). */
    void save(DecisionTreeDefinition definition, Instant validFrom, String author);

    /** The definition in force for a form at a moment — what a traversal resolves against. */
    Optional<DecisionTreeDefinition> findInForce(LeverageFormType form, Instant at);
}
