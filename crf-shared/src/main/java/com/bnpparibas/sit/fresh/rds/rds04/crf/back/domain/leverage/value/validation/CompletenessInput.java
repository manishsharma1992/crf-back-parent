package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.validation;

import java.util.List;

/**
 * Everything the completeness rule needs, assembled by the application layer.
 *
 * <p>One entry per applicable form, in display order (PRELIMINARY, ECB, FED). The
 * analysis is complete only when every applicable form is complete - BR01 gates a
 * single analysis-level Validate button, not one button per form.
 *
 * <p>Deliberately decoupled from the traversal types. Which fields are visible and
 * mandatory has already been decided by the tree definition and the walk, so
 * re-deriving it here would duplicate that logic. The domain service owns the
 * rule; the assembler owns the projection.
 */
public record CompletenessInput(List<FormCompletenessInput> forms) {

    public CompletenessInput {
        forms = List.copyOf(forms);
    }
}
