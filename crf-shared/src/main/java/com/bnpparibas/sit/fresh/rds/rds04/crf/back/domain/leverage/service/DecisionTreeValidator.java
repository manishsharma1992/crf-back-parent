package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LabelDetails;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LocalizedLabel;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.math.BigDecimal;
import java.util.*;

import static com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.ValidationResult.Error;
import static com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.ValidationResult.Error.Aspect;

/**
 * Validates the STRUCTURE of a decision tree before it is published.
 *
 * <p><b>Purity.</b> No I/O, no Spring, no Excel. In: a {@link DecisionTreeDefinition}. Out: a list
 * of domain-located {@link Error}s. This is the transcription safety net — Risk &amp; Finance
 * approve the question CONTENT upstream, but a human still types it into a spreadsheet, so the
 * wiring is re-checked on every import.
 *
 * <p><b>Never throws on bad data.</b> A mistranscribed sheet can produce almost any shape of
 * object graph. Every per-question pass is wrapped so one malformed question cannot abort the
 * rest; an unexpected failure becomes an {@code INTERNAL_ERROR} entry.
 *
 * <p><b>Rules.</b>
 * <ol>
 *   <li>Form: entry declared and present; at least one question.</li>
 *   <li>Question keys unique; DATA_ENTRY field keys unique across the WHOLE form, because a
 *       condition names a field bare ({@code field ecbLeverageRatio}) without its question.</li>
 *   <li>Labels: EN and FR present.</li>
 *   <li>Branch shape: terminal XOR onward; every {@code goTo} resolves; every branch has a
 *       {@code when}.</li>
 *   <li>Conditions: composites recurse; {@code questionKey} and {@code fieldKey} resolve;
 *       {@code aggregate} only on the owning CHECKLIST; {@code ranges} and {@code comparison}
 *       only against numeric operands.</li>
 *   <li>Range routing needs a default — a numeric domain is continuous and cannot be enumerated.
 *       This closes the "0 &le; r &lt; 4" gap by construction.</li>
 *   <li>Type specifics: option coverage; CHECKLIST item + aggregate completeness; DATA_ENTRY
 *       fields and must-continue; COMPUTED carries {@code derivedFrom} XOR {@code valueRules}.</li>
 *   <li>Reachability + cycles from the entry, EXCEPT a display-only computed output
 *       ({@link Question#isDisplayOnlyOutput()}) which is shown, not walked.</li>
 *   <li>Catalogues: every outcome, flag, flag value, validation-message target and info-panel
 *       flag referenced anywhere is declared, and this form is allowed to set it.</li>
 * </ol>
 *
 * <p><b>Deliberately NOT checked:</b> {@code prefillFrom} names a question on ANOTHER form
 * ({@code FED/Q01}) and the validator sees one definition at a time. Only the SHAPE is checked
 * here; whether that answer exists is resolved at traversal time against the stored answers.
 */
@DomainDrivenDesign.DomainService
public final class DecisionTreeValidator {

    /** Stands in for a key the author left blank, so the error still has somewhere to point. */
    private static final String BLANK_KEY = "(blank)";
    private static final String FLAG_UNKNOWN = "FLAG_UNKNOWN";
    private static final String MESSAGE = "Message '";
    private static final String PANEL = "Panel '";

    public ValidationResult validate(DecisionTreeDefinition def) {
        List<Error> errors = new ArrayList<>();
        LeverageFormType ft = def == null ? null : def.formType();
        try {
            if (def == null) {
                errors.add(Error.form(null, "NULL_DEFINITION", "Definition is null"));
                return new ValidationResult(errors);
            }

            Map<String, Question> byKey = indexQuestions(def, errors);
            Map<String, DataField> fieldsByKey = indexFields(def, errors);
            validateEntry(def, byKey, errors);
            validateFlagCatalogue(def, errors);

            Ctx ctx = new Ctx(def, ft, byKey, fieldsByKey, errors);

            for (Question q : byKey.values()) {
                try {
                    validateQuestion(ctx, q);
                } catch (RuntimeException ex) {
                    errors.add(Error.question(ft, safeKey(q), Aspect.KEY, "INTERNAL_ERROR",
                            "Unexpected error validating this question: " + ex.getMessage()));
                }
            }

            validateReachabilityAndCycles(ctx);
            validateValidationMessages(ctx);
            validateInfoPanels(ctx);

        } catch (RuntimeException ex) {
            errors.add(Error.form(ft, "INTERNAL_ERROR", "Unexpected error during validation: " + ex.getMessage()));
        }
        return new ValidationResult(List.copyOf(errors));
    }

    /** Everything the per-question passes need, so signatures stay short. */
    private record Ctx(DecisionTreeDefinition def,
                       LeverageFormType ft,
                       Map<String, Question> byKey,
                       Map<String, DataField> fieldsByKey,
                       List<Error> errors) {
    }

    // ------------------------------------------------------------------ indexing & entry

    private Map<String, Question> indexQuestions(DecisionTreeDefinition def, List<Error> errors) {
        LeverageFormType ft = def.formType();
        Map<String, Question> byKey = new LinkedHashMap<>();
        for (Section section : nullToEmpty(def.sections())) {
            if (section == null) continue;
            for (Question q : nullToEmpty(section.questions())) {
                if (q == null) continue;
                if (!hasText(q.key())) {
                    errors.add(Error.question(ft, BLANK_KEY, Aspect.KEY, "BLANK_KEY", "Question key is blank"));
                    continue;
                }
                if (byKey.putIfAbsent(q.key(), q) != null) {
                    errors.add(Error.question(ft, q.key(), Aspect.KEY, "DUPLICATE_KEY", "Duplicate question key"));
                }
            }
        }
        if (byKey.isEmpty()) {
            errors.add(Error.form(ft, "NO_QUESTIONS", "The form has no questions"));
        }
        return byKey;
    }

