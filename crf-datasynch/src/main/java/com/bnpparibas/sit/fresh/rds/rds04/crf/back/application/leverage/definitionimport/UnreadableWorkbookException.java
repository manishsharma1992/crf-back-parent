package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

/**
 * The upload is not a workbook this importer can open — wrong format, truncated, or encrypted.
 *
 * <p>Distinct from every other failure in the import. A workbook with a bad CELL is an
 * {@code ImportIssue} and comes back as a report the BA can act on; a file that is not a workbook
 * at all has no cells to report on, so it is an exception and becomes a 400 rather than a 422.
 */
public class UnreadableWorkbookException extends RuntimeException {

    public UnreadableWorkbookException(String message, Throwable cause) {
        super(message, cause);
    }
}
