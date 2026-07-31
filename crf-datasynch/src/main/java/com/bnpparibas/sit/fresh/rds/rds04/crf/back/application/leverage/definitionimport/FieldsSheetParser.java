package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LocalizedLabel;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.*;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.*;

/**
 * Reads the <b>Fields</b> tab — one row per box inside a DATA_ENTRY question.
 *
 * <p>Returns the boxes grouped by form and question key so {@link QuestionSheetParser} can join
 * them onto their owning question. ROW ORDER IS PRESERVED, because it is the screen order: Total
 * Net Funded Debt must render above the ratio it feeds.
 *
 * <p>Nothing here validates that the question exists, that a {@code CALC/} field is non-editable,
 * or that keys are unique across the form. Those need the assembled definition and belong to
 * {@code DecisionTreeValidator}; this parser only reports what it could not BUILD.
 */
@DomainDrivenDesign.ApplicationService
public final class FieldsSheetParser {

    public static final String SHEET = "Fields";

    private static final String FORM = "Form";
    private static final String QUESTION_KEY = "Question Key";
    private static final String GROUP = "Group";
    private static final String FIELD_KEY = "Field Key";
    private static final String LABEL_EN = "Label EN";
    private static final String LABEL_FR = "Label FR";
    private static final String TYPE = "Type";
    private static final String MANDATORY = "Mandatory";
    private static final String EDITABLE = "Editable";
    private static final String DERIVED_FROM = "Derived From";
    private static final String NOTE_EN = "Note EN";
    private static final String NOTE_FR = "Note FR";
    private static final String FORMULA = "Formula (documentation only)";
    private static final String FILLS_FLAG = "Fills Flag";

    /** form -> question key -> boxes, in sheet order. */
    public Map<LeverageFormType, Map<String, List<DataField>>> parse(WorkbookSource workbook, ImportIssues issues) {
        Optional<SheetTable> table = SheetTable.locate(workbook, SHEET,
                List.of(FORM, QUESTION_KEY, FIELD_KEY, TYPE), issues);
        if (table.isEmpty()) return Map.of();

        Map<LeverageFormType, Map<String, List<DataField>>> byForm = new EnumMap<>(LeverageFormType.class);
        for (TableRow row : table.get().rows()) {
            LeverageFormType form = row.enumValue(FORM, LeverageFormType.class);
            String questionKey = row.required(QUESTION_KEY);
            String fieldKey = row.required(FIELD_KEY);
            DataFieldType type = row.enumValue(TYPE, DataFieldType.class);
            if (form == null || questionKey == null || fieldKey == null || type == null) continue;

            LocalizedLabel note = row.get(NOTE_EN).isEmpty() && row.get(NOTE_FR).isEmpty()
                    ? null
                    : new LocalizedLabel(row.get(NOTE_EN).orElse(null), row.get(NOTE_FR).orElse(null));

            DataField field = new DataField(
                    fieldKey,
                    row.get(GROUP).orElse(null),
                    new LocalizedLabel(row.get(LABEL_EN).orElse(null), row.get(LABEL_FR).orElse(null)),
                    note,
                    type,
                    row.flag(MANDATORY),
                    row.flag(EDITABLE),
                    row.get(DERIVED_FROM).orElse(null),
                    row.get(FORMULA).orElse(null),
                    row.get(FILLS_FLAG).orElse(null));

            List<DataField> boxes = byForm
                    .computeIfAbsent(form, f -> new LinkedHashMap<>())
                    .computeIfAbsent(questionKey, q -> new ArrayList<>());
            if (boxes.stream().anyMatch(existing -> existing.key().equals(fieldKey))) {
                issues.add(row.at(FIELD_KEY), "FIELD_DUPLICATE_IN_QUESTION",
                        "Field '" + fieldKey + "' is listed twice for question '" + questionKey + "'");
                continue;
            }
            boxes.add(field);
        }
        return Collections.unmodifiableMap(byForm);
    }
}
