package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.ValidationResult;

import java.util.Optional;

/**
 * Bridges a PURE domain {@link ValidationResult.Error} back to a physical place in the source
 * artifact.
 *
 * <p>The seam exists so the validator can stay ignorant of Excel. A future authoring UI supplies a
 * different implementation — or none, in which case
 * {@link ValidationReportAssembler} falls back to the logical location and the message is still
 * useful, just less precise.
 */
public interface SourceLocator {

    Optional<SourceLocation> locate(ValidationResult.Error error);

    /** For sources with no physical coordinates. */
    static SourceLocator none() {
        return error -> Optional.empty();
    }
}
