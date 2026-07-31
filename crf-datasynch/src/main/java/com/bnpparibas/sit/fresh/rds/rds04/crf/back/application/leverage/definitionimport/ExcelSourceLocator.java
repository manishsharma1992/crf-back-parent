package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.ValidationResult;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

import static com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.ValidationResult.Error.Aspect;

/**
 * Turns a domain validation error into a cell reference, using the rows a
 * {@link SourceIndex} recorded during parsing.
 *
 * <p>Two lookups combine. The COLUMN comes from a fixed {@link Aspect} table, because an aspect
 * maps one-to-one onto a template column and that mapping is a property of the template, not of
 * any particular workbook. The ROW comes from the index, because only the parser ever knew it.
 *
 * <p>Which SHEET depends on the aspect too: a bad field lives on the Fields tab, a bad flag value
 * on Flag Values, a bad message or panel on Forms, and everything else on the form's own question
 * tab.
 *
 * <p>Returns empty rather than guessing. A half-right cell reference sends a BA to the wrong row,
 * which is worse than the logical fallback the report prints instead.
 */
@DomainDrivenDesign.ApplicationService
public final class ExcelSourceLocator implements SourceLocator {

    private static final Map<Aspect, String> COLUMN_BY_ASPECT = new EnumMap<>(Map.ofEntries(
            Map.entry(Aspect.KEY, "Question Key"),
            Map.entry(Aspect.TYPE, "Type"),
            Map.entry(Aspect.LABEL_EN, "Label EN"),
            Map.entry(Aspect.LABEL_FR, "Label FR"),
            Map.entry(Aspect.OPTIONS, "Options"),
            Map.entry(Aspect.ITEMS, "Items"),
            Map.entry(Aspect.FIELDS, "Field Key"),
            Map.entry(Aspect.DERIVED_FROM, "Derived From"),
            Map.entry(Aspect.VALUE_RULES, "Value Rules"),
            Map.entry(Aspect.PREFILL_FROM, "Prefill From"),
            Map.entry(Aspect.BRANCHES, "Branches"),
            Map.entry(Aspect.FILLS_FLAG, "Fills Flag"),
            Map.entry(Aspect.FLAGS_CATALOGUE, "Flag Key"),
            Map.entry(Aspect.FLAG_VALUES, "Code"),
            Map.entry(Aspect.VALIDATION_MESSAGES, "Message Key"),
            Map.entry(Aspect.INFO_PANELS, "Panel Key")));

    private final SourceIndex index;

    public ExcelSourceLocator(SourceIndex index) {
        this.index = index;
    }

    @Override
    public Optional<SourceLocation> locate(ValidationResult.Error error) {
        if (error == null || error.formType() == null) return Optional.empty();
        LeverageFormType form = error.formType();
        Aspect aspect = error.aspect() == null ? Aspect.KEY : error.aspect();
        String column = COLUMN_BY_ASPECT.get(aspect);

        return switch (aspect) {
            case FIELDS -> index.fieldRow(form, error.questionKey(), error.subKey())
                    .map(row -> new SourceLocation(FieldsSheetParser.SHEET, row, column, null));

            case FLAG_VALUES -> index.flagValueRow(error.subKey())
                    .map(row -> new SourceLocation(FlagValuesSheetParser.SHEET, row, column, null));

            case FLAGS_CATALOGUE -> index.flagRow(form, error.subKey())
                    .map(row -> new SourceLocation(FormsSheetParser.SHEET, row, column, null));

            case VALIDATION_MESSAGES -> index.validationMessageRow(form, error.subKey())
                    .map(row -> new SourceLocation(FormsSheetParser.SHEET, row, column, null));

            case INFO_PANELS -> index.infoPanelRow(error.subKey())
                    .map(row -> new SourceLocation(FormsSheetParser.SHEET, row, column, null));

            // A form-level problem has no question and therefore no row on the question tab; the
            // metadata row on Forms is the closest actionable place.
            case FORM -> index.formRow(form)
                    .map(row -> new SourceLocation(FormsSheetParser.SHEET, row, "Form Type", null));

            default -> onQuestionTab(form, error, column);
        };
    }

    /**
     * Everything else sits on the form's own tab. A branch index becomes the LINE within the
     * multi-line cell, which is what makes "row 12, column 'Branches', line 3" possible — and that
     * precision matters most exactly where the grammar is densest.
     */
    private Optional<SourceLocation> onQuestionTab(LeverageFormType form,
                                                   ValidationResult.Error error,
                                                   String column) {
        if (error.questionKey() == null) return Optional.empty();
        Integer line = error.branchIndex() == null ? null : error.branchIndex() + 1;
        return index.questionRow(form, error.questionKey())
                .map(row -> new SourceLocation(QuestionSheetParser.sheetNameFor(form), row, column, line));
    }
}
