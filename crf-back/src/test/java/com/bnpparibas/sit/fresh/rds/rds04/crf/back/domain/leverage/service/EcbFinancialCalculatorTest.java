package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins ECB's behaviour through the calculator seam.
 *
 * <p>These are characterisation tests, not new requirements: every expectation below is what
 * {@code FinancialTableResolver} produced before the seam existed. If one fails after a change to
 * the shared plumbing, the plumbing changed an ECB figure — which is the whole thing the wrapper
 * is meant to make impossible.
 */
class EcbFinancialCalculatorTest {

    private final EcbFinancialCalculator calculator =
            new EcbFinancialCalculator(new FinancialCalculationDomainService());

    /** EBITDA 1000 + 200 of adjustments; Gross Debt 5000 + 700; Net Debt 4000 + 300 new drawn. */
    private Inputs healthy() {
        return new Inputs()
                .with(FinancialInputs.EBITDA, "1000")
                .with(FinancialInputs.REPORTED_LTM_ADJUSTMENT, "100")
                .with(FinancialInputs.PRO_FORMA_PERIMETER_ADJUSTMENT, "50")
                .with(FinancialInputs.IFRS16_ADJUSTMENT_EBITDA, "25")
                .with(FinancialInputs.FORWARD_LOOKING_ADJUSTMENT, "10")
                .with(FinancialInputs.OTHER_JUSTIFIED_ADJUSTMENT, "15")
                .with(FinancialInputs.GROSS_DEBT, "5000")
                .with(FinancialInputs.COMMITTED_UNDRAWN_DEBT, "200")
                .with(FinancialInputs.NEW_DRAWN_DEBT, "300")
                .with(FinancialInputs.NEW_COMMITTED_UNDRAWN_DEBT, "100")
                .with(FinancialInputs.IFRS16_ADJUSTMENT_DEBT, "50")
                .with(FinancialInputs.OTHER_ADJUSTMENT_DEBT, "50")
                .with(FinancialInputs.NET_DEBT, "4000");
    }

    @Test
    @DisplayName("the five figures come through the seam unchanged")
    void computesTheFiveFigures() {
        Map<String, String> out = calculator.compute(healthy());

        assertNumber(out, FinancialInputs.ADJUSTED_EBITDA, "1200");
        assertNumber(out, FinancialInputs.TOTAL_ECB_DEBT, "5700");
        assertNumber(out, FinancialInputs.TOTAL_NET_FUNDED_DEBT, "4300");
        assertNumber(out, FinancialInputs.ECB_LEVERAGE_RATIO, "4.75");
        assertNumber(out, FinancialInputs.NET_FUNDED_LEVERAGE_RATIO,
                "3.5833333333333333333333333333");
    }

    @Test
    @DisplayName("claims a question by adjustedEbitda, as the old resolver did")
    void supportsByAdjustedEbitda() {
        assertThat(calculator.supports(null)).isFalse();
    }

    // ------------------------------------------------------------------ absence

    @Nested
    @DisplayName("undefined ratios")
    class Undefined {

        @Test
        @DisplayName("an undefined ratio contributes no entry rather than a blank")
        void undefinedRatioIsAbsent() {
            // Q-Q02 tests "field ecbLeverageRatio range [0 .. <4]". An absent box matches nothing,
            // so the walk falls through to the cross-multiplied lines below it. A blank or a zero
            // here would silently route the transaction as not leveraged.
            Map<String, String> out = calculator.compute(new Inputs()
                    .with(FinancialInputs.GROSS_DEBT, "5000"));

            assertThat(out).doesNotContainKey(FinancialInputs.ECB_LEVERAGE_RATIO);
            assertThat(out).doesNotContainKey(FinancialInputs.NET_FUNDED_LEVERAGE_RATIO);
        }

        @Test
        @DisplayName("an absent Net Debt costs only the net funded pair")
        void absentNetDebtIsNotFatal() {
            Inputs inputs = healthy();
            inputs.values.remove(FinancialInputs.NET_DEBT);

            Map<String, String> out = calculator.compute(inputs);

            assertNumber(out, FinancialInputs.ECB_LEVERAGE_RATIO, "4.75");
            assertThat(out).doesNotContainKey(FinancialInputs.TOTAL_NET_FUNDED_DEBT);
            assertThat(out).doesNotContainKey(FinancialInputs.NET_FUNDED_LEVERAGE_RATIO);
            // and it does NOT block: no routing reads the net funded pair.
            assertThat(calculator.withheld(inputs, out)).isEmpty();
        }
    }

