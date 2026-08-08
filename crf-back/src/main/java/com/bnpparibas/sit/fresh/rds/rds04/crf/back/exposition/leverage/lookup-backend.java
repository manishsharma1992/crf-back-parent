/**
 * The LOOKUP piece. Q-S06 authors its source in the OPTIONS column as {@code LOOKUP/COUNTERPARTY},
 * so the list is not in the definition — only the name of where to get it.
 *
 * <p>Kept off the form-state call on purpose: a counterparty list is large, changes independently
 * of the walk, and would be re-sent on every keystroke if it rode along with the state.
 */

// ══════════════════════════════════════════ PORT  (crf-shared, beside DerivedValueResolver)

package com.bnpparibas.sit.fresh.rds.rds04.crf.back.exposition.leverage;

public interface LookupOptionsResolver {

    /**
     * @param source         the authored source, e.g. {@code LOOKUP/COUNTERPARTY}
     * @param counterpartyId the counterparty under analysis, which scopes what is offerable
     * @param query          what the analyst has typed, or null for the initial list
     * @param locale         the language the labels are rendered in
     * @return value/label pairs, already rendered and already ordered for display
     */
    List<LookupOption> resolve(String source, String counterpartyId, String query, String locale);

    /** For forms that look nothing up, and for tests that do not care. */
    static LookupOptionsResolver none() {
        return (source, counterpartyId, query, locale) -> List.of();
    }
}

/** @param value what is stored and routed on; @param label what the analyst reads */
public record LookupOption(String value, String label) {}

// ══════════════════════════════════════════ USE CASE

@Service
@DomainDrivenDesign.ApplicationService
@RequiredArgsConstructor
public class GetLookupOptionsUseCase {

    private final LeverageAnalysisRepository analyses;
    private final DecisionTreeResolver resolver;
    private final LookupOptionsResolver lookups;

    @Transactional(readOnly = true)
    public List<LookupOption> get(String analysisUid, LeverageFormType formType,
                                  String questionKey, String query, String locale) {

        LeverageAnalysis analysis = analyses.findByAnalysisUid(analysisUid)
                .orElseThrow(() -> new AnalysisNotFoundException(analysisUid));

        LeverageDecisionTreeDefinition pinned = analysis.decisionTreeFor(formType);
        DecisionTreeDefinition definition = pinned == null
                ? resolver.resolveActive(formType)
                : resolver.resolvePinned(formType, pinned.getVersion());

        Question question = definition.questions().stream()
                .filter(candidate -> candidate.key().equals(questionKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no such question: " + questionKey));

        // The source is read from the definition, never from the request: a client naming its own
        // source could reach reference data this form was never authored to expose.
        return lookups.resolve(sourceOf(question), counterpartyIdOf(analysis), query,
                locale == null ? definition.defaultLocale() : locale);
    }

    /**
     * A LOOKUP authors its source in the options column rather than listing values there, so the
     * single option's value IS the source name.
     */
    private String sourceOf(Question question) {
        if (question.type() != QuestionType.LOOKUP) {
            throw new IllegalArgumentException(question.key() + " is not a LOOKUP");
        }
        return question.options().stream()
                .map(Option::value)
                .filter(value -> value != null && value.startsWith("LOOKUP/"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        question.key() + " declares no LOOKUP/ source"));
    }

    private String counterpartyIdOf(LeverageAnalysis analysis) {
        return analysis.getFinancials().getCounterparty().getId();   // TODO confirm accessors
    }
}

// ══════════════════════════════════════════ CONTROLLER

    /** Separate from the form state so a large list is fetched once, not on every keystroke. */
    @GetMapping("analyses/{uid}/forms/{formType}/lookups/{questionKey}")
    public List<LookupOption> lookupOptions(@PathVariable String uid,
                                            @PathVariable LeverageFormType formType,
                                            @PathVariable String questionKey,
                                            @RequestParam(required = false) String query,
                                            @RequestParam(required = false) String locale) {
        return getLookupOptions.get(uid, formType, questionKey, query, locale);
    }

// ══════════════════════════════════════════ CONFIG

    @Bean
    @ConditionalOnMissingBean(LookupOptionsResolver.class)
    public LookupOptionsResolver lookupOptionsResolver() {
        return LookupOptionsResolver.none();   // Q-S06 offers nothing until the adapter lands
    }
