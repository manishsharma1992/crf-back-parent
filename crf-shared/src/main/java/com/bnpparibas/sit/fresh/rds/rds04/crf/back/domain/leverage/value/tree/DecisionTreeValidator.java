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
                    errors.add(Error.question(ft, "(blank)", Aspect.KEY, "BLANK_KEY", "Question key is blank"));
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
        Set<String> seen = new HashSet<>();
        for (ChecklistItem it : items) {
            if (it == null || !hasText(it.key())) {
                ctx.errors.add(Error.item(ctx.ft, q.key(), "(blank)", "CHECKLIST_ITEM_BLANK_KEY", "A checklist item has a blank key"));
                continue;
            }
            if (!seen.add(it.key())) {
                ctx.errors.add(Error.item(ctx.ft, q.key(), it.key(), "CHECKLIST_ITEM_DUPLICATE",
                        "Duplicate checklist item key '" + it.key() + "'"));
            }
            if (!bothLocales(it.label())) {
                ctx.errors.add(Error.item(ctx.ft, q.key(), it.key(), "CHECKLIST_ITEM_LABEL",
                        "Checklist item '" + it.key() + "' is missing an EN or FR label"));
            }
        }
        boolean anyYes = hasAggregateBranch(q, Aggregate.ANY_YES);
        boolean allNo = hasAggregateBranch(q, Aggregate.ALL_NO);
        boolean dflt = hasDefaultBranch(q);
        if (!(anyYes || dflt) || !(allNo || dflt)) {
            ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.BRANCHES, "CHECKLIST_AGG_INCOMPLETE",
                    "CHECKLIST must route both ANY_YES and ALL_NO (or carry a default branch)"));
        }
        // Each branch must be aggregate, default, or an aggregate combined with a cross-question
        // test: Q-T01 routes "Q01 is NO -> Q-T02" ahead of ALL_NO because ALL_NO is true on BOTH
        // LBO paths and would otherwise swallow the non-LBO route.
        int i = 0;
        for (Branch b : nullToEmpty(q.branches())) {
            Condition w = b == null ? null : b.when();
            boolean ok = w != null && (w.isDefault() || mentionsAggregate(w) || refersElsewhere(w));
            if (!ok) {
                ctx.errors.add(Error.branch(ctx.ft, q.key(), i, "CHECKLIST_BRANCH_NOT_AGGREGATE",
                        "CHECKLIST branch must use an aggregate (ANY_YES / ALL_NO), test another question, or be the default"));
            }
            i++;
        }
    }

    private void validateDataEntry(Ctx ctx, Question q) {
        List<DataField> fields = nullToEmpty(q.fields());
        if (fields.isEmpty()) {
            ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.FIELDS, "DATA_NO_FIELDS",
                    "DATA_ENTRY question must declare at least one field"));
        }
        Set<String> seen = new HashSet<>();
        boolean anyInput = false;
        for (DataField f : fields) {
            if (f == null || !hasText(f.key())) {
                ctx.errors.add(Error.field(ctx.ft, q.key(), "(blank)", "DATA_FIELD_BLANK_KEY", "A data field has a blank key"));
                continue;
            }
            if (!seen.add(f.key())) {
                ctx.errors.add(Error.field(ctx.ft, q.key(), f.key(), "DATA_FIELD_DUPLICATE",
                        "Duplicate data field key '" + f.key() + "'"));
            }
            if (!bothLocales(f.label())) {
                ctx.errors.add(Error.field(ctx.ft, q.key(), f.key(), "DATA_FIELD_LABEL",
                        "Data field '" + f.key() + "' is missing an EN or FR label"));
            }
            if (f.isCalculated() && f.editable()) {
                ctx.errors.add(Error.field(ctx.ft, q.key(), f.key(), "DATA_FIELD_CALC_EDITABLE",
                        "Field '" + f.key() + "' is calculated (CALC/) and cannot also be editable"));
            }
            if (hasText(f.fillsFlag())) {
                checkFlagKnown(ctx, q.key(), f.key(), f.fillsFlag(), Aspect.FIELDS);
            }
            if (f.isAnalystInput()) anyInput = true;
        }
        if (!fields.isEmpty() && !anyInput) {
            ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.FIELDS, "DATA_NO_INPUT",
                    "DATA_ENTRY question has no field the analyst can type into"));
        }
        // The financials table feeds the qualitative block — it must CONTINUE, never terminate.
        List<Branch> branches = nullToEmpty(q.branches());
        for (int i = 0; i < branches.size(); i++) {
            Branch b = branches.get(i);
            if (b != null && b.isTerminal()) {
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
        Set<String> declared = new HashSet<>();
        for (Option o : nullToEmpty(q.options())) {
            if (o != null && hasText(o.value())) declared.add(o.value());
        }
        for (int i = 0; i < rules.size(); i++) {
            ValueRule rule = rules.get(i);
            if (rule == null) {
                ctx.errors.add(Error.valueRule(ctx.ft, q.key(), i, "VALUE_RULE_NULL", "Value rule is null"));
                continue;
            }
            if (!hasText(rule.value())) {
                ctx.errors.add(Error.valueRule(ctx.ft, q.key(), i, "VALUE_RULE_NO_VALUE", "Value rule assigns no value"));
            } else if (!declared.isEmpty() && !declared.contains(rule.value())) {
                ctx.errors.add(Error.valueRule(ctx.ft, q.key(), i, "VALUE_RULE_UNKNOWN_VALUE",
                        "Value rule assigns '" + rule.value() + "', which is not one of this question's options"));
            }
            if (rule.when() == null) {
                ctx.errors.add(Error.valueRule(ctx.ft, q.key(), i, "VALUE_RULE_NO_CONDITION", "Value rule has no condition"));
            } else {
                validateCondition(ctx, q, i, rule.when(), Aspect.VALUE_RULES);
            }
        }
    }

    private void validateFillsFlag(Ctx ctx, Question q) {
        if (!hasText(q.fillsFlag())) return;
        FlagDefinition flag = ctx.def.flags().get(q.fillsFlag());
        if (flag == null) {
            ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.FILLS_FLAG, "FLAG_UNKNOWN",
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
            Branch b = branches.get(i);
            if (b == null) {
                ctx.errors.add(Error.branch(ctx.ft, q.key(), i, "BRANCH_NULL", "Branch is null"));
                continue;
            }
            boolean terminal = b.isTerminal();
            boolean hasGoTo = hasText(b.goTo());

            if (terminal && hasGoTo)
                ctx.errors.add(Error.branch(ctx.ft, q.key(), i, "BRANCH_AMBIGUOUS", "Branch both terminates and points onward"));
            if (!terminal && !hasGoTo)
                ctx.errors.add(Error.branch(ctx.ft, q.key(), i, "BRANCH_DANGLING", "Branch neither terminates nor points onward"));
            if (hasGoTo && !ctx.byKey.containsKey(b.goTo()))
                ctx.errors.add(Error.branch(ctx.ft, q.key(), i, "UNKNOWN_GOTO",
                        "Branch points to unknown question '" + b.goTo() + "'"));

            if (b.when() == null) {
                ctx.errors.add(Error.branch(ctx.ft, q.key(), i, "BRANCH_NO_CONDITION", "Branch has no 'when' condition"));
            } else {
                validateCondition(ctx, q, i, b.when(), Aspect.BRANCHES);
            }
            if (b.effect() != null) validateEffectFlags(ctx, q, i, b.effect());
        }
    }

    private void validateEffectFlags(Ctx ctx, Question q, int branchIndex, Effect effect) {
        for (Map.Entry<String, String> e : effect.flags().entrySet()) {
            FlagDefinition flag = ctx.def.flags().get(e.getKey());
            if (flag == null) {
                ctx.errors.add(Error.branch(ctx.ft, q.key(), branchIndex, "FLAG_UNKNOWN",
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
        if (c.isDefault()) return;

        if (c.isComposite()) {
            for (Condition sub : c.allOf()) {
                if (sub == null) {
                    addAt(ctx, owner, index, aspect, "COND_NULL_CHILD", "A composite condition has a null child");
                } else {
                    validateCondition(ctx, owner, index, sub, aspect);
                }
            }
            return;
        }

        if (!c.hasPredicate()) {
            addAt(ctx, owner, index, aspect, "COND_EMPTY",
                    "Condition is neither default nor a composite and carries no predicate");
            return;
        }
        if (hasText(c.questionKey()) && hasText(c.fieldKey())) {
            addAt(ctx, owner, index, aspect, "COND_AMBIGUOUS_TARGET",
                    "Condition names both a question and a field; use one");
        }

        Question target = owner;
        if (hasText(c.questionKey())) {
            target = ctx.byKey.get(c.questionKey());
            if (target == null) {
                addAt(ctx, owner, index, aspect, "COND_UNKNOWN_QUESTION",
                        "Condition references unknown question '" + c.questionKey() + "'");
            }
        }

        DataField field = null;
        if (hasText(c.fieldKey())) {
            field = ctx.fieldsByKey.get(c.fieldKey());
            if (field == null) {
                addAt(ctx, owner, index, aspect, "COND_UNKNOWN_FIELD",
                        "Condition references unknown field '" + c.fieldKey() + "'");
            } else if (field.type() != DataFieldType.NUMERIC) {
                addAt(ctx, owner, index, aspect, "COND_FIELD_NOT_NUMERIC",
                        "Field '" + c.fieldKey() + "' is " + field.type() + "; only NUMERIC fields can be compared");
            }
        }

        if (c.aggregate() != null && (hasText(c.questionKey()) || hasText(c.fieldKey())
                || owner.type() != QuestionType.CHECKLIST)) {
            addAt(ctx, owner, index, aspect, "AGGREGATE_MISUSE",
                    "'aggregate' is only valid on the owning CHECKLIST question's own branches");
        }

        if (c.ranges() != null && !c.ranges().isEmpty()) {
            if (field == null && !hasText(c.fieldKey()) && target != null && target.type() != QuestionType.NUMERIC) {
                addAt(ctx, owner, index, aspect, "RANGE_ON_NON_NUMERIC",
                        "'ranges' target '" + target.key() + "' is not numeric; name a NUMERIC field instead");
            }
            validateRanges(ctx, owner, index, aspect, c.ranges());
        }

        if (c.comparison() != null) validateComparison(ctx, owner, index, aspect, c.comparison());
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
        for (Range r : ranges) {
            if (r == null) {
                addAt(ctx, owner, index, aspect, "RANGE_NULL", "A range is null");
                continue;
            }
            boolean anyBound = r.gte() != null || r.gt() != null || r.lte() != null || r.lt() != null;
            if (!anyBound) {
                addAt(ctx, owner, index, aspect, "RANGE_EMPTY", "A range has no bounds (matches everything)");
                continue;
            }
            if (r.gte() != null && r.gt() != null)
                addAt(ctx, owner, index, aspect, "RANGE_DOUBLE_LOWER", "Range sets both gte and gt");
            if (r.lte() != null && r.lt() != null)
                addAt(ctx, owner, index, aspect, "RANGE_DOUBLE_UPPER", "Range sets both lte and lt");
            BigDecimal low = r.gte() != null ? r.gte() : r.gt();
            BigDecimal high = r.lte() != null ? r.lte() : r.lt();
            if (low != null && high != null && low.compareTo(high) > 0)
                addAt(ctx, owner, index, aspect, "RANGE_IMPOSSIBLE",
                        "Range lower bound " + low + " exceeds upper bound " + high);
        }
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
        if (!hasText(def.entryQuestion()) || !ctx.byKey.containsKey(def.entryQuestion())) return;

        Set<String> reachable = new HashSet<>();
        dfs(def.entryQuestion(), ctx, reachable, new LinkedHashSet<>());

        for (Question q : ctx.byKey.values()) {
            if (!q.isDisplayOnlyOutput() && !reachable.contains(q.key())) {
                ctx.errors.add(Error.question(ctx.ft, q.key(), Aspect.REACHABILITY, "UNREACHABLE",
                        "Question is not reachable from the entry question"));
            }
        }
        for (String key : reachable) {
            Question q = ctx.byKey.get(key);
            if (q != null && nullToEmpty(q.branches()).isEmpty()) {
                ctx.errors.add(Error.question(ctx.ft, key, Aspect.BRANCHES, "NO_BRANCH",
                        "Reachable question has no onward or terminal branch"));
            }
        }

        Set<RecommendationOutcome> declared = def.outcomes().keySet();
        for (Question q : ctx.byKey.values()) {
            int i = 0;
            for (Branch b : nullToEmpty(q.branches())) {
                if (b != null && b.effect() != null && b.effect().setOutcome() != null
                        && !declared.contains(b.effect().setOutcome())) {
                    ctx.errors.add(Error.branch(ctx.ft, q.key(), i, "OUTCOME_NOT_DECLARED",
                            "Outcome " + b.effect().setOutcome() + " is not in the outcomes catalog"));
                }
                i++;
            }
        }
    }

    private void dfs(String key, Ctx ctx, Set<String> visited, Set<String> onStack) {
        if (onStack.contains(key)) {
            ctx.errors.add(Error.question(ctx.ft, key, Aspect.REACHABILITY, "CYCLE", "Cycle detected involving this question"));
            return;
        }
        if (!visited.add(key)) return;
        onStack.add(key);
        Question q = ctx.byKey.get(key);
        if (q != null) {
            for (Branch b : nullToEmpty(q.branches())) {
                if (b != null && !b.isTerminal() && hasText(b.goTo()) && ctx.byKey.containsKey(b.goTo())) {
                    dfs(b.goTo(), ctx, visited, onStack);
                }
            }
        }
        onStack.remove(key);
    }

    // ------------------------------------------------------------------ catalogues

    private void validateValidationMessages(Ctx ctx) {
        Set<String> messageKeys = new HashSet<>();
        for (ValidationMessage m : ctx.def.validationMessages()) {
            if (m == null) continue;
            if (!hasText(m.messageKey())) {
                ctx.errors.add(Error.catalogue(ctx.ft, Aspect.VALIDATION_MESSAGES, null, "MESSAGE_NO_KEY",
                        "A validation message has no message key"));
            } else if (!messageKeys.add(m.messageKey())) {
                ctx.errors.add(Error.catalogue(ctx.ft, Aspect.VALIDATION_MESSAGES, m.messageKey(), "MESSAGE_DUPLICATE_KEY",
                        "Duplicate message key '" + m.messageKey() + "'"));
            }
            if (m.rule() == null) {
                ctx.errors.add(Error.catalogue(ctx.ft, Aspect.VALIDATION_MESSAGES, m.messageKey(), "MESSAGE_NO_RULE",
                        "Message '" + m.messageKey() + "' names no rule"));
            }
            if (!bothLocales(m.text())) {
                ctx.errors.add(Error.catalogue(ctx.ft, Aspect.VALIDATION_MESSAGES, m.messageKey(), "MESSAGE_LABEL",
                        "Message '" + m.messageKey() + "' is missing EN or FR text"));
            }
            if (hasText(m.questionKey()) && !ctx.byKey.containsKey(m.questionKey())) {
                ctx.errors.add(Error.catalogue(ctx.ft, Aspect.VALIDATION_MESSAGES, m.messageKey(), "MESSAGE_UNKNOWN_QUESTION",
                        "Message '" + m.messageKey() + "' targets unknown question '" + m.questionKey() + "'"));
            }
            if (hasText(m.fieldKey()) && !ctx.fieldsByKey.containsKey(m.fieldKey())) {
                ctx.errors.add(Error.catalogue(ctx.ft, Aspect.VALIDATION_MESSAGES, m.messageKey(), "MESSAGE_UNKNOWN_FIELD",
                        "Message '" + m.messageKey() + "' targets unknown field '" + m.fieldKey() + "'"));
            }
        }
    }

    private void validateInfoPanels(Ctx ctx) {
        for (InfoPanel p : ctx.def.infoPanels()) {
            if (p == null) continue;
            if (!bothLocales(p.title())) {
                ctx.errors.add(Error.catalogue(ctx.ft, Aspect.INFO_PANELS, p.key(), "PANEL_TITLE",
                        "Panel '" + p.key() + "' is missing an EN or FR title"));
            }
            if (nullToEmpty(p.fields()).isEmpty()) {
                ctx.errors.add(Error.catalogue(ctx.ft, Aspect.INFO_PANELS, p.key(), "PANEL_NO_FIELDS",
                        "Panel '" + p.key() + "' displays no fields"));
            }
            FlagDefinition flag = ctx.def.flags().get(p.whenFlagKey());
            if (flag == null) {
                ctx.errors.add(Error.catalogue(ctx.ft, Aspect.INFO_PANELS, p.key(), "PANEL_UNKNOWN_FLAG",
                        "Panel '" + p.key() + "' is shown when '" + p.whenFlagKey() + "', which is not a known flag"));
            } else if (flag.storage() == FlagStorage.CODE
                    && ctx.def.flagValue(flag.valueSet(), p.whenFlagValue()).isEmpty()) {
                ctx.errors.add(Error.catalogue(ctx.ft, Aspect.INFO_PANELS, p.key(), "PANEL_UNKNOWN_FLAG_VALUE",
                        "Panel '" + p.key() + "' triggers on '" + p.whenFlagValue()
                                + "', which is not a code of value set '" + flag.valueSet() + "'"));
            }
        }
    }

    private void checkFlagKnown(Ctx ctx, String questionKey, String subKey, String flagKey, Aspect aspect) {
        if (!ctx.def.flags().containsKey(flagKey)) {
            ctx.errors.add(new Error(ctx.ft, questionKey, subKey, null, aspect, "FLAG_UNKNOWN",
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
