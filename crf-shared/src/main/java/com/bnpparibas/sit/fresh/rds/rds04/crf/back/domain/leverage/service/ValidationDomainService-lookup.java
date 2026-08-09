/**
 * The Q-S06 rules. Add to ValidationDomainService.
 *
 * <p>The signature grows one argument. These three rules cannot be decided from answers alone — two
 * rmpmids sharing a business group is a fact about reference data — so the facts are resolved
 * upstream and handed in already reduced to booleans, which keeps this service pure.
 */

    public List<ValidationMessage> violations(DecisionTreeDefinition definition,
                                              Map<String, String> answers,
                                              TraversalResult result,
                                              EntityEligibility entity) {
        List<ValidationMessage> fired = new ArrayList<>();
        addMandatoryViolations(definition, answers, result, fired);
        addEntityViolations(definition, result, entity, fired);
        return List.copyOf(fired);
    }

    private static final String LOOKUP_QUESTION = "Q-S06";

    /**
     * Three rules on one question, mutually exclusive by construction.
     *
     * <p>Only evaluated when the walk actually reached Q-S06 — a rule about a question the analyst
     * has not been shown would be an error they cannot act on.
     */
    private void addEntityViolations(DecisionTreeDefinition definition, TraversalResult result,
                                     EntityEligibility entity, List<ValidationMessage> fired) {

        if (entity == null || !result.path().contains(LOOKUP_QUESTION)) {
            return;
        }

        // Choosing the analysed company itself: it cannot be its own parent.
        if (entity.answered() && entity.sameAsAnalysed()) {
            add(definition, ValidationRule.NOT_SELF, fired);
            return;
        }

        // Not chosen at all, or chosen from outside the business group. Both mean the same to the
        // analyst — the parent still has to be identified — and the sheet authors one message for
        // the rule rather than one per cause.
        if (!entity.answered() || !entity.inSameBusinessGroup()) {
            add(definition, ValidationRule.PARENT_ENTITY_ELIGIBLE, fired);
            return;
        }

        // Eligible, but not the parent RMPM already holds. INFO, not ERROR: the analyst may proceed,
        // and the message tells them to correct RMPM rather than refusing the save.
        if (entity.nameDiffersFromParent()) {
            add(definition, ValidationRule.PARENT_NAME_DIFFERS, fired);
        }
    }

    private void add(DecisionTreeDefinition definition, ValidationRule rule,
                     List<ValidationMessage> fired) {
        ValidationMessage message = questionScopedMessage(definition, rule, LOOKUP_QUESTION);
        if (message != null) {
            fired.add(message);
        }
    }

    /**
     * A row naming a question speaks for that question. Distinct from the form-wide lookup, which
     * requires BLANK keys — the Q-S06 rows carry a question key, so that one would never find them.
     */
    private ValidationMessage questionScopedMessage(DecisionTreeDefinition definition,
                                                    ValidationRule rule, String questionKey) {
        return definition.validationMessages().stream()
                .filter(message -> message.rule() == rule)
                .filter(message -> questionKey.equals(message.questionKey()))
                .filter(message -> isBlank(message.fieldKey()))
                .findFirst()
                .orElse(null);
    }
