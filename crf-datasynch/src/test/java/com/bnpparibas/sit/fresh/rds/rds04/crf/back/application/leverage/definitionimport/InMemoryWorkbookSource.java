package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import java.util.*;

/**
 * Test double for {@link WorkbookSource}: a sheet is a list of rows, a row a list of cells.
 *
 * <p>Its existence is the point of the port. Every parser test below builds a few rows in Java
 * and runs the real parsing code — no .xlsx fixture to maintain, no POI on the test classpath,
 * and a failing test names the rule that broke rather than a file that drifted.
 */
public final class InMemoryWorkbookSource implements WorkbookSource {

    private final LinkedHashMap<String, List<List<String>>> sheets = new LinkedHashMap<>();

    public InMemoryWorkbookSource sheet(String name, List<List<String>> rows) {
        sheets.put(name, rows);
        return this;
    }

    /** Convenience: a header row followed by data rows. */
    public static List<List<String>> table(List<String> header, List<String>... rows) {
        List<List<String>> all = new ArrayList<>();
        all.add(header);
        all.addAll(Arrays.asList(rows));
        return all;
    }

    public static List<String> row(String... cells) {
        return Arrays.asList(cells);
    }

    @Override
    public List<String> sheetNames() {
        return List.copyOf(sheets.keySet());
    }

    @Override
    public int lastRow(String sheet) {
        return sheets.containsKey(sheet) ? sheets.get(sheet).size() : 0;
    }

    @Override
    public int lastColumn(String sheet) {
        return sheets.getOrDefault(sheet, List.of()).stream().mapToInt(List::size).max().orElse(0);
    }

    @Override
    public Optional<String> cell(String sheet, int row, int column) {
        List<List<String>> rows = sheets.get(sheet);
        if (rows == null || row < 1 || row > rows.size()) return Optional.empty();
        List<String> cells = rows.get(row - 1);
        if (column < 1 || column > cells.size()) return Optional.empty();
        String v = cells.get(column - 1);
        return v == null || v.isBlank() ? Optional.empty() : Optional.of(v.trim());
    }
}
