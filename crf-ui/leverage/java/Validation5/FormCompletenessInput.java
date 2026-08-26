package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.List;

/**
 * Completeness facts for ONE form, reduced to the two things that actually decide it.
 *
 * <p>There is no list of required fields here. The traversal already refuses to
 * reach TERMINAL while a mandatory box is empty - see
 * DecisionTreeTraversalService#dataEntryAnswer - so TERMINAL IS the statement
 * "every mandatory answer on the walked path is present". Re-deriving that list
 * would be a second implementation of a rule the engine already owns, and the two
 * would drift the first time the DSL gained a field type.
 *
 * @param formType             which form these facts describe
 * @param traversalState       where this form's walk stopped
 * @param blockingMessageCodes codes of ERROR-severity violations on this form, in
 *                             message order so the UI can jump to the first
 */
@DomainDrivenDesign.ValueObject
public record FormCompletenessInput(LeverageFormType formType,
                                    TraversalState traversalState,
                                    List<String> blockingMessageCodes) {

    public FormCompletenessInput {
        blockingMessageCodes = List.copyOf(blockingMessageCodes);
    }
}
