package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

/**
 * The REIT arithmetic, including the carve-out — the only branching calculation in either form.
 *
 * <p>The fixtures use round facility figures (100 + 200 + 50 + 30 + 20 = 400) so that every
 * expected value below can be checked by hand from the class javadoc.
 */
class ReitFinancialCalculatorTest {

    private final ReitFinancialCalculator calculator = new ReitFinancialCalculator();

    /** The five facilities and the two deductions, shared by most cases: 400 gross, 320 per def. */
    private Inputs baseTable() {
        return new Inputs()
                .with(ReitFinancialCalculator.COMMITTED_LOAN_FACILITY, "100")
                .with(ReitFinancialCalculator.COMMITTED_LC_FACILITY, "200")
                .with(ReitFinancialCalculator.FUNDED_DEBT_UNCOMMITTED_LOAN, "50")
                .with(ReitFinancialCalculator.OUTSTANDING_UNCOMMITTED_LC, "30")
                .with(ReitFinancialCalculator.OTHER_FINANCIAL_COMMITMENTS, "20")
                .with(ReitFinancialCalculator.NON_RECOURSE_DEBT, "50")
                .with(ReitFinancialCalculator.QUALIFYING_SUBORDINATED_DEBT, "30");
    }

    // ------------------------------------------------------------------ totals

    @Test
    @DisplayName("sums the five facilities and deducts the two non-qualifying amounts")
    void computesTheTotals() {
        Map<String, String> out = calculator.compute(baseTable());

        assertNumber(out, ReitFinancialCalculator.TOTAL_COMMITTED_DEBT, "400");
        assertNumber(out, ReitFinancialCalculator.TOTAL_COMMITTED_DEBT_PER_DEFINITION, "320");
    }

    @Test
    @DisplayName("always publishes the three thresholds as fractions")
    void publishesThresholds() {
        Map<String, String> out = calculator.compute(new Inputs());

        // Stored as fractions and rendered as percentages, so both sides of every threshold
        // comparison are on one scale.
        assertNumber(out, ReitFinancialCalculator.EXCLUSION_THRESHOLD, "0.60");
        assertNumber(out, ReitFinancialCalculator.COMMITTED_DEBT_YIELD_THRESHOLD, "0.08");
        assertNumber(out, ReitFinancialCalculator.DEBT_TO_MARKET_VALUE_THRESHOLD, "0.85");
    }

    // ------------------------------------------------------------------ the carve-out

    @Nested
    @DisplayName("carve out")
    class CarveOut {

        @Test
        @DisplayName("below 60% the carve-out is NO and the adjusted total is left whole")
        void belowThreshold() {
            Map<String, String> out = calculator.compute(baseTable()
                    .with(ReitFinancialCalculator.HIGHLY_SECURED_PORTION, "100"));

            assertNumber(out, ReitFinancialCalculator.PCT_HIGHLY_SECURED_PORTION, "0.3125");
            assertThat(out.get(ReitFinancialCalculator.CARVE_OUT))
                    .isEqualTo(ReitFinancialCalculator.CARVE_OUT_NO);
            assertNumber(out, ReitFinancialCalculator.ADJUSTED_TOTAL_COMMITTED_DEBT, "320");
        }

        @Test
        @DisplayName("above 60% the carve-out is YES and the highly secured portion is removed")
        void aboveThreshold() {
            Map<String, String> out = calculator.compute(baseTable()
                    .with(ReitFinancialCalculator.HIGHLY_SECURED_PORTION, "250"));

            assertNumber(out, ReitFinancialCalculator.PCT_HIGHLY_SECURED_PORTION, "0.78125");
            assertThat(out.get(ReitFinancialCalculator.CARVE_OUT))
                    .isEqualTo(ReitFinancialCalculator.CARVE_OUT_YES);
            assertNumber(out, ReitFinancialCalculator.ADJUSTED_TOTAL_COMMITTED_DEBT, "70");
        }

        @Test
        @DisplayName("exactly 60% carves out — the boundary is inclusive on the upper side")
        void atTheBoundary() {
            // "NO when below 60%, otherwise YES". This is the value a BA will use as a test case,
            // and the two readings of that sentence disagree on precisely this point.
            Map<String, String> out = calculator.compute(new Inputs()
                    .with(ReitFinancialCalculator.COMMITTED_LOAN_FACILITY, "1000")
                    .with(ReitFinancialCalculator.HIGHLY_SECURED_PORTION, "600"));

            assertNumber(out, ReitFinancialCalculator.PCT_HIGHLY_SECURED_PORTION, "0.6");
            assertThat(out.get(ReitFinancialCalculator.CARVE_OUT))
                    .isEqualTo(ReitFinancialCalculator.CARVE_OUT_YES);
            assertNumber(out, ReitFinancialCalculator.ADJUSTED_TOTAL_COMMITTED_DEBT, "400");
        }

        @ParameterizedTest(name = "{0} of 1000 -> {1}")
        @CsvSource({"599.99, NO", "600, YES", "600.01, YES", "0, NO", "1000, YES"})
        @DisplayName("either side of the boundary")
        void boundaryTable(String highlySecured, String expected) {
            Map<String, String> out = calculator.compute(new Inputs()
                    .with(ReitFinancialCalculator.COMMITTED_LOAN_FACILITY, "1000")
                    .with(ReitFinancialCalculator.HIGHLY_SECURED_PORTION, highlySecured));

            assertThat(out.get(ReitFinancialCalculator.CARVE_OUT)).isEqualTo(expected);
        }

