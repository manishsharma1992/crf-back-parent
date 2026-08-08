package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

/**
 * Searches the choices for one LOOKUP question.
 *
 * <p>Separate from the form state on purpose: the counterparty table holds ten million rows, the
 * list changes independently of the walk, and riding along with the state would re-send it on every
 * answer.
 */
@Service
@DomainDrivenDesign.ApplicationService
@RequiredArgsConstructor
public class GetLookupOptionsUseCase {

    /**
     * Below this, a fuzzy search over ten million rows scans rather than uses the trigram index,
     * and returns noise the analyst cannot use anyway.
     *
     * <p>Enforced here rather than trusted to the client: the endpoint is reachable without the UI,
     * and one careless caller typing a single character would be a table scan per keystroke.
     */
    private static final int MINIMUM_QUERY_LENGTH = 3;

    private final LeverageAnalysisRepository analyses;
    private final DecisionTreeResolver resolver;
    private final LookupOptionsResolver lookups;

    @Transactional(readOnly = true)
    public List<LookupOption> get(String analysisUid, LeverageFormType formType,
                                  String questionKey, String query, String locale) {

        String term = query == null ? "" : query.trim();
        if (term.length() < MINIMUM_QUERY_LENGTH) {
            return List.of();   // the autocomplete shows nothing until it is worth searching for
        }

        LeverageAnalysis analysis = analyses.findByAnalysisUid(analysisUid)
                .orElseThrow(() -> new AnalysisNotFoundException(analysisUid));

        DecisionTreeDefinition definition = definitionFor(analysis, formType);
        Question question = questionOf(definition, questionKey);

        // The source is read from the DEFINITION, never from the request. A client naming its own
        // source could otherwise reach reference data this form was never authored to expose.
        return lookups.resolve(sourceOf(question), AnalysisSubject.of(analysis), term,
                locale == null ? definition.defaultLocale() : locale);
    }

    private DecisionTreeDefinition definitionFor(LeverageAnalysis analysis, LeverageFormType formType) {
        LeverageDecisionTreeDefinition pinned = analysis.decisionTreeFor(formType);
        return pinned == null
                ? resolver.resolveActive(formType)
                : resolver.resolvePinned(formType, pinned.getVersion());
    }

    private Question questionOf(DecisionTreeDefinition definition, String questionKey) {
        return definition.questions().stream()
                .filter(candidate -> candidate.key().equals(questionKey))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "no question " + questionKey + " on " + definition.formType()));
    }

    /**
     * A LOOKUP authors its source in the OPTIONS column rather than listing values there, so the
     * option's value IS the source name.
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
}
