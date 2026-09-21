package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

/**
 * The APLC arithmetic, tested as a pure function.
 *
 * <p>No Spring, no mocks of the resolver, no definition — the calculator takes a lookup function
 * and returns a map, so every case here is a table of numbers and an expected table of numbers.
 *
 * <p><b>Values are compared numerically, not as strings.</b> {@code 2.6} and {@code 2.60} are the
 * same ratio; a test that fails on trailing zeros tests {@code BigDecimal.toPlainString}, not the
 * formula.
 */
class AplcFinancialCalculatorTest {

    private final AplcFinancialCalculator calculator = new AplcFinancialCalculator();

    // ------------------------------------------------------------------ the happy path

    @Test
    @DisplayName("computes applicable funded debt, adjusted equity and the ratio")
    void computesTheThreeFigures() {
        Map<String, String> out = calculator.compute(inputs()
                .with(AplcFinancialCalculator.TOTAL_BS_DEBT, "1000")
                .with(AplcFinancialCalculator.NON_RECOURSE_DEBT, "200")
                .with(AplcFinancialCalculator.HIGHLY_SECURED_PORTION_GROSS_DEBT, "100")
                .with(AplcFinancialCalculator.QUALIFYING_FUNDED_SUBORDINATED_DEBT, "50")
                .with(AplcFinancialCalculator.BOOK_VALUE_OF_EQUITY, "300")
                .with(AplcFinancialCalculator.EQUITY_HELD_SPVS, "50"));

        assertNumber(out, AplcFinancialCalculator.APPLICABLE_FUNDED_DEBT, "650");
        assertNumber(out, AplcFinancialCalculator.ADJUSTED_BOOK_VALUE_OF_EQUITY, "250");
        assertNumber(out, AplcFinancialCalculator.DEBT_TO_EQUITY_RATIO, "2.6");
    }

    @Test
    @DisplayName("always publishes the three constants, even with no inputs at all")
    void publishesConstants() {
        Map<String, String> out = calculator.compute(inputs());

        // Mandatory and non-editable on the Fields tab: if the calculator did not fill them the
        // table could never be complete and no APLC analysis could ever finish.
        assertNumber(out, AplcFinancialCalculator.IMPLIED_DEBT_TO_EQUITY, "4");
        assertNumber(out, AplcFinancialCalculator.LEVERAGE_THRESHOLD, "5");
        assertNumber(out, AplcFinancialCalculator.ESCALATION_THRESHOLD, "6.5");
    }

    // ------------------------------------------------------------------ absence

    @Nested
    @DisplayName("absent inputs")
    class Absent {

        @Test
        @DisplayName("a missing base leaves the total absent rather than treating it as zero")
        void missingBaseIsAbsent() {
            Map<String, String> out = calculator.compute(inputs()
                    .with(AplcFinancialCalculator.NON_RECOURSE_DEBT, "200"));

            assertThat(out).doesNotContainKey(AplcFinancialCalculator.APPLICABLE_FUNDED_DEBT);
            assertThat(out).doesNotContainKey(AplcFinancialCalculator.ADJUSTED_BOOK_VALUE_OF_EQUITY);
            assertThat(out).doesNotContainKey(AplcFinancialCalculator.DEBT_TO_EQUITY_RATIO);
        }

        @Test
        @DisplayName("a missing subtrahend counts as zero")
        void missingSubtrahendIsZero() {
            Map<String, String> out = calculator.compute(inputs()
                    .with(AplcFinancialCalculator.TOTAL_BS_DEBT, "1000")
                    .with(AplcFinancialCalculator.BOOK_VALUE_OF_EQUITY, "300"));

            assertNumber(out, AplcFinancialCalculator.APPLICABLE_FUNDED_DEBT, "1000");
            assertNumber(out, AplcFinancialCalculator.ADJUSTED_BOOK_VALUE_OF_EQUITY, "300");
        }

        @Test
        @DisplayName("never throws, whatever it is given")
        void isTotal() {
            assertThat(calculator.compute(key -> null)).isNotEmpty();
            assertThat(calculator.compute(key -> new BigDecimal("-1"))).isNotEmpty();
        }
    }

    // ------------------------------------------------------------------ the ratio

