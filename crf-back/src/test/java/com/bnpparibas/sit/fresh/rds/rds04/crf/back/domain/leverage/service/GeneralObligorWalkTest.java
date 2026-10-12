package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.FinancialTable;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.FormAnswers;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.AnalysisSubject;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.FinancialTableResolver;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.FinancialTableSupport;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.FinancialsResolver;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.ecb.EcbFinancialTableSupport;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.fed.FedFinancialSourcesResolver;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.fed.FedFinancialTableSupport;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.ecb.EcbFinancialCalculator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.fed.AplcFinancialCalculator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.fed.GoCommittedDebtCalculator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.fed.GoNetFundedDebtCalculator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.fed.ReitFinancialCalculator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service.*;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.EntityEligibility;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.TraversalResult;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.TraversalState;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeDefinition;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.catalogue.ValidationMessage;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.leverage.FedFormWalkTest;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.leverage.StubFinstar;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * General Obligor / Utilities, walked end to end against the PUBLISHED workbook.
 *
 * <p><b>Written before the code it tests</b>, as the handover asked: five of the last cycle's
 * defects came out of walk tests, all in seams between components that passed their own unit
 * tests. Until the workbook rows and the two calculators exist this class does not compile; that is
 * the point — it is the specification the workbook step and the calculator step are built to.
 *
 * <h2>The authoring contract this test assumes</h2>
 * Every key below is a PROPOSAL, named once in the constants so a rename is one edit.
 *
 * <pre>
 *  Q-MI01   + GENERAL_OBLIGOR, UTILITIES            -&gt; Q-HS01 (no date question on this path)
 *  shared   Q-HS01 Q-HS02 Q-IG01 Q-IG02 Q-WD01      (puml PATH_QUESTION_1..5)
 *  Q-WD01   + Q-MI01 is GENERAL_OBLIGOR -&gt; Q-F-GO1, Q-MI01 is UTILITIES -&gt; Q-F-GO1
 *
 *  Q-F-GO1  Table 1 (goCe*), DATA_ENTRY             exits, first match wins:
 *             field goCeFedLeverageRatio range [&gt;0]
 *               AND field goCeFedLeverageRatio &lt; 1 x field goCeEscalationThreshold -&gt; Q-E03
 *             field goCeFedLeverageRatio &gt;= 1 x field goCeEscalationThreshold      -&gt; Q-E01
 *             field goCeFedLeverageRatio &lt;  1 x field goCeEscalationThreshold      -&gt; Q-E01
 *             *                                                                     -&gt; Q-E03
 *           goCeFedLeverageRatio has Fills Flag = fedLeverageRatio
 *  Q-E01    SINGLE_CHOICE, mandatory, Section "Escalation tests for Highly Leveraged Transactions"
 *             YES -&gt; Q-E02, NO -&gt; Q-E03
 *  Q-E02    SINGLE_CHOICE, Section "Rule for escalation to CCDG"          * -&gt; Q-E03
 *  Q-E03    COMPUTED: band line (identical to Table 1's first exit) -&gt; NO | Q-E01 is NO -&gt; YES
 *             | Q-E02 is YES -&gt; YES | * -&gt; NO                            * -&gt; Q-F-GO2
 *  Q-F-GO2  Table 2 (goRa*), DATA_ENTRY                                  * -&gt; Q-GO20
 *
 *  Q-GO20   COMPUTED, Visible = No — "is the obligor leveraged?"
 *             Q-HS01 is YES                                                       -&gt; NO
 *             Q01 is YES                                                          -&gt; YES
 *             field goCeFedLeverageRatio &gt;= 1 x field goCeThresholdPerPolicyBasedOnSector -&gt; YES
 *             field goCeFedLeverageRatio range [&lt;0]                              -&gt; YES
 *             *                                                                   -&gt; NO
 *           Branches: NO -&gt; END, flags: fedLeveragedFlag=FED_NOT_LEVERAGED | * -&gt; Q-GO21
 *  Q-GO21   COMPUTED, Visible = No — "high or low risk?"   (reached only when leveraged)
 *             Q01 is YES -&gt; HIGH | Q-WD01 is YES -&gt; HIGH
 *             Q-IG01 is YES -&gt; LOW | Q-IG02 is YES -&gt; LOW | Q-HS02 is YES -&gt; LOW
 *             field goRaAdjustedEbitda range [&lt;0] AND field goRaAdjustedFundedTotalDebt range [&lt;0] -&gt; LOW
 *             field goRaAdjustedEbitda range [&lt;0]                                -&gt; HIGH
 *             field goRaFedLeverageRatio &gt;= 1 x field goRaThresholdPerPolicyBasedOnSector -&gt; HIGH
 *             *                                                                   -&gt; LOW
 *           Branches: HIGH -&gt; END, flags: …=FED_LEVERAGED_HIGH_RISK | * -&gt; END, flags: …=FED_LEVERAGED_LOW_RISK
 * </pre>
 *
 * <p>Two computed questions rather than one seven-deep cascade (handover item 4): each decision
 * lands in the snapshot on its own, and a not-leveraged analysis records no classification at all,
 * the same way Project Finance does. Both are hidden, so Clara never sees them — the lesson of
 * Q-RT20 applied before the feedback rather than after.
 *
 * <p><b>Thresholds</b>, from Q-MI01: sector 4x (GO) / 6x (Utilities), escalation 6x / 7x. Table 2's
 * sector threshold is a flat 4x. Every figure is compared NUMERICALLY ({@link #assertFigure}), so a
 * calculator publishing "6.0" instead of "6" does not fail a test that is about something else.
 *
 * <p><b>Every walk also asserts that no message reaches the screen without text</b> — see
 * {@link #walk}. That is Clara's REIT #2, made impossible to repeat on this path.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GeneralObligorWalkTest {

    private static final String RMPMID = "12345678";

    // ---------------------------------------------------------------- the proposed keys
    private static final String GENERAL_OBLIGOR = "GENERAL_OBLIGOR";
    private static final String UTILITIES = "UTILITIES";
    private static final String T1 = "Q-F-GO1";
    private static final String T2 = "Q-F-GO2";
    private static final String E01 = "Q-E01";
    private static final String E02 = "Q-E02";
    private static final String E03 = "Q-E03";
    private static final String LEVERAGED_TEST = "Q-GO20";
    private static final String CLASSIFICATION = "Q-GO21";

    private static final String NOT_LEVERAGED = "FED_NOT_LEVERAGED";
    private static final String HIGH_RISK = "FED_LEVERAGED_HIGH_RISK";
    private static final String LOW_RISK = "FED_LEVERAGED_LOW_RISK";

    private DecisionTreeDefinition fed;
    private DecisionTreeTraversalService traversal;
    private ChecklistCoercionDomainService coercion;
    private DateAnswerNormaliser dates;
    private ValidationDomainService validation;
    private FinancialTableResolver financialTables;

    @BeforeAll
    void setUp() {
        // Same seam as FedFormWalkTest: the PUBLISHED v15 workbook, imported the way the
        // application imports it. Point both classes at one helper rather than two copies.
        fed = FedFormWalkTest.publishedFedDefinition();

        traversal = new DecisionTreeTraversalService(new ConditionEvaluator());
        coercion = new ChecklistCoercionDomainService();
        dates = new DateAnswerNormaliser();
        validation = new ValidationDomainService();
        financialTables = new FinancialTableResolver(supports());
    }

    /**
     * Five tables now, wired as {@code FinancialTableSupportConfiguration} must wire them —
     * {@code FinancialTableSupportConfigurationTest} goes from three supports to five with this.
     *
     * <p>Table 1's calculator reads Q-MI01 for its two thresholds, so the resolver must hand it the
     * answers ({@code compute(inputs, answers)}). Table 2 reads nothing outside its own boxes.
     * Both read Accounting EBITDA from {@code FINANCIALS/ebitda}: FED's sources resolver has so far
     * served only {@code gross_debt} and {@code total_equity}, so it gains {@code ebitda}.
     */
    private List<FinancialTableSupport> supports() {
        FedFinancialSourcesResolver fedSources = subject -> StubFinstar.FED_SOURCES;
        return List.of(
                new EcbFinancialTableSupport(FinancialsResolver.none(),
                        new EcbFinancialCalculator(new FinancialCalculationDomainService())),
                new FedFinancialTableSupport(fedSources, new AplcFinancialCalculator()),
                new FedFinancialTableSupport(fedSources, new ReitFinancialCalculator()),
                new FedFinancialTableSupport(fedSources, new GoCommittedDebtCalculator()),
                new FedFinancialTableSupport(fedSources, new GoNetFundedDebtCalculator()));
    }

    // ================================================================== routing

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Routing {

        @Test
        @DisplayName("General Obligor walks the shared questions, Table 1, the escalation, Table 2, then the two tests")
        void theWholeRoute() {
            Walk walk = walk(go());

            walk.assertTerminalWith(LOW_RISK);
            assertThat(walk.result.path()).containsSubsequence(
                    "Q-MI01", "Q-HS01", "Q-HS02", "Q-IG01", "Q-IG02", "Q-WD01",
                    T1, E03, T2, LEVERAGED_TEST, CLASSIFICATION);
        }

        @Test
        @DisplayName("Utilities takes exactly the same route")
        void utilitiesSameRoute() {
            Walk walk = walk(go().industry(UTILITIES).table1("100", "750")   // 7.5x: leveraged at 6x
                    .escalation("NO", null));

            walk.assertTerminalWith(LOW_RISK);
            assertThat(walk.result.path()).containsSubsequence(
                    "Q-WD01", T1, E01, E03, T2, LEVERAGED_TEST, CLASSIFICATION);
        }

        @Test
        @DisplayName("the two tests are hidden, and the escalation questions carry their section headers")
        void authoredPresentation() {
            assertThat(fed.question(LEVERAGED_TEST).orElseThrow().hidden()).isTrue();
            assertThat(fed.question(CLASSIFICATION).orElseThrow().hidden()).isTrue();

            var e01 = fed.question(E01).orElseThrow();
            var e02 = fed.question(E02).orElseThrow();
            assertThat(e01.section().en().text()).isEqualTo("Escalation tests for Highly Leveraged Transactions");
            assertThat(e02.section().en().text()).isEqualTo("Rule for escalation to CCDG");
            // The header is not part of the label any more.
            assertThat(e01.label().en().text()).doesNotContain("Escalation tests");
        }
    }

    // ================================================================== Q-GO20: is it leveraged?

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class LeveragedTest {

        @Test
        @DisplayName("highly secured debt is 100% -> not leveraged, whatever the ratio says")
        void fullySecuredShortCircuits() {
            Walk walk = walk(go().put("Q-HS01", "YES").table1("100", "900")   // 9x
                    .escalation("NO", null));

            assertThat(walk.result.computedAnswers()).containsEntry(LEVERAGED_TEST, "NO");
            walk.assertTerminalWith(NOT_LEVERAGED);
            assertThat(walk.result.path()).doesNotContain(CLASSIFICATION);
        }

        @Test
        @DisplayName("below the 4x sector threshold -> not leveraged, and no classification is recorded")
        void belowTheSectorThreshold() {
            Walk walk = walk(go().table1("100", "300"));   // 3x

            assertThat(walk.result.computedAnswers()).containsEntry(LEVERAGED_TEST, "NO");
            walk.assertTerminalWith(NOT_LEVERAGED);
            assertThat(walk.result.path()).doesNotContain(CLASSIFICATION);
        }

        @Test
        @DisplayName("exactly 4x is leveraged: the threshold is inclusive")
        void atTheSectorThreshold() {
            Walk walk = walk(go().table1("100", "400"));

            assertThat(walk.result.computedAnswers()).containsEntry(LEVERAGED_TEST, "YES");
        }

        @Test
        @DisplayName("a negative FED leverage ratio is leveraged")
        void negativeRatio() {
            Walk walk = walk(go().table1("-100", "500")   // -5x: outside the band, so Q-E01 is asked
                    .escalation("NO", null));

            assertFigure(walk, "goCeFedLeverageRatio", "-5");
            assertThat(walk.result.computedAnswers()).containsEntry(LEVERAGED_TEST, "YES");
        }

        @Test
        @DisplayName("an LBO is leveraged and high risk, even at 3x")
        void lboAlone() {
            Walk walk = walk(go().put("Q01", "YES").table1("100", "300"));

            assertThat(walk.result.computedAnswers())
                    .containsEntry(LEVERAGED_TEST, "YES")
                    .containsEntry(CLASSIFICATION, "HIGH");
            walk.assertTerminalWith(HIGH_RISK);
        }
    }

    // ================================================================== Q-GO21: high or low?

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Classification {
        // Every case here is leveraged by the base 5x, so only the classification varies.

        @Test
        @DisplayName("WL/DD -> high risk")
        void watchList() {
            walk(go().put("Q-WD01", "YES")).assertTerminalWith(HIGH_RISK);
        }

        @ParameterizedTest(name = "{0} = YES -> low risk, even with Table 2 at 6x")
        @ValueSource(strings = {"Q-IG01", "Q-IG02", "Q-HS02"})
        void mitigants(String mitigant) {
            Walk walk = walk(go().put(mitigant, "YES").table2("100", "600"));

            assertThat(walk.result.computedAnswers()).containsEntry(CLASSIFICATION, "LOW");
            walk.assertTerminalWith(LOW_RISK);
        }

        @Test
        @DisplayName("negative Adjusted EBITDA AND negative net funded debt -> low risk")
        void bothNegative() {
            Walk walk = walk(go().table2("-50", "100").cash("200"));   // net 100 - 200 = -100

            assertFigure(walk, "goRaAdjustedFundedTotalDebt", "-100");
            walk.assertTerminalWith(LOW_RISK);
        }

        @Test
        @DisplayName("negative Adjusted EBITDA with positive net funded debt -> high risk")
        void negativeEbitdaOnly() {
            walk(go().table2("-50", "300")).assertTerminalWith(HIGH_RISK);
        }

        @Test
        @DisplayName("Table 2 at exactly 4x -> high risk: inclusive, like the sector threshold")
        void tableTwoAtTheThreshold() {
            Walk walk = walk(go().table2("100", "400"));

            assertFigure(walk, "goRaFedLeverageRatio", "4");
            walk.assertTerminalWith(HIGH_RISK);
        }

        @Test
        @DisplayName("Table 2 below 4x -> low risk")
        void tableTwoBelowTheThreshold() {
            Walk walk = walk(go());   // 300 / 100

            assertFigure(walk, "goRaFedLeverageRatio", "3");
            walk.assertTerminalWith(LOW_RISK);
        }

        @Test
        @DisplayName("cash above debt -> a NEGATIVE ratio, which is a real figure and low risk")
        void cashAboveDebt() {
            Walk walk = walk(go().cash("500"));   // net 300 - 500 = -200, ratio -2

            assertFigure(walk, "goRaFedLeverageRatio", "-2");
            walk.assertTerminalWith(LOW_RISK);
        }
    }

    // ================================================================== escalation (GO: 6x)

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Escalation {
        // The six cases listed on Q_E03's note in FED.puml, plus the ones around them.

        @Test
        @DisplayName("the legacy screen: -0.02x, delegation YES, repayment YES -> escalate YES")
        void legacyScreen() {
            Walk walk = walk(go().table1("-5000", "100").escalation("YES", "YES"));

            assertFigure(walk, "goCeFedLeverageRatio", "-0.02");
            assertThat(walk.result.computedAnswers()).containsEntry(E03, "YES");
        }

        @Test
        @DisplayName("0.0x asks the escalation questions (confirmed) - and still ends not leveraged")
        void zeroRatio() {
            Walk walk = walk(go().table1("100", "0").escalation("NO", null));

            assertFigure(walk, "goCeFedLeverageRatio", "0");
            assertThat(walk.result.path()).contains(E01);
            assertThat(walk.result.computedAnswers()).containsEntry(E03, "YES");
            // 0 is neither >= 4x nor < 0, so the leveraged test fails. Worth Clara knowing.
            walk.assertTerminalWith(NOT_LEVERAGED);
        }

        @Test
        @DisplayName("inside the band (0, 6x) -> no escalation questions, escalate NO")
        void insideTheBand() {
            Walk walk = walk(go());   // 5x

            assertThat(walk.result.path()).doesNotContain(E01, E02);
            assertThat(walk.result.computedAnswers()).containsEntry(E03, "NO");
        }

        @Test
        @DisplayName("exactly 6x -> the questions ARE asked: the routing and Q-E03 agree on the boundary")
        void atTheEscalationThreshold() {
            Walk walk = walk(go().table1("100", "600").escalation("NO", null));

            assertThat(walk.result.path()).contains(E01);
            assertThat(walk.result.computedAnswers()).containsEntry(E03, "YES");
        }

        @Test
        @DisplayName("not just below CCDG -> escalate YES, and the repayment question is never asked")
        void delegationNo() {
            Walk walk = walk(go().table1("100", "800").escalation("NO", null));

            assertThat(walk.result.path()).doesNotContain(E02);
            assertThat(walk.result.computedAnswers()).containsEntry(E03, "YES");
        }

        @Test
        @DisplayName("just below CCDG, repayment test not interrupted -> escalate NO")
        void repaymentNotInterrupted() {
            Walk walk = walk(go().table1("100", "800").escalation("YES", "NO"));

            assertThat(walk.result.computedAnswers()).containsEntry(E03, "NO");
        }

        @Test
        @DisplayName("just below CCDG, repayment test interrupted -> escalate YES")
        void repaymentInterrupted() {
            Walk walk = walk(go().table1("100", "800").escalation("YES", "YES"));

            assertThat(walk.result.computedAnswers()).containsEntry(E03, "YES");
        }

        @Test
        @DisplayName("Q-E01 unanswered -> the walk stops there, with its message")
        void delegationUnanswered() {
            Walk walk = walk(go().table1("100", "800"));

            walk.assertStoppedAt(E01);
            walk.assertViolation("FED_MANDATORY_Q_E01");
        }
    }

    // ================================================================== thresholds by industry

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Utilities {

        @Test
        @DisplayName("the thresholds follow Q-MI01: GO 4x / 6x, Utilities 6x / 7x, Table 2 a flat 4x")
        void thresholdsFollowTheIndustry() {
            Walk go = walk(go());
            assertFigure(go, "goCeThresholdPerPolicyBasedOnSector", "4");
            assertFigure(go, "goCeEscalationThreshold", "6");
            assertFigure(go, "goRaThresholdPerPolicyBasedOnSector", "4");

            Walk utilities = walk(go().industry(UTILITIES));
            assertFigure(utilities, "goCeThresholdPerPolicyBasedOnSector", "6");
            assertFigure(utilities, "goCeEscalationThreshold", "7");
            assertFigure(utilities, "goRaThresholdPerPolicyBasedOnSector", "4");
        }

        @Test
        @DisplayName("5x: leveraged for a General Obligor, not for a Utility")
        void fiveTimes() {
            walk(go()).assertTerminalWith(LOW_RISK);
            walk(go().industry(UTILITIES)).assertTerminalWith(NOT_LEVERAGED);
        }

        @Test
        @DisplayName("6.5x: a General Obligor is asked the escalation questions, a Utility is not")
        void sixAndAHalf() {
            Walk go = walk(go().table1("100", "650").escalation("NO", null));
            Walk utility = walk(go().industry(UTILITIES).table1("100", "650"));

            assertThat(go.result.path()).contains(E01);
            assertThat(utility.result.path()).doesNotContain(E01);
            assertThat(utility.result.computedAnswers()).containsEntry(E03, "NO");
        }

        @Test
        @DisplayName("any other industry publishes no GO thresholds - one number for both would be wrong for one")
        void otherIndustries() {
            Walk others = walk(new Answers().put("Q01", "NO").put("Q-S01", "SUBSIDIARY").put("Q-S02", "NO")
                    .put("Q-MI01", "OTHERS"));

            assertThat(others.table.computed()).doesNotContainKeys(
                    "goCeThresholdPerPolicyBasedOnSector", "goCeEscalationThreshold");
        }
    }

    // ================================================================== the two tables

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class TheTables {

        @Test
        @DisplayName("fedLeverageRatio is finally set, from Table 1's ratio")
        void leverageRatioFlag() {
            Walk walk = walk(go());

            assertThat(walk.result.flags()).containsKey("fedLeverageRatio");
            assertThat(new BigDecimal(walk.result.flags().get("fedLeverageRatio"))).isEqualByComparingTo("5");
        }

        @Test
        @DisplayName("adjustments ADD to Accounting EBITDA")
        void adjustmentsAdd() {
            Walk walk = walk(go().put(T1 + ".goCeAccountingEbitda", "80").put(T1 + ".goCeAdjustment1", "20"));

            assertFigure(walk, "goCeAdjustedEbitda", "100");
            assertFigure(walk, "goCeFedLeverageRatio", "5");
        }

        @Test
        @DisplayName("Adjusted EBITDA of zero -> no ratio, and the walk stops at Table 1 with a message")
        void zeroAdjustedEbitda() {
            Walk walk = walk(go().put(T1 + ".goCeAdjustment1", "-100"));   // 100 - 100

            assertThat(walk.table.computed()).doesNotContainKey("goCeFedLeverageRatio");
            walk.assertStoppedAt(T1);
        }

        /*
         * Clara, 2026-10-12: NO message for a missing Total Committed Debt Per Definition (Table 1)
         * or Adjusted Gross Funded Total Debt (Table 2). Both boxes are mandatory, so the walk still
         * stops at the table — it just says nothing. These two tests pin exactly that, so the
         * behaviour is a recorded decision rather than an accident.
         *
         * OPEN with Clara: should no debt at all mean "stop" (this) or "zero debt" (ratio 0, carry
         * on)? If the answer is zero, these two tests are what changes, together with one line in
         * each calculator.
         */

        @Test
        @DisplayName("no facility amount at all -> the walk stops at Table 1, without a message (Clara)")
        void noFacilityAmounts() {
            Walk walk = walk(go().without(T1 + ".goCeCommittedLoanFacility"));

            walk.assertStoppedSilentlyAt(T1);
        }

        @Test
        @DisplayName("no Total B/S Debt -> the walk stops at Table 2, without a message (Clara)")
        void noTotalBsDebt() {
            Walk walk = walk(go().without(T2 + ".goRaTotalBSDebt"));

            walk.assertStoppedSilentlyAt(T2);
        }

        @Test
        @DisplayName("Table 2 is independent of Table 1 (Clara's correction, 2026-10-12)")
        void tablesAreIndependent() {
            Walk lower = walk(go().table1("100", "500"));
            Walk higher = walk(go().table1("100", "900"));

            // Every declared table is resolved on every request, so the figures exist whatever
            // path the walk took. Table 1's debt moved; Table 2's ratio must not.
            assertFigure(lower, "goRaFedLeverageRatio", "3");
            assertFigure(higher, "goRaFedLeverageRatio", "3");
        }

        @Test
        @DisplayName("a ratio that stops being computable is dropped, and does not fill fedLeverageRatio")
        void staleRatio() {
            Walk walk = walk(go()
                    .put(T1 + ".goCeAdjustment1", "-100")              // Adjusted EBITDA 0: no ratio
                    .put(T1 + ".goCeFedLeverageRatio", "5"));          // what the last response showed

            assertThat(walk.resolved).doesNotContainKey(T1 + ".goCeFedLeverageRatio");
            assertThat(walk.result.flags()).doesNotContainKey("fedLeverageRatio");
            walk.assertStoppedAt(T1);
        }
    }

    // ================================================================== the chain

    /**
     * Exactly the sequence {@code GetLeverageFormStateUseCase.project(...)} runs — see
     * {@code FedFormWalkTest.walk} — plus one assertion every scenario gets for free: no message
     * may reach the screen without text.
     */
    private Walk walk(Answers posted) {
        Map<String, String> settled = coercion.coerce(fed, posted.map());
        settled = dates.normalise(fed, settled).answers();

        AnalysisSubject subject = StubFinstar.subject(RMPMID);
        FinancialTable table = financialTables.resolve(fed, settled, subject);
        Map<String, String> resolved = table.applyTo(settled);

        FormAnswers answers = FormAnswers.of(fed, resolved, Map.of(), derivedValues());
        TraversalResult result = traversal.resolve(fed, answers);

        List<ValidationMessage> violations = validation.violations(
                fed, resolved, result, EntityEligibility.UNANSWERED, table.computed());

        assertThat(violations).allSatisfy(message -> assertThat(
                message.text() == null ? null : message.text().en())
                .as("message %s has no text", message.messageKey())
                .isNotBlank());

        return new Walk(table, resolved, result, violations);
    }

    /** The reference data the status block reads — the same facts as FedFormWalkTest. */
    private static Map<String, String> derivedValues() {
        return Map.of(
                "COUNTERPARTY/PARENT", "87654321 - ACME HOLDING SA",
                "COUNTERPARTY/BNPP_COUNTERPARTY_RATING", "A- (2026-03-31)",
                "COUNTERPARTY/SP_ISSUER_RATING", "BBB+ (2026-01-15)",
                "COUNTERPARTY/MOODYS_ISSUER_RATING", "/");
    }

    /** Numeric comparison, so "6" and "6.0" are the same threshold. */
    private static void assertFigure(Walk walk, String fieldKey, String expected) {
        assertThat(walk.table.computed()).as("computed figures").containsKey(fieldKey);
        assertThat(new BigDecimal(walk.table.computed().get(fieldKey)))
                .as(fieldKey)
                .isEqualByComparingTo(expected);
    }

    private record Walk(FinancialTable table, Map<String, String> resolved,
                        TraversalResult result, List<ValidationMessage> violations) {

        void assertTerminalWith(String flagValue) {
            assertThat(result.state()).describedAs("path: %s", result.path()).isEqualTo(TraversalState.TERMINAL);
            assertThat(result.flags()).containsEntry("fedLeveragedFlag", flagValue);
        }

        /** Stopped HERE, and said why. A silent stop is the failure this guards. */
        void assertStoppedAt(String questionKey) {
            assertThat(result.state()).describedAs("path: %s", result.path()).isEqualTo(TraversalState.PENDING_INPUT);
            assertThat(result.path()).last().isEqualTo(questionKey);
            assertThat(violations).as("a walk that stops must say why").isNotEmpty();
        }

        /** Stopped HERE with nothing to say — only where Clara decided that (see TheTables). */
        void assertStoppedSilentlyAt(String questionKey) {
            assertThat(result.state()).describedAs("path: %s", result.path()).isEqualTo(TraversalState.PENDING_INPUT);
            assertThat(result.path()).last().isEqualTo(questionKey);
            assertThat(violations).isEmpty();
        }

        void assertViolation(String messageKey) {
            assertThat(violations).extracting(ValidationMessage::messageKey).contains(messageKey);
        }
    }

    // ================================================================== fixtures

    /**
     * The base case, chosen so every scenario moves ONE thing:
     * Table 1 = 500 / 100 = 5x (leveraged at 4x, inside the 6x escalation band, so Q-E01 is not
     * asked); Table 2 = 300 / 100 = 3x (below 4x). No mitigant, no WL/DD, no LBO. Result: low risk.
     *
     * <p>Accounting EBITDA is TYPED, not left to the FINSTAR prefill: it is editable, so a typed
     * figure wins, which keeps these tests independent of whatever StubFinstar holds.
     */
    private Answers go() {
        return new Answers()
                .put("Q01", "NO").put("Q-S01", "SUBSIDIARY").put("Q-S02", "NO")
                .industry(GENERAL_OBLIGOR)
                .put("Q-HS01", "NO").put("Q-HS02", "NO").put("Q-IG01", "NO").put("Q-IG02", "NO")
                .put("Q-WD01", "NO")
                .put(T1 + ".goCeOriginationDate", "2024-06-30")
                .table1("100", "500")
                .put(T2 + ".goRaLtmDate", "2024-06-30")          // "LTM Date" on Table 2 (Clara)
                .table2("100", "300");
    }

    private static final class Answers {

        private final Map<String, String> values = new LinkedHashMap<>();

        Answers put(String key, String value) {
            values.put(key, value);
            return this;
        }

        /** The analyst left this box empty, which the screen sends as no key at all. */
        Answers without(String key) {
            values.remove(key);
            return this;
        }

        Answers industry(String code) {
            return put("Q-MI01", code);
        }

        /** Table 1: Accounting EBITDA and one committed loan facility, nothing else. */
        Answers table1(String accountingEbitda, String committedLoanFacility) {
            return put(T1 + ".goCeAccountingEbitda", accountingEbitda)
                    .put(T1 + ".goCeCommittedLoanFacility", committedLoanFacility);
        }

        /** Table 2: Accounting EBITDA and Total B/S Debt, nothing else. */
        Answers table2(String accountingEbitda, String totalBsDebt) {
            return put(T2 + ".goRaAccountingEbitda", accountingEbitda)
                    .put(T2 + ".goRaTotalBSDebt", totalBsDebt);
        }

        Answers cash(String cash) {
            return put(T2 + ".goRaCash", cash);
        }

        /**
         * The two escalation answers. Safe to post whether or not the ratio makes them ASKED: an
         * answer to a question off the path matches nothing and is not frozen.
         */
        Answers escalation(String delegation, String repayment) {
            put(E01, delegation);
            return repayment == null ? this : put(E02, repayment);
        }

        Map<String, String> map() {
            return Map.copyOf(values);
        }
    }
}