    /**
     * Field keys must be unique across the FORM, not merely within their question: a condition
     * writes {@code field ecbLeverageRatio} with no question, so a duplicate would be ambiguous.
     */
    private Map<String, DataField> indexFields(DecisionTreeDefinition def, List<Error> errors) {
        LeverageFormType ft = def.formType();
        Map<String, DataField> byKey = new LinkedHashMap<>();
        Map<String, String> owner = new HashMap<>();
        for (Question q : def.questions()) {
            for (DataField f : nullToEmpty(q.fields())) {
                if (f == null || !hasText(f.key())) continue;
                String previous = owner.putIfAbsent(f.key(), q.key());
                if (previous != null) {
                    errors.add(Error.field(ft, q.key(), f.key(), "DATA_FIELD_DUPLICATE_IN_FORM",
                            "Field key '" + f.key() + "' is also used by question '" + previous
                                    + "'; conditions reference fields by key alone, so keys must be unique per form"));
                } else {
                    byKey.put(f.key(), f);
                }
            }
        }
        return byKey;
    }

    private void validateEntry(DecisionTreeDefinition def, Map<String, Question> byKey, List<Error> errors) {
        String entry = def.entryQuestion();
        if (!hasText(entry) || !byKey.containsKey(entry)) {
            errors.add(Error.form(def.formType(), "MISSING_ENTRY", "Entry question '" + entry + "' is not defined"));
        }
    }

    /** Every CODE flag must point at a value set that exists and is non-empty. */
    private void validateFlagCatalogue(DecisionTreeDefinition def, List<Error> errors) {
        LeverageFormType ft = def.formType();
        for (FlagDefinition flag : def.flags().values()) {
            if (flag == null) continue;
            if (flag.storage() != FlagStorage.CODE) continue;
            if (!hasText(flag.valueSet())) {
                errors.add(Error.catalogue(ft, Aspect.FLAGS_CATALOGUE, flag.key(), "FLAG_NO_VALUE_SET",
                        "Coded flag '" + flag.key() + "' declares no value set"));
            } else if (nullToEmpty(def.flagValueSets().get(flag.valueSet())).isEmpty()) {
                errors.add(Error.catalogue(ft, Aspect.FLAG_VALUES, flag.valueSet(), "FLAG_VALUE_SET_UNKNOWN",
                        "Value set '" + flag.valueSet() + "' of flag '" + flag.key() + "' has no values"));
            }
        }
    }

    // ------------------------------------------------------------------ per-question

