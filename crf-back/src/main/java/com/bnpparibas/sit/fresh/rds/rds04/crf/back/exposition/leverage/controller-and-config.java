// ══════════════════════════════════════════ CONTROLLER  (LeverageAnalysisController)

    private final GetLookupOptionsUseCase getLookupOptions;

    /**
     * Choices for one LOOKUP question.
     *
     * <p>Its own endpoint rather than a field on FormState: ten million counterparties cannot ride
     * along with a form that re-renders on every answer, and the search term changes far more often
     * than the walk does.
     *
     * <p>Returns an empty list below three characters, so the client may call freely while the
     * analyst types without special-casing short input.
     */
    @GetMapping("analyses/{uid}/forms/{formType}/lookups/{questionKey}")
    public List<LookupOption> lookupOptions(@PathVariable String uid,
                                            @PathVariable LeverageFormType formType,
                                            @PathVariable String questionKey,
                                            @RequestParam(required = false) String query,
                                            @RequestParam(required = false) String locale) {
        return getLookupOptions.get(uid, formType, questionKey, query, locale);
    }

// ══════════════════════════════════════════ CONFIG
//
// No fallback bean. LookupOptionsResolverImpl is a @Component, so a none() bean declared beside it
// would collide — and worse, if it ever won, Q-S06 would offer nothing with no error anywhere.
// LookupOptionsResolver.none() stays for tests.
