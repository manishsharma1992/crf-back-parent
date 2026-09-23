package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

/**
 * The ECB financial table, behind the common calculator seam.
 *
 * <p><b>A wrapper, not a rewrite.</b> {@link FinancialCalculationDomainService} is untouched: the
 * five formulas, {@link com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.Ratio}'s
 * undefined handling and the 28-place scale all stay exactly as they were signed off. This class
 * only adapts shapes — {@link FinancialInputs} in, a field-keyed map out — so that adding FED to
 * the resolver cannot change an ECB number. If the seam turns out to be wrong, it is wrong against
 * a calculation that already has tests, rather than against APLC.
 *
 * <p><b>The blocking rule moves here with its meaning intact.</b> It used to live in
 * {@code FinancialTableResolver.blocked(...)}, which is now form-agnostic. Withholding rather than
 * omitting is the whole point: the figures ARE computed and handed to
 * {@code ValidationDomainService}, which is how {@code ECB_ADJUSTED_EBITDA_ZERO} fires on a box
 * the analyst never sees. Only publication is suppressed.
 */
@DomainDrivenDesign.DomainService
public final class EcbFinancialCalculator implements FinancialTableCalculator {

    /** Exactly the five keys the old overlay published, in the order it published them. */
    private static final Set<String> CALCULATED = Set.of(
            FinancialInputs.ADJUSTED_EBITDA,
            FinancialInputs.TOTAL_ECB_DEBT,
            FinancialInputs.TOTAL_NET_FUNDED_DEBT,
            FinancialInputs.ECB_LEVERAGE_RATIO,
            FinancialInputs.NET_FUNDED_LEVERAGE_RATIO);

    private final FinancialCalculationDomainService calculator;

    public EcbFinancialCalculator(FinancialCalculationDomainService calculator) {
        this.calculator = calculator;
    }

    /**
     * Recognised by {@code adjustedEbitda}, which is what
     * {@code FinancialTableResolver.financialQuestionKey} looked for — the key is the BA's to
     * choose and a renamed question must not silently switch the arithmetic off.
     */
    @Override
    public boolean supports(Question question) {
        return question != null && question.fields().stream()
                .filter(field -> field != null)
                .map(DataField::key)
                .anyMatch(FinancialInputs.ADJUSTED_EBITDA::equals);
    }

    @Override
    public Map<String, String> compute(Function<String, BigDecimal> inputs) {
        ComputedFinancials computed = calculator.compute(FinancialInputs.from(inputs::apply));

        Map<String, String> out = new LinkedHashMap<>();
        for (String key : orderedKeys()) {
            // valueOf already returns empty for an undefined ratio, so an undefined one
            // contributes NO entry rather than a blank — which is what lets
            // "range [0 .. <4]" match nothing and the cross-multiplied lines below it decide.
            computed.valueOf(key).ifPresent(value -> out.put(key, value.toPlainString()));
        }
        return out;
    }

    /**
     * ECB's three blocking conditions, unchanged: a base EBITDA that is absent or zero, a Gross
     * Debt that is absent or zero, and an Adjusted EBITDA that comes to zero however healthy the
     * base was. The last is the silent-failure case — five adjustments cancelling a good figure
     * out — which is why it is judged on the computed value rather than on the source.
     *
     * <p>An absent Net Debt is deliberately NOT blocking: it feeds only the net funded pair, no
     * routing reads them, and a form must not be refused over a figure nothing depends on.
     */
    @Override
    public Set<String> withheld(Function<String, BigDecimal> inputs, Map<String, String> computed) {
        boolean blocked = Amounts.isAbsentOrZero(inputs.apply(FinancialInputs.EBITDA))
                || Amounts.isAbsentOrZero(inputs.apply(FinancialInputs.GROSS_DEBT))
                || isZero(computed.get(FinancialInputs.ADJUSTED_EBITDA));

        return blocked ? CALCULATED : Set.of();
    }

    /** Absent is not zero. A missing Adjusted EBITDA is already blocked by its absent EBITDA. */
    private static boolean isZero(String value) {
        return value != null && new BigDecimal(value).signum() == 0;
    }

    private static String[] orderedKeys() {
        return new String[]{
                FinancialInputs.ADJUSTED_EBITDA,
                FinancialInputs.TOTAL_ECB_DEBT,
                FinancialInputs.TOTAL_NET_FUNDED_DEBT,
                FinancialInputs.ECB_LEVERAGE_RATIO,
                FinancialInputs.NET_FUNDED_LEVERAGE_RATIO};
    }
}

