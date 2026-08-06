package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto;

import java.time.Instant;

/**
 * When this analysis was last written and, if it has been, when it was validated.
 *
 * <p>Passed into the assembler rather than read there: the assembler is a projection over a
 * definition and a traversal, and reaching for the aggregate would make it depend on persistence.
 *
 * <p>{@link #NONE} is for the stateless path, where there is no analysis row to read from — that
 * call answers "what would this form look like with these answers", which has no history.
 */
public record FormAudit(Instant lastModifiedTimestamp, Instant validatedAt, String validatedBy) {

    public static final FormAudit NONE = new FormAudit(null, null, null);

    public static FormAudit of(LeverageAnalysis analysis) {
        return new FormAudit(analysis.getLastModifiedDate(),      // TODO confirm BaseEntity accessor
                analysis.getValidatedTimestamp(), analysis.getValidatedBy());
    }
}