    private void validateQuestion(Ctx ctx, Question q) {
        if (q.type() == null) {
            ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.TYPE, "MISSING_TYPE", "Question type is missing"));
        }
        validateLabels(ctx, q);
        validatePrefillShape(ctx, q);
        validateTypeSpecifics(ctx, q);
        validateValueRules(ctx, q);
        validateBranches(ctx, q);
        validateRangeRoutingHasDefault(ctx, q);
        validateFillsFlag(ctx, q);
    }

    private void validateLabels(Ctx ctx, Question q) {
        LabelDetails en = q.label() == null ? null : q.label().en();
        LabelDetails fr = q.label() == null ? null : q.label().fr();
        if (!hasText(en)) ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.LABEL_EN, "MISSING_LABEL_EN", "EN label text is missing"));
        if (!hasText(fr)) ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.LABEL_FR, "MISSING_LABEL_FR", "FR label text is missing"));
    }

    /**
     * Shape only — {@code FORM/QUESTION_KEY} with a known form. Whether that question exists is
     * another definition's business and is resolved at traversal time.
     */
    private void validatePrefillShape(Ctx ctx, Question q) {
        String prefill = q.prefillFrom();
        if (!hasText(prefill)) return;
        String[] parts = prefill.split("/", 2);
        boolean wellFormed = parts.length == 2 && hasText(parts[0]) && hasText(parts[1])
                && Arrays.stream(LeverageFormType.values()).anyMatch(f -> f.name().equals(parts[0]));
        if (!wellFormed) {
            ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.PREFILL_FROM, "PREFILL_BAD_FORMAT",
                    "Prefill From must read FORM/QUESTION_KEY with a known form, e.g. FED/Q01; got '" + prefill + "'"));
        }
    }

    private void validateTypeSpecifics(Ctx ctx, Question q) {
        QuestionType type = q.type();
        if (type == null) return;
        switch (type) {
            case SINGLE_CHOICE, BOOLEAN -> validateOptions(ctx, q);
            case CHECKLIST -> validateChecklist(ctx, q);
            case DATA_ENTRY -> validateDataEntry(ctx, q);
            case COMPUTED -> validateComputed(ctx, q);
            case LOOKUP -> validateLookup(ctx, q);
            case NUMERIC, TEXT -> { /* nothing beyond label and branch checks */ }
        }
    }

    private void validateOptions(Ctx ctx, Question q) {
        List<Option> options = nullToEmpty(q.options());
        if (options.size() < 2) {
            ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.OPTIONS, "OPTIONS_TOO_FEW",
                    q.type() + " question must declare at least two options"));
            return;
        }
        checkOptionValues(ctx, q, options);
        if (hasDefaultBranch(q)) return;
        Set<String> covered = new HashSet<>();
        for (Branch b : nullToEmpty(q.branches())) {
            Condition w = b == null ? null : b.when();
            if (w == null) continue;
            collectCoveredValues(w, covered);
        }
        for (Option o : options) {
            if (o != null && hasText(o.value()) && !covered.contains(o.value())) {
                ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.OPTIONS, "OPTION_UNCOVERED",
                        "Option '" + o.value() + "' has no branch and there is no default branch"));
            }
        }
    }

    private void checkOptionValues(Ctx ctx, Question q, List<Option> options) {
        Set<String> seen = new HashSet<>();
        for (Option o : options) {
            if (o == null || !hasText(o.value())) {
                ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.OPTIONS, "OPTION_BLANK_VALUE", "An option has a blank value"));
            } else if (!seen.add(o.value())) {
                ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.OPTIONS, "OPTION_DUPLICATE",
                        "Duplicate option value '" + o.value() + "'"));
            }
        }
    }

    /** An option is covered by its own {@code equals}/{@code in}, including inside an {@code allOf}. */
    private void collectCoveredValues(Condition c, Set<String> covered) {
        if (c == null) return;
        if (c.isComposite()) {
            c.allOf().forEach(sub -> collectCoveredValues(sub, covered));
            return;
        }
        if (hasText(c.questionKey()) || hasText(c.fieldKey())) return; // refers to something else
        if (hasText(c.equals())) covered.add(c.equals());
        if (c.in() != null) covered.addAll(c.in());
    }

    private void validateChecklist(Ctx ctx, Question q) {
        List<ChecklistItem> items = nullToEmpty(q.items());
        if (items.isEmpty()) {
            ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.ITEMS, "CHECKLIST_NO_ITEMS",
                    "CHECKLIST question must declare at least one item"));
        }
        validateChecklistItems(ctx, q, items);
        validateAggregateCoverage(ctx, q);
        validateChecklistBranchShape(ctx, q);
    }

    private void validateChecklistItems(Ctx ctx, Question q, List<ChecklistItem> items) {
        Set<String> seen = new HashSet<>();
        for (ChecklistItem item : items) {
            validateChecklistItem(ctx, q, item, seen);
        }
    }

    private void validateChecklistItem(Ctx ctx, Question q, ChecklistItem item, Set<String> seen) {
        if (item == null || !hasText(item.key())) {
            ctx.errors.add(Error.item(ctx.ft, q.key(), BLANK_KEY, "CHECKLIST_ITEM_BLANK_KEY",
                    "A checklist item has a blank key"));
            return;
        }
        if (!seen.add(item.key())) {
            ctx.errors.add(Error.item(ctx.ft, q.key(), item.key(), "CHECKLIST_ITEM_DUPLICATE",
                    "Duplicate checklist item key '" + item.key() + "'"));
        }
        if (!bothLocales(item.label())) {
            ctx.errors.add(Error.item(ctx.ft, q.key(), item.key(), "CHECKLIST_ITEM_LABEL",
                    "Checklist item '" + item.key() + "' is missing an EN or FR label"));
        }
    }

    /** ANY_YES and ALL_NO are complements, so both must be routed or a default must catch them. */
    private void validateAggregateCoverage(Ctx ctx, Question q) {
        boolean dflt = hasDefaultBranch(q);
        boolean anyYesCovered = dflt || hasAggregateBranch(q, Aggregate.ANY_YES);
        boolean allNoCovered = dflt || hasAggregateBranch(q, Aggregate.ALL_NO);
        if (!anyYesCovered || !allNoCovered) {
            ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.BRANCHES, "CHECKLIST_AGG_INCOMPLETE",
                    "CHECKLIST must route both ANY_YES and ALL_NO (or carry a default branch)"));
        }
    }

    /**
     * Each branch must be an aggregate, the default, or a test of ANOTHER question: Q-T01 routes
     * "Q01 is NO -> Q-T02" ahead of ALL_NO, because ALL_NO is true on BOTH LBO paths and would
     * otherwise swallow the non-LBO route.
     */
    private void validateChecklistBranchShape(Ctx ctx, Question q) {
        List<Branch> branches = nullToEmpty(q.branches());
        for (int i = 0; i < branches.size(); i++) {
            if (!isValidChecklistBranch(branches.get(i))) {
                ctx.errors.add(Error.branch(ctx.ft, q.key(), i, "CHECKLIST_BRANCH_NOT_AGGREGATE",
                        "CHECKLIST branch must use an aggregate (ANY_YES / ALL_NO), test another question, "
                                + "or be the default"));
            }
        }
    }

    private boolean isValidChecklistBranch(Branch branch) {
        Condition when = branch == null ? null : branch.when();
        return when != null && (when.isDefault() || mentionsAggregate(when) || refersElsewhere(when));
    }

    private void validateDataEntry(Ctx ctx, Question q) {
        List<DataField> fields = nullToEmpty(q.fields());
        if (fields.isEmpty()) {
            ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.FIELDS, "DATA_NO_FIELDS",
                    "DATA_ENTRY question must declare at least one field"));
        }
        Set<String> seen = new HashSet<>();
        for (DataField field : fields) {
            validateDataField(ctx, q, field, seen);
        }
        validateHasAnalystInput(ctx, q, fields);
        rejectTerminalDataEntry(ctx, q);
    }

    private void validateDataField(Ctx ctx, Question q, DataField field, Set<String> seen) {
        if (field == null || !hasText(field.key())) {
            ctx.errors.add(Error.field(ctx.ft, q.key(), BLANK_KEY, "DATA_FIELD_BLANK_KEY",
                    "A data field has a blank key"));
            return;
        }
        if (!seen.add(field.key())) {
            ctx.errors.add(Error.field(ctx.ft, q.key(), field.key(), "DATA_FIELD_DUPLICATE",
                    "Duplicate data field key '" + field.key() + "'"));
        }
        if (!bothLocales(field.label())) {
            ctx.errors.add(Error.field(ctx.ft, q.key(), field.key(), "DATA_FIELD_LABEL",
                    "Data field '" + field.key() + "' is missing an EN or FR label"));
        }
        if (field.isCalculated() && field.editable()) {
            ctx.errors.add(Error.field(ctx.ft, q.key(), field.key(), "DATA_FIELD_CALC_EDITABLE",
                    "Field '" + field.key() + "' is calculated (CALC/) and cannot also be editable"));
        }
        if (hasText(field.fillsFlag())) {
            checkFlagKnown(ctx, q.key(), field.key(), field.fillsFlag(), Aspect.FIELDS);
        }
    }

    private void validateHasAnalystInput(Ctx ctx, Question q, List<DataField> fields) {
        boolean anyInput = fields.stream().anyMatch(f -> f != null && f.isAnalystInput());
        if (!fields.isEmpty() && !anyInput) {
            ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.FIELDS, "DATA_NO_INPUT",
                    "DATA_ENTRY question has no field the analyst can type into"));
        }
    }

    /** The financials table feeds the qualitative block — it must CONTINUE, never terminate. */
    private void rejectTerminalDataEntry(Ctx ctx, Question q) {
        List<Branch> branches = nullToEmpty(q.branches());
        for (int i = 0; i < branches.size(); i++) {
            Branch branch = branches.get(i);
            if (branch != null && branch.isTerminal()) {
                ctx.errors.add(Error.branch(ctx.ft, q.key(), i, "DATA_ENTRY_TERMINAL",
                        "DATA_ENTRY question must continue to the next node, not terminate the form"));
            }
        }
    }

    /**
     * A COMPUTED question must have exactly ONE source of its value: a fixed {@code derivedFrom}
     * or ordered {@code valueRules}. (The old EXTERNAL_NO_DERIVED_FROM rule is gone with the
     * external flag — nothing waits on another service any more.)
     */
    private void validateComputed(Ctx ctx, Question q) {
        boolean derived = hasText(q.derivedFrom());
        boolean ruled = !nullToEmpty(q.valueRules()).isEmpty();
        if (derived && ruled) {
            ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.DERIVED_FROM, "COMPUTED_BOTH_SOURCES",
                    "COMPUTED question declares both 'Derived From' and 'Value Rules'; use one"));
        } else if (!derived && !ruled) {
            ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.DERIVED_FROM, "COMPUTED_NO_SOURCE",
                    "COMPUTED question must declare either 'Derived From' or 'Value Rules'"));
        }
        if (q.editable()) {
            ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.TYPE, "COMPUTED_EDITABLE",
                    "COMPUTED question cannot be editable"));
        }
    }

    /** The list comes from a service at runtime, so Options names the source rather than values. */
    private void validateLookup(Ctx ctx, Question q) {
        List<Option> options = nullToEmpty(q.options());
        boolean namesSource = options.size() == 1
                && options.get(0) != null
                && hasText(options.get(0).value())
                && options.get(0).value().startsWith("LOOKUP/");
        if (!namesSource) {
            ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.OPTIONS, "LOOKUP_NO_SOURCE",
                    "LOOKUP question must name its list in Options, e.g. LOOKUP/COUNTERPARTY"));
        }
    }

    /** Each rule's value must be one of the question's declared options; conditions recurse. */
    private void validateValueRules(Ctx ctx, Question q) {
        List<ValueRule> rules = nullToEmpty(q.valueRules());
        if (rules.isEmpty()) return;
        if (q.type() != QuestionType.COMPUTED) {
            ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.VALUE_RULES, "VALUE_RULES_ON_NON_COMPUTED",
                    "Only a COMPUTED question may declare Value Rules"));
        }
        Set<String> declared = declaredOptionValues(q);
        for (int i = 0; i < rules.size(); i++) {
            validateValueRule(ctx, q, i, rules.get(i), declared);
        }
    }

    private void validateValueRule(Ctx ctx, Question q, int index, ValueRule rule, Set<String> declared) {
        if (rule == null) {
            ctx.errors.add(Error.valueRule(ctx.ft, q.key(), index, "VALUE_RULE_NULL", "Value rule is null"));
            return;
        }
        validateValueRuleTarget(ctx, q, index, rule, declared);
        if (rule.when() == null) {
            ctx.errors.add(Error.valueRule(ctx.ft, q.key(), index, "VALUE_RULE_NO_CONDITION",
                    "Value rule has no condition"));
            return;
        }
        validateCondition(ctx, q, index, rule.when(), Aspect.VALUE_RULES);
    }

    private void validateValueRuleTarget(Ctx ctx, Question q, int index, ValueRule rule, Set<String> declared) {
        if (!hasText(rule.value())) {
            ctx.errors.add(Error.valueRule(ctx.ft, q.key(), index, "VALUE_RULE_NO_VALUE",
                    "Value rule assigns no value"));
            return;
        }
        if (!declared.isEmpty() && !declared.contains(rule.value())) {
            ctx.errors.add(Error.valueRule(ctx.ft, q.key(), index, "VALUE_RULE_UNKNOWN_VALUE",
                    "Value rule assigns '" + rule.value() + "', which is not one of this question's options"));
        }
    }

    private Set<String> declaredOptionValues(Question q) {
        Set<String> declared = new HashSet<>();
        for (Option option : nullToEmpty(q.options())) {
            if (option != null && hasText(option.value())) {
                declared.add(option.value());
            }
        }
        return declared;
    }

    private void validateFillsFlag(Ctx ctx, Question q) {
        if (!hasText(q.fillsFlag())) return;
        FlagDefinition flag = ctx.def.flags().get(q.fillsFlag());
        if (flag == null) {
            ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.FILLS_FLAG, FLAG_UNKNOWN,
                    "Fills Flag names '" + q.fillsFlag() + "', which is not in the flags catalogue"));
            return;
        }
        if (flag.storage() != FlagStorage.CODE) return;
        // The chosen option BECOMES the flag, so every option value must be a code of its set.
        for (Option o : nullToEmpty(q.options())) {
            if (o == null || !hasText(o.value())) continue;
            if (ctx.def.flagValue(flag.valueSet(), o.value()).isEmpty()) {
                ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.FILLS_FLAG, "FILLS_FLAG_OPTION_MISMATCH",
                        "Option '" + o.value() + "' is not a code of value set '" + flag.valueSet()
                                + "', so it cannot become flag '" + flag.key() + "'"));
            }
        }
    }

    // ------------------------------------------------------------------ branches & conditions

    private void validateBranches(Ctx ctx, Question q) {
        List<Branch> branches = nullToEmpty(q.branches());
        for (int i = 0; i < branches.size(); i++) {
            validateBranch(ctx, q, i, branches.get(i));
        }
    }

    private void validateBranch(Ctx ctx, Question q, int index, Branch branch) {
        if (branch == null) {
            ctx.errors.add(Error.branch(ctx.ft, q.key(), index, "BRANCH_NULL", "Branch is null"));
            return;
        }
        validateBranchTarget(ctx, q, index, branch);
        validateBranchCondition(ctx, q, index, branch);
        if (branch.effect() != null) {
            validateEffectFlags(ctx, q, index, branch.effect());
        }
    }

    /** Terminal XOR onward, and an onward target must resolve. */
    private void validateBranchTarget(Ctx ctx, Question q, int index, Branch branch) {
        boolean terminal = branch.isTerminal();
        boolean hasGoTo = hasText(branch.goTo());

        if (terminal && hasGoTo) {
            ctx.errors.add(Error.branch(ctx.ft, q.key(), index, "BRANCH_AMBIGUOUS",
                    "Branch both terminates and points onward"));
        }
        if (!terminal && !hasGoTo) {
            ctx.errors.add(Error.branch(ctx.ft, q.key(), index, "BRANCH_DANGLING",
                    "Branch neither terminates nor points onward"));
        }
        if (hasGoTo && !ctx.byKey.containsKey(branch.goTo())) {
            ctx.errors.add(Error.branch(ctx.ft, q.key(), index, "UNKNOWN_GOTO",
                    "Branch points to unknown question '" + branch.goTo() + "'"));
        }
    }

    private void validateBranchCondition(Ctx ctx, Question q, int index, Branch branch) {
        if (branch.when() == null) {
            ctx.errors.add(Error.branch(ctx.ft, q.key(), index, "BRANCH_NO_CONDITION",
                    "Branch has no 'when' condition"));
            return;
        }
        validateCondition(ctx, q, index, branch.when(), Aspect.BRANCHES);
    }

    private void validateEffectFlags(Ctx ctx, Question q, int branchIndex, Effect effect) {
        for (Map.Entry<String, String> e : effect.flags().entrySet()) {
            FlagDefinition flag = ctx.def.flags().get(e.getKey());
            if (flag == null) {
                ctx.errors.add(Error.branch(ctx.ft, q.key(), branchIndex, FLAG_UNKNOWN,
                        "Branch sets '" + e.getKey() + "', which is not in the flags catalogue"));
                continue;
            }
            if (flag.storage() != FlagStorage.CODE) continue;
            Optional<FlagValue> value = ctx.def.flagValue(flag.valueSet(), e.getValue());
            if (value.isEmpty()) {
                ctx.errors.add(Error.branch(ctx.ft, q.key(), branchIndex, "FLAG_VALUE_UNKNOWN",
                        "'" + e.getValue() + "' is not a code of value set '" + flag.valueSet()
                                + "' used by flag '" + flag.key() + "'"));
            } else if (!value.get().setBy().contains(ctx.ft)) {
                ctx.errors.add(Error.branch(ctx.ft, q.key(), branchIndex, "FLAG_VALUE_FORM_NOT_ALLOWED",
                        "Value '" + e.getValue() + "' may only be set by " + value.get().setBy() + ", not by " + ctx.ft));
            }
        }
    }

    /** Recursively validate a condition and any {@code allOf} children. */
    private void validateCondition(Ctx ctx, Question owner, int index, Condition c, Aspect aspect) {
        if (c.isDefault()) {
            return;
        }
        if (c.isComposite()) {
            validateCompositeCondition(ctx, owner, index, c, aspect);
            return;
        }
        validateLeafCondition(ctx, owner, index, c, aspect);
    }

    private void validateCompositeCondition(Ctx ctx, Question owner, int index, Condition c, Aspect aspect) {
        for (Condition child : c.allOf()) {
            if (child == null) {
                addAt(ctx, owner, index, aspect, "COND_NULL_CHILD", "A composite condition has a null child");
            } else {
                validateCondition(ctx, owner, index, child, aspect);
            }
        }
    }

    private void validateLeafCondition(Ctx ctx, Question owner, int index, Condition c, Aspect aspect) {
        if (!c.hasPredicate()) {
            addAt(ctx, owner, index, aspect, "COND_EMPTY",
                    "Condition is neither default nor a composite and carries no predicate");
            return;
        }
        if (hasText(c.questionKey()) && hasText(c.fieldKey())) {
            addAt(ctx, owner, index, aspect, "COND_AMBIGUOUS_TARGET",
                    "Condition names both a question and a field; use one");
        }
        Question target = resolveConditionQuestion(ctx, owner, index, c, aspect);
        DataField field = resolveConditionField(ctx, owner, index, c, aspect);

        validateAggregateUsage(ctx, owner, index, c, aspect);
        validateConditionRanges(ctx, owner, index, c, aspect, target, field);
        if (c.comparison() != null) {
            validateComparison(ctx, owner, index, aspect, c.comparison());
        }
    }

    /** The question the predicate applies to: the one named, or the owner when none is. */
    private Question resolveConditionQuestion(Ctx ctx, Question owner, int index, Condition c, Aspect aspect) {
        if (!hasText(c.questionKey())) {
            return owner;
        }
        Question target = ctx.byKey.get(c.questionKey());
        if (target == null) {
            addAt(ctx, owner, index, aspect, "COND_UNKNOWN_QUESTION",
                    "Condition references unknown question '" + c.questionKey() + "'");
        }
        return target;
    }

    private DataField resolveConditionField(Ctx ctx, Question owner, int index, Condition c, Aspect aspect) {
        if (!hasText(c.fieldKey())) {
            return null;
        }
        DataField field = ctx.fieldsByKey.get(c.fieldKey());
        if (field == null) {
            addAt(ctx, owner, index, aspect, "COND_UNKNOWN_FIELD",
                    "Condition references unknown field '" + c.fieldKey() + "'");
        } else if (field.type() != DataFieldType.NUMERIC) {
            addAt(ctx, owner, index, aspect, "COND_FIELD_NOT_NUMERIC",
                    "Field '" + c.fieldKey() + "' is " + field.type() + "; only NUMERIC fields can be compared");
        }
        return field;
    }

    private void validateAggregateUsage(Ctx ctx, Question owner, int index, Condition c, Aspect aspect) {
        if (c.aggregate() == null) {
            return;
        }
        boolean namesSomethingElse = hasText(c.questionKey()) || hasText(c.fieldKey());
        if (namesSomethingElse || owner.type() != QuestionType.CHECKLIST) {
            addAt(ctx, owner, index, aspect, "AGGREGATE_MISUSE",
                    "'aggregate' is only valid on the owning CHECKLIST question's own branches");
        }
    }

    private void validateConditionRanges(Ctx ctx, Question owner, int index, Condition c, Aspect aspect,
                                         Question target, DataField field) {
        if (c.ranges() == null || c.ranges().isEmpty()) {
            return;
        }
        boolean questionScoped = field == null && !hasText(c.fieldKey());
        if (questionScoped && target != null && target.type() != QuestionType.NUMERIC) {
            addAt(ctx, owner, index, aspect, "RANGE_ON_NON_NUMERIC",
                    "'ranges' target '" + target.key() + "' is not numeric; name a NUMERIC field instead");
        }
        validateRanges(ctx, owner, index, aspect, c.ranges());
    }

    /** {@code field totalEcbDebt > 4 x field adjustedEbitda} — both operands numeric fields. */
    private void validateComparison(Ctx ctx, Question owner, int index, Aspect aspect, Comparison cmp) {
        if (cmp.operator() == null) {
            addAt(ctx, owner, index, aspect, "COMPARISON_NO_OPERATOR", "Comparison has no operator");
        }
        if (cmp.multiplier() == null) {
            addAt(ctx, owner, index, aspect, "COMPARISON_NO_MULTIPLIER", "Comparison has no multiplier");
        }
        for (String key : new String[]{cmp.leftFieldKey(), cmp.rightFieldKey()}) {
            if (!hasText(key)) {
                addAt(ctx, owner, index, aspect, "COMPARISON_MISSING_OPERAND", "Comparison is missing an operand");
                continue;
            }
            DataField f = ctx.fieldsByKey.get(key);
            if (f == null) {
                addAt(ctx, owner, index, aspect, "COMPARISON_UNKNOWN_FIELD",
                        "Comparison references unknown field '" + key + "'");
            } else if (f.type() != DataFieldType.NUMERIC) {
                addAt(ctx, owner, index, aspect, "COMPARISON_FIELD_NOT_NUMERIC",
                        "Comparison operand '" + key + "' is " + f.type() + ", not NUMERIC");
            }
        }
    }

    private void validateRanges(Ctx ctx, Question owner, int index, Aspect aspect, List<Range> ranges) {
        for (Range range : ranges) {
            validateRange(ctx, owner, index, aspect, range);
        }
    }

    private void validateRange(Ctx ctx, Question owner, int index, Aspect aspect, Range range) {
        if (range == null) {
            addAt(ctx, owner, index, aspect, "RANGE_NULL", "A range is null");
            return;
        }
        if (!hasAnyBound(range)) {
            addAt(ctx, owner, index, aspect, "RANGE_EMPTY", "A range has no bounds (matches everything)");
            return;
        }
        if (range.gte() != null && range.gt() != null) {
            addAt(ctx, owner, index, aspect, "RANGE_DOUBLE_LOWER", "Range sets both gte and gt");
        }
        if (range.lte() != null && range.lt() != null) {
            addAt(ctx, owner, index, aspect, "RANGE_DOUBLE_UPPER", "Range sets both lte and lt");
        }
        validateRangeBounds(ctx, owner, index, aspect, range);
    }

    private void validateRangeBounds(Ctx ctx, Question owner, int index, Aspect aspect, Range range) {
        BigDecimal low = range.gte() != null ? range.gte() : range.gt();
        BigDecimal high = range.lte() != null ? range.lte() : range.lt();
        if (low != null && high != null && low.compareTo(high) > 0) {
            addAt(ctx, owner, index, aspect, "RANGE_IMPOSSIBLE",
                    "Range lower bound " + low + " exceeds upper bound " + high);
        }
    }

    private boolean hasAnyBound(Range range) {
        return range.gte() != null || range.gt() != null || range.lte() != null || range.lt() != null;
    }

    /** A numeric domain is continuous, so range routing without a catch-all leaves a hole. */
    private void validateRangeRoutingHasDefault(Ctx ctx, Question q) {
        boolean routesOnRanges = nullToEmpty(q.branches()).stream()
                .anyMatch(b -> b != null && conditionUsesRanges(b.when()));
        if (routesOnRanges && !hasDefaultBranch(q)) {
            ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.BRANCHES, "RANGE_ROUTING_NO_DEFAULT",
                    "Question routes on numeric ranges but has no default branch; some values would match nothing"));
        }
    }

    // ------------------------------------------------------------------ reachability, cycles, outcomes

    private void validateReachabilityAndCycles(Ctx ctx) {
        DecisionTreeDefinition def = ctx.def;
        if (!hasText(def.entryQuestion()) || !ctx.byKey.containsKey(def.entryQuestion())) {
            return;
        }
        Set<String> reachable = new HashSet<>();
        dfs(def.entryQuestion(), ctx, reachable, new LinkedHashSet<>(), Map.of());

        reportUnreachable(ctx, reachable);
        reportDeadEnds(ctx, reachable);
        reportUndeclaredOutcomes(ctx);
    }

    /** A display-only computed output is shown, not walked, so it is exempt. */
    private void reportUnreachable(Ctx ctx, Set<String> reachable) {
        for (Question q : ctx.byKey.values()) {
            if (!q.isDisplayOnlyOutput() && !reachable.contains(q.key())) {
                ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.REACHABILITY, "UNREACHABLE",
                        "Question is not reachable from the entry question"));
            }
        }
    }

    private void reportDeadEnds(Ctx ctx, Set<String> reachable) {
        for (String key : reachable) {
            Question q = ctx.byKey.get(key);
            if (q != null && nullToEmpty(q.branches()).isEmpty()) {
                ctx.errors.add(Error.question(ctx.ft, key, Aspect.BRANCHES, "NO_BRANCH",
                        "Reachable question has no onward or terminal branch"));
            }
        }
    }

    private void reportUndeclaredOutcomes(Ctx ctx) {
        Set<RecommendationOutcome> declared = ctx.def.outcomes().keySet();
        for (Question q : ctx.byKey.values()) {
            reportUndeclaredOutcomes(ctx, q, declared);
        }
    }

    private void reportUndeclaredOutcomes(Ctx ctx, Question q, Set<RecommendationOutcome> declared) {
        List<Branch> branches = nullToEmpty(q.branches());
        for (int i = 0; i < branches.size(); i++) {
            RecommendationOutcome outcome = outcomeOf(branches.get(i));
            if (outcome != null && !declared.contains(outcome)) {
                ctx.errors.add(Error.branch(ctx.ft, q.key(), i, "OUTCOME_NOT_DECLARED",
                        "Outcome " + outcome + " is not in the outcomes catalog"));
            }
        }
    }

    private RecommendationOutcome outcomeOf(Branch branch) {
        if (branch == null || branch.effect() == null) {
            return null;
        }
        return branch.effect().setOutcome();
    }

    /**
     * Walks the graph the way the ENGINE would, not the way a plain edge-follower would.
     *
     * <p>Two rules make the difference, and without them the ECB tree reports a cycle it can never
     * walk. The same rows serve both LBO orderings — the Status block runs before the transaction
     * block on one path and after it on the other — so the graph genuinely contains
     * {@code Q-T01 -> Q-C01 -> Q-C02 -> Q-S01 -> Q-S04 -> Q-T01}. No analyst can traverse it,
     * because getting round the loop needs Q01 to be both YES and NO.
     *
     * <p>So the walk carries the equality constraints it has assumed so far and applies:
     * <ul>
     *   <li><b>contradiction</b> — an edge whose condition disagrees with something already assumed
     *       on this path cannot be taken ({@code Q01 is NO} after {@code Q01 = YES});</li>
     *   <li><b>domination</b> — first match wins, so once a branch is CERTAIN under the current
     *       assumptions, every branch below it on that question is dead. This is the rule that
     *       kills the ECB cycle: at Q-S04 with Q01 = YES, {@code Q01 is YES -> Q-F01} always fires
     *       and the {@code * -> Q-T01} beneath it is unreachable.</li>
     * </ul>
     *
     * <p>Only plain equalities are tracked. Ranges, aggregates and comparisons are left
     * unconstrained, so the walk stays conservative: it may still explore an edge the engine would
     * not, which risks a false CYCLE but never a missed one.
     */
    private void dfs(String key, Ctx ctx, Set<String> visited, Set<String> onStack,
                     Map<String, String> assumed) {
        if (onStack.contains(key)) {
            ctx.errors.add(Error.question(ctx.ft, key, Aspect.REACHABILITY, "CYCLE",
                    "Cycle detected involving this question"));
            return;
        }
        if (!visited.add(key)) {
            return;
        }
        onStack.add(key);
        Question q = ctx.byKey.get(key);
        if (q != null) {
            walkBranches(q, ctx, visited, onStack, assumed);
        }
        onStack.remove(key);
    }

    private void walkBranches(Question q, Ctx ctx, Set<String> visited, Set<String> onStack,
                              Map<String, String> assumed) {
        for (Branch branch : nullToEmpty(q.branches())) {
            if (branch == null || branch.when() == null) {
                continue;
            }
            Map<String, String> implied = equalitiesOf(branch.when(), q);
            if (contradicts(assumed, implied)) {
                continue;
            }
            followEdge(branch, ctx, visited, onStack, merged(assumed, implied));
            if (isCertain(branch.when(), assumed, implied)) {
                return;   // first match wins: nothing below this branch can ever be reached
            }
        }
    }

    private void followEdge(Branch branch, Ctx ctx, Set<String> visited, Set<String> onStack,
                            Map<String, String> assumed) {
        if (!branch.isTerminal() && hasText(branch.goTo()) && ctx.byKey.containsKey(branch.goTo())) {
            dfs(branch.goTo(), ctx, visited, onStack, assumed);
        }
    }

    /**
     * The plain {@code key = value} facts a condition asserts. An unnamed subject means the owning
     * question's own answer, which is how {@code YES -> Q-B01A} on Q01 pins Q01 = YES for
     * everything downstream.
     */
    private Map<String, String> equalitiesOf(Condition condition, Question owner) {
        Map<String, String> equalities = new LinkedHashMap<>();
        collectEqualities(condition, owner, equalities);
        return equalities;
    }

    private void collectEqualities(Condition condition, Question owner, Map<String, String> into) {
        if (condition == null || condition.isDefault()) {
            return;
        }
        if (condition.isComposite()) {
            condition.allOf().forEach(child -> collectEqualities(child, owner, into));
            return;
        }
        if (hasText(condition.equals()) && !hasText(condition.fieldKey())) {
            String subject = hasText(condition.questionKey()) ? condition.questionKey() : owner.key();
            into.putIfAbsent(subject, condition.equals());
        }
    }

    private boolean contradicts(Map<String, String> assumed, Map<String, String> implied) {
        return implied.entrySet().stream()
                .anyMatch(e -> assumed.containsKey(e.getKey()) && !assumed.get(e.getKey()).equals(e.getValue()));
    }

    /**
     * True when this branch is bound to fire: everything it tests is a plain equality already
     * assumed on this path. Anything numeric or aggregate is never certain, because the walk does
     * not evaluate answers.
     */
    private boolean isCertain(Condition condition, Map<String, String> assumed, Map<String, String> implied) {
        if (condition.isDefault()) {
            return true;
        }
        if (!isPurelyEqualities(condition) || implied.isEmpty()) {
            return false;
        }
        return implied.entrySet().stream()
                .allMatch(e -> e.getValue().equals(assumed.get(e.getKey())));
    }

    private boolean isPurelyEqualities(Condition condition) {
        if (condition.isComposite()) {
            return condition.allOf().stream().allMatch(this::isPurelyEqualities);
        }
        boolean nothingElse = condition.aggregate() == null && condition.comparison() == null
                && (condition.ranges() == null || condition.ranges().isEmpty())
                && (condition.in() == null || condition.in().isEmpty())
                && !hasText(condition.fieldKey());
        return nothingElse && hasText(condition.equals());
    }

    private Map<String, String> merged(Map<String, String> assumed, Map<String, String> implied) {
        if (implied.isEmpty()) {
            return assumed;
        }
        Map<String, String> merged = new LinkedHashMap<>(assumed);
        merged.putAll(implied);
        return merged;
    }

    // ------------------------------------------------------------------ catalogues

    private void validateValidationMessages(Ctx ctx) {
        Set<String> messageKeys = new HashSet<>();
        for (ValidationMessage message : ctx.def.validationMessages()) {
            if (message != null) {
                validateValidationMessage(ctx, message, messageKeys);
            }
        }
    }

    private void validateValidationMessage(Ctx ctx, ValidationMessage m, Set<String> messageKeys) {
        validateMessageKey(ctx, m, messageKeys);
        if (m.rule() == null) {
            ctx.errors.add(Error.catalogue(ctx.ft, Aspect.VALIDATION_MESSAGES, m.messageKey(), "MESSAGE_NO_RULE",
                    MESSAGE + m.messageKey() + "' names no rule"));
        }
        if (!bothLocales(m.text())) {
            ctx.errors.add(Error.catalogue(ctx.ft, Aspect.VALIDATION_MESSAGES, m.messageKey(), "MESSAGE_LABEL",
                    MESSAGE + m.messageKey() + "' is missing EN or FR text"));
        }
        validateMessageTargets(ctx, m);
    }

    private void validateMessageKey(Ctx ctx, ValidationMessage m, Set<String> messageKeys) {
        if (!hasText(m.messageKey())) {
            ctx.errors.add(Error.catalogue(ctx.ft, Aspect.VALIDATION_MESSAGES, null, "MESSAGE_NO_KEY",
                    "A validation message has no message key"));
            return;
        }
        if (!messageKeys.add(m.messageKey())) {
            ctx.errors.add(Error.catalogue(ctx.ft, Aspect.VALIDATION_MESSAGES, m.messageKey(),
                    "MESSAGE_DUPLICATE_KEY", "Duplicate message key '" + m.messageKey() + "'"));
        }
    }

    /** Both targets are optional, but a named one must resolve. */
    private void validateMessageTargets(Ctx ctx, ValidationMessage m) {
        if (hasText(m.questionKey()) && !ctx.byKey.containsKey(m.questionKey())) {
            ctx.errors.add(Error.catalogue(ctx.ft, Aspect.VALIDATION_MESSAGES, m.messageKey(),
                    "MESSAGE_UNKNOWN_QUESTION",
                    MESSAGE + m.messageKey() + "' targets unknown question '" + m.questionKey() + "'"));
        }
        if (hasText(m.fieldKey()) && !ctx.fieldsByKey.containsKey(m.fieldKey())) {
            ctx.errors.add(Error.catalogue(ctx.ft, Aspect.VALIDATION_MESSAGES, m.messageKey(),
                    "MESSAGE_UNKNOWN_FIELD",
                    MESSAGE + m.messageKey() + "' targets unknown field '" + m.fieldKey() + "'"));
        }
    }

    private void validateInfoPanels(Ctx ctx) {
        for (InfoPanel p : ctx.def.infoPanels()) {
            if (p == null) continue;
            if (!bothLocales(p.title())) {
                ctx.errors.add(Error.catalogue(ctx.ft, Aspect.INFO_PANELS, p.key(), "PANEL_TITLE",
                        PANEL + p.key() + "' is missing an EN or FR title"));
            }
            if (nullToEmpty(p.fields()).isEmpty()) {
                ctx.errors.add(Error.catalogue(ctx.ft, Aspect.INFO_PANELS, p.key(), "PANEL_NO_FIELDS",
                        PANEL + p.key() + "' displays no fields"));
            }
            FlagDefinition flag = ctx.def.flags().get(p.whenFlagKey());
            if (flag == null) {
                ctx.errors.add(Error.catalogue(ctx.ft, Aspect.INFO_PANELS, p.key(), "PANEL_UNKNOWN_FLAG",
                        PANEL + p.key() + "' is shown when '" + p.whenFlagKey() + "', which is not a known flag"));
            } else if (flag.storage() == FlagStorage.CODE
                    && ctx.def.flagValue(flag.valueSet(), p.whenFlagValue()).isEmpty()) {
                ctx.errors.add(Error.catalogue(ctx.ft, Aspect.INFO_PANELS, p.key(), "PANEL_UNKNOWN_FLAG_VALUE",
                        PANEL + p.key() + "' triggers on '" + p.whenFlagValue()
                                + "', which is not a code of value set '" + flag.valueSet() + "'"));
            }
        }
    }

    private void checkFlagKnown(Ctx ctx, String questionKey, String subKey, String flagKey, Aspect aspect) {
        if (!ctx.def.flags().containsKey(flagKey)) {
            ctx.errors.add(new Error(ctx.ft, questionKey, subKey, null, aspect, FLAG_UNKNOWN,
                    "'" + flagKey + "' is not in the flags catalogue"));
        }
    }

    // ------------------------------------------------------------------ helpers

    private void addAt(Ctx ctx, Question owner, int index, Aspect aspect, String code, String message) {
        ctx.errors.add(aspect == Aspect.VALUE_RULES
                ? Error.valueRule(ctx.ft, owner.key(), index, code, message)
                : Error.branch(ctx.ft, owner.key(), index, code, message));
    }

    private boolean mentionsAggregate(Condition c) {
        if (c == null) return false;
        if (c.aggregate() != null) return true;
        return c.isComposite() && c.allOf().stream().anyMatch(this::mentionsAggregate);
    }

    private boolean refersElsewhere(Condition c) {
        if (c == null) return false;
        if (hasText(c.questionKey()) || hasText(c.fieldKey())) return true;
        return c.isComposite() && c.allOf().stream().anyMatch(this::refersElsewhere);
    }

    private boolean conditionUsesRanges(Condition c) {
        if (c == null) return false;
        if (c.ranges() != null && !c.ranges().isEmpty()) return true;
        return c.isComposite() && c.allOf().stream().anyMatch(this::conditionUsesRanges);
    }

    private boolean hasDefaultBranch(Question q) {
        return nullToEmpty(q.branches()).stream().anyMatch(b -> b != null && b.when() != null && b.when().isDefault());
    }

    private boolean hasAggregateBranch(Question q, Aggregate agg) {
        return nullToEmpty(q.branches()).stream()
                .anyMatch(b -> b != null && b.when() != null && b.when().aggregate() == agg);
    }

    private static boolean bothLocales(LocalizedLabel l) {
        return l != null && hasText(l.en()) && hasText(l.fr());
    }

    private static boolean hasText(LabelDetails d) {
        return d != null && hasText(d.text());
    }

    private static boolean hasText(String s) {
        return s != null && !s.isBlank();
    }

    private static String safeKey(Question q) {
        return q == null || q.key() == null ? "(unknown)" : q.key();
    }

    private static <T> List<T> nullToEmpty(List<T> l) {
        return l == null ? List.of() : l;
    }
}
