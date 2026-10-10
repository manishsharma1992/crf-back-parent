package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LocalizedLabel;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LocalizedQuestionLabel;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.*;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.*;

/**
 * Reads one question tab — <b>Preliminary Q</b>, <b>ECB Q</b> or <b>FED Q</b> — into a list of
 * {@link Question}s, joining each DATA_ENTRY question to its boxes from the Fields tab.
 *
 * <p>All three tabs share one column layout, so one parser serves all three; only the sheet name
 * and the form type differ.
 *
 * <p><b>Sheet row order is preserved but carries no meaning.</b> The template has no Order column:
 * screen order comes from the routing, because the SAME rows serve both LBO scenarios in a
 * different order and any static number would be wrong for one of them. Rows are kept in sheet
 * order purely so a report reads the way the BA typed it.
 */
@DomainDrivenDesign.ApplicationService
public final class QuestionSheetParser {

    private static final String QUESTION_KEY = "Question Key";
    private static final String TYPE = "Type";
    private static final String MANDATORY = "Mandatory";
    private static final String COMPUTED = "Computed";
    private static final String EDITABLE = "Editable";
    private static final String VISIBLE = "Visible";
    private static final String DERIVED_FROM = "Derived From";
    private static final String VALUE_RULES = "Value Rules";
    private static final String PREFILL_FROM = "Prefill From";
    private static final String SECTION_EN = "Section EN";
    private static final String SECTION_FR = "Section FR";
    private static final String LABEL_EN = "Label EN";
    private static final String BULLETS_EN = "Bullets EN";
    private static final String LABEL_FR = "Label FR";
    private static final String BULLETS_FR = "Bullets FR";
    private static final String SUBTITLE_EN = "Subtitle EN";
    private static final String SUBTITLE_FR = "Subtitle FR";
    private static final String NOTE_EN = "Note EN";
    private static final String NOTE_FR = "Note FR";
    private static final String NOTE_BULLETS_EN = "Note Bullets EN";
    private static final String NOTE_BULLETS_FR = "Note Bullets FR";
    private static final String OPTIONS = "Options";
    private static final String ITEMS = "Items";
    private static final String BRANCHES = "Branches";
    private static final String FILLS_FLAG = "Fills Flag";

    private final LabelParser labelParser;
    private final OptionsParser optionsParser;
    private final BranchExpressionParser branchParser;
    private final ValueRuleExpressionParser valueRuleParser;

    public QuestionSheetParser(LabelParser labelParser,
                               OptionsParser optionsParser,
                               BranchExpressionParser branchParser,
                               ValueRuleExpressionParser valueRuleParser) {
        this.labelParser = labelParser;
        this.optionsParser = optionsParser;
        this.branchParser = branchParser;
        this.valueRuleParser = valueRuleParser;
    }

    /** The tab holding a given form's questions. */
    public static String sheetNameFor(LeverageFormType form) {
        return switch (form) {
            case PRELIMINARY -> "Preliminary Q";
            case ECB -> "ECB Q";
            case FED -> "FED Q";
        };
    }

    public List<Question> parse(WorkbookSource workbook,
                                LeverageFormType form,
                                Map<String, List<DataField>> fieldsByQuestion,
                                ImportIssues issues,
                                SourceIndex index) {

        String sheet = sheetNameFor(form);
        Optional<SheetTable> table = SheetTable.locate(workbook, sheet,
                List.of(QUESTION_KEY, TYPE, BRANCHES), issues);
        if (table.isEmpty()) return List.of();

        List<Question> questions = new ArrayList<>();
        Set<String> seen = new HashSet<>();

        for (TableRow row : table.get().rows()) {
            String key = row.required(QUESTION_KEY);
            QuestionType type = row.enumValue(TYPE, QuestionType.class);
            if (key == null || type == null) continue;
            if (!seen.add(key)) {
                issues.add(row.at(QUESTION_KEY), "QUESTION_DUPLICATE", "Question '" + key + "' appears twice");
                continue;
            }
            index.question(form, key, row.rowNumber());
            questions.add(buildQuestion(row, key, type, fieldsByQuestion.getOrDefault(key, List.of()), issues));
        }

        reportOrphanFields(sheet, table.get(), fieldsByQuestion, seen, issues);
        return List.copyOf(questions);
    }

