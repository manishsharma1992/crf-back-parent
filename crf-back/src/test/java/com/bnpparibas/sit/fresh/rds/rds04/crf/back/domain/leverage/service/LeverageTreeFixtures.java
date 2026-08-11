package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Shared builders for the leverage domain-service tests.
 *
 * <p>Deliberately terse so a test reads as a TREE SHAPE rather than constructor noise — the
 * records now have seventeen components between them, and spelling those out inline would bury
 * the rule each test exists to pin.
 *
 * <p>{@link #def} ships a working flags catalogue, so a happy-path fixture passes validation
 * without every test having to declare one. Tests about the catalogues build their own.
 */
public final class LeverageTreeFixtures {

    private LeverageTreeFixtures() {
    }

    // ------------------------------------------------------------------ labels

    public static LabelDetails ld(String text) {
        return LabelDetails.of(text);
    }

    public static LocalizedQuestionLabel ql(String en, String fr) {
        return new LocalizedQuestionLabel(en == null ? null : ld(en), fr == null ? null : ld(fr));
    }

    public static LocalizedLabel ll(String en, String fr) {
        return new LocalizedLabel(en, fr);
    }

    public static Bullet bullet(String text, Bullet... children) {
        return new Bullet(text, List.of(children));
    }

    // ------------------------------------------------------------------ parts

    public static Option opt(String value) {
        return new Option(value, ll(value, value));
    }

    public static ChecklistItem item(String key) {
        return new ChecklistItem(key, ll(key, key));
    }

    public static ChecklistItem item(String key, String en, String fr) {
        return new ChecklistItem(key, ll(en, fr));
    }

    // ------------------------------------------------------------------ data fields
    //
    // Component order: key, group, label, note, type, mandatory, editable, visible,
    //                  derivedFrom, formula, fillsFlag

    /** A box the analyst types into — one of the ten adjustments. */
    public static DataField field(String key) {
        return new DataField(key, "G", ll(key, key), null, DataFieldType.NUMERIC,
                true, true, true, null, null, null);
    }

    /** A box the domain layer calculates — read-only, never typed. */
    public static DataField calcField(String key) {
        return new DataField(key, "G", ll(key, key), null, DataFieldType.NUMERIC,
                false, false, true, "CALC/" + key, null, null);
    }

    /** A box read from FINSTAR — shown, read-only. EBITDA and Gross Debt. */
    public static DataField sourceField(String key) {
        return new DataField(key, "G", ll(key, key), null, DataFieldType.NUMERIC,
                true, false, true, "FINANCIALS/" + key, null, null);
    }

    /**
     * The {@code netDebt} shape: part of the record, frozen with the answer, never rendered.
     * Legal only because it is system-filled and nobody types into it.
     */
    public static DataField hiddenSourceField(String key) {
        return new DataField(key, "G", ll(key, key), null, DataFieldType.NUMERIC,
                true, false, false, "FINANCIALS/" + key, null, null);
    }

    /** Hidden AND editable — the analyst could never reach it. */
    public static DataField hiddenEditableField(String key) {
        return new DataField(key, "G", ll(key, key), null, DataFieldType.NUMERIC,
                true, true, false, null, null, null);
    }

    /** Hidden with nothing to fill it — the value could never arrive. */
    public static DataField hiddenOrphanField(String key) {
        return new DataField(key, "G", ll(key, key), null, DataFieldType.NUMERIC,
                false, false, false, null, null, null);
    }

    /** A non-numeric box, for the rules that only make sense against a number. */
    public static DataField textField(String key) {
        return new DataField(key, "G", ll(key, key), null, DataFieldType.TEXT,
                false, true, true, null, null, null);
    }

    public static DataField fieldFillingFlag(String key, String flagKey) {
        return new DataField(key, "G", ll(key, key), null, DataFieldType.NUMERIC,
                true, false, true, "CALC/" + key, null, flagKey);
    }

    // ------------------------------------------------------------------ conditions

    /** This question's own answer. */
    public static Condition eq(String value) {
        return new Condition(false, null, null, value, null, null, null, null, null);
    }

    /** Another question's answer: {@code Q01 is NO}. */
    public static Condition other(String questionKey, String value) {
        return new Condition(false, questionKey, null, value, null, null, null, null, null);
    }

    public static Condition dflt() {
        return Condition.defaultBranch();
    }

    public static Condition agg(Aggregate aggregate) {
        return new Condition(false, null, null, null, null, aggregate, null, null, null);
    }

    public static Condition in(String questionKey, List<String> values) {
        return new Condition(false, questionKey, null, null, values, null, null, null, null);
    }

    /** Ranges over another question's numeric answer. */
    public static Condition ranges(String questionKey, Range... rs) {
        return new Condition(false, questionKey, null, null, null, null, List.of(rs), null, null);
    }

    /** Ranges over a DATA_ENTRY box: {@code field ecbLeverageRatio range [...]}. */
    public static Condition fieldRanges(String fieldKey, Range... rs) {
        return new Condition(false, null, fieldKey, null, null, null, List.of(rs), null, null);
    }

    /** {@code field totalEcbDebt > 4 x field adjustedEbitda}. */
    public static Condition compare(String left, ComparisonOperator op, String factor, String right) {
        return new Condition(false, null, null, null, null, null, null,
                new Comparison(left, op, new BigDecimal(factor), right), null);
    }

    public static Condition allOf(Condition... children) {
        return new Condition(false, null, null, null, null, null, null, null, List.of(children));
    }

    // ------------------------------------------------------------------ ranges

    public static Range range(Double gte, Double gt, Double lte, Double lt) {
        return new Range(bd(gte), bd(gt), bd(lte), bd(lt));
    }

    /** Closed band, inclusive both ends — {@code [4..6]}. */
    public static Range band(double low, double high) {
        return new Range(bd(low), null, bd(high), null);
    }

    /** Half-open band — {@code [0 .. <4]}, the one that ends the ECB form. */
    public static Range halfOpen(double low, double highExclusive) {
        return new Range(bd(low), null, null, bd(highExclusive));
    }

    public static Range below(double value) {
        return new Range(null, null, null, bd(value));
    }

    public static Range above(double value) {
        return new Range(null, bd(value), null, null);
    }

    private static BigDecimal bd(Double d) {
        return d == null ? null : BigDecimal.valueOf(d);
    }

    // ------------------------------------------------------------------ branches

    public static Branch goTo(Condition when, String target) {
        return new Branch(when, target, null);
    }

    /** Continues, AND fills a flag on the way — legal now that flags are not terminal-only. */
    public static Branch goToFlags(Condition when, String target, Map<String, String> flags) {
        return new Branch(when, target, new Effect(null, flags, false));
    }

    /** Ends with an outcome — PRELIMINARY. */
    public static Branch end(Condition when, RecommendationOutcome outcome) {
        return new Branch(when, null, new Effect(outcome, Map.of(), true));
    }

    /** Ends with flags — ECB / FED, which have no outcome. */
    public static Branch endFlags(Condition when, Map<String, String> flags) {
        return new Branch(when, null, new Effect(null, flags, true));
    }

    public static Branch endFlags(Condition when, RecommendationOutcome outcome, Map<String, String> flags) {
        return new Branch(when, null, new Effect(outcome, flags, true));
    }

    public static ValueRule rule(Condition when, String value) {
        return new ValueRule(when, value);
    }

    // ------------------------------------------------------------------ questions

    public static Question sc(String key, List<Branch> branches) {
        return scOpts(key, List.of(opt("YES"), opt("NO")), branches);
    }

    public static Question scOpts(String key, List<Option> options, List<Branch> branches) {
        return question(key, QuestionType.SINGLE_CHOICE, false, true, null, List.of(), null,
                options, List.of(), List.of(), branches, null);
    }

    public static Question scFillingFlag(String key, List<Option> options, List<Branch> branches, String flagKey) {
        return question(key, QuestionType.SINGLE_CHOICE, false, true, null, List.of(), null,
                options, List.of(), List.of(), branches, flagKey);
    }

    public static Question bool(String key, List<Branch> branches) {
        return question(key, QuestionType.BOOLEAN, false, true, null, List.of(), null,
                List.of(opt("YES"), opt("NO")), List.of(), List.of(), branches, null);
    }

    public static Question numeric(String key, List<Branch> branches) {
        return question(key, QuestionType.NUMERIC, false, true, null, List.of(), null,
                List.of(), List.of(), List.of(), branches, null);
    }

    public static Question checklist(String key, List<ChecklistItem> items, List<Branch> branches) {
        return question(key, QuestionType.CHECKLIST, false, true, null, List.of(), null,
                List.of(), items, List.of(), branches, null);
    }

    public static Question dataEntry(String key, List<DataField> fields, List<Branch> branches) {
        return question(key, QuestionType.DATA_ENTRY, false, true, null, List.of(), null,
                List.of(), List.of(), fields, branches, null);
    }

    /** COMPUTED with a single fixed source — Q-S05's parent company. */
    public static Question computedDerived(String key, String derivedFrom, List<Branch> branches) {
        return question(key, QuestionType.COMPUTED, true, false, derivedFrom, List.of(), null,
                List.of(), List.of(), List.of(), branches, null);
    }

    /** COMPUTED from earlier answers — Q-S04, Q-Q01, Q-Q02. */
    public static Question computedRuled(String key, List<Option> options,
                                         List<ValueRule> rules, List<Branch> branches) {
        return question(key, QuestionType.COMPUTED, true, false, null, rules, null,
                options, List.of(), List.of(), branches, null);
    }

    /** Displayed, never walked — exempt from the reachability rule. */
    public static Question computedOutput(String key) {
        return question(key, QuestionType.COMPUTED, true, false, "OUTCOME", List.of(), null,
                List.of(), List.of(), List.of(), List.of(), null);
    }

    public static Question lookup(String key, String source, List<Branch> branches) {
        return question(key, QuestionType.LOOKUP, false, true, null, List.of(), null,
                List.of(new Option(source, null)), List.of(), List.of(), branches, null);
    }

    public static Question prefilled(String key, String prefillFrom, List<Branch> branches) {
        return question(key, QuestionType.SINGLE_CHOICE, false, true, null, List.of(), prefillFrom,
                List.of(opt("YES"), opt("NO")), List.of(), List.of(), branches, null);
    }

    /** Escape hatch for tests that need a shape the helpers above do not cover. */
    public static Question question(String key, QuestionType type, boolean computed, boolean editable,
                                    String derivedFrom, List<ValueRule> valueRules, String prefillFrom,
                                    List<Option> options, List<ChecklistItem> items, List<DataField> fields,
                                    List<Branch> branches, String fillsFlag) {
        return new Question(key, type, true, computed, editable, derivedFrom, valueRules, prefillFrom,
                ql(key + " en", key + " fr"), null, null, options, items, fields, branches, fillsFlag);
    }

    // ------------------------------------------------------------------ catalogues

    /** A field-scoped validation message: {@code SOURCE_EMPTY} on {@code ebitda}, and friends. */
    public static ValidationMessage message(String questionKey, String fieldKey,
                                            ValidationRule rule, String messageKey) {
        return new ValidationMessage(questionKey, fieldKey, rule, messageKey, Severity.ERROR,
                ll(messageKey + " en", messageKey + " fr"));
    }

    public static Map<String, FlagDefinition> standardFlags() {
        return Map.of(
                "ecbLeveragedFlag", new FlagDefinition("ecbLeveragedFlag", ll("Leveraged Flag", "Flag Leveraged"),
                        FlagStorage.CODE, "LEVERAGED_FLAG"),
                "ecbCovenantStructure", new FlagDefinition("ecbCovenantStructure", ll("Covenant", "Covenant"),
                        FlagStorage.CODE, "COVENANT_STRUCTURE"),
                "ecbLeverageRatio", new FlagDefinition("ecbLeverageRatio", ll("Ratio", "Ratio"),
                        FlagStorage.NUMBER, null),
                "ecbLboFlag", new FlagDefinition("ecbLboFlag", ll("LBO", "LBO"), FlagStorage.BOOLEAN, null));
    }

    public static Map<String, List<FlagValue>> standardFlagValues() {
        Set<LeverageFormType> ecb = Set.of(LeverageFormType.ECB);
        Set<LeverageFormType> both = Set.of(LeverageFormType.values());
        return Map.of(
                "LEVERAGED_FLAG", List.of(
                        new FlagValue("LEVERAGED_FLAG", "ECB_NOT_LEVERAGED", 0, ll("ECB Not Leveraged", ""), ecb),
                        new FlagValue("LEVERAGED_FLAG", "ECB_LEVERAGED", 1, ll("ECB Leveraged", ""), ecb),
                        new FlagValue("LEVERAGED_FLAG", "INR", 2, ll("INR", "INR"), both),
                        new FlagValue("LEVERAGED_FLAG", "FED_NOT_LEVERAGED", 3, ll("FED Not Leveraged", ""),
                                Set.of(LeverageFormType.FED))),
                "COVENANT_STRUCTURE", List.of(
                        new FlagValue("COVENANT_STRUCTURE", "NONE", 0, ll("No Covenant", "Sans covenant"), ecb),
                        new FlagValue("COVENANT_STRUCTURE", "FULL", 1, ll("Full Covenant", "Full Covenant"), ecb)));
    }

    public static Map<RecommendationOutcome, Outcome> standardOutcomes() {
        return Map.of(
                RecommendationOutcome.NOT_REQUIRED, new Outcome("NR", List.of(), Map.of()),
                RecommendationOutcome.ECB, new Outcome("ECB", List.of(LeverageFormType.ECB),
                        Map.of("fedLeveragedFlag", "INR")),
                RecommendationOutcome.ECB_AND_FED, new Outcome("ECB+FED",
                        List.of(LeverageFormType.FED, LeverageFormType.ECB), Map.of()));
    }

    // ------------------------------------------------------------------ definitions

    /** PRELIMINARY v1, PUBLISHED, with the standard catalogues. */
    public static DecisionTreeDefinition def(String entry, Question... questions) {
        return defWith(LeverageFormType.PRELIMINARY, 1, DefinitionStatus.PUBLISHED, entry, questions);
    }

    /** An ECB definition — no outcomes, since ECB expresses its result as flags. */
    public static DecisionTreeDefinition ecbDef(String entry, Question... questions) {
        return new DecisionTreeDefinition(LeverageFormType.ECB, 1, DefinitionStatus.PUBLISHED, "EN",
                List.of("EN", "FR"), entry, sections(questions), Map.of(),
                standardFlags(), standardFlagValues(), List.of(), List.of());
    }

    /** An ECB definition carrying validation messages — the Q-F01 rules. */
    public static DecisionTreeDefinition ecbDefWithMessages(String entry, List<ValidationMessage> messages,
                                                            Question... questions) {
        return new DecisionTreeDefinition(LeverageFormType.ECB, 1, DefinitionStatus.PUBLISHED, "EN",
                List.of("EN", "FR"), entry, sections(questions), Map.of(),
                standardFlags(), standardFlagValues(), messages, List.of());
    }

    public static DecisionTreeDefinition defWith(LeverageFormType formType, int version,
                                                 DefinitionStatus status, String entry, Question... questions) {
        return new DecisionTreeDefinition(formType, version, status, "EN", List.of("EN", "FR"), entry,
                sections(questions), standardOutcomes(), standardFlags(), standardFlagValues(),
                List.of(), List.of());
    }

    /** Full control, for catalogue tests. */
    public static DecisionTreeDefinition defWithCatalogues(LeverageFormType formType, String entry,
                                                           List<Question> questions,
                                                           Map<String, FlagDefinition> flags,
                                                           Map<String, List<FlagValue>> flagValues,
                                                           List<ValidationMessage> messages,
                                                           List<InfoPanel> panels) {
        return new DecisionTreeDefinition(formType, 1, DefinitionStatus.PUBLISHED, "EN", List.of("EN", "FR"),
                entry, List.of(new Section("s", 1, ll("S", "S"), questions)),
                standardOutcomes(), flags, flagValues, messages, panels);
    }

    private static List<Section> sections(Question... questions) {
        return List.of(new Section("s", 1, ll("S", "S"), Arrays.asList(questions)));
    }
}
