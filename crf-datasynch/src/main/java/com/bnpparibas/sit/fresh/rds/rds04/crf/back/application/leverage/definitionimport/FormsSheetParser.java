package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LocalizedLabel;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.*;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.*;

/**
 * Reads the <b>Forms</b> tab, which stacks four independent tables in the same columns:
 * form metadata, the outcomes catalogue, the flags catalogue, the validation messages and the
 * info panels.
 *
 * <p>Each is located by its HEADER SIGNATURE, never by row number, so inserting a row or a
 * footnote between tables cannot shift the parse.
 *
 * <p>Nothing here interprets business rules — it builds catalogue value objects and records what
 * it could not build. Whether a flag is actually USED, whether a message targets a real question,
 * and whether a form may set a given code are all structural questions answered afterwards by
 * {@code DecisionTreeValidator}, which sees the assembled definition.
 */
@DomainDrivenDesign.ApplicationService
public final class FormsSheetParser {

    public static final String SHEET = "Forms";

    // -- form metadata
    private static final String FORM_TYPE = "Form Type";
    private static final String DEFAULT_LOCALE = "Default Locale";
    private static final String LOCALES = "Locales";
    private static final String ENTRY_QUESTION = "Entry Question";
    // -- outcomes
    private static final String OUTCOME_CODE = "Outcome Code";
    private static final String DISPLAY_VALUE = "Display Value";
    private static final String FORMS_TO_SHOW = "Forms To Show";
    private static final String FORCED_FLAGS = "Forced Flags";
    // -- flags
    private static final String FORM = "Form";
    private static final String FLAG_KEY = "Flag Key";
    /** Prefix for messages about a flag row, so the wording stays identical across them. */
    private static final String FLAG = "Flag '";
    private static final String DISPLAY_EN = "Display EN";
    private static final String DISPLAY_FR = "Display FR";
    private static final String STORED_AS = "Stored As";
    private static final String VALUE_SET = "Value Set";
    // -- validation messages
    private static final String QUESTION_KEY = "Question Key";
    private static final String FIELD_KEY = "Field Key";
    private static final String RULE = "Rule";
    private static final String MESSAGE_KEY = "Message Key";
    private static final String SEVERITY = "Severity";
    private static final String TEXT_EN = "Text EN";
    private static final String TEXT_FR = "Text FR";
    // -- info panels
    private static final String PANEL_KEY = "Panel Key";
    private static final String TITLE_EN = "Title EN";
    private static final String TITLE_FR = "Title FR";
    private static final String SOURCE = "Source";
    private static final String FIELDS = "Fields";
    private static final String SHOWN_WHEN = "Shown When";

    private final FlagValuesSheetParser flagValuesParser;

    public FormsSheetParser(FlagValuesSheetParser flagValuesParser) {
        this.flagValuesParser = flagValuesParser;
    }

    public ParsedCatalogues parse(WorkbookSource workbook, ImportIssues issues, SourceIndex index) {
        Map<LeverageFormType, FormMetadata> metadata = parseMetadata(workbook, issues, index);
        Map<RecommendationOutcome, Outcome> outcomes = parseOutcomes(workbook, issues);
        Map<LeverageFormType, Map<String, FlagDefinition>> flags = parseFlags(workbook, issues, index);
        Map<String, List<FlagValue>> flagValueSets = flagValuesParser.parse(workbook, issues, index);
        Map<LeverageFormType, List<ValidationMessage>> messages =
                parseValidationMessages(workbook, issues, index);
        Map<LeverageFormType, List<InfoPanel>> panels = parseInfoPanels(workbook, flags, issues, index);

        return new ParsedCatalogues(metadata, outcomes, flags, flagValueSets, messages, panels);
    }

    // ------------------------------------------------------------------ form metadata

    private Map<LeverageFormType, FormMetadata> parseMetadata(WorkbookSource workbook, ImportIssues issues,
                                                              SourceIndex index) {
        Optional<SheetTable> table = SheetTable.locate(workbook, SHEET,
                List.of(FORM_TYPE, DEFAULT_LOCALE, LOCALES, ENTRY_QUESTION), issues);
        if (table.isEmpty()) return Map.of();

        Map<LeverageFormType, FormMetadata> byForm = new EnumMap<>(LeverageFormType.class);
        for (TableRow row : table.get().rows()) {
            LeverageFormType form = row.enumValue(FORM_TYPE, LeverageFormType.class);
            if (form == null) continue;
            if (byForm.containsKey(form)) {
                issues.add(row.at(FORM_TYPE), "FORM_DUPLICATE", "Form '" + form + "' is declared twice");
                continue;
            }
            String entry = row.required(ENTRY_QUESTION);
            List<String> locales = row.list(LOCALES);
            if (locales.isEmpty()) {
                issues.add(row.at(LOCALES), "FORM_NO_LOCALES", "A form must declare at least one locale");
            }
            index.form(form, row.rowNumber());
            byForm.put(form, new FormMetadata(form, row.get(DEFAULT_LOCALE).orElse(null), locales, entry));
        }
        for (LeverageFormType form : LeverageFormType.values()) {
            if (!byForm.containsKey(form)) {
                issues.add(SourceLocation.of(SHEET, table.get().headerRow(), FORM_TYPE), "FORM_MISSING",
                        "The workbook declares no metadata row for " + form);
            }
        }
        return Collections.unmodifiableMap(byForm);
    }

