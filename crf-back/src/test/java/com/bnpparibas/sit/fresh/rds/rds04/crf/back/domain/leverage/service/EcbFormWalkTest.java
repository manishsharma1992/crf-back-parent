package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.FinancialTable;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.FormAnswers;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.AnalysisSubject;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.FinancialTableResolver;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.FinancialTableSupport;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.FinancialsResolver;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.ecb.EcbFinancialTableSupport;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.ecb.EcbFinancialCalculator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.EntityEligibility;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.FinancialInputs;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.TraversalResult;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.TraversalState;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeDefinition;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.catalogue.ValidationMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One walk per ECB route, from Q01 to a terminal flag.
 *
 * <p><b>Why this exists, and why now.</b> ECB is the live form and had no end-to-end test. Seven
 * things it runs were changed while FED was built: {@code recordPrefilled}, {@code
 * FinancialTable.computed()} becoming a map, {@code addMandatoryViolations}, MANDATORY joining
 * FIELD_RULES, completeness moving to {@code fieldText}, the whole calculation going behind
 * {@link EcbFinancialCalculator}, and the Jackson round trip. Each has unit tests. The five
 * defects the FED walks found all lived between components that passed their own.
 *
 * <p><b>One of those changes alters the frozen record.</b> Q-S05's parent company was used for
 * routing and never recorded, so every ECB snapshot taken so far is missing it and every snapshot
 * from now on will have it. {@link StatusBlock#parentCompanyIsRecorded()} is that fix pinned.
 *
 * <p>Same harness as {@code FedFormWalkTest}: the chain {@code
 * GetLeverageFormStateUseCase.project(...)} runs, assembled by hand, no Spring and no database.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EcbFormWalkTest {

    private static final String RMPMID = "12345678";

    /** Healthy figures: EBITDA 1000, Gross Debt 3000, Net Debt 2500 — a ratio of 3. */
    private static final FinancialInputs.Sources HEALTHY = sources("1000", "3000", "2500");

    private DecisionTreeDefinition ecb;
    private DecisionTreeTraversalService traversal;
    private ChecklistCoercionDomainService coercion;
    private DateAnswerNormaliser dates;
    private ValidationDomainService validation;

    @BeforeAll
    void setUp() {
        // SEAM — same as FedFormWalkTest: the PUBLISHED definition, imported from the workbook.
        // Assert the import came back with no issues before walking it.
        ecb = publishedEcbDefinition();

        traversal = new DecisionTreeTraversalService(new ConditionEvaluator());
        coercion = new ChecklistCoercionDomainService();
        dates = new DateAnswerNormaliser();
        validation = new ValidationDomainService();
    }

    // ================================================================== the two LBO openings

    @Nested
    @DisplayName("the exclusion checklists")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Exclusions {

        @Test
        @DisplayName("LBO: one YES on Q-B01A excludes the transaction outright")
        void lboAnyYesExcludes() {
            Walk walk = walk(HEALTHY, answers().lbo("YES").put("Q-B01A.sovereign", "YES"));

            walk.assertTerminalWith("ecbLeveragedFlag", "ECB_NOT_LEVERAGED");
            // A YES settles the block, so the other two items are never required.
            assertThat(walk.result.path()).containsExactly("Q01", "Q-B01A");
        }

        @Test
        @DisplayName("non-LBO uses Q-B01B, which has a fourth item")
        void nonLboUsesTheOtherChecklist() {
            Walk walk = walk(HEALTHY, answers().lbo("NO").put("Q-B01B.sme", "YES"));

            walk.assertTerminalWith("ecbLeveragedFlag", "ECB_NOT_LEVERAGED");
            assertThat(walk.result.path()).containsExactly("Q01", "Q-B01B");
        }

        @Test
        @DisplayName("every item NO carries on — the checklist did not exclude anything")
        void allNoContinues() {
            Walk walk = walk(HEALTHY, answers().lbo("YES").allNo("Q-B01A", "sovereign", "financialSector",
                    "investmentGrade"));

            assertThat(walk.result.path()).contains("Q-T01");
        }

        @Test
        @DisplayName("a half-answered checklist refuses the save and names no question")
        void halfAnsweredChecklistFires() {
            // Two of three answered, none YES: the engine cannot tell whether the untouched item
            // is inapplicable or was never reached, so it refuses to record the block.
            Walk walk = walk(HEALTHY, answers().lbo("YES")
                    .put("Q-B01A.sovereign", "NO")
                    .put("Q-B01A.financialSector", "NO"));

            assertThat(walk.result.state()).isEqualTo(TraversalState.PENDING_INPUT);
            walk.assertViolation("ECB_CHECKLIST_MANDATORY");
        }

        @Test
        @DisplayName("an untouched checklist is silent — the analyst has not got there yet")
        void untouchedChecklistIsSilent() {
            Walk walk = walk(HEALTHY, answers().lbo("YES"));

            assertThat(walk.result.state()).isEqualTo(TraversalState.PENDING_INPUT);
            assertThat(walk.violations).isEmpty();
        }
    }

    // ================================================================== the transaction block

    @Nested
    @DisplayName("transaction type and credit event")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class TransactionBlock {

        @Test
        @DisplayName("an excluded transaction type ends the form as INR")
        void anyYesOnQT01IsInr() {
            Walk walk = walk(HEALTHY, clearedChecklists("YES").put("Q-T01.derivatives", "YES"));

            walk.assertTerminalWith("ecbLeveragedFlag", "INR");
        }

        @Test
        @DisplayName("the SAME rows serve both LBO orderings, and route differently")
        void qt01RoutesOnTheLboAnswer() {
            // ALL_NO sits BELOW "Q01 is NO" on purpose: ALL_NO is true on both LBO paths and
            // would otherwise swallow the non-LBO route. First match wins, so the order IS the
            // rule — this is the assertion that breaks if someone re-sorts the branch cell.
            Walk lbo = walk(HEALTHY, clearedChecklists("YES").allNo("Q-T01", T01_ITEMS));
            Walk nonLbo = walk(HEALTHY, clearedChecklists("NO").allNo("Q-T01", T01_ITEMS));

            assertThat(lbo.result.path()).contains("Q-C01").doesNotContain("Q-T02");
            assertThat(nonLbo.result.path()).contains("Q-T02").doesNotContain("Q-C01");
        }

        @Test
        @DisplayName("non-LBO, not a qualifying new transaction -> INR")
        void nonLboNotQualifyingIsInr() {
            Walk walk = walk(HEALTHY, clearedChecklists("NO").allNo("Q-T01", T01_ITEMS)
                    .put("Q-T02", "YES").put("Q-T03", "NO"));

            walk.assertTerminalWith("ecbLeveragedFlag", "INR");
        }

        @Test
        @DisplayName("no credit event -> INR")
        void noCreditEventIsInr() {
            Walk walk = walk(HEALTHY, clearedChecklists("YES").allNo("Q-T01", T01_ITEMS)
                    .put("Q-C01", "NO"));

            walk.assertTerminalWith("ecbLeveragedFlag", "INR");
        }
    }

    // ================================================================== the status block

    @Nested
    @DisplayName("status of the counterparty")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class StatusBlock {

        @Test
        @DisplayName("an investment grade supporting entity ends the form as not leveraged")
        void investmentGradeSupportEndsIt() {
            Walk walk = walk(HEALTHY, statusBlock().put("Q-S01", "SUBSIDIARY")
                    .put("Q-S02", "YES").put("Q-S03", "YES"));

            walk.assertTerminalWith("ecbLeveragedFlag", "ECB_NOT_LEVERAGED");
            assertThat(walk.result.computedAnswers()).containsEntry("Q-S04", "BORROWER");
        }

        @Test
        @DisplayName("a non-investment-grade supporting entity routes to the parent lookup")
        void nonInvestmentGradeSupportGoesToTheLookup() {
            Walk walk = walk(HEALTHY, statusBlock().put("Q-S01", "SUBSIDIARY")
                    .put("Q-S02", "YES").put("Q-S03", "NO")
                    .put("Q-S06", "87654321"));

            walk.assertTerminalWith("ecbLeveragedFlag", "CALCULATION_AT_BG_OR_OTHER_LE");
            assertThat(walk.result.computedAnswers()).containsEntry("Q-S04", "BUSINESS_GROUP");
        }

        @Test
        @DisplayName("REGRESSION: the parent company is recorded, not only routed through")
        void parentCompanyIsRecorded() {
            // Q-S05 is COMPUTED from COUNTERPARTY/PARENT. recordPrefilled returned early when
            // prefillFrom was null, so a derived-only question's value reached the ROUTING but
            // never the result — absent from computedAnswers, prefilledAnswers and the posted map
            // alike. QuestionView and PreliminaryResponseAssembler read exactly those three, so
            // the parent rendered blank AND was missing from every frozen snapshot.
            Walk walk = walk(HEALTHY, statusBlock().put("Q-S01", "SUBSIDIARY")
                    .put("Q-S02", "YES").put("Q-S03", "NO")
                    .put("Q-S06", "87654321"));

            assertThat(walk.result.prefilledAnswers())
                    .containsEntry("Q-S05", "87654321 - ACME HOLDING SA");
        }

        @Test
        @DisplayName("UMC on the LBO path skips the lookup and goes straight to the figures")
        void umcOnLboGoesToTheTable() {
            Walk walk = walk(HEALTHY, statusBlock().put("Q-S01", "UMC").put("Q-F01", "")
                    .withHealthyTable());

            assertThat(walk.result.computedAnswers()).containsEntry("Q-S04", "BUSINESS_GROUP");
            assertThat(walk.result.path()).contains("Q-F01").doesNotContain("Q-S05", "Q-S06");
        }
    }

    // ================================================================== the financial table

    @Nested
    @DisplayName("the figures")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Figures {

        @Test
        @DisplayName("a ratio under 4 ends the form as not leveraged")
        void underFourIsNotLeveraged() {
            // 3000 / 1000 = 3. Q-Q02's first branch catches it before Q-Q03 is ever asked.
            Walk walk = walk(HEALTHY, leveragedRoute().withHealthyTable());

            walk.assertTerminalWith("ecbLeveragedFlag", "ECB_NOT_LEVERAGED");
            assertThat(walk.result.computedAnswers())
                    .containsEntry("Q-Q01", "NO")
                    .containsEntry("Q-Q02", "NO");
            assertThat(walk.table.computed()).containsEntry("ecbLeverageRatio", "3");
            assertThat(walk.result.path()).doesNotContain("Q-Q03");
        }

        @Test
        @DisplayName("between 4 and 6: leveraged, highly leveraged NO, no escalation question")
        void betweenFourAndSix() {
            // 5000 / 1000 = 5. Q-Q01 YES on the 4x cross-multiplication, Q-Q02 NO on the 6x one.
            Walk walk = walk(sources("1000", "5000", "2500"),
                    leveragedRoute().withHealthyTable().put("Q-Q03", "FULL"));

            walk.assertTerminalWith("ecbLeveragedFlag", "ECB_LEVERAGED");
            assertThat(walk.result.computedAnswers())
                    .containsEntry("Q-Q01", "YES")
                    .containsEntry("Q-Q02", "NO");
            assertThat(walk.result.path()).doesNotContain("Q-Q04", "Q-Q05");
            assertThat(walk.result.flags()).containsEntry("ecbCovenantStructure", "FULL");
        }

        @Test
        @DisplayName("above 6 on a new transaction opens the escalation questions")
        void aboveSixOpensEscalation() {
            Walk walk = walk(sources("1000", "7000", "2500"),
                    leveragedRoute().put("Q-C02", "ORIGINATION").withHealthyTable()
                            .put("Q-Q03", "LITE").put("Q-Q04", "CCDG"));

            walk.assertTerminalWith("ecbLeveragedFlag", "ECB_LEVERAGED");
            assertThat(walk.result.flags()).containsEntry("escalatedTransactions", "YES");
        }

        @Test
        @DisplayName("just below CCDG asks one more question, and a NO means no escalation")
        void justBelowCcdgAsksQQ05() {
            Walk walk = walk(sources("1000", "7000", "2500"),
                    leveragedRoute().put("Q-C02", "REFINANCING").withHealthyTable()
                            .put("Q-Q03", "NONE").put("Q-Q04", "JUST_BELOW_CCDG").put("Q-Q05", "NO"));

            assertThat(walk.result.path()).contains("Q-Q05");
            walk.assertTerminalWith("escalatedTransactions", "NO");
        }

        @Test
        @DisplayName("an annual review above 6 is leveraged but never escalated")
        void annualReviewDoesNotEscalate() {
            // Q-Q03's branch names only ORIGINATION, MATERIAL_MODIFICATION and REFINANCING, so an
            // annual review of an already-leveraged counterparty stops at ECB_LEVERAGED.
            Walk walk = walk(sources("1000", "7000", "2500"),
                    leveragedRoute().put("Q-C02", "ANNUAL_REVIEW_NO_CREDIT_EVENT").withHealthyTable()
                            .put("Q-Q03", "LOOSE"));

            walk.assertTerminalWith("ecbLeveragedFlag", "ECB_LEVERAGED");
            assertThat(walk.result.flags()).doesNotContainKey("escalatedTransactions");
        }

        @Test
        @DisplayName("a negative Total ECB Debt is highly leveraged, not an error")
        void negativeDebtIsHighlyLeveraged() {
            // Q-Q02's third rule reads the DEBT, not the ratio: cross-multiplication is defined at
            // every value of Adjusted EBITDA, which a division is not.
            Walk walk = walk(sources("1000", "-500", "2500"),
                    leveragedRoute().put("Q-C02", "ORIGINATION").withHealthyTable()
                            .put("Q-Q03", "FULL").put("Q-Q04", "CCDG"));

            assertThat(walk.result.computedAnswers()).containsEntry("Q-Q02", "YES");
            assertThat(walk.result.flags()).containsEntry("escalatedTransactions", "YES");
        }

        @Test
        @DisplayName("the ratio flag carries the figure the walk routed on")
        void ratioFillsItsFlag() {
            Walk walk = walk(HEALTHY, leveragedRoute().withHealthyTable());

            assertThat(walk.result.flags()).containsEntry("ecbLeverageRatio", "3");
        }
    }

    // ================================================================== blocking

    @Nested
    @DisplayName("figures that stop the analysis")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Blocking {

        @Test
        @DisplayName("a zero EBITDA withholds every calculated box and names the cause")
        void zeroEbitdaBlocks() {
            Walk walk = walk(sources("0", "3000", "2500"), leveragedRoute().withHealthyTable());

            // The source IS published — the analyst has to see the zero that blocked them.
            assertThat(walk.table.overlay()).containsEntry("Q-F01.ebitda", "0");
            // The calculated boxes are not, so Q-F01 is incomplete and the walk stops there.
            assertThat(walk.table.overlay()).doesNotContainKey("Q-F01.adjustedEbitda");
            assertThat(walk.result.state()).isEqualTo(TraversalState.PENDING_INPUT);
            walk.assertViolation("ECB_EBITDA_ZERO");
        }

        @Test
        @DisplayName("an absent EBITDA blocks too, with a different message")
        void absentEbitdaBlocks() {
            // SOURCE_EMPTY and MUST_NOT_BE_ZERO share no predicate and no wording: one points at
            // FINSTAR, the other at a figure that came to nothing.
            Walk walk = walk(sources(null, "3000", "2500"), leveragedRoute().withHealthyTable());

            walk.assertViolation("ECB_EBITDA_EMPTY");
            assertThat(walk.result.state()).isEqualTo(TraversalState.PENDING_INPUT);
        }

        @Test
        @DisplayName("adjustments cancelling a healthy EBITDA out is the silent-failure case")
        void adjustmentsCancellingOutBlock() {
            // Sources are fine; the analyst's own five adjustments bring Adjusted EBITDA to zero.
            // Judged on the COMPUTED value, which is why the figure is still handed to validation
            // even though it is withheld from the screen.
            Walk walk = walk(HEALTHY, leveragedRoute().withHealthyTable()
                    .justified("reportedLtmAdjustment", "-1000"));

            assertThat(walk.table.computed()).containsEntry("adjustedEbitda", "0");
            assertThat(walk.table.overlay()).doesNotContainKey("Q-F01.adjustedEbitda");
            walk.assertViolation("ECB_ADJUSTED_EBITDA_ZERO");
        }

        @Test
        @DisplayName("an input message suppresses the calculated one underneath it")
        void inputMessagesComeFirst() {
            // One cause, one message, and the one naming something the analyst can act on.
            Walk walk = walk(sources("0", "3000", "2500"), leveragedRoute().withHealthyTable());

            walk.assertViolation("ECB_EBITDA_ZERO");
            assertThat(walk.messageKeys()).doesNotContain("ECB_ADJUSTED_EBITDA_ZERO");
        }

        @Test
        @DisplayName("an absent Net Debt costs the net funded pair and nothing else")
        void absentNetDebtDoesNotBlock() {
            // Nothing routes on the net funded pair, so a form must not be refused over it.
            Walk walk = walk(sources("1000", "3000", null), leveragedRoute().withHealthyTable());

            assertThat(walk.table.computed()).doesNotContainKey("totalNetFundedDebt");
            walk.assertTerminalWith("ecbLeveragedFlag", "ECB_NOT_LEVERAGED");
        }
    }

    // ================================================================== per-box rules

    @Nested
    @DisplayName("box rules on the table")
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class BoxRules {

        @Test
        @DisplayName("an adjustment with no justification is refused")
        void unjustifiedAdjustmentFires() {
            Walk walk = walk(HEALTHY, leveragedRoute().withHealthyTable()
                    .put("Q-F01.reportedLtmAdjustment", "250"));

            walk.assertViolation("ECB_JUSTIF_REPORTED_LTM");
        }

        @Test
        @DisplayName("a wording with no comment is not a justification")
        void halfAJustificationFires() {
            Walk walk = walk(HEALTHY, leveragedRoute().withHealthyTable()
                    .put("Q-F01.reportedLtmAdjustment", "250")
                    .put("Q-F01.reportedLtmAdjustment.wording", "Perimeter change"));

            walk.assertViolation("ECB_JUSTIF_REPORTED_LTM");
        }

        @Test
        @DisplayName("a justified adjustment is accepted and moves the ratio")
        void justifiedAdjustmentIsAccepted() {
            // EBITDA 1000 + 1000 = 2000, debt 3000 -> 1.5, still under 4.
            Walk walk = walk(HEALTHY, leveragedRoute().withHealthyTable()
                    .justified("reportedLtmAdjustment", "1000"));

            assertThat(walk.messageKeys()).doesNotContain("ECB_JUSTIF_REPORTED_LTM");
            assertThat(walk.table.computed()).containsEntry("adjustedEbitda", "2000");
            assertThat(walk.table.computed()).containsEntry("ecbLeverageRatio", "1.5");
        }

        @Test
        @DisplayName("a zero adjustment still owes a justification")
        void zeroIsAFigureToo() {
            // Typing zero is a decision; leaving the box blank is not.
            Walk walk = walk(HEALTHY, leveragedRoute().withHealthyTable()
                    .put("Q-F01.committedUndrawnDebt", "0"));

            walk.assertViolation("ECB_JUSTIF_COMMITTED_UNDRAWN");
        }

        @Test
        @DisplayName("a negative debt adjustment is refused, zero is not")
        void mustBePositive() {
            Walk negative = walk(HEALTHY, leveragedRoute().withHealthyTable()
                    .justified("committedUndrawnDebt", "-100"));
            Walk zero = walk(HEALTHY, leveragedRoute().withHealthyTable()
                    .justified("committedUndrawnDebt", "0"));

            negative.assertViolation("ECB_POSITIVE_COMMITTED_UNDRAWN");
            assertThat(zero.messageKeys()).doesNotContain("ECB_POSITIVE_COMMITTED_UNDRAWN");
        }
    }

    // ================================================================== cross-form

    @Test
    @DisplayName("Q01 copied from the FED form is not asked again")
    void q01IsPrefilledFromFed() {
        // Prefill From = FED/Q01. The question is answered, so the walk advances past it — and
        // the value has to be visible to the CONDITIONS that route on it, not merely to the
        // completeness check, or "Q01 is YES" further down would silently fall through.
        Walk walk = walk(HEALTHY, answers()
                        .crossForm("FED/Q01", "YES")
                        .put("Q-B01A.sovereign", "YES"));

        walk.assertTerminalWith("ecbLeveragedFlag", "ECB_NOT_LEVERAGED");
        assertThat(walk.result.prefilledAnswers()).containsEntry("Q01", "YES");
        assertThat(walk.result.flags()).containsEntry("ecbLboFlag", "YES");
    }

    // ================================================================== the chain

    private Walk walk(FinancialInputs.Sources finstar, Answers posted) {
        Map<String, String> settled = coercion.coerce(ecb, posted.map());
        settled = dates.normalise(ecb, settled).answers();

        AnalysisSubject subject = StubFinstar.subject(RMPMID);
        FinancialTable table = resolverFor(finstar).resolve(ecb, settled, subject);
        Map<String, String> resolved = table.applyTo(settled);

        FormAnswers answers = FormAnswers.of(ecb, resolved, posted.crossForm(), derivedValues());
        TraversalResult result = traversal.resolve(ecb, answers);

        List<ValidationMessage> violations = validation.violations(
                ecb, resolved, result, EntityEligibility.UNANSWERED, table.computed());

        return new Walk(table, result, violations);
    }

    /** ECB only — the FED supports claim no question in this definition, so they are left out. */
    private FinancialTableResolver resolverFor(FinancialInputs.Sources finstar) {
        FinancialsResolver sources = subject -> finstar;
        FinancialTableSupport ecbSupport = new EcbFinancialTableSupport(sources,
                new EcbFinancialCalculator(new FinancialCalculationDomainService()));
        return new FinancialTableResolver(List.of(ecbSupport));
    }

    private Map<String, String> derivedValues() {
        return Map.of("COUNTERPARTY/PARENT", "87654321 - ACME HOLDING SA");
    }

    private record Walk(FinancialTable table, TraversalResult result, List<ValidationMessage> violations) {

        void assertTerminalWith(String flagKey, String flagValue) {
            assertThat(result.state())
                    .describedAs("path: %s%nviolations: %s", result.path(), messageKeys())
                    .isEqualTo(TraversalState.TERMINAL);
            assertThat(result.flags()).containsEntry(flagKey, flagValue);
        }

        void assertViolation(String messageKey) {
            assertThat(messageKeys()).contains(messageKey);
        }

        List<String> messageKeys() {
            return violations.stream().map(ValidationMessage::messageKey).toList();
        }
    }

    // ================================================================== fixtures

    private static final String[] T01_ITEMS = {
            "tradeFinance", "specialisedLending", "bondsHybrid", "derivatives", "factoring",
            "lease", "exportFinanceEca", "subordinatedDebt", "marginLoans", "groupExposure"};

    private static FinancialInputs.Sources sources(String ebitda, String grossDebt, String netDebt) {
        return new FinancialInputs.Sources(amount(ebitda), amount(grossDebt), amount(netDebt));
    }

    private static BigDecimal amount(String value) {
        return value == null ? null : new BigDecimal(value);
    }

    private Answers answers() {
        return new Answers();
    }

    /** Both exclusion checklists cleared, so the walk reaches the transaction block. */
    private Answers clearedChecklists(String lbo) {
        Answers answers = answers().lbo(lbo);
        return "YES".equals(lbo)
                ? answers.allNo("Q-B01A", "sovereign", "financialSector", "investmentGrade")
                : answers.allNo("Q-B01B", "sme", "sovereign", "financialSector", "investmentGrade");
    }

    /** LBO, nothing excluded, a credit event: the route that reaches the status block. */
    private Answers statusBlock() {
        return clearedChecklists("YES").allNo("Q-T01", T01_ITEMS)
                .put("Q-C01", "YES").put("Q-C02", "ORIGINATION");
    }

    /** Non-LBO, nothing excluded: the shortest route to Q-F01. */
    private Answers leveragedRoute() {
        return clearedChecklists("NO").allNo("Q-T01", T01_ITEMS)
                .put("Q-T02", "YES").put("Q-T03", "YES")
                .put("Q-C01", "YES").put("Q-C02", "ORIGINATION");
    }

    private static final class Answers {

        private final Map<String, String> values = new LinkedHashMap<>();
        private final Map<String, String> crossForm = new LinkedHashMap<>();

        Answers put(String key, String value) {
            values.put(key, value);
            return this;
        }

        Answers lbo(String yesNo) {
            return put("Q01", yesNo);
        }

        Answers crossForm(String formAndQuestionKey, String value) {
            crossForm.put(formAndQuestionKey, value);
            return this;
        }

        Answers allNo(String questionKey, String... itemKeys) {
            for (String item : itemKeys) {
                put(questionKey + '.' + item, "NO");
            }
            return this;
        }

        /**
         * The table with nothing adjusted. Only the two FINSTAR sources are mandatory and they are
         * not posted, so this adds nothing — it exists to say so at the call site rather than
         * leaving a reader wondering where Q-F01's answers are.
         */
        Answers withHealthyTable() {
            return this;
        }

        /** A figure with both halves of its justification, which is the only accepted shape. */
        Answers justified(String fieldKey, String value) {
            return put("Q-F01." + fieldKey, value)
                    .put("Q-F01." + fieldKey + ".wording", "Perimeter change")
                    .put("Q-F01." + fieldKey + ".comment", "Agreed with the credit analyst.");
        }

        Map<String, String> map() {
            return Map.copyOf(values);
        }

        Map<String, String> crossForm() {
            return Map.copyOf(crossForm);
        }
    }
}