    @Nested
    @DisplayName("debt to equity")
    class Ratio {

        @Test
        @DisplayName("zero adjusted equity leaves the ratio absent, and the other figures published")
        void zeroEquityWithholdsOnlyTheRatio() {
            Map<String, String> out = calculator.compute(inputs()
                    .with(AplcFinancialCalculator.TOTAL_BS_DEBT, "1000")
                    .with(AplcFinancialCalculator.NON_RECOURSE_DEBT, "200")
                    .with(AplcFinancialCalculator.HIGHLY_SECURED_PORTION_GROSS_DEBT, "100")
                    .with(AplcFinancialCalculator.QUALIFYING_FUNDED_SUBORDINATED_DEBT, "50")
                    .with(AplcFinancialCalculator.BOOK_VALUE_OF_EQUITY, "300")
                    .with(AplcFinancialCalculator.EQUITY_HELD_SPVS, "300"));

            // The analyst has to SEE the zero that blocked them, so the equity figure is published.
            assertNumber(out, AplcFinancialCalculator.ADJUSTED_BOOK_VALUE_OF_EQUITY, "0");
            assertNumber(out, AplcFinancialCalculator.APPLICABLE_FUNDED_DEBT, "650");
            // The ratio is not. The mandatory box stays unanswered, the walk stops at the table,
            // and FED_APLC_ADJ_BOOK_VALUE_EQUITY_ZERO explains why.
            assertThat(out).doesNotContainKey(AplcFinancialCalculator.DEBT_TO_EQUITY_RATIO);
        }

        @Test
        @DisplayName("a negative ratio is an ordinary outcome, not an error")
        void negativeRatioIsPublished() {
            Map<String, String> out = calculator.compute(inputs()
                    .with(AplcFinancialCalculator.TOTAL_BS_DEBT, "1000")
                    .with(AplcFinancialCalculator.NON_RECOURSE_DEBT, "200")
                    .with(AplcFinancialCalculator.HIGHLY_SECURED_PORTION_GROSS_DEBT, "100")
                    .with(AplcFinancialCalculator.QUALIFYING_FUNDED_SUBORDINATED_DEBT, "50")
                    .with(AplcFinancialCalculator.BOOK_VALUE_OF_EQUITY, "50")
                    .with(AplcFinancialCalculator.EQUITY_HELD_SPVS, "300"));

            // Q-APLC02 reads "range [<0]" on this value and classifies it as leveraged.
            assertNumber(out, AplcFinancialCalculator.ADJUSTED_BOOK_VALUE_OF_EQUITY, "-250");
            assertNumber(out, AplcFinancialCalculator.DEBT_TO_EQUITY_RATIO, "-2.6");
        }

        @Test
        @DisplayName("a non-terminating division is carried to 28 decimal places")
        void dividesAt28Places() {
            Map<String, String> out = calculator.compute(inputs()
                    .with(AplcFinancialCalculator.TOTAL_BS_DEBT, "100")
                    .with(AplcFinancialCalculator.BOOK_VALUE_OF_EQUITY, "3"));

            assertNumber(out, AplcFinancialCalculator.DEBT_TO_EQUITY_RATIO,
                    "33.3333333333333333333333333333");
        }

        @Test
        @DisplayName("is written in plain notation — this string lands in the JSONB column")
        void isPlainNotation() {
            Map<String, String> out = calculator.compute(inputs()
                    .with(AplcFinancialCalculator.TOTAL_BS_DEBT, "0.00000001")
                    .with(AplcFinancialCalculator.BOOK_VALUE_OF_EQUITY, "1000000000"));

            assertThat(out.get(AplcFinancialCalculator.DEBT_TO_EQUITY_RATIO))
                    .doesNotContainIgnoringCase("E");
        }
    }

    // ------------------------------------------------------------------ helpers

    private static void assertNumber(Map<String, String> out, String key, String expected) {
        assertThat(out).containsKey(key);
        assertThat(new BigDecimal(out.get(key)))
                .describedAs(key)
                .isEqualByComparingTo(new BigDecimal(expected));
    }

    private static Inputs inputs() {
        return new Inputs();
    }

    /** A lookup function built from a literal table, so each test reads as its own fixture. */
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

