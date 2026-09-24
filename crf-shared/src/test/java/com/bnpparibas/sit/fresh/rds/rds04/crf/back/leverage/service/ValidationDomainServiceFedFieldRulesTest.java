package com.bnpparibas.sit.fresh.rds.rds04.crf.back.leverage.service;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Field rules on a form that declares TWO financial tables.
 *
 * <p>ECB has one, so nothing until now exercised what happens when a definition holds a table the
 * analyst never reached. FED holds two — {@code Q-F-APLC} and {@code Q-F-REIT} — on mutually
 * exclusive Main Industry paths, and both are resolved on every request. Everything here is about
 * the second one staying silent.
 *
 * <p>Kept apart from the ECB field-rules test rather than folded into it: those are
 * characterisation tests for a signed-off calculation, and their worth is that they do not move.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ValidationDomainServiceFedFieldRulesTest {

    private static final String APLC = "Q-F-APLC";
    private static final String REIT = "Q-F-REIT";

    private ValidationDomainService validation;

    @BeforeAll
    void setUp() {
        validation = new ValidationDomainService();
    }

    // ------------------------------------------------------------------ fixtures

    private static LocalizedLabel ll(String text) {
        return new LocalizedLabel(text, text);
    }

    private static LocalizedQuestionLabel label(String text) {
        return new LocalizedQuestionLabel(LabelDetails.of(text), LabelDetails.of(text));
    }

    /** Prefilled AND editable — the FED shape, unlike ECB's read-only sources. */
    private static DataField prefilled(String key, String column) {
        return new DataField(key, "G", ll(key), null, DataFieldType.NUMERIC,
                true, true, true, "FINANCIALS/" + column, null, null);
    }

    private static DataField typed(String key) {
        return new DataField(key, "G", ll(key), null, DataFieldType.NUMERIC,
                true, true, true, null, null, null);
    }

    private static DataField calculated(String key) {
        return new DataField(key, "G", ll(key), null, DataFieldType.NUMERIC,
                true, false, true, "CALC/" + key, null, null);
    }

    private static Question table(String key, List<DataField> fields) {
        return new Question(key, QuestionType.DATA_ENTRY, true, false, true, null, List.of(), null,
                label(key), null, null, List.of(), List.of(), fields, List.of(), null);
    }

    private static Question aplcTable() {
        return table(APLC, List.of(
                prefilled("aplcBookValueOfEquity", "total_equity"),
                typed("aplcEquityHeldSPVs"),
                calculated("aplcAdjustedBookValueOfEquity"),
                calculated("aplcDebtToEquityRatio")));
    }

    private static Question reitTable() {
        return table(REIT, List.of(
                typed("reitNetOperatingIncome"),
                typed("reitMarketCapitalization"),
                calculated("reitTotalCommittedDebtPerDefinition"),
                calculated("reitAdjustedTotalCommittedDebt")));
    }

    private static ValidationMessage message(String questionKey, ValidationRule rule,
                                             String fieldKey, String messageKey) {
        return new ValidationMessage(questionKey, fieldKey, rule, messageKey, Severity.ERROR,
                ll(messageKey));
    }

    /** Both tables in one definition, exactly as the FED workbook declares them. */
    private static DecisionTreeDefinition bothTables(List<ValidationMessage> messages) {
        return new DecisionTreeDefinition(LeverageFormType.FED, 14, DefinitionStatus.PUBLISHED,
                "EN", List.of("EN", "FR"), APLC,
                List.of(new Section("MAIN", 1, ll("M"), List.of(aplcTable(), reitTable()))),
                Map.of(), Map.of(), Map.of(), messages, List.of());
    }

    private static TraversalResult reached(String... path) {
        return new TraversalResult(TraversalState.PENDING_INPUT, null,
                Map.of(), Map.of(), Map.of(), null, List.of(path));
    }

    private List<String> firedKeys(List<ValidationMessage> rows, Map<String, String> answers,
                                   Map<String, String> computed, String... path) {
        return validation.violations(bothTables(rows), answers, reached(path), null, computed)
                .stream()
                .map(ValidationMessage::messageKey)
                .toList();
    }

    // ------------------------------------------------------------------ the table not taken

    private static final List<ValidationMessage> BOTH_TABLES_ROWS = List.of(
            message(APLC, ValidationRule.MUST_NOT_BE_ZERO, "aplcAdjustedBookValueOfEquity",
                    "FED_APLC_ADJ_BOOK_VALUE_EQUITY_ZERO"),
            message(REIT, ValidationRule.MANDATORY, "reitNetOperatingIncome", "FED_REIT_NOI_MANDATORY"),
            message(REIT, ValidationRule.MUST_NOT_BE_ZERO, "reitTotalCommittedDebtPerDefinition",
                    "FED_REIT_TCDPD_ZERO"));

    @Test
    @DisplayName("an APLC analysis never hears about REIT's boxes")
    void theOtherTableIsSilent() {
        // Every REIT input is absent and every REIT calculated figure is zero — which is exactly
        // what happens on a real APLC analysis, because the resolver computes BOTH tables and the
        // REIT one has nothing to work from. If the path filter were missing, the analyst would
        // get errors about Net Operating Income on a form that never showed it.
        List<String> fired = firedKeys(BOTH_TABLES_ROWS,
                Map.of(APLC + ".aplcBookValueOfEquity", "300",
                        APLC + ".aplcEquityHeldSPVs", "300"),
                Map.of("aplcAdjustedBookValueOfEquity", "0",
                        "reitTotalCommittedDebtPerDefinition", "0"),
                APLC);

        assertEquals(List.of("FED_APLC_ADJ_BOOK_VALUE_EQUITY_ZERO"), fired);
    }

    @Test
    @DisplayName("and a REIT analysis never hears about APLC's")
    void andTheOtherWayRound() {
        List<String> fired = firedKeys(BOTH_TABLES_ROWS,
                Map.of(REIT + ".reitMarketCapitalization", "1000"),
                Map.of("aplcAdjustedBookValueOfEquity", "0",
                        "reitTotalCommittedDebtPerDefinition", "0"),
                REIT);

        assertEquals(List.of("FED_REIT_NOI_MANDATORY", "FED_REIT_TCDPD_ZERO"), fired);
    }

    @Test
    @DisplayName("a calculated box is judged by its own field key, whatever form it belongs to")
    void calculatedBoxesAreFormAgnostic() {
        // The service used to read a calculated box out of ComputedFinancials, which knew five ECB
        // keys and nothing else — so every FED calculated box read as empty, MUST_NOT_BE_ZERO
        // could never fire, and MANDATORY fired on all of them at once. This is that regression.
        List<String> fired = firedKeys(
                List.of(message(APLC, ValidationRule.MUST_NOT_BE_ZERO,
                        "aplcAdjustedBookValueOfEquity", "FED_APLC_ADJ_BOOK_VALUE_EQUITY_ZERO")),
                Map.of(),
                Map.of("aplcAdjustedBookValueOfEquity", "0"),
                APLC);

        assertEquals(List.of("FED_APLC_ADJ_BOOK_VALUE_EQUITY_ZERO"), fired);
    }

    @Test
    @DisplayName("a withheld figure is still judged — absent from the screen, present to the rules")
    void withheldFiguresAreStillJudged() {
        // The point of FinancialTable.computed() staying COMPLETE while the overlay is trimmed.
        List<String> fired = firedKeys(
                List.of(message(REIT, ValidationRule.MUST_NOT_BE_ZERO,
                        "reitTotalCommittedDebtPerDefinition", "FED_REIT_TCDPD_ZERO")),
                Map.of(),                                            // nothing published
                Map.of("reitTotalCommittedDebtPerDefinition", "0"),  // but computed
                REIT);

        assertEquals(List.of("FED_REIT_TCDPD_ZERO"), fired);
    }

    @Test
    @DisplayName("no computed figures at all leaves the calculated rules quiet")
    void noComputedFigures() {
        assertTrue(firedKeys(BOTH_TABLES_ROWS, Map.of(), Map.of(), APLC).isEmpty());
        assertTrue(firedKeys(BOTH_TABLES_ROWS, Map.of(), null, APLC).isEmpty());
    }

    // ------------------------------------------------------------------ the key-uniqueness trap

    @Test
    @DisplayName("field keys must stay unique across the whole form, not just within a table")
    void fieldKeysMustBeUniqueAcrossTheForm() {
        // ValidationDomainService.indexBoxes uses putIfAbsent keyed by the BARE field key across
        // the entire definition, and DecisionTreeDefinition.field(key) assumes the same. Two
        // tables sharing "totalBSDebt" would bind every row for it to whichever question is
        // declared first, and the other table's rule would silently never fire.
        //
        // That is why the FED workbook prefixes its keys aplc* and reit*. This test fails if
        // someone tidies the prefixes away, which is otherwise invisible until a rule goes quiet
        // in production.
        DecisionTreeDefinition definition = bothTables(List.of());

        List<String> keys = definition.questions().stream()
                .flatMap(question -> question.fields().stream())
                .map(DataField::key)
                .toList();

        assertEquals(keys.size(), keys.stream().distinct().count(),
                "two boxes share a field key: " + keys);
    }
}

