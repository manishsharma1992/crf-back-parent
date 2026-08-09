/**
 * Panels on the screen, not only in the record. Merge into FormState and FormStateAssembler.
 *
 * <p>Until now InfoPanelResolver's output went only to responseAssembler, so panels were frozen for
 * export but never rendered — the analyst saw nothing while filling the form. The sheet is explicit
 * that a panel is "read-only blocks pulled from RMPM and SHOWN when a flag takes a given value", so
 * they have to reach FormState too.
 */

// ══════════════════════════════════════════ FormState — one more component

                        /** Panels triggered by the flags so far, already read and already decoded. */
                        List<PanelSnapshot> infoPanels,

    public FormState {
        visibleQuestions = visibleQuestions == null ? List.of() : List.copyOf(visibleQuestions);
        flags = flags == null ? Map.of() : Map.copyOf(flags);
        validationMessages = validationMessages == null ? List.of() : List.copyOf(validationMessages);
        infoPanels = infoPanels == null ? List.of() : List.copyOf(infoPanels);
    }

// ══════════════════════════════════════════ FormStateAssembler — one more argument

    public FormState assemble(DecisionTreeDefinition definition,
                              Map<String, String> answers,
                              TraversalResult result,
                              List<ValidationMessage> violations,
                              List<PanelSnapshot> panels,
                              String locale,
                              FormAudit audit) {

        if (result.state() == TraversalState.STRANDED) {
            throw new StrandedTraversalException(definition.formType(), definition.version());
        }
        Map<String, Question> byKey = index(definition);
        String currentKey = result.pendingQuestion().map(Question::key).orElse(null);

        List<QuestionView> views = new ArrayList<>();
        for (String key : result.path()) {
            Question question = byKey.get(key);
            if (question == null) {
                continue;   // defensive: a path key with no definition cannot survive validation
            }
            views.add(QuestionView.from(question, answers, result.computedAnswers(),
                    result.prefilledAnswers(), key.equals(currentKey)));
        }
        return state(definition, result, views, currentKey,
                localise(violations, locale), panels, audit);
    }

    private FormState state(DecisionTreeDefinition definition,
                            TraversalResult result,
                            List<QuestionView> views,
                            String currentKey,
                            List<ValidationMessageView> messages,
                            List<PanelSnapshot> panels,
                            FormAudit audit) {

        if (result.state() == TraversalState.TERMINAL) {
            return new FormState(definition.formType(), definition.version(), FormState.Status.COMPLETED,
                    views, null, result.flags(), outcomeView(definition, result),
                    messages, panels,
                    audit.lastModifiedTimestamp(), audit.validatedAt(), audit.validatedBy());
        }
        // Flags travel even mid-form, and so do the panels they trigger: the LBO flag is filled by
        // the very first question, and a panel keyed on it has to appear then rather than at the end.
        return new FormState(definition.formType(), definition.version(), FormState.Status.IN_PROGRESS,
                views, currentKey, result.flags(), null,
                messages, panels,
                audit.lastModifiedTimestamp(), audit.validatedAt(), audit.validatedBy());
    }
