package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.FlagValue;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LocalizedLabel;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.*;

/**
 * Reads the <b>Flag Values</b> tab: the dictionary of coded flag values and the integer each one
 * is stored as.
 *
 * <p>Grouped by Value Set, not by form, because ECB and FED SHARE the LEVERAGED_FLAG set — code 2
 * (INR) is written by both. {@code Set By} records who may WRITE each code; everyone may read it,
 * which is how an Info Panel renders a number that the other form wrote.
 */
@DomainDrivenDesign.ApplicationService
public final class FlagValuesSheetParser {

    public static final String SHEET = "Flag Values";

    private static final String VALUE_SET = "Value Set";
    private static final String CODE = "Code";
    private static final String STORED_VALUE = "Stored Value";
    private static final String DISPLAY_EN = "Display EN";
    private static final String DISPLAY_FR = "Display FR";
    private static final String SET_BY = "Set By";

    public Map<String, List<FlagValue>> parse(WorkbookSource workbook, ImportIssues issues, SourceIndex index) {
        Optional<SheetTable> table = SheetTable.locate(workbook, SHEET,
                List.of(VALUE_SET, CODE, STORED_VALUE, SET_BY), issues);
        if (table.isEmpty()) return Map.of();

        Map<String, List<FlagValue>> bySet = new LinkedHashMap<>();
        Set<String> seen = new HashSet<>();

        for (TableRow row : table.get().rows()) {
            String valueSet = row.required(VALUE_SET);
            String code = row.required(CODE);
            Integer stored = row.intValue(STORED_VALUE);
            if (valueSet == null || code == null) continue;

            if (stored == null) {
                issues.add(row.at(STORED_VALUE), "FLAG_VALUE_NO_NUMBER",
                        "Code '" + code + "' has no stored number");
                continue;
            }
            if (!seen.add(valueSet + '/' + code)) {
                issues.add(row.at(CODE), "FLAG_VALUE_DUPLICATE",
                        "Code '" + code + "' appears twice in value set '" + valueSet + "'");
                continue;
            }
            index.flagValue(valueSet, code, row.rowNumber());
            Set<LeverageFormType> setBy = parseSetBy(row, issues);
            bySet.computeIfAbsent(valueSet, k -> new ArrayList<>())
                    .add(new FlagValue(valueSet, code, stored,
                            new LocalizedLabel(row.get(DISPLAY_EN).orElse(null), row.get(DISPLAY_FR).orElse(null)),
                            setBy));
        }
        rejectDuplicateNumbers(bySet, issues, table.get());
        return Map.copyOf(bySet);
    }

    /** {@code BOTH} expands to every form; anything else must name a form. */
    private Set<LeverageFormType> parseSetBy(TableRow row, ImportIssues issues) {
        List<String> tokens = row.list(SET_BY);
        if (tokens.isEmpty()) {
            issues.add(row.at(SET_BY), "FLAG_VALUE_NO_SET_BY", "Set By must name a form, or BOTH");
            return Set.of();
        }
        Set<LeverageFormType> forms = new LinkedHashSet<>();
        for (String token : tokens) {
            if (token.equalsIgnoreCase("BOTH")) {
                forms.addAll(Arrays.asList(LeverageFormType.values()));
                continue;
            }
            Arrays.stream(LeverageFormType.values())
                    .filter(f -> f.name().equalsIgnoreCase(token))
                    .findFirst()
                    .ifPresentOrElse(forms::add, () -> issues.add(row.at(SET_BY), "FLAG_VALUE_UNKNOWN_FORM",
                            "'" + token + "' is not a form or BOTH"));
        }
        return forms;
    }

    /**
     * Two codes sharing a stored number would make the value unreadable coming BACK out of the
     * database — an Info Panel could not tell which one it holds.
     */
    private void rejectDuplicateNumbers(Map<String, List<FlagValue>> bySet, ImportIssues issues, SheetTable table) {
        bySet.forEach((set, values) -> {
            Map<Integer, String> byNumber = new HashMap<>();
            for (FlagValue v : values) {
                String previous = byNumber.putIfAbsent(v.storedValue(), v.code());
                if (previous != null) {
                    issues.add(SourceLocation.of(table.sheet(), table.headerRow(), STORED_VALUE),
                            "FLAG_VALUE_NUMBER_CLASH",
                            "In set '" + set + "', codes '" + previous + "' and '" + v.code()
                                    + "' both store " + v.storedValue());
                }
            }
        });
    }
}
