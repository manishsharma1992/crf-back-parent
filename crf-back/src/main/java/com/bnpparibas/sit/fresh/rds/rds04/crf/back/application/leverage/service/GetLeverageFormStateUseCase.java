package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.*;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.AnalysisSubject;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.DerivedValueResolver;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.EntityEligibilityResolver;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.FinancialTableResolver;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.repository.LeverageAnalysisRepository;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service.DateAnswerNormaliser;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service.InfoPanelSelector;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.EntityEligibility;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Only project() changes, plus one new collaborator. Everything above it is untouched.
 */
@Service
@DomainDrivenDesign.ApplicationService
@RequiredArgsConstructor
public class GetLeverageFormStateUseCase {

    private static final String LOOKUP_QUESTION = "Q-S06";

    private final LeverageAnalysisRepository analyses;
    private final DecisionTreeResolver resolver;
    private final DecisionTreeTraversalService traversal;
    private final FormStateAssembler formStateAssembler;
    private final ChecklistCoercionDomainService coercion;
    private final DateAnswerNormaliser dateNormaliser;
    private final ValidationDomainService validation;
    private final DerivedValueResolver derivedValues;
    private final InfoPanelSelector panelSelector;
    private final InfoPanelResolver infoPanelResolver;
    private final EntityEligibilityResolver entityEligibility;
    private final FinancialTableResolver financialTable;   // new collaborator

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
        LeverageAnalysis analysis = load(analysisUid);

        DecisionTreeDefinition definition = version == null
                ? definitionFor(analysis, formType)
                : resolver.resolvePinned(formType, version);

        return project(analysis, definition, formType, coercion.coerce(definition, answers), locale);
    }

    /**
     * Shared tail of both reads.
     *
     * <p><b>Order matters.</b> Coercion settles the checklists, then the financial table is
     * resolved against those settled answers, then its figures are written OVER them. Everything
     * downstream — traversal, validation, the projection — reads the same merged map, so the
     * ratio the analyst sees is the ratio that routed.
     */
    private FormState project(LeverageAnalysis analysis, DecisionTreeDefinition definition,
                              LeverageFormType formType, Map<String, String> coerced, String locale) {

        String language = locale == null ? definition.defaultLocale() : locale;
        AnalysisSubject subject = AnalysisSubject.of(analysis);

        // Canonical ISO-8601 before anything reads a date. NOT rejected here, unlike the save path:
        // answer-as-you-type posts a half-finished form, and a malformed date simply matches nothing,
        // so the question reads unanswered and the walk stops there — which is the feedback the
        // analyst needs, without a 400 on every keystroke.
        Map<String, String> settled = dateNormaliser.normalise(definition, coerced).answers();

        // Resolved before traversal, because Q-Q01 and Q-Q02 compare boxes it fills in.
        FinancialTable financials = financialTable.resolve(definition, settled, subject);
        Map<String, String> resolved = financials.applyTo(settled);

        FormAnswers answers = FormAnswers.of(definition, resolved,
                crossFormAnswers(analysis, formType),
                derivedValues.resolve(derivedSources(definition), subject, language));

        TraversalResult result = traversal.resolve(definition, answers);

        EntityEligibility entity = entityEligibility(definition, resolved, subject);

        List<PanelSnapshot> panels = infoPanelResolver.resolve(definition,
                panelSelector.triggeredBy(definition, result.flags()), subject, language);

        return formStateAssembler.assemble(definition, resolved, result,
                validation.violations(definition, resolved, result, entity, financials.computed()),
                panels, language, FormAudit.of(analysis));
    }

    /**
     * BUG-02. The lookup question is the tree's, not a constant.
     *
     * <p>Was {@code entityEligibility.resolve(resolved.get("Q-S06"), subject)} — a literal key in
     * application code, evaluated against every form. On a tree with no such question it read null
     * from the map, resolved eligibility for nothing, and fed that result into the violations list,
     * where the three Q-S06 checks then ran against an entity that was never chosen.
     *
     * <p>Two guards now, and they are different things: no lookup question in this tree at all, and
     * a lookup question the analyst has not yet reached or answered. Both mean "there is nothing to
     * check", so both yield {@link EntityEligibility#notApplicable()} and the checks stay silent
     * rather than firing on an absent answer.
     */
    private EntityEligibility entityEligibility(DecisionTreeDefinition definition,
                                                Map<String, String> resolved,
                                                AnalysisSubject subject) {
        String answer = definition.lookupQuestion()
                .map(Question::key)
                .map(resolved::get)
                .orElse(null);

        return answer == null || answer.isBlank()
                ? EntityEligibility.notApplicable()
                : entityEligibility.resolve(answer, subject);
    }

    private DecisionTreeDefinition definitionFor(LeverageAnalysis analysis, LeverageFormType formType) {
        LeverageDecisionTreeDefinition pinned = analysis.decisionTreeFor(formType);
        return pinned == null
                ? resolver.resolveActive(formType)
                : resolver.resolvePinned(formType, pinned.getVersion());
    }

    /**
     * Answers given on the OTHER forms, for Prefill From. The loop skipped on {@code target},
     * which is not a variable in scope — the form being projected is {@code formType}, and without
     * that test a form would be offered its own answers as cross-form prefill.
     */
    private Map<String, String> crossFormAnswers(LeverageAnalysis analysis, LeverageFormType formType) {
        Map<String, String> crossForm = new LinkedHashMap<>();
        for (LeverageFormType source : LeverageFormType.values()) {
            if (source != formType) {
                crossForm.putAll(SnapshotAnswers.flattenForCrossForm(source, analysis.responsesFor(source)));
            }
        }
        return crossForm;
    }

    /**
     * Which external sources this tree needs resolving. CALC/ values are computed in the domain
     * layer and are not fetched, so they are excluded.
     *
     * <p>{@code derivedFrom} is null on every question that has no source — most of them — so the
     * null filter has to come before the prefix test or this throws on the first ordinary question.
     * ({@code startWith} was also a typo for {@code startsWith}.)
     */
    static Set<String> derivedSources(DecisionTreeDefinition definition) {
        return definition.questions().stream()
                .map(Question::derivedFrom)
                .filter(Objects::nonNull)
                .filter(source -> !source.startsWith("CALC/"))
                .collect(Collectors.toUnmodifiableSet());
    }

    private LeverageAnalysis load(String analysisUid) {
        return analyses.findByAnalysisUid(analysisUid)
                .orElseThrow(() -> new AnalysisNotFoundException(analysisUid));
    }
}