    private Question buildQuestion(TableRow row, String key, QuestionType type,
                                   List<DataField> fields, ImportIssues issues) {

        LocalizedQuestionLabel label = labelParser.parse(row, LABEL_EN, LABEL_FR, BULLETS_EN, BULLETS_FR, issues);
        LocalizedQuestionLabel subtitle = labelParser.parse(row, SUBTITLE_EN, SUBTITLE_FR, null, null, issues);
        LocalizedQuestionLabel note =
                labelParser.parse(row, NOTE_EN, NOTE_FR, NOTE_BULLETS_EN, NOTE_BULLETS_FR, issues);
        LocalizedQuestionLabel section = labelParser.parse(row, SECTION_EN, SECTION_FR, null, null, issues);
        boolean hidden = hidden(row, issues);
        checkSectionAndVisibility(row, key, type, section, hidden, issues);

        List<Option> options = row.get(OPTIONS)
                .map(cell -> optionsParser.parseOptions(cell, row.at(OPTIONS), issues))
                .orElseGet(List::of);
        List<ChecklistItem> items = row.get(ITEMS)
                .map(cell -> optionsParser.parseItems(cell, row.at(ITEMS), issues))
                .orElseGet(List::of);
        List<Branch> branches = row.get(BRANCHES)
                .map(cell -> branchParser.parse(cell, row.at(BRANCHES), issues))
                .orElseGet(List::of);
        List<ValueRule> valueRules = row.get(VALUE_RULES)
                .map(cell -> valueRuleParser.parse(cell, row.at(VALUE_RULES), issues))
                .orElseGet(List::of);

        if (type != QuestionType.DATA_ENTRY && !fields.isEmpty()) {
            issues.add(row.at(TYPE), "FIELDS_ON_NON_DATA_ENTRY",
                    "The Fields tab lists boxes for '" + key + "', but it is a " + type + ", not a DATA_ENTRY");
        }

        return new Question(
                key,
                type,
                row.flag(MANDATORY),
                row.flag(COMPUTED),
                row.flag(EDITABLE),
                row.get(DERIVED_FROM).orElse(null),
                valueRules,
                row.get(PREFILL_FROM).orElse(null),
                label,
                subtitle,
                note,
                options,
                items,
                fields,
                branches,
                row.get(FILLS_FLAG).orElse(null),
                section,
                hidden);
    }

    /**
     * A box whose question key matches nothing on this tab would vanish silently, taking its
     * validation messages with it — so it is reported here rather than left to the validator,
     * which never sees it.
     */
    private void reportOrphanFields(String sheet, SheetTable table,
                                    Map<String, List<DataField>> fieldsByQuestion,
                                    Set<String> knownQuestions, ImportIssues issues) {
        for (String questionKey : fieldsByQuestion.keySet()) {
            if (!knownQuestions.contains(questionKey)) {
                issues.add(SourceLocation.of(FieldsSheetParser.SHEET, table.headerRow(), QUESTION_KEY),
                        "FIELDS_ORPHAN_QUESTION",
                        "The Fields tab lists boxes for '" + questionKey + "', which is not on '" + sheet + "'");
            }
        }
    }

    /** The template has no sections; one synthetic section keeps the aggregate's shape. */
    public static Section singleSection(LeverageFormType form, List<Question> questions) {
        return new Section("MAIN", 1, new LocalizedLabel(form.name(), form.name()), questions);
    }

    /**
     * {@code Visible} is read as HIDDEN, and blank means shown.
     *
     * <p>Two reasons, and the second is the one that matters. A BA leaves the column blank on
     * almost every row, so blank has to mean the ordinary case. And the definition is stored as
     * JSON of these records: every PUBLISHED v14 definition has no such property, and a missing
     * primitive reads back as {@code false}. Named {@code visible}, that would hide every question
     * on every analysis already pinned to v14. Named {@code hidden}, the same default is harmless.
     *
     * <p>{@link TableRow#flag} is not used here because it maps blank to false, which is the
     * opposite of what this column means.
     */
    private boolean hidden(TableRow row, ImportIssues issues) {
        Optional<String> value = row.get(VISIBLE);
        if (value.isEmpty() || value.get().equalsIgnoreCase("yes")) return false;
        if (value.get().equalsIgnoreCase("no")) return true;
        issues.add(row.at(VISIBLE), "CELL_UNKNOWN_VALUE",
                "'" + value.get() + "' is not Yes or No (blank means Yes)");
        return false;
    }

    /**
     * Three authoring mistakes that would otherwise import cleanly and fail silently on screen.
     *
     * <ul>
     *   <li><b>A hidden question the analyst must answer.</b> Only the system can answer a question
     *       nobody sees, so Visible = No on anything but a COMPUTED question would stop the walk at
     *       an invisible step with no message.</li>
     *   <li><b>A section header in one language.</b> The header is frozen with the labels, so a
     *       French analysis would show a blank header. Both or neither, like the labels.</li>
     *   <li><b>A section header on a hidden question.</b> It would never be shown.</li>
     * </ul>
     */
    private void checkSectionAndVisibility(TableRow row, String key, QuestionType type,
                                           LocalizedQuestionLabel section, boolean hidden,
                                           ImportIssues issues) {
        if (hidden && type != QuestionType.COMPUTED && !row.flag(COMPUTED)) {
            issues.add(row.at(VISIBLE), "QUESTION_HIDDEN_NOT_COMPUTED",
                    "'" + key + "' is hidden but the analyst has to answer it; only a COMPUTED question can be hidden");
        }
        if (row.get(SECTION_EN).isPresent() != row.get(SECTION_FR).isPresent()) {
            issues.add(row.at(row.get(SECTION_EN).isPresent() ? SECTION_FR : SECTION_EN),
                    "SECTION_LANGUAGE_MISSING",
                    "'" + key + "' has a section header in one language only; fill both or neither");
        }
        if (hidden && section != null) {
            issues.add(row.at(SECTION_EN), "SECTION_ON_HIDDEN_QUESTION",
                    "'" + key + "' is hidden, so its section header would never be shown");
        }
    }
}
