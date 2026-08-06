package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * When this analysis was last written and, if it has been, when it was validated.
 *
 * <p>Passed into the assembler rather than read there: the assembler is a projection over a
 * definition and a traversal, and reaching for the aggregate would make it depend on persistence.
 *
 * <p>{@link #NONE} is for the stateless path, where there is no analysis row behind the call — it
 * answers "what would this form look like with these answers", which has no history.
 */
public record FormAudit(Instant lastModifiedTimestamp, Instant validatedAt, String validatedBy) {

    public static final FormAudit NONE = new FormAudit(null, null, null);

    public static FormAudit of(LeverageAnalysis analysis) {
        return new FormAudit(
                toInstant(analysis.getModifiedTimestamp()),
                analysis.getValidatedTimestamp(),      // already an Instant on the entity
                analysis.getValidatedBy());
    }

    /**
     * BaseEntity stamps a LocalDateTime, which carries no zone, so the server's zone is the only
     * thing available to anchor it.
     *
     * <p>Worth knowing this is lossy: an analysis written under a different server zone, or during
     * the hour a DST change repeats, resolves to the wrong instant. Harmless for a "last saved"
     * caption; if that stamp ever becomes evidence, the column wants to be timestamptz and the
     * field an Instant, as validated_timestamp already is.
     */
    private static Instant toInstant(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