    // ------------------------------------------------------------------ outcomes

    /**
     * PRELIMINARY only: which forms a recommendation opens, plus any flag it forces on them.
     * ECB and FED express their results as flags instead, so their branches carry no outcome.
     */
    private Map<RecommendationOutcome, Outcome> parseOutcomes(WorkbookSource workbook, ImportIssues issues) {
        Optional<SheetTable> table = SheetTable.locate(workbook, SHEET,
                List.of(OUTCOME_CODE, DISPLAY_VALUE, FORMS_TO_SHOW), issues);
        if (table.isEmpty()) return Map.of();

        Map<RecommendationOutcome, Outcome> byCode = new LinkedHashMap<>();
        for (TableRow row : table.get().rows()) {
            RecommendationOutcome code = row.enumValue(OUTCOME_CODE, RecommendationOutcome.class);
            if (code == null) continue;
            if (byCode.containsKey(code)) {
                issues.add(row.at(OUTCOME_CODE), "OUTCOME_DUPLICATE", "Outcome '" + code + "' is declared twice");
                continue;
            }
            List<LeverageFormType> formsToShow = new ArrayList<>();
            for (String token : row.list(FORMS_TO_SHOW)) {
                Arrays.stream(LeverageFormType.values())
                        .filter(f -> f.name().equalsIgnoreCase(token))
                        .findFirst()
                        .ifPresentOrElse(formsToShow::add, () -> issues.add(row.at(FORMS_TO_SHOW),
                                "OUTCOME_UNKNOWN_FORM", "'" + token + "' is not a form"));
            }
            byCode.put(code, new Outcome(row.get(DISPLAY_VALUE).orElse(code.name()), formsToShow,
                    parseFlagAssignments(row, FORCED_FLAGS, issues)));
        }
        return Map.copyOf(byCode);
    }

    // ------------------------------------------------------------------ flags catalogue

    private Map<LeverageFormType, Map<String, FlagDefinition>> parseFlags(WorkbookSource workbook,
                                                                          ImportIssues issues,
                                                                          SourceIndex index) {
        Optional<SheetTable> table = SheetTable.locate(workbook, SHEET,
                List.of(FORM, FLAG_KEY, STORED_AS), issues);
        if (table.isEmpty()) return Map.of();

        Map<LeverageFormType, Map<String, FlagDefinition>> byForm = new EnumMap<>(LeverageFormType.class);
        for (TableRow row : table.get().rows()) {
            LeverageFormType form = row.enumValue(FORM, LeverageFormType.class);
            String key = row.required(FLAG_KEY);
            FlagStorage storage = row.enumValue(STORED_AS, FlagStorage.class);
            if (form == null || key == null) continue;
            if (storage == null) {
                issues.add(row.at(STORED_AS), "FLAG_NO_STORAGE", FLAG + key + "' does not say how it is stored");
                continue;
            }
            String valueSet = row.get(VALUE_SET).orElse(null);
            if (storage == FlagStorage.CODE && valueSet == null) {
                issues.add(row.at(VALUE_SET), "FLAG_NO_VALUE_SET",
                        FLAG + key + "' is stored as a code but names no value set");
            }
            Map<String, FlagDefinition> flags = byForm.computeIfAbsent(form, f -> new LinkedHashMap<>());
            if (flags.containsKey(key)) {
                issues.add(row.at(FLAG_KEY), "FLAG_DUPLICATE", FLAG + key + "' is declared twice for " + form);
                continue;
            }
            index.flag(form, key, row.rowNumber());
            flags.put(key, new FlagDefinition(key,
                    new LocalizedLabel(row.get(DISPLAY_EN).orElse(null), row.get(DISPLAY_FR).orElse(null)),
                    storage, valueSet));
        }
        byForm.replaceAll((form, flags) -> Collections.unmodifiableMap(flags));
        return Collections.unmodifiableMap(byForm);
    }

    // ------------------------------------------------------------------ validation messages

