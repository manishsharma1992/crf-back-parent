package com.bnpparibas.sit.fresh.rds.rds04.crf.back.leverage;

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
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.fed.ReitFinancialCalculator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service.*;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.EntityEligibility;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.TraversalResult;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.TraversalState;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeDefinition;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.catalogue.ValidationMessage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One walk per FED path, from Q01 to a terminal flag.
 *
 * <p><b>Why these exist.</b> Every collaborator has unit tests and every one of them passed while
 * the form was broken. The defects lived in the SEAMS — a rule missing from FIELD_RULES, a
 * @Component on a class that must not be scanned, an early return that skipped FED, a Jackson
 * getter, a field type the parser rejects. Two of those five threw. The others would have been
 * found by an analyst.
 *
 * <p><b>Not Spring, not the database.</b> The chain under test is exactly what
 * {@code GetLeverageFormStateUseCase.project(...)} does once the aggregate is loaded: coerce,
 * normalise dates, resolve the financial tables, merge the overlay, traverse, validate. Persistence
 * and the HTTP layer are someone else's tests. Assembling the chain by hand is the point — if a
 * collaborator stops fitting, this fails to compile rather than failing in front of Clara.
 *
 * <p><b>The definition is the PUBLISHED one</b>, imported from the workbook, not a fixture built in
 * Java. A hand-built definition tests the engine against what the test author believed; the
 * workbook tests it against what the BA actually authored, which is where
 * TEXTAREA-is-not-a-type and SINGLE_CHOICE-is-not-a-field-type came from.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FedFormWalkTest {

    private static final String RMPMID = "12345678";

    private DecisionTreeDefinition fed;
    private DecisionTreeTraversalService traversal;
    private ChecklistCoercionDomainService coercion;
    private DateAnswerNormaliser dates;
    private ValidationDomainService validation;
    private FinancialTableResolver financialTables;

    @BeforeAll
    void setUp() {
        // ---------------------------------------------------------------- SEAM 1
        // The published FED definition. Point this at whatever your import tests already use to
        // turn a workbook into a definition — the same route the application takes, so the test
        // breaks when authoring breaks.
        //
        //   fed = importService.importWorkbook(resource("leverage-decision-tree-authoring-template_14.xlsx"))
        //           .definition(LeverageFormType.FED);
        //
        // Assert it imported clean before anything else: a test walking a definition that failed
        // validation tells you nothing useful.
        fed = publishedFedDefinition();

        traversal = new DecisionTreeTraversalService(new ConditionEvaluator());
        coercion = new ChecklistCoercionDomainService();
        dates = new DateAnswerNormaliser();
        validation = new ValidationDomainService();
        financialTables = new FinancialTableResolver(supports());
    }

    /**
     * The three financial tables, wired exactly as {@code FinancialTableSupportConfiguration} does.
     *
     * <p>Built here rather than pulled from a context on purpose: this test is about the walk, and
     * a Spring slice would hide a wiring mistake behind an autowired list. The context test covers
     * the wiring.
     */
    private List<FinancialTableSupport> supports() {
        FedFinancialSourcesResolver fedSources = subject -> StubFinstar.FED_SOURCES;
        return List.of(
                new EcbFinancialTableSupport(FinancialsResolver.none(),
                        new EcbFinancialCalculator(new FinancialCalculationDomainService())),
                new FedFinancialTableSupport(fedSources, new AplcFinancialCalculator()),
                new FedFinancialTableSupport(fedSources, new ReitFinancialCalculator()));
    }

    // ================================================================== Project Finance

    @Test
    @DisplayName("Project Finance, leveraged and WL/DD -> high risk")
    void projectFinanceLeveragedAndWatchList() {
        Walk walk = walk(answers()
                .lbo("NO").subsidiary().noSupportingEntity()
                .industry("PROJECT_FINANCE")
                .put("Q-D01", "2026-03-31")
                .put("Q-PF01", "YES")           // leveraged
                .put("Q-WD01", "YES")           // WL/DD -> classification is computed as HIGH
                .put("Q-PF04", "DEBT_SERVICE")
                .put("Q-PF05", "POWER_ASSETS")
                .put("Q-PF06", "Assessed against the 2026 policy."));

        walk.assertTerminalWith("FED_LEVERAGED_HIGH_RISK");
        // The classification question is COMPUTED on this branch; the analyst is never asked.
        assertThat(walk.result.computedAnswers()).containsEntry("Q-PF02", "HIGH");
        assertThat(walk.result.path()).doesNotContain("Q-PF03");
        // Project Finance declares no financial table, so nothing is computed and nothing blocks.
        assertThat(walk.table.computed()).isEmpty();
    }

    @Test
    @DisplayName("Project Finance, leveraged, not WL/DD, analyst says low -> low risk")
    void projectFinanceLeveragedAnalystChoice() {
        Walk walk = walk(answers()
                .lbo("NO").subsidiary().noSupportingEntity()
                .industry("PROJECT_FINANCE")
                .put("Q-D01", "2026-03-31")
                .put("Q-PF01", "YES")
                .put("Q-WD01", "NO")            // not WL/DD -> the analyst classifies
                .put("Q-PF03", "LOW")
                .put("Q-PF04", "BULLET_TRANSACTION")
                .put("Q-PF05", "INFRASTRUCTURE_TOLL_ROADS")
                .put("Q-PF06", "Assessed."));

        walk.assertTerminalWith("FED_LEVERAGED_LOW_RISK");
        assertThat(walk.result.path()).contains("Q-PF03");
    }

    @Test
    @DisplayName("Project Finance, not leveraged -> no classification is asked at all")
    void projectFinanceNotLeveraged() {
        Walk walk = walk(answers()
                .lbo("NO").subsidiary().noSupportingEntity()
                .industry("PROJECT_FINANCE")
                .put("Q-D01", "2026-03-31")
                .put("Q-PF01", "NO")
                .put("Q-WD01", "NO")
                .put("Q-PF04", "CASH_FLOW_RECAPTURE")
                .put("Q-PF05", "GENERAL_PROJECT_OBLIGOR_POWER")
                .put("Q-PF06", "Not leveraged."));

        walk.assertTerminalWith("FED_NOT_LEVERAGED");
        assertThat(walk.result.path()).doesNotContain("Q-PF02", "Q-PF03");
    }

    // ================================================================== APLC

    @Test
    @DisplayName("APLC, highly secured debt is 100% -> not leveraged, whatever the ratio says")
    void aplcFullySecuredShortCircuits() {
        Walk walk = walk(aplcAnswers().put("Q-HS01", "YES"));

        // First line of the cascade, and it wins before any ratio is looked at.
        walk.assertTerminalWith("FED_NOT_LEVERAGED");
    }

    @Test
    @DisplayName("APLC, debt/equity above the threshold and WL/DD -> high risk")
    void aplcAboveThresholdAndWatchList() {
        Walk walk = walk(aplcAnswers()
                .put("Q-HS01", "NO")
                .put("Q-WD01", "YES")
                .put("Q-F-APLC.aplcEquityHeldSPVs", "50"));

        // Applicable funded debt 1000 - 0 = 1000 over adjusted equity 300 - 50 = 250, so 4x,
        // which is below the 5x leverage threshold... but Q01 = NO and the ratio is not negative,
        // so the gate fails and the obligor is not leveraged. Spelled out because this is the
        // case a reader will want to check by hand.
        walk.assertTerminalWith("FED_NOT_LEVERAGED");
    }

    @Test
    @DisplayName("APLC, equity cancelled out -> the ratio is withheld and the save is refused")
    void aplcZeroEquityBlocks() {
        Walk walk = walk(aplcAnswers()
                .put("Q-HS01", "NO")
                .put("Q-WD01", "NO")
                .put("Q-F-APLC.aplcEquityHeldSPVs", "300"));   // 300 - 300 = 0

        // The zero IS published — the analyst has to see what blocked them.
        assertThat(walk.table.computed()).containsEntry("aplcAdjustedBookValueOfEquity", "0");
        // The ratio is not, so the mandatory box is unanswered and the walk stops at the table.
        assertThat(walk.table.computed()).doesNotContainKey("aplcDebtToEquityRatio");
        assertThat(walk.result.state()).isEqualTo(TraversalState.PENDING_INPUT);
        walk.assertViolation("FED_APLC_ADJ_BOOK_VALUE_EQUITY_ZERO");
    }

    // ================================================================== REITs

    @Test
    @DisplayName("REIT, leverage test fails -> not leveraged")
    void reitLeverageTestFails() {
        Walk walk = walk(reitAnswers().put("Q-RT03", "SPV"));

        // SPV is the first line of Q-RT20's rules and settles it without reading a ratio.
        walk.assertTerminalWith("FED_NOT_LEVERAGED");
        assertThat(walk.result.computedAnswers()).containsEntry("Q-RT20", "NO");
    }

    @Test
    @DisplayName("REIT, a counterparty with no Moody's rating still finishes")
    void reitMissingRatingDoesNotStall() {
        // The regression this guards: an absent rating used to contribute no entry, leaving a
        // COMPUTED question unanswered and halting the walk with NO message, because
        // stoppedAtUnanswered skips COMPUTED. It now renders "/" and the analysis carries on.
        Walk walk = walk(reitAnswers().put("Q-RT03", "SPV").put("Q-IG01", "YES"));

        assertThat(walk.result.state()).isEqualTo(TraversalState.TERMINAL);
        assertThat(walk.result.computedAnswers()).containsEntry("Q-RT12", "/");
    }

    // ================================================================== Others

    @Test
    @DisplayName("Others ends the form with no flags and the worksheet message")
    void othersEndsWithAWarning() {
        Walk walk = walk(answers().lbo("NO").subsidiary().noSupportingEntity().industry("OTHERS"));

        assertThat(walk.result.state()).isEqualTo(TraversalState.TERMINAL);
        assertThat(walk.result.flags()).isEmpty();
        walk.assertViolation("FED_OTHERS_USE_WORKSHEET");
    }

    // ================================================================== the status block

    @Test
    @DisplayName("a supporting entity routes to the parent lookup and ends there")
    void supportingEntityEndsAtTheLookup() {
        Walk walk = walk(answers()
                .lbo("YES").subsidiary()
                .put("Q-S02", "YES")
                .put("Q-S06", "87654321"));

        walk.assertTerminalWith("CALCULATION_AT_BG_OR_OTHER_LE");
        // Main Industry is never reached on this route.
        assertThat(walk.result.path()).doesNotContain("Q-MI01");
        // Q01 fills the LBO flag on the way past, even though the form ends elsewhere.
        assertThat(walk.result.flags()).containsEntry("fedLboFlag", "YES");
    }

    @Test
    @DisplayName("fedLeverageRatio is declared but never set in v14")
    void leverageRatioFlagIsNeverSet() {
        // Its only source is goCeFedLeverageRatio on the General Obligor / Utilities path, which
        // is v15. Deliberate, and asserted so an always-empty flag is not read as a bug later.
        Walk walk = walk(reitAnswers().put("Q-RT03", "SPV"));

        assertThat(fed.flags()).containsKey("fedLeverageRatio");
        assertThat(walk.result.flags()).doesNotContainKey("fedLeverageRatio");
    }

    // ================================================================== the chain

    /**
     * Runs the same sequence {@code GetLeverageFormStateUseCase.project(...)} runs.
     *
     * <p>Order is load-bearing and is the thing under test: checklists settle, dates canonicalise,
     * the tables compute and their figures are written OVER the posted answers, and only then does
     * anything read the map. Traversal, validation and the frozen snapshot all see the same
     * strings, which is what stops the screen and the record disagreeing.
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

        return new Walk(table, result, violations);
    }

    // ---------------------------------------------------------------- SEAM 2
    // Reference data the walk reads: COUNTERPARTY/PARENT and the three ratings. Stubbed rather
    // than mocked so a test reads as a table of facts. Point the REIT cases at a counterparty with
    // no Moody's rating to exercise the "/" path.
    private Map<String, String> derivedValues() {
        return Map.of(
                "COUNTERPARTY/PARENT", "87654321 - ACME HOLDING SA",
                "COUNTERPARTY/BNPP_COUNTERPARTY_RATING", "A- (2026-03-31)",
                "COUNTERPARTY/SP_ISSUER_RATING", "BBB+ (2026-01-15)",
                "COUNTERPARTY/MOODYS_ISSUER_RATING", "/");
    }

    /** What a walk produced, with the assertions worth repeating. */
    private record Walk(FinancialTable table, TraversalResult result, List<ValidationMessage> violations) {

        void assertTerminalWith(String flagValue) {
            assertThat(result.state())
                    .describedAs("path: %s", result.path())
                    .isEqualTo(TraversalState.TERMINAL);
            assertThat(result.flags().values()).contains(flagValue);
        }

        void assertViolation(String messageKey) {
            assertThat(violations).extracting(ValidationMessage::messageKey).contains(messageKey);
        }
    }

    // ================================================================== fixtures

    private Answers answers() {
        return new Answers();
    }

    private Answers aplcAnswers() {
        return answers().lbo("NO").subsidiary().noSupportingEntity()
                .industry("APLC")
                .put("Q-D01", "2026-03-31")
                .put("Q-HS02", "NO").put("Q-IG01", "NO").put("Q-IG02", "NO")
                .put("Q-APLC02", "Reviewed.");
        // aplcTotalBSDebt and aplcBookValueOfEquity come from FINSTAR; see StubFinstar.
    }

    private Answers reitAnswers() {
        return answers().lbo("NO").subsidiary().noSupportingEntity()
                .industry("REIT")
                .put("Q-D01", "2026-03-31")
                .put("Q-RT01", "YES")
                .put("Q-HS02", "NO").put("Q-IG01", "NO").put("Q-IG02", "NO").put("Q-WD01", "NO")
                .put("Q-F-REIT.reitOriginationDate", "2024-06-30")
                .put("Q-F-REIT.reitCommittedLoanFacility", "400")
                .put("Q-F-REIT.reitHighlySecuredPortionCommittedTotalDebt", "100")
                .put("Q-F-REIT.reitNetOperatingIncome", "40")
                .put("Q-F-REIT.reitMarketCapitalization", "680")
                .put("Q-RT21", "Reviewed.");
    }

    /** A readable answer map: each method names what the analyst did, not which key it writes. */
    private static final class Answers {

        private final Map<String, String> values = new LinkedHashMap<>();

        Answers put(String key, String value) {
            values.put(key, value);
            return this;
        }

        Answers lbo(String yesNo) {
            return put("Q01", yesNo);
        }

        Answers subsidiary() {
            return put("Q-S01", "SUBSIDIARY");
        }

        Answers noSupportingEntity() {
            return put("Q-S02", "NO");
        }

        Answers industry(String code) {
            return put("Q-MI01", code);
        }

        Map<String, String> map() {
            return Map.copyOf(values);
        }
    }
}