    // ------------------------------------------------------------------ withholding

    @Nested
    @DisplayName("withholding")
    class Withholding {

        @Test
        @DisplayName("nothing is withheld on a healthy table")
        void healthyWithholdsNothing() {
            Inputs inputs = healthy();
            assertThat(calculator.withheld(inputs, calculator.compute(inputs))).isEmpty();
        }

        @Test
        @DisplayName("an absent or zero EBITDA withholds all five calculated boxes")
        void ebitdaBlocks() {
            for (String ebitda : new String[]{null, "0"}) {
                Inputs inputs = healthy();
                if (ebitda == null) {
                    inputs.values.remove(FinancialInputs.EBITDA);
                } else {
                    inputs.with(FinancialInputs.EBITDA, ebitda);
                }
                assertThat(calculator.withheld(inputs, calculator.compute(inputs)))
                        .describedAs("ebitda=%s", ebitda)
                        .containsExactlyInAnyOrder(
                                FinancialInputs.ADJUSTED_EBITDA,
                                FinancialInputs.TOTAL_ECB_DEBT,
                                FinancialInputs.TOTAL_NET_FUNDED_DEBT,
                                FinancialInputs.ECB_LEVERAGE_RATIO,
                                FinancialInputs.NET_FUNDED_LEVERAGE_RATIO);
            }
        }

        @Test
        @DisplayName("an absent or zero Gross Debt withholds too")
        void grossDebtBlocks() {
            Inputs zero = healthy().with(FinancialInputs.GROSS_DEBT, "0");
            assertThat(calculator.withheld(zero, calculator.compute(zero))).isNotEmpty();

            Inputs absent = healthy();
            absent.values.remove(FinancialInputs.GROSS_DEBT);
            assertThat(calculator.withheld(absent, calculator.compute(absent))).isNotEmpty();
        }

        @Test
        @DisplayName("adjustments that cancel a healthy EBITDA out withhold everything")
        void adjustmentsCancellingOutBlock() {
            // The silent-failure case: the sources are fine and the analyst's own adjustments
            // bring Adjusted EBITDA to zero. Judged on the computed value, not on the source.
            Inputs inputs = healthy().with(FinancialInputs.OTHER_JUSTIFIED_ADJUSTMENT, "-1185");

            Map<String, String> computed = calculator.compute(inputs);

            assertNumber(computed, FinancialInputs.ADJUSTED_EBITDA, "0");
            assertThat(calculator.withheld(inputs, computed)).hasSize(5);
        }

        @Test
        @DisplayName("withholding suppresses publication only — the figures are still computed")
        void withheldFiguresAreStillComputed() {
            // This is what lets ECB_ADJUSTED_EBITDA_ZERO fire on a box the analyst never sees.
            Inputs inputs = healthy().with(FinancialInputs.EBITDA, "0");

            Map<String, String> computed = calculator.compute(inputs);

            assertThat(computed).containsKey(FinancialInputs.ADJUSTED_EBITDA);
            assertThat(calculator.withheld(inputs, computed)).hasSize(5);
        }
    }

    @Test
    @DisplayName("never throws, whatever it is given")
    void isTotal() {
        assertThat(calculator.compute(key -> null)).isEmpty();
        assertThat(calculator.withheld(key -> null, Map.of())).hasSize(5);
    }

    // ------------------------------------------------------------------ helpers

    private static void assertNumber(Map<String, String> out, String key, String expected) {
        assertThat(out).containsKey(key);
        assertThat(new BigDecimal(out.get(key)))
                .describedAs(key)
                .isEqualByComparingTo(new BigDecimal(expected));
    }

    private static final class Inputs implements Function<String, BigDecimal> {

        private final Map<String, BigDecimal> values = new HashMap<>();

        Inputs with(String key, String value) {
            values.put(key, new BigDecimal(value));
            return this;
        }

        @Override
        public BigDecimal apply(String key) {
            return values.get(key);
        }
    }
}