    private Map<LeverageFormType, List<ValidationMessage>> parseValidationMessages(WorkbookSource workbook,
                                                                                   ImportIssues issues,
                                                                                   SourceIndex index) {
        Optional<SheetTable> table = SheetTable.locate(workbook, SHEET,
                List.of(FORM, RULE, MESSAGE_KEY, SEVERITY), issues);
        if (table.isEmpty()) return Map.of();

        Map<LeverageFormType, List<ValidationMessage>> byForm = new EnumMap<>(LeverageFormType.class);
        for (TableRow row : table.get().rows()) {
            LeverageFormType form = row.enumValue(FORM, LeverageFormType.class);
            String messageKey = row.required(MESSAGE_KEY);
            ValidationRule rule = row.enumValue(RULE, ValidationRule.class);
            Severity severity = row.enumValue(SEVERITY, Severity.class);
            if (form == null || messageKey == null) continue;
            if (rule == null || severity == null) continue;   // already reported by enumValue

            index.validationMessage(form, messageKey, row.rowNumber());
            byForm.computeIfAbsent(form, f -> new ArrayList<>())
                    .add(new ValidationMessage(
                            row.get(QUESTION_KEY).orElse(null),
                            row.get(FIELD_KEY).orElse(null),
                            rule, messageKey, severity,
                            new LocalizedLabel(row.get(TEXT_EN).orElse(null), row.get(TEXT_FR).orElse(null))));
        }
        byForm.replaceAll((form, list) -> List.copyOf(list));
        return Collections.unmodifiableMap(byForm);
    }

    // ------------------------------------------------------------------ info panels

    /**
     * The panel table carries no Form column, so a panel is attached to whichever form OWNS the
     * flag it triggers on. That keeps the sheet short and cannot drift, since a panel with no
     * trigger flag would be meaningless anyway.
     */
    private Map<LeverageFormType, List<InfoPanel>> parseInfoPanels(
            WorkbookSource workbook,
            Map<LeverageFormType, Map<String, FlagDefinition>> flags,
            ImportIssues issues,
            SourceIndex index) {

        Optional<SheetTable> table = SheetTable.locate(workbook, SHEET,
                List.of(PANEL_KEY, SOURCE, FIELDS, SHOWN_WHEN), issues);
        if (table.isEmpty()) return Map.of();

        Map<LeverageFormType, List<InfoPanel>> byForm = new EnumMap<>(LeverageFormType.class);
        for (TableRow row : table.get().rows()) {
            String key = row.required(PANEL_KEY);
            String shownWhen = row.required(SHOWN_WHEN);
            if (key == null || shownWhen == null) continue;

            String[] parts = shownWhen.split("(?i)\\sis\\s", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                issues.add(row.at(SHOWN_WHEN), "PANEL_TRIGGER_MALFORMED",
                        "Shown When must read '<flagKey> is <VALUE>'; got '" + shownWhen + "'");
                continue;
            }
            String flagKey = parts[0].trim();
            String flagValue = parts[1].trim();

            Optional<LeverageFormType> owner = flags.entrySet().stream()
                    .filter(e -> e.getValue().containsKey(flagKey))
                    .map(Map.Entry::getKey)
                    .findFirst();
            if (owner.isEmpty()) {
                issues.add(row.at(SHOWN_WHEN), "PANEL_UNKNOWN_FLAG",
                        "No form declares flag '" + flagKey + "', so this panel cannot be attached");
                continue;
            }
            index.infoPanel(key, row.rowNumber());
            byForm.computeIfAbsent(owner.get(), f -> new ArrayList<>())
                    .add(new InfoPanel(key,
                            new LocalizedLabel(row.get(TITLE_EN).orElse(null), row.get(TITLE_FR).orElse(null)),
                            row.get(SOURCE).orElse(null),
                            row.list(FIELDS),
                            flagKey, flagValue));
        }
        byForm.replaceAll((form, list) -> List.copyOf(list));
        return Collections.unmodifiableMap(byForm);
    }

    // ------------------------------------------------------------------ shared

    /** Parses {@code key=VALUE ; key2=VALUE2} into a map, used by Forced Flags. */
    private Map<String, String> parseFlagAssignments(TableRow row, String header, ImportIssues issues) {
        Map<String, String> assignments = new LinkedHashMap<>();
        for (String token : row.list(header)) {
            int eq = token.indexOf('=');
            if (eq <= 0 || eq == token.length() - 1) {
                issues.add(row.at(header), "FLAG_ASSIGNMENT_MALFORMED",
                        "Expected 'flagKey=VALUE'; got '" + token + "'. An empty right-hand side is never valid — "
                                + "a flag that nothing sets is simply left empty.");
                continue;
            }
            assignments.put(token.substring(0, eq).trim(), token.substring(eq + 1).trim());
        }
        return assignments;
    }
}
