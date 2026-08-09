/**
 * Both read paths and the save path resolve the facts before validating.
 *
 * <p>Q-S06's answer is the input to its own rules, so it is read from the settled answers rather
 * than from anywhere else.
 */

    private static final String LOOKUP_QUESTION = "Q-S06";

    // ... in project(...) / save(...), after the traversal:

    EntityEligibility entity = entityEligibility.resolve(settled.get(LOOKUP_QUESTION), subject);

    return formStateAssembler.assemble(definition, settled, result,
            validation.violations(definition, settled, result, entity),
            language, FormAudit.of(analysis));
