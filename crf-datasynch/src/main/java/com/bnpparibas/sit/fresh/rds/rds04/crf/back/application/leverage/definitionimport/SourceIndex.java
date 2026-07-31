package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.LeverageFormType;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Records WHERE each thing was read from, while it is being read.
 *
 * <p>The validator is pure and deliberately knows nothing about spreadsheets, so a
 * {@code ValidationResult.Error} says "question Q-S04, aspect BRANCHES" and stops there. Only the
 * parser ever knew that Q-S04 came from row 15 — this index is that knowledge, kept so the report
 * can say "Sheet 'ECB Q', row 15, column 'Branches'" instead.
 *
 * <p>The COLUMN never needs recording: an {@code Aspect} maps one-to-one onto a template column,
 * so {@link ExcelSourceLocator} resolves it from a fixed table. Only the ROW varies.
 *
 * <p>Mutable and single-use, like {@link ImportIssues}: one instance per import, discarded after.
 */
public final class SourceIndex {

    private record QuestionRef(LeverageFormType form, String questionKey) {
    }

    private record FieldRef(LeverageFormType form, String questionKey, String fieldKey) {
    }

    private record CatalogueRef(LeverageFormType form, String key) {
    }

    private final Map<QuestionRef, Integer> questionRows = new HashMap<>();
    private final Map<FieldRef, Integer> fieldRows = new HashMap<>();
    private final Map<CatalogueRef, Integer> messageRows = new HashMap<>();
    private final Map<String, Integer> panelRows = new HashMap<>();
    private final Map<CatalogueRef, Integer> flagRows = new HashMap<>();
    private final Map<String, Integer> flagValueRows = new HashMap<>();
    private final Map<LeverageFormType, Integer> formRows = new HashMap<>();

    private final boolean recording;

    private SourceIndex(boolean recording) {
        this.recording = recording;
    }

    public static SourceIndex recording() {
        return new SourceIndex(true);
    }

    /** For tests and for non-Excel sources, where there is nothing to point at. */
    public static SourceIndex discarding() {
        return new SourceIndex(false);
    }

    // ---------------------------------------------------------------- recording

    public void question(LeverageFormType form, String questionKey, int row) {
        if (recording) questionRows.put(new QuestionRef(form, questionKey), row);
    }

    public void field(LeverageFormType form, String questionKey, String fieldKey, int row) {
        if (recording) fieldRows.put(new FieldRef(form, questionKey, fieldKey), row);
    }

    public void validationMessage(LeverageFormType form, String messageKey, int row) {
        if (recording) messageRows.put(new CatalogueRef(form, messageKey), row);
    }

    public void infoPanel(String panelKey, int row) {
        if (recording) panelRows.put(panelKey, row);
    }

    public void flag(LeverageFormType form, String flagKey, int row) {
        if (recording) flagRows.put(new CatalogueRef(form, flagKey), row);
    }

    public void flagValue(String valueSet, String code, int row) {
        if (recording) flagValueRows.put(valueSet + '/' + code, row);
    }

    public void form(LeverageFormType form, int row) {
        if (recording) formRows.put(form, row);
    }

    // ---------------------------------------------------------------- lookup

    public Optional<Integer> questionRow(LeverageFormType form, String questionKey) {
        return Optional.ofNullable(questionRows.get(new QuestionRef(form, questionKey)));
    }

    /**
     * A field is looked up by its key alone when the owning question is not known — field keys are
     * unique across a form, which the validator enforces, so the first match is the only match.
     */
    public Optional<Integer> fieldRow(LeverageFormType form, String questionKey, String fieldKey) {
        Integer exact = fieldRows.get(new FieldRef(form, questionKey, fieldKey));
        if (exact != null) return Optional.of(exact);
        return fieldRows.entrySet().stream()
                .filter(e -> e.getKey().form() == form && e.getKey().fieldKey().equals(fieldKey))
                .map(Map.Entry::getValue)
                .findFirst();
    }

    public Optional<Integer> validationMessageRow(LeverageFormType form, String messageKey) {
        return Optional.ofNullable(messageRows.get(new CatalogueRef(form, messageKey)));
    }

    public Optional<Integer> infoPanelRow(String panelKey) {
        return Optional.ofNullable(panelRows.get(panelKey));
    }

    public Optional<Integer> flagRow(LeverageFormType form, String flagKey) {
        return Optional.ofNullable(flagRows.get(new CatalogueRef(form, flagKey)));
    }

    public Optional<Integer> flagValueRow(String valueSetSlashCode) {
        return Optional.ofNullable(flagValueRows.get(valueSetSlashCode));
    }

    public Optional<Integer> formRow(LeverageFormType form) {
        return Optional.ofNullable(formRows.get(form));
    }
}