        @Test
        @DisplayName("an uncomputable percentage leaves the carve-out absent, not NO")
        void absentPercentageIsNotNo() {
            // Total per definition comes to zero, so the percentage cannot be worked out.
            // Defaulting the carve-out to NO would publish an adjusted total derived from a
            // percentage we never calculated, indistinguishable from a real one.
            Map<String, String> out = calculator.compute(new Inputs()
                    .with(ReitFinancialCalculator.COMMITTED_LOAN_FACILITY, "100")
                    .with(ReitFinancialCalculator.NON_RECOURSE_DEBT, "100")
                    .with(ReitFinancialCalculator.HIGHLY_SECURED_PORTION, "50"));

            assertNumber(out, ReitFinancialCalculator.TOTAL_COMMITTED_DEBT_PER_DEFINITION, "0");
            assertThat(out).doesNotContainKey(ReitFinancialCalculator.PCT_HIGHLY_SECURED_PORTION);
            assertThat(out).doesNotContainKey(ReitFinancialCalculator.CARVE_OUT);
            assertThat(out).doesNotContainKey(ReitFinancialCalculator.ADJUSTED_TOTAL_COMMITTED_DEBT);
        }
    }

    // ------------------------------------------------------------------ the two ratios

    @Nested
    @DisplayName("ratios")
    class Ratios {

        @Test
        @DisplayName("debt yield and debt to market cap, carve-out NO")
        void ratiosWithoutCarveOut() {
            Map<String, String> out = calculator.compute(baseTable()
                    .with(ReitFinancialCalculator.HIGHLY_SECURED_PORTION, "100")
                    .with(ReitFinancialCalculator.NET_OPERATING_INCOME, "40")
                    .with(ReitFinancialCalculator.MARKET_CAPITALIZATION, "680"));

            assertNumber(out, ReitFinancialCalculator.COMMITTED_DEBT_YIELD, "0.125");
            assertNumber(out, ReitFinancialCalculator.DEBT_TO_MARKET_CAP, "0.32");
        }

        @Test
        @DisplayName("both ratios follow the carved-out total, not the gross one")
        void ratiosWithCarveOut() {
            Map<String, String> out = calculator.compute(baseTable()
                    .with(ReitFinancialCalculator.HIGHLY_SECURED_PORTION, "250")
                    .with(ReitFinancialCalculator.NET_OPERATING_INCOME, "40")
                    .with(ReitFinancialCalculator.MARKET_CAPITALIZATION, "680"));

            assertNumber(out, ReitFinancialCalculator.COMMITTED_DEBT_YIELD,
                    "0.5714285714285714285714285714");
            assertNumber(out, ReitFinancialCalculator.DEBT_TO_MARKET_CAP,
                    "0.0933333333333333333333333333");
        }

        @Test
        @DisplayName("a full carve-out drives the adjusted total to zero and withholds the yield")
        void fullCarveOutWithholdsYield() {
            // Every input box legitimately filled, and the yield's denominator is still zero —
            // which is why reitAdjustedTotalCommittedDebt wants a MUST_NOT_BE_ZERO row of its own.
            Map<String, String> out = calculator.compute(baseTable()
                    .with(ReitFinancialCalculator.HIGHLY_SECURED_PORTION, "320")
                    .with(ReitFinancialCalculator.NET_OPERATING_INCOME, "40")
                    .with(ReitFinancialCalculator.MARKET_CAPITALIZATION, "680"));

            assertNumber(out, ReitFinancialCalculator.ADJUSTED_TOTAL_COMMITTED_DEBT, "0");
            assertThat(out).doesNotContainKey(ReitFinancialCalculator.COMMITTED_DEBT_YIELD);
            assertNumber(out, ReitFinancialCalculator.DEBT_TO_MARKET_CAP, "0");
        }

        @Test
        @DisplayName("debt to market cap returns nothing silently on a zero denominator")
        void zeroDenominatorIsSilent() {
            // The one deliberate exception to the blocking rule, confirmed with Clara. Note the
            // consequence: Q-RT20 reads this value, an absent one makes its condition false, and
            // the classification comes out FED_NOT_LEVERAGED.
            Map<String, String> out = calculator.compute(baseTable()
                    .with(ReitFinancialCalculator.HIGHLY_SECURED_PORTION, "320")
                    .with(ReitFinancialCalculator.NET_OPERATING_INCOME, "40")
                    .with(ReitFinancialCalculator.MARKET_CAPITALIZATION, "0"));

            assertThat(out).doesNotContainKey(ReitFinancialCalculator.DEBT_TO_MARKET_CAP);
        }
    }

    // ------------------------------------------------------------------ totality

    @Test
    @DisplayName("never throws, whatever it is given")
    void isTotal() {
        assertThat(calculator.compute(key -> null)).isNotEmpty();
        assertThat(calculator.compute(key -> new BigDecimal("-1"))).isNotEmpty();
        assertThat(calculator.compute(key -> BigDecimal.ZERO)).isNotEmpty();
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
