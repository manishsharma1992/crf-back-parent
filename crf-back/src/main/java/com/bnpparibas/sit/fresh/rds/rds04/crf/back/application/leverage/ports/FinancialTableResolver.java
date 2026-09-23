package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.FinancialTable;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service.FinancialCalculationDomainService;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.Amounts;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.ComputedFinancials;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.FinancialInputs;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DataField;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeDefinition;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.Question;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves the financial tables for one request: reads the prefilled figures, runs the arithmetic,
 * and says which results may be published.
 *
 * <p>Application layer, because it navigates reference data and the definition. The arithmetic is
 * pure and lives behind {@link FinancialTableCalculator}, one implementation per form.
 *
 * <p><b>Called on both the read and the write path</b>, exactly as info panels are, so the screen
 * and the frozen record never disagree about what a ratio was.
 *
 * <p><b>SEVERAL tables, not one.</b> The old version found "the" financial question by hunting for
 * a field called {@code adjustedEbitda}. FED declares two — {@code Q-F-APLC} and
 * {@code Q-F-REIT} — on mutually exclusive Main Industry paths, so every declared table is
 * resolved and the results merged. That is safe precisely because field keys are unique per path
 * ({@code aplc…}, {@code reit…}): two tables can never write the same answer key, so a flat merge
 * needs no table qualification anywhere in the condition language. The table the analyst did not
 * take contributes its constants and nothing else, and its question is not on the walk's path, so
 * it is neither rendered nor frozen.
 *
 * <p><b>The block is expressed as ABSENCE, not as a gate.</b> A figure a form refuses to stand
 * behind is withheld from the overlay, so the mandatory calculated box has no value, the question
 * is not answered, and the walk stops there of its own accord. Traversal is never gated, which is
 * what keeps questions the answers never reached off the screen. The analyst still gets a message,
 * because {@code ValidationDomainService} judges {@link FinancialTable#computed()} — which stays
 * COMPLETE — rather than the published overlay.
 *
 * <p>That does mean the block relies on at least one calculated box being mandatory on the Fields
 * tab. Every form's totals are, so a workbook would have to be actively edited to break it.
 */
@DomainDrivenDesign.ApplicationService
public class FinancialTableResolver {

    private final List<FinancialTableSupport> supports;

    public FinancialTableResolver(List<FinancialTableSupport> supports) {
        this.supports = List.copyOf(supports);
    }

    /**
     * @param answers the settled answers, AFTER checklist coercion and date normalisation, and
     *                BEFORE the overlay
     * @return {@link FinancialTable#NONE} when this form declares no financial table at all —
     *         PRELIMINARY, and a FED definition cut down to Project Finance
     */
    public FinancialTable resolve(DecisionTreeDefinition definition,
                                  Map<String, String> answers,
                                  AnalysisSubject subject) {

        Map<String, String> computed = new LinkedHashMap<>();
        Map<String, String> overlay = new LinkedHashMap<>();

        for (FinancialTableSupport support : supports) {
            for (Question question : definition.questions()) {
                if (support.calculator().supports(question)) {
                    resolveOne(support, question, answers, subject, computed, overlay);
                }
            }
        }
        return computed.isEmpty() && overlay.isEmpty()
                ? FinancialTable.NONE
                : new FinancialTable(computed, overlay);
    }

    private void resolveOne(FinancialTableSupport support,
                            Question question,
                            Map<String, String> answers,
                            AnalysisSubject subject,
                            Map<String, String> computed,
                            Map<String, String> overlay) {

        Function<String, BigDecimal> prefills = support.prefills(subject);
        Function<String, BigDecimal> inputs = inputs(question, answers, prefills);

        FinancialTableCalculator calculator = support.calculator();
        Map<String, String> figures = calculator.compute(inputs);
        Set<String> withheld = calculator.withheld(inputs, figures);

        computed.putAll(figures);

        // The prefilled boxes are published whatever happens — the analyst has to SEE the EBITDA
        // that blocked them. Publishing the RESOLVED value rather than the raw prefill is what
        // stops an editable source overwriting what the analyst typed over it.
        for (DataField field : question.fields()) {
            if (field != null && field.derivedFrom() != null && !field.isCalculated()) {
                put(overlay, question.key(), field.key(), inputs.apply(field.key()));
            }
        }
        figures.forEach((key, value) -> {
            if (!withheld.contains(key)) {
                overlay.put(question.key() + '.' + key, value);
            }
        });
    }

    /**
     * What each box holds, for the arithmetic.
     *
     * <p><b>A prefill wins unless the analyst may overwrite it and has.</b> One rule, and it gives
     * each form the behaviour its Fields tab already asks for: ECB's three sources are
     * {@code Editable = No}, so FINSTAR always wins and nothing about ECB changes; FED's
     * {@code aplcTotalBSDebt} and {@code aplcBookValueOfEquity} are {@code Editable = Yes} and
     * prefilled, so a typed figure wins. No form is named here.
     *
     * <p>Unparseable text reads as absent. The analyst still owes a justification for it — that
     * rule tests presence in the raw map — but the arithmetic will not guess at a number.
     */
    private Function<String, BigDecimal> inputs(Question question,
                                                Map<String, String> answers,
                                                Function<String, BigDecimal> prefills) {
        Map<String, DataField> byKey = new LinkedHashMap<>();
        question.fields().stream()
                .filter(field -> field != null)
                .forEach(field -> byKey.put(field.key(), field));

        return fieldKey -> {
            DataField field = byKey.get(fieldKey);
            BigDecimal typed = number(answers.get(question.key() + '.' + fieldKey));

            if (field == null) {
                return typed;   // a key the calculator knows and this table does not declare
            }
            if (field.derivedFrom() == null) {
                return typed;   // the analyst's own box
            }
            if (field.isAnalystInput() && typed != null) {
                return typed;   // prefilled, editable, and overwritten
            }
            return prefills.apply(field.derivedFrom());
        };
    }

    /** Plain notation, never scientific: this string is what lands in the JSONB column. */
    private void put(Map<String, String> overlay, String questionKey, String fieldKey, BigDecimal value) {
        if (value != null) {
            overlay.put(questionKey + '.' + fieldKey, value.toPlainString());
        }
    }

    private static BigDecimal number(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
