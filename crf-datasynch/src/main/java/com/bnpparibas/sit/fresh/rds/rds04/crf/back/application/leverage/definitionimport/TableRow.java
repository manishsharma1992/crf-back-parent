package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * One data row of a located table, addressed by COLUMN HEADER rather than by index.
 *
 * <p>Header addressing is what lets a BA insert or reorder a column without breaking the import,
 * and it makes every read self-describing at the call site: {@code row.required("Flag Key")}.
 */
public final class TableRow {

    private final String sheet;
    private final int rowNumber;
    private final Map<String, String> byHeader;
    private final ImportIssues issues;

    TableRow(String sheet, int rowNumber, Map<String, String> byHeader, ImportIssues issues) {
        this.sheet = sheet;
        this.rowNumber = rowNumber;
        this.byHeader = new LinkedHashMap<>(byHeader);
        this.issues = issues;
    }

    public int rowNumber() {
        return rowNumber;
    }

    public SourceLocation at(String header) {
        return SourceLocation.of(sheet, rowNumber, header);
    }

    public SourceLocation at() {
        return SourceLocation.of(sheet, rowNumber);
    }

    /** Trimmed value, or empty when the cell is blank. */
    public Optional<String> get(String header) {
        String v = byHeader.get(Cells.normaliseHeader(header));
        return v == null || v.isBlank() ? Optional.empty() : Optional.of(v.trim());
    }

    /** Value, or null plus a recorded issue when the cell is blank. */
    public String required(String header) {
        Optional<String> v = get(header);
        if (v.isEmpty()) {
            issues.add(at(header), "CELL_REQUIRED", "'" + header + "' must not be blank");
            return null;
        }
        return v.get();
    }

    public boolean flag(String header) {
        return get(header).map(v -> v.equalsIgnoreCase("yes") || v.equalsIgnoreCase("true")).orElse(false);
    }

    public Integer intValue(String header) {
        Optional<String> v = get(header);
        if (v.isEmpty()) return null;
        try {
            return new BigDecimal(v.get()).intValueExact();
        } catch (RuntimeException ex) {
            issues.add(at(header), "CELL_NOT_INTEGER", "'" + v.get() + "' is not a whole number");
            return null;
        }
    }

    /** Splits a {@code ;}-separated list cell, trimming and dropping blanks. */
    public List<String> list(String header) {
        return get(header).map(Cells::splitList).orElseGet(List::of);
    }

    /** Parses an enum by name, case-insensitively, recording an issue when unknown. */
    public <E extends Enum<E>> E enumValue(String header, Class<E> type) {
        Optional<String> v = get(header);
        if (v.isEmpty()) return null;
        for (E candidate : type.getEnumConstants()) {
            if (candidate.name().equalsIgnoreCase(v.get())) return candidate;
        }
        issues.add(at(header), "CELL_UNKNOWN_VALUE",
                "'" + v.get() + "' is not one of " + java.util.Arrays.toString(type.getEnumConstants()));
        return null;
    }
}
