package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

/**
 * A problem found while READING the workbook — a missing table, an unparseable enum, a malformed
 * branch line.
 *
 * <p>Distinct from a domain {@code ValidationResult.Error}, and the distinction matters: an
 * import issue means "I could not build the object", a validation error means "I built it and the
 * wiring is wrong". Parse issues therefore carry their location directly, while validation errors
 * are located afterwards through the SourceLocator seam.
 */
public record ImportIssue(SourceLocation where, String code, String message) {

    public String describe() {
        return where.describe() + " — [" + code + "] " + message;
    }
}
