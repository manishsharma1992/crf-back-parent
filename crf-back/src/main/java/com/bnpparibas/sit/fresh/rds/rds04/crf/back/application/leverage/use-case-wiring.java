/**
 * Step 2 — the four call sites that build a FormState, plus the Spring wiring.
 *
 * Each now computes violations before assembling, and supplies the audit stamps. Nothing else in
 * these classes changes.
 */

// ═══════════════════════════════════════════════════ GetFormStateUseCase  (stateless)

    private final ValidationDomainService validation;   // new collaborator

    @Transactional(readOnly = true)
    public FormState getFormState(GetFormStateRequest request) {
        DecisionTreeDefinition definition = request.version() == null
                ? resolver.resolveActive(LeverageFormType.valueOf(request.formType()))
                : resolver.resolvePinned(LeverageFormType.valueOf(request.formType()), request.version());

        FormAnswers answers = FormAnswers.of(definition, request.answers());
        TraversalResult result = traversal.resolve(definition, answers);

        // No analysis behind this call, so no audit stamps. The default locale is right here too:
        // this path serves PRELIMINARY, which has no validation rows authored against it, so the
        // message list is empty whichever language is asked for.
        return assembler.assemble(definition, request.answers(), result,
                validation.violations(definition, request.answers(), result),
                definition.defaultLocale(), FormAudit.NONE);
    }

// ═══════════════════════════════════════════════════ GetLeverageFormStateUseCase

    private final ValidationDomainService validation;   // new collaborator

    /** Reload: replays what is stored. Locale comes from the caller, defaulting to the form's. */
    @Transactional(readOnly = true)
    public FormState get(String analysisUid, LeverageFormType formType, String locale) {
        LeverageAnalysis analysis = analyses.findByAnalysisUid(analysisUid)
                .orElseThrow(() -> new AnalysisNotFoundException(analysisUid));

        DecisionTreeDefinition definition = definitionFor(analysis, formType);
        Map<String, String> settled = coercion.coerce(definition,
                SnapshotAnswers.flattenForReplay(analysis.responsesFor(formType)));

        return project(analysis, definition, formType, settled, locale);
    }

    /** Answer-as-you-type: answers come from the request, nothing is stored yet. */
    @Transactional(readOnly = true)
    public FormState resolve(String analysisUid, LeverageFormType formType,
                             Integer version, Map<String, String> answers, String locale) {
        LeverageAnalysis analysis = analyses.findByAnalysisUid(analysisUid)
                .orElseThrow(() -> new AnalysisNotFoundException(analysisUid));

        DecisionTreeDefinition definition = version == null
                ? definitionFor(analysis, formType)
                : resolver.resolvePinned(formType, version);

        return project(analysis, definition, formType, coercion.coerce(definition, answers), locale);
    }

    /**
     * Shared tail of both reads. Cross-form is taken from the aggregate either way — that is what
     * neither of these can borrow from the stateless use case.
     */
    private FormState project(LeverageAnalysis analysis, DecisionTreeDefinition definition,
                              LeverageFormType formType, Map<String, String> settled, String locale) {

        TraversalResult result = traversal.resolve(definition,
                FormAnswers.of(definition, settled, crossFormAnswers(analysis, formType)));

        return formStateAssembler.assemble(definition, settled, result,
                validation.violations(definition, settled, result),
                locale == null ? definition.defaultLocale() : locale,
                FormAudit.of(analysis));
    }

// ═══════════════════════════════════════════════════ SaveLeverageFormUseCase

    private final ValidationDomainService validation;   // new collaborator

        // ... unchanged up to and including analyses.save(analysis) ...

        analysis.recordSection(formType, snapshot);
        analyses.save(analysis);

        // Audit read AFTER the save: lastModifiedTimestamp has to describe the write that just
        // happened, not the one before it, or the screen shows a stamp older than the data.
        return formStateAssembler.assemble(definition, settled, result,
                validation.violations(definition, settled, result),
                locale, FormAudit.of(analysis));

// ═══════════════════════════════════════════════════ SavePreliminaryFormUseCase

    private final ValidationDomainService validation;   // new collaborator

        analysis.recordPreliminary(snapshot, outcome);
        analyses.save(analysis);

        return formStateAssembler.assemble(definition, request.answers(), result,
                validation.violations(definition, request.answers(), result),
                locale, FormAudit.of(analysis));

// ═══════════════════════════════════════════════════ Controller

    /** Locale is optional — the form's default is used when the client does not say. */
    @GetMapping("analyses/{uid}/forms/{formType}/state")
    public FormState formState(@PathVariable String uid,
                               @PathVariable LeverageFormType formType,
                               @RequestParam(required = false) String locale) {
        return getLeverageFormState.get(uid, formType, locale);
    }

    @PostMapping("analyses/{uid}/forms/{formType}/state")
    public FormState resolveFormState(@PathVariable String uid,
                                      @PathVariable LeverageFormType formType,
                                      @RequestBody StateRequest body) {
        Map<String, String> answers = body.answers() == null ? Map.of() : body.answers();
        return getLeverageFormState.resolve(uid, formType, body.version(), answers, body.locale());
    }

// ═══════════════════════════════════════════════════ Config
// ValidationDomainService carries @DomainDrivenDesign.DomainService, which is not a Spring
// stereotype, so it needs an explicit bean like the other domain services.

    @Bean
    public ValidationDomainService validationDomainService() {
        return new ValidationDomainService();
    }
