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
 * Resolves the financial table for one request: reads FINSTAR, runs the arithmetic, and says which
 * figures may be published.
 *
 * <p>Application layer, because it navigates reference data and the definition; the arithmetic
 * itself is pure and lives in {@link FinancialCalculationDomainService}.
 *
 * <p><b>Called on both the read and the write path</b>, exactly as info panels are, so the screen
 * and the frozen record never disagree about what the ratio was.
 *
 * <p><b>The block is expressed as ABSENCE, not as a gate.</b> When EBITDA or Gross Debt is missing
 * or zero, or Adjusted EBITDA comes to zero, the calculated figures are withheld — so the
 * mandatory calculated boxes have no value, Q-F01 is not answered, and the walk stops there of its
 * own accord. Semantic 1 does the work. Traversal is never gated, which is what kept retracted
 * questions off the screen, and the analyst still gets a message because
 * {@code ValidationDomainService} judges the computed figures rather than the published ones.
 *
 * <p>That does mean the block relies on at least one CALCULATED box being mandatory on the Fields
 * tab. All three totals are, so a workbook would have to be actively edited to break it.
 */
@DomainDrivenDesign.ApplicationService
public class FinancialTableResolver {

    private final FinancialsResolver sources;
    private final FinancialCalculationDomainService calculator;

    public FinancialTableResolver(FinancialsResolver sources,
                                  FinancialCalculationDomainService calculator) {
        this.sources = sources;
        this.calculator = calculator;
    }

    /**
     * @param answers the settled answers, AFTER checklist coercion and BEFORE the overlay
     * @return {@link FinancialTable#NONE} when this form declares no financial table
     */
    public FinancialTable resolve(DecisionTreeDefinition definition,
                                  Map<String, String> answers,
                                  AnalysisSubject subject) {

        String questionKey = financialQuestionKey(definition);
        if (questionKey == null) {
            return FinancialTable.NONE;   // PRELIMINARY and FED declare no such boxes
        }

        FinancialInputs.Sources finstar = sources.resolve(subject);
        FinancialInputs inputs = FinancialInputs.from(key -> lookup(key, questionKey, finstar, answers));
        ComputedFinancials computed = calculator.compute(inputs);

        return new FinancialTable(computed, overlay(questionKey, finstar, computed, blocked(finstar, computed)));
    }

    /**
     * A source is read from FINSTAR; an adjustment is read from what the analyst posted.
     *
     * <p>Unparseable text reads as absent. The analyst still owes a justification for it — that
     * rule tests presence in the raw map — but the arithmetic will not guess at a number.
     */
    private BigDecimal lookup(String fieldKey, String questionKey,
                              FinancialInputs.Sources finstar, Map<String, String> answers) {
        return switch (fieldKey) {
            case FinancialInputs.EBITDA -> finstar.ebitda();
            case FinancialInputs.GROSS_DEBT -> finstar.grossDebt();
            case FinancialInputs.NET_DEBT -> finstar.netDebt();
            default -> number(answers.get(questionKey + '.' + fieldKey));
        };
    }

    /**
     * Whether the analysis must stop at the financial table.
     *
     * <p>Three conditions, all confirmed by the BA: a base EBITDA that is absent or zero, a Gross
     * Debt that is absent or zero, and an Adjusted EBITDA that comes to zero however healthy the
     * base was. The last is the silent-failure case — five adjustments cancelling a good figure
     * out — and it is the reason this is checked here rather than only on the source.
     *
     * <p>An absent Net Debt is deliberately NOT blocking: it only feeds the net funded pair, no
     * routing reads them, and a form should not be refused over a figure nothing depends on.
     */
    private boolean blocked(FinancialInputs.Sources finstar, ComputedFinancials computed) {
        return Amounts.isAbsentOrZero(finstar.ebitda())
                || Amounts.isAbsentOrZero(finstar.grossDebt())
                || Amounts.isAbsentOrZero(computed.adjustedEbitda());
    }

    /**
     * The answer entries the resolved figures occupy.
     *
     * <p>The sources are always published — the analyst must see the EBITDA that blocked them.
     * The calculated boxes are published only when nothing blocks, which is what leaves Q-F01
     * unanswered and stops the walk.
     *
     * <p>An undefined ratio contributes NO entry rather than a blank one, so
     * {@code range [0 .. <4]} matches nothing and the cross-multiplied lines below it decide.
     */
    private Map<String, String> overlay(String questionKey, FinancialInputs.Sources finstar,
                                        ComputedFinancials computed, boolean blocked) {
        Map<String, String> overlay = new LinkedHashMap<>();
        put(overlay, questionKey, FinancialInputs.EBITDA, finstar.ebitda());
        put(overlay, questionKey, FinancialInputs.GROSS_DEBT, finstar.grossDebt());
        put(overlay, questionKey, FinancialInputs.NET_DEBT, finstar.netDebt());

        if (blocked) {
            return overlay;
        }
        put(overlay, questionKey, FinancialInputs.ADJUSTED_EBITDA, computed.adjustedEbitda());
        put(overlay, questionKey, FinancialInputs.TOTAL_ECB_DEBT, computed.totalEcbDebt());
        put(overlay, questionKey, FinancialInputs.TOTAL_NET_FUNDED_DEBT, computed.totalNetFundedDebt());
        put(overlay, questionKey, FinancialInputs.ECB_LEVERAGE_RATIO,
                computed.ecbLeverageRatio().valueOrNull());
        put(overlay, questionKey, FinancialInputs.NET_FUNDED_LEVERAGE_RATIO,
                computed.netFundedLeverageRatio().valueOrNull());
        return overlay;
    }

    /** Plain notation, never scientific: this string is what lands in the JSONB column. */
    private void put(Map<String, String> overlay, String questionKey, String fieldKey, BigDecimal value) {
        if (value != null) {
            overlay.put(questionKey + '.' + fieldKey, value.toPlainString());
        }
    }

    /**
     * The question owning the financial boxes, found by looking for the calculated ones rather
     * than by hard-coding {@code Q-F01} — the key is the BA's to choose, and a renamed question
     * should not silently switch the arithmetic off.
     */
    private String financialQuestionKey(DecisionTreeDefinition definition) {
        for (Question question : definition.questions()) {
            for (DataField field : question.fields()) {
                if (field != null && FinancialInputs.ADJUSTED_EBITDA.equals(field.key())) {
                    return question.key();
                }
            }
        }
        return null;
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

    /** Convenience for callers that have no subject to resolve against. */
    public static Optional<ComputedFinancials> computedOf(FinancialTable table) {
        return Optional.ofNullable(table).map(FinancialTable::computed);
    }
}
