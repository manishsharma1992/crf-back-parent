package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The arithmetic of the two General Obligor / Utilities tables, with no definition and no walk.
 *
 * <p>The walk test proves the tables are wired; these prove what they compute — including the one
 * case the walk cannot reach reliably, an adjustment with no Accounting EBITDA, because the walk's
 * EBITDA may come from the FINSTAR stub.
 */
class GoCalculatorsTest {

    private static Function<String, BigDecimal> boxes(String... keyValues) {
        Map<String, BigDecimal> map = new HashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], new BigDecimal(keyValues[i + 1]));
        }
        return map::get;
    }

    private static void assertFigure(Map<String, String> computed, String key, String expected) {
        assertThat(computed).containsKey(key);
        assertThat(new BigDecimal(computed.get(key))).as(key).isEqualByComparingTo(expected);
    }

    @Nested
    class Table1 {

        private final GoCommittedDebtCalculator calculator = new GoCommittedDebtCalculator();
        private final Function<String, String> generalObligor = key -> "Q-MI01".equals(key) ? "GENERAL_OBLIGOR" : null;

        @Test
        @DisplayName("ratio = (facilities - non recourse - qualifying subordinated) / (EBITDA + adjustments)")
        void ratio() {
            Map<String, String> computed = calculator.compute(boxes(
                    "goCeAccountingEbitda", "80", "goCeAdjustment1", "15", "goCeAdjustment8", "5",
                    "goCeCommittedLoanFacility", "400", "goCeOtherFinancialCommitments", "150",
                    "goCeNonRecourseDebt", "30", "goCeQualifyingSubordinatedDebt", "20"), generalObligor);

            assertFigure(computed, "goCeAdjustedEbitda", "100");
            assertFigure(computed, "goCeTotalCommittedDebt", "550");
            assertFigure(computed, "goCeTotalCommittedDebtPerDefinition", "500");
            assertFigure(computed, "goCeFedLeverageRatio", "5");
        }

        @Test
        @DisplayName("the ratio is stored at the two decimals the screen shows, rounded half up (Clara, 16/10)")
        void ratioAtTwoDecimals() {
            assertThat(ratio("3", "2")).isEqualTo("0.67");                 // 0.666...
            assertThat(ratio("1000", "3995")).isEqualTo("4");               // 3.995 -> 4.00
            assertThat(ratio("1000", "5")).isEqualTo("0.01");               // 0.005 -> 0.01
            assertThat(ratio("3929964300335214", "4485")).isEqualTo("0");   // ~1.1e-12 -> 0.00
        }

        private String ratio(String ebitda, String facility) {
            return calculator.compute(boxes("goCeAccountingEbitda", ebitda, "goCeCommittedLoanFacility", facility),
                    generalObligor).get("goCeFedLeverageRatio");
        }

        @Test
        @DisplayName("an adjustment with no Accounting EBITDA is NOT an Adjusted EBITDA (plus, not sum)")
        void adjustmentWithoutBase() {
            Map<String, String> computed = calculator.compute(boxes(
                    "goCeAdjustment1", "20", "goCeCommittedLoanFacility", "500"), generalObligor);

            assertThat(computed).doesNotContainKeys("goCeAdjustedEbitda", "goCeFedLeverageRatio");
        }

        @Test
        @DisplayName("a zero Adjusted EBITDA is published, the ratio is not")
        void zeroEbitda() {
            Map<String, String> computed = calculator.compute(boxes(
                    "goCeAccountingEbitda", "100", "goCeAdjustment1", "-100",
                    "goCeCommittedLoanFacility", "500"), generalObligor);

            assertFigure(computed, "goCeAdjustedEbitda", "0");
            assertThat(computed).doesNotContainKey("goCeFedLeverageRatio");
        }

        @Test
        @DisplayName("no facility amount at all -> zero debt and a 0x ratio, not a blocked table (Clara)")
        void noFacilities() {
            Map<String, String> computed = calculator.compute(boxes("goCeAccountingEbitda", "100"), generalObligor);

            assertFigure(computed, "goCeTotalCommittedDebt", "0");
            assertFigure(computed, "goCeTotalCommittedDebtPerDefinition", "0");
            assertFigure(computed, "goCeFedLeverageRatio", "0");
        }

        @Test
        @DisplayName("thresholds follow Q-MI01: General Obligor 4 / 6, Utilities 6 / 7")
        void thresholds() {
            Map<String, String> go = calculator.compute(boxes(), generalObligor);
            assertFigure(go, "goCeThresholdPerPolicyBasedOnSector", "4");
            assertFigure(go, "goCeEscalationThreshold", "6");

            Map<String, String> utilities = calculator.compute(boxes(), key -> "UTILITIES");
            assertFigure(utilities, "goCeThresholdPerPolicyBasedOnSector", "6");
            assertFigure(utilities, "goCeEscalationThreshold", "7");
        }

        @Test
        @DisplayName("any other industry, an unanswered Q-MI01, or the one-argument compute -> no thresholds")
        void noThresholdsWithoutTheIndustry() {
            assertThat(calculator.compute(boxes(), key -> "REIT"))
                    .doesNotContainKeys("goCeThresholdPerPolicyBasedOnSector", "goCeEscalationThreshold");
            assertThat(calculator.compute(boxes(), key -> null))
                    .doesNotContainKeys("goCeThresholdPerPolicyBasedOnSector", "goCeEscalationThreshold");
            assertThat(calculator.compute(boxes()))
                    .doesNotContainKeys("goCeThresholdPerPolicyBasedOnSector", "goCeEscalationThreshold");
        }

        @Test
        @DisplayName("declares the one question it reads, so the resolver can refuse a renamed key")
        void declaresQ_MI01() {
            assertThat(calculator.requiredAnswers()).isEqualTo(Set.of("Q-MI01"));
        }
    }

    @Nested
    class Table2 {

        private final GoNetFundedDebtCalculator calculator = new GoNetFundedDebtCalculator();

        @Test
        @DisplayName("ratio = (B/S debt - non recourse - highly secured - qualifying funded sub - cash) / Adjusted EBITDA")
        void ratio() {
            Map<String, String> computed = calculator.compute(boxes(
                    "goRaAccountingEbitda", "100",
                    "goRaTotalBSDebt", "600", "goRaNonRecourseDebt", "50", "goRaHighlySecuredGrossFundedDebt", "100",
                    "goRaQualifyingFundedSubordinatedDebt", "50", "goRaCash", "100"));

            assertFigure(computed, "goRaAdjustedGrossFundedTotalDebt", "400");
            assertFigure(computed, "goRaAdjustedFundedTotalDebt", "300");
            assertFigure(computed, "goRaFedLeverageRatio", "3");
            assertFigure(computed, "goRaThresholdPerPolicyBasedOnSector", "4");
        }

        @Test
        @DisplayName("the Table 2 ratio is stored at two decimals too")
        void ratioAtTwoDecimals() {
            Map<String, String> computed = calculator.compute(boxes(
                    "goRaAccountingEbitda", "3", "goRaTotalBSDebt", "1"));

            assertThat(computed.get("goRaFedLeverageRatio")).isEqualTo("0.33");
        }

        @Test
        @DisplayName("cash above debt -> negative net funded debt and a negative ratio, both published")
        void cashAboveDebt() {
            Map<String, String> computed = calculator.compute(boxes(
                    "goRaAccountingEbitda", "100", "goRaTotalBSDebt", "300", "goRaCash", "500"));

            assertFigure(computed, "goRaAdjustedFundedTotalDebt", "-200");
            assertFigure(computed, "goRaFedLeverageRatio", "-2");
        }

        @Test
        @DisplayName("no Total B/S Debt -> zero gross debt; cash still counts, so net and ratio go negative (Clara)")
        void noBalanceSheetDebt() {
            Map<String, String> computed = calculator.compute(boxes("goRaAccountingEbitda", "100", "goRaCash", "50"));

            assertFigure(computed, "goRaAdjustedGrossFundedTotalDebt", "0");
            assertFigure(computed, "goRaAdjustedFundedTotalDebt", "-50");
            assertFigure(computed, "goRaFedLeverageRatio", "-0.5");
        }

        @Test
        @DisplayName("independent of Table 1 and of every answer (Clara's correction, 2026-10-12)")
        void readsNothingOutsideItsOwnBoxes() {
            Function<String, BigDecimal> ownBoxesOnly = key -> {
                if (!key.startsWith("goRa")) {
                    throw new AssertionError("Table 2 asked for " + key);
                }
                return switch (key) {
                    case "goRaAccountingEbitda" -> new BigDecimal("100");
                    case "goRaTotalBSDebt" -> new BigDecimal("300");
                    default -> null;
                };
            };
            Function<String, String> noAnswers = key -> {
                throw new AssertionError("Table 2 read the answer to " + key);
            };

            assertFigure(calculator.compute(ownBoxesOnly, noAnswers), "goRaFedLeverageRatio", "3");
        }
    }
}
