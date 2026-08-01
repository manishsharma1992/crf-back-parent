package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.application.leverage.definitionimport.WorkbookSource;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * ADAPTER. The ONLY class in the import that knows Apache POI exists.
 *
 * <p>Everything above {@link WorkbookSource} works on strings, so the catalogue and question
 * parsers are unit-testable with an in-memory fake and never need a real .xlsx on the classpath.
 *
 * <p>Two conversions matter and are done here deliberately:
 * <ul>
 *   <li><b>Numbers render without a decimal tail.</b> POI hands back every numeric cell as a
 *       double, so a stored value of 0 arrives as "0.0" and would fail to parse as an integer.
 *       {@link BigDecimal#stripTrailingZeros()} gives back "0".</li>
 *   <li><b>Formula cells are read at their CACHED value.</b> A BA may well use a formula to build
 *       a label; the import wants what the sheet shows, not the expression.</li>
 * </ul>
 */
@DomainDrivenDesign.InfrastructureService
public final class PoiWorkbookSource implements WorkbookSource {

    private final Workbook workbook;
    private final DataFormatter formatter = new DataFormatter();

    public PoiWorkbookSource(InputStream xlsx) throws IOException {
        this.workbook = new XSSFWorkbook(xlsx);
    }

    @Override
    public List<String> sheetNames() {
        List<String> names = new ArrayList<>(workbook.getNumberOfSheets());
        for (int i = 0; i < workbook.getNumberOfSheets(); i++) {
            names.add(workbook.getSheetName(i));
        }
        return List.copyOf(names);
    }

    @Override
    public int lastRow(String sheetName) {
        Sheet sheet = workbook.getSheet(sheetName);
        return sheet == null ? 0 : sheet.getLastRowNum() + 1;   // POI is 0-based, the BA is not
    }

    @Override
    public int lastColumn(String sheetName) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null) return 0;
        int max = 0;
        for (Row row : sheet) {
            max = Math.max(max, row.getLastCellNum());          // already exclusive, so 1-based
        }
        return Math.max(max, 0);
    }

    @Override
    public Optional<String> cell(String sheetName, int row, int column) {
        Sheet sheet = workbook.getSheet(sheetName);
        if (sheet == null || row < 1 || column < 1) return Optional.empty();
        Row poiRow = sheet.getRow(row - 1);
        if (poiRow == null) return Optional.empty();
        Cell poiCell = poiRow.getCell(column - 1, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (poiCell == null) return Optional.empty();

        String text = switch (poiCell.getCellType()) {
            case STRING -> poiCell.getStringCellValue();
            case NUMERIC -> numeric(poiCell);
            case BOOLEAN -> String.valueOf(poiCell.getBooleanCellValue());
            case FORMULA -> cachedFormulaValue(poiCell);
            default -> "";
        };
        text = text == null ? "" : text.trim();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    private String numeric(Cell cell) {
        if (DateUtil.isCellDateFormatted(cell)) {
            return formatter.formatCellValue(cell);
        }
        return BigDecimal.valueOf(cell.getNumericCellValue()).stripTrailingZeros().toPlainString();
    }

    private String cachedFormulaValue(Cell cell) {
        return switch (cell.getCachedFormulaResultType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> numeric(cell);
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            default -> "";
        };
    }

    @Override
    public void close() {
        try {
            workbook.close();
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not close the workbook", ex);
        }
    }
}
