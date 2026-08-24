package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.validation;

import java.util.List;

/**
 * Everything the completeness rule needs, assembled by the application layer from
 * the traversal outcome.
 *
 * <p>Deliberately decoupled from the traversal types. Which fields are visible and
 * mandatory has already been decided by the tree definition and the walk, so
 * re-deriving it here would duplicate that logic. The domain service owns the
 * rule; the application-layer assembler owns the projection.
 *
 * @param traversalStatus            terminal / pending-input / awaiting-external
 * @param requiredFields             visible mandatory fields, in form order
 * @param blockingValidationMessages number of blocking messages on the form
 */
public record CompletenessInput(TraversalStatus traversalStatus,
                                List<RequiredField> requiredFields,
                                int blockingValidationMessages) {

    public CompletenessInput {
        requiredFields = List.copyOf(requiredFields);
    }
}
