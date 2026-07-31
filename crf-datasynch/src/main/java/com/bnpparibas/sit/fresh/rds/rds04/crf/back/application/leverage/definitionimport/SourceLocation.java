package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

/**
 * A physical place in the authoring workbook, used only to make problems actionable for the BA.
 *
 * <p>Lives in the application layer because it is about the IMPORT BOUNDARY, not the domain: the
 * validator and the tree model know nothing about sheets and rows.
 *
 * @param sheet        worksheet name, e.g. "ECB Q"
 * @param row          1-based row as the BA sees it
 * @param columnHeader column header, e.g. "Branches", or null for a whole-row problem
 * @param line         1-based line within a multi-line cell, or null
 */
public record SourceLocation(String sheet, int row, String columnHeader, Integer line) {

    public static SourceLocation of(String sheet, int row) {
        return new SourceLocation(sheet, row, null, null);
    }

    public static SourceLocation of(String sheet, int row, String columnHeader) {
        return new SourceLocation(sheet, row, columnHeader, null);
    }

    public String describe() {
        StringBuilder sb = new StringBuilder("Sheet '").append(sheet).append("', row ").append(row);
        if (columnHeader != null) sb.append(", column '").append(columnHeader).append('\'');
        if (line != null) sb.append(" (line ").append(line).append(')');
        return sb.toString();
    }
}
