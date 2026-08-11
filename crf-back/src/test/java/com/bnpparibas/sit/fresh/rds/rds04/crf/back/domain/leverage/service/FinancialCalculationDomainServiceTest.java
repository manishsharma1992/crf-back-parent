package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.FinancialInputs.DebtAdjustments;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.FinancialInputs.EbitdaAdjustments;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.FinancialInputs.Sources;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class FinancialCalculationDomainServiceTest {

    private final FinancialCalculationDomainService service = new FinancialCalculationDomainService();

    private static BigDecimal n(String value) {
        return new BigDecimal(value);
    }

    /** What the analyst sees. Never fed back into a comparison — see {@link Ratio}. */
    private static BigDecimal onScreen(Ratio ratio) {
        return ratio.valueOrNull().setScale(2, RoundingMode.HALF_UP);
    }

    private static FinancialInputs inputs(Sources sources) {
        return new FinancialInputs(sources, EbitdaAdjustments.NONE, DebtAdjustments.NONE);
    }

    @Nested
    @DisplayName("reproduces the legacy screen")
    class Legacy {

        /**
         * The CRF screenshot: every adjustment box empty, both sources negative. If this drifts,
         * crf-next and crf disagree on a live analysis.
         */
        @Test
        void negativeSourcesWithNoAdjustments() {
            ComputedFinancials result = service.compute(inputs(
                    new Sources(n("-418.87"), n("-8.05"), n("-751.86"))));

            assertThat(result.adjustedEbitda()).isEqualByComparingTo("-418.87");
            assertThat(result.totalEcbDebt()).isEqualByComparingTo("-8.05");
            assertThat(result.totalNetFundedDebt()).isEqualByComparingTo("-751.86");
            assertThat(onScreen(result.ecbLeverageRatio())).isEqualByComparingTo("0.02");
            assertThat(onScreen(result.netFundedLeverageRatio())).isEqualByComparingTo("1.79");
        }

        /** The UX mock-up's figures: three EBITDA adjustments and four debt adjustments. */
        @Test
        void sumsTheAdjustmentsFromTheMockUp() {
            ComputedFinancials result = service.compute(new FinancialInputs(
                    new Sources(n("802468656.00"), n("5632230.00"), null),
                    new EbitdaAdjustments(n("1000000.00"), n("100000.00"), n("10000.00"), null, null),
                    new DebtAdjustments(n("1000000.00"), n("100000.00"), n("10000.00"), n("1000.00"), null)));

            assertThat(result.adjustedEbitda()).isEqualByComparingTo("803578656.00");
            assertThat(result.totalEcbDebt()).isEqualByComparingTo("6743230.00");
        }
    }

    @Nested
    @DisplayName("absent is not zero")
    class Absence {

        @Test
        void anAbsentAdjustmentContributesNothing() {
            ComputedFinancials result = service.compute(inputs(new Sources(n("50"), n("200"), n("180"))));

            assertThat(result.adjustedEbitda()).isEqualByComparingTo("50");
            assertThat(result.totalEcbDebt()).isEqualByComparingTo("200");
        }

        @Test
        void anAbsentEbitdaLeavesAdjustedEbitdaAbsentRatherThanSummingTheAdjustments() {
            ComputedFinancials result = service.compute(new FinancialInputs(
                    new Sources(null, n("200"), n("180")),
                    new EbitdaAdjustments(n("10"), n("20"), null, null, null),
                    DebtAdjustments.NONE));

            assertThat(result.adjustedEbitda()).isNull();
            assertThat(result.ecbLeverageRatio())
                    .isEqualTo(new Ratio.Undefined(Ratio.Reason.DENOMINATOR_ABSENT));
        }

        @Test
        void anAbsentGrossDebtLeavesTheEcbRatioUndefinedButNotTheNetFundedOne() {
            ComputedFinancials result = service.compute(inputs(new Sources(n("50"), null, n("180"))));

            assertThat(result.totalEcbDebt()).isNull();
            assertThat(result.ecbLeverageRatio())
                    .isEqualTo(new Ratio.Undefined(Ratio.Reason.NUMERATOR_ABSENT));
            assertThat(result.netFundedLeverageRatio().isDefined()).isTrue();
        }

        @Test
        void anAbsentNetDebtAffectsOnlyTheNetFundedSide() {
            ComputedFinancials result = service.compute(inputs(new Sources(n("50"), n("200"), null)));

            assertThat(result.totalNetFundedDebt()).isNull();
            assertThat(result.netFundedLeverageRatio())
                    .isEqualTo(new Ratio.Undefined(Ratio.Reason.NUMERATOR_ABSENT));
            assertThat(result.ecbLeverageRatio().isDefined()).isTrue();
        }

        @Test
        void everythingAbsentIsStillNotAFailure() {
            assertThatCode(() -> service.compute(inputs(new Sources(null, null, null))))
                    .doesNotThrowAnyException();
        }
    }

    @Nested
    @DisplayName("a zero denominator never throws")
    class ZeroDenominator {

        /**
         * The case put to the BA: a healthy base EBITDA cancelled to zero by the adjustments.
         * Blocked by ECB_ADJUSTED_EBITDA_ZERO upstream, but the arithmetic must not depend on
         * that rule being in place.
         */
        @Test
        void adjustmentsCancellingTheBaseOutYieldAnUndefinedRatioRatherThanAnException() {
            ComputedFinancials result = service.compute(new FinancialInputs(
                    new Sources(n("50000000"), n("200000000"), n("180000000")),
                    new EbitdaAdjustments(null, n("-50000000"), null, null, null),
                    DebtAdjustments.NONE));

            assertThat(result.adjustedEbitda()).isEqualByComparingTo("0");
            assertThat(result.adjustedEbitdaIsZero()).isTrue();
            assertThat(result.ecbLeverageRatio())
                    .isEqualTo(new Ratio.Undefined(Ratio.Reason.DENOMINATOR_ZERO));
            assertThat(result.netFundedLeverageRatio())
                    .isEqualTo(new Ratio.Undefined(Ratio.Reason.DENOMINATOR_ZERO));
        }

        /** Trailing zeros must not defeat the test: 0.00 is zero. */
        @Test
        void recognisesZeroWhateverItsScale() {
            ComputedFinancials result = service.compute(new FinancialInputs(
                    new Sources(n("50.00"), n("200"), n("180")),
                    new EbitdaAdjustments(n("-50.0000"), null, null, null, null),
                    DebtAdjustments.NONE));

            assertThat(result.adjustedEbitdaIsZero()).isTrue();
        }

        /** Absent is a different fault from zero, and the reason must say which. */
        @Test
        void distinguishesAZeroDenominatorFromAnAbsentOne() {
            ComputedFinancials zero = service.compute(new FinancialInputs(
                    new Sources(n("0"), n("200"), n("180")),
                    EbitdaAdjustments.NONE, DebtAdjustments.NONE));

            assertThat(zero.ecbLeverageRatio())
                    .isEqualTo(new Ratio.Undefined(Ratio.Reason.DENOMINATOR_ZERO));
        }
    }

    @Nested
    @DisplayName("negatives are ordinary")
    class Negatives {

        @Test
        void aNegativeAdjustedEbitdaGivesANegativeRatio() {
            ComputedFinancials result = service.compute(inputs(new Sources(n("-100"), n("400"), n("400"))));

            assertThat(onScreen(result.ecbLeverageRatio())).isEqualByComparingTo("-4.00");
        }

        @Test
        void aNegativeTotalEcbDebtIsProducedNotRejected() {
            ComputedFinancials result = service.compute(new FinancialInputs(
                    new Sources(n("100"), n("50"), n("50")),
                    EbitdaAdjustments.NONE,
                    new DebtAdjustments(null, null, null, null, n("-80"))));

            assertThat(result.totalEcbDebt()).isEqualByComparingTo("-30");
        }
    }

    @Nested
    @DisplayName("precision")
    class Precision {

        @Test
        void divisionKeepsTwentyEightDecimals() {
            ComputedFinancials result = service.compute(inputs(new Sources(n("3"), n("1"), n("1"))));

            assertThat(result.ecbLeverageRatio().valueOrNull().scale()).isEqualTo(Ratio.SCALE);
            assertThat(result.ecbLeverageRatio().valueOrNull())
                    .isEqualByComparingTo("0.3333333333333333333333333333");
        }

        @Test
        void additionIsExactAndNeverRounds() {
            ComputedFinancials result = service.compute(new FinancialInputs(
                    new Sources(n("0.000000000000001"), n("1"), n("1")),
                    new EbitdaAdjustments(n("0.000000000000002"), null, null, null, null),
                    DebtAdjustments.NONE));

            assertThat(result.adjustedEbitda()).isEqualByComparingTo("0.000000000000003");
        }

        /**
         * The rounding hazard the stored scale exists to prevent: a true ratio a hair under four
         * must not be allowed to present as 4.00 to a predicate. The screen may show 4.00; the
         * stored value, which is what {@code range [0 .. <4]} reads, must stay below four.
         */
        @Test
        void aRatioJustBelowFourStaysBelowFourInTheStoredValue() {
            ComputedFinancials result = service.compute(inputs(new Sources(n("10000"), n("39999"), n("39999"))));

            assertThat(result.ecbLeverageRatio().valueOrNull()).isLessThan(n("4"));
            assertThat(onScreen(result.ecbLeverageRatio())).isEqualByComparingTo("4.00");
        }
    }

    @Nested
    @DisplayName("projection onto CALC keys")
    class Projection {

        @Test
        void resolvesEachCalculatedFieldKey() {
            ComputedFinancials result = service.compute(inputs(new Sources(n("100"), n("400"), n("300"))));

            assertThat(result.valueOf(FinancialInputs.ADJUSTED_EBITDA)).contains(n("100"));
            assertThat(result.valueOf(FinancialInputs.TOTAL_ECB_DEBT)).contains(n("400"));
            assertThat(result.valueOf(FinancialInputs.TOTAL_NET_FUNDED_DEBT)).contains(n("300"));
            assertThat(result.valueOf(FinancialInputs.ECB_LEVERAGE_RATIO)).isPresent();
            assertThat(result.valueOf(FinancialInputs.NET_FUNDED_LEVERAGE_RATIO)).isPresent();
        }

        /** Empty means the box is not frozen at all — absent, never blank and never zero. */
        @Test
        void yieldsNothingForAnUndefinedRatio() {
            ComputedFinancials result = service.compute(inputs(new Sources(n("0"), n("400"), n("300"))));

            assertThat(result.valueOf(FinancialInputs.ECB_LEVERAGE_RATIO)).isEmpty();
        }

        @Test
        void degradesToEmptyForAnUnknownCalculation() {
            ComputedFinancials result = service.compute(inputs(new Sources(n("100"), n("400"), n("300"))));

            assertThat(result.valueOf("someCalculationThisReleaseDoesNotImplement")).isEmpty();
        }
    }

    @Nested
    @DisplayName("recomputation")
    class Recomputation {

        @Test
        void isIdempotent() {
            FinancialInputs given = new FinancialInputs(
                    new Sources(n("418.87"), n("8.05"), n("751.86")),
                    new EbitdaAdjustments(n("1.5"), null, n("-0.25"), null, null),
                    new DebtAdjustments(n("2"), n("3"), null, null, null));

            assertThat(service.compute(given)).isEqualTo(service.compute(given));
        }
    }
}
