package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import java.util.List;
import java.util.Optional;

/**
 * PORT. Technology-neutral read access to the authoring workbook.
 *
 * <p>Everything above this interface — table location, grammar parsing, catalogue assembly —
 * works on plain strings and can be unit-tested with an in-memory fake. Apache POI appears in
 * exactly ONE implementation, in the infrastructure layer, so swapping the authoring surface
 * (a JSON file, an admin screen) replaces one adapter and nothing else.
 *
 * <p>Coordinates are 1-BASED, matching what the BA sees in Excel, because they end up in a
 * {@link SourceLocation} that a human has to act on.
 */
public interface WorkbookSource {

    /** Sheet names in workbook order. */
    List<String> sheetNames();

    /** Last row that contains anything, 1-based; 0 when the sheet is empty or absent. */
    int lastRow(String sheet);

    /** Last column that contains anything, 1-based; 0 when the sheet is empty or absent. */
    int lastColumn(String sheet);

    /**
     * Trimmed text of a cell, or empty when blank / absent. Implementations render numbers and
     * booleans as text: the authoring template is a document, and "0" must not arrive as "0.0".
     */
    Optional<String> cell(String sheet, int row, int column);
}
