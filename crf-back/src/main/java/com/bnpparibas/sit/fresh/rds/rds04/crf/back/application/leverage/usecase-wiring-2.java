import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.FormAnswers;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.FormAudit;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.FormState;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.AnalysisSubject;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service.InfoPanelSelector;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.EntityEligibility;

import java.util.Map;

/**
 * All four call sites. The reads now resolve panels too, which they did not before.
 */

// ══════════════════════════════════════════ GetLeverageFormStateUseCase

    private final InfoPanelSelector panelSelector;
    private final InfoPanelResolver infoPanels;

    private FormState project(LeverageAnalysis analysis, DecisionTreeDefinition definition,
                              LeverageFormType formType, Map<String, String> settled, String locale) {

        String language = locale == null ? definition.defaultLocale() : locale;
        AnalysisSubject subject = AnalysisSubject.of(analysis);

        FormAnswers answers = FormAnswers.of(definition, settled,
                crossFormAnswers(analysis, formType),
                derivedValues.resolve(derivedSources(definition), subject, language));

        TraversalResult result = traversal.resolve(definition, answers);

        EntityEligibility entity = entityEligibility.resolve(settled.get(LOOKUP_QUESTION), subject);

        // Panels are resolved on the READ path too now. Without this an analyst reloading a form
        // would lose the INR block until their next answer triggered a save.
        List<PanelSnapshot> panels = infoPanels.resolve(definition,
                panelSelector.triggeredBy(definition, result.flags()), subject, language);

        return formStateAssembler.assemble(definition, settled, result,
                validation.violations(definition, settled, result, entity),
                panels, language, FormAudit.of(analysis));
    }

// ══════════════════════════════════════════ SaveLeverageFormUseCase
// The panels are resolved ONCE and used twice — the snapshot freezes them, the state renders them.
// Resolving separately would let the record and the screen disagree if RMPM changed in between.

        List<PanelSnapshot> panels = infoPanels.resolve(definition,
                panelSelector.triggeredBy(definition, result.flags()), subject, locale);

        FormResponses snapshot = responseAssembler.assemble(definition, settled, result, locale, panels);

        analysis.recordSection(formType, snapshot);
        analyses.save(analysis);

        return formStateAssembler.assemble(definition, settled, result,
                validation.violations(definition, settled, result, entity),
                panels, locale, FormAudit.of(analysis));

// ══════════════════════════════════════════ GetFormStateUseCase  (stateless)
// No analysis, so no counterparty, so no panels — a preview of "what would this form look like"
// has no RMPM row to read from.

        return assembler.assemble(definition, request.answers(), result,
                validation.violations(definition, request.answers(), result, EntityEligibility.UNANSWERED),
                List.of(), definition.defaultLocale(), FormAudit.NONE);

// ══════════════════════════════════════════ SavePreliminaryFormUseCase
// Preliminary declares no panels, so InfoPanelResolver.none() output is what it already passes.

        return formStateAssembler.assemble(definition, request.answers(), result,
                validation.violations(definition, request.answers(), result, EntityEligibility.UNANSWERED),
                List.of(), locale, FormAudit.of(analysis));
