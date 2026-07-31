package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A block of rows under one header row, located by its HEADER SIGNATURE rather than by a fixed
 * row number.
 *
 * <p>The Forms tab stacks four tables in the same columns, separated by banner and footnote rows.
 * Anchoring on row numbers would break the moment a BA inserts a line — which they will — so the
 * locator scans for a row containing the required headers and reads downward from there.
 *
 * <p>A row ENDS the table when it has fewer than two populated cells across the header span. That
 * one rule covers blank separators, coloured banner rows and the italic footnotes, all of which
 * populate only the first column, while every real data row here fills at least two.
 */
public final class SheetTable {

    private final String sheet;
    private final int headerRow;
    private final Map<String, Integer> columnByHeader;
    private final List<TableRow> rows;

    private SheetTable(String sheet, int headerRow, Map<String, Integer> columnByHeader, List<TableRow> rows) {
        this.sheet = sheet;
        this.headerRow = headerRow;
        this.columnByHeader = columnByHeader;
        this.rows = rows;
    }

    public String sheet() {
        return sheet;
    }

    public int headerRow() {
        return headerRow;
    }

    public List<TableRow> rows() {
        return rows;
    }

    public boolean hasColumn(String header) {
        return columnByHeader.containsKey(Cells.normaliseHeader(header));
    }

    /**
     * Finds the table on {@code sheet} whose header row contains all of {@code requiredHeaders},
     * and reads its data rows.
     *
     * @return empty, plus a recorded issue, when no such header row exists
     */
    public static Optional<SheetTable> locate(WorkbookSource workbook,
                                              String sheet,
                                              List<String> requiredHeaders,
                                              ImportIssues issues) {
        if (!workbook.sheetNames().contains(sheet)) {
            issues.add(SourceLocation.of(sheet, 0), "SHEET_MISSING", "Sheet '" + sheet + "' is not in the workbook");
            return Optional.empty();
        }
        int lastRow = workbook.lastRow(sheet);
        int lastCol = workbook.lastColumn(sheet);

        for (int row = 1; row <= lastRow; row++) {
            Map<String, Integer> columns = headerColumns(workbook, sheet, row, lastCol);
            if (!containsAll(columns, requiredHeaders)) continue;
            return Optional.of(new SheetTable(sheet, row, columns,
                    readRows(workbook, sheet, row, columns, lastRow, issues)));
        }
        issues.add(SourceLocation.of(sheet, 0), "TABLE_NOT_FOUND",
                "No table on '" + sheet + "' has the columns " + requiredHeaders);
        return Optional.empty();
    }

    private static Map<String, Integer> headerColumns(WorkbookSource workbook, String sheet, int row, int lastCol) {
        Map<String, Integer> columns = new LinkedHashMap<>();
        for (int col = 1; col <= lastCol; col++) {
            workbook.cell(sheet, row, col)
                    .map(Cells::normaliseHeader)
                    .filter(h -> !h.isEmpty())
                    .ifPresent(h -> columns.putIfAbsent(h, col));
        }
        return columns;
    }

    private static boolean containsAll(Map<String, Integer> columns, List<String> required) {
        return required.stream().map(Cells::normaliseHeader).allMatch(columns::containsKey);
    }

    private static List<TableRow> readRows(WorkbookSource workbook, String sheet, int headerRow,
                                           Map<String, Integer> columns, int lastRow, ImportIssues issues) {
        List<TableRow> rows = new ArrayList<>();
        for (int row = headerRow + 1; row <= lastRow; row++) {
            Map<String, String> values = new LinkedHashMap<>();
            int populated = 0;
            for (Map.Entry<String, Integer> e : columns.entrySet()) {
                Optional<String> value = workbook.cell(sheet, row, e.getValue());
                if (value.isPresent()) {
                    values.put(e.getKey(), value.get());
                    populated++;
                }
            }
            if (populated < 2) break;   // blank line, banner, or footnote — the table has ended
            rows.add(new TableRow(sheet, row, values, issues));
        }
        return rows;
    }

    /** Header-addressed access used by {@link TableRow}; headers are matched case-insensitively. */
    Map<String, Integer> columnByHeader() {
        return columnByHeader;
    }
}
