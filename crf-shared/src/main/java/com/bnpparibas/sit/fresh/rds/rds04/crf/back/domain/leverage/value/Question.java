package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LocalizedQuestionLabel;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.input.DataField;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.routing.Branch;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.routing.ValueRule;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.List;

/**
 * One node of the tree — a row of a question tab.
 *
 * <p>There is no {@code order}: screen order comes from the routing, not from a column. The
 * SAME rows serve both LBO scenarios in a DIFFERENT order, so any static number would be wrong
 * for one of them.
 *
 * <p>There is no {@code external} either. Every calculation now happens in the domain layer, so
 * the walk never pauses on an outside service and the engine has two states, PENDING_INPUT and
 * TERMINAL.
 *
 * @param key         stable code, e.g. {@code Q-S04}. Never renamed once analyses exist.
 * @param type        see {@link QuestionType}
 * @param mandatory   for a CHECKLIST this applies to EVERY item, unless a YES short-circuits it
 * @param computed    true when the system fills the value rather than the analyst
 * @param editable    false for computed values; "editable when not prefilled" elsewhere
 * @param derivedFrom single fixed source, e.g. {@code COUNTERPARTY/PARENT}. Mutually exclusive
 *                    with {@code valueRules}.
 * @param valueRules  ordered rules deciding the value from earlier answers; first match wins
 * @param prefillFrom {@code FED/Q01} — copied from another form when answered there, and the
 *                    field is then read-only; otherwise the analyst answers it here
 * @param label       EN / FR caption, with optional bullets
 * @param subtitle    EN / FR guidance shown under the label
 * @param note        EN / FR tooltip behind the info icon; its bullets may nest one level
 * @param options     SINGLE_CHOICE / BOOLEAN / COMPUTED values, or the {@code LOOKUP/x} source
 * @param items       CHECKLIST sub-items
 * @param fields      DATA_ENTRY boxes, joined from the Fields tab by question key
 * @param branches    routing, in authored order
 * @param fillsFlag   the chosen option BECOMES this flag — Q01 fills {@code ecbLboFlag}, Q-Q03
 *                    fills {@code ecbCovenantStructure}. Saves one branch per option, and works
 *                    on paths that CONTINUE rather than end, where {@code flags:} cannot reach.
 */
@DomainDrivenDesign.Entity
public record Question(
        String key,
        QuestionType type,
        boolean mandatory,
        boolean computed,
        boolean editable,
        String derivedFrom,
        List<ValueRule> valueRules,
        String prefillFrom,
        LocalizedQuestionLabel label,
        LocalizedQuestionLabel subtitle,
        LocalizedQuestionLabel note,
        List<Option> options,
        List<ChecklistItem> items,
        List<DataField> fields,
        List<Branch> branches,
        String fillsFlag) {

    public Question {
        valueRules = valueRules == null ? List.of() : List.copyOf(valueRules);
        options = options == null ? List.of() : List.copyOf(options);
        items = items == null ? List.of() : List.copyOf(items);
        fields = fields == null ? List.of() : List.copyOf(fields);
        branches = branches == null ? List.of() : List.copyOf(branches);
    }

    /**
     * A COMPUTED node with no branches is DISPLAYED, not walked, so it is exempt from the
     * reachability rule. Previously this was {@code COMPUTED && !external}; with external gone
     * that test would exempt every computed node, including Q-S04 and Q-Q01, which ARE walked.
     */
    public boolean isDisplayOnlyOutput() {
        return type == QuestionType.COMPUTED && branches.isEmpty();
    }

    public java.util.Optional<DataField> field(String fieldKey) {
        return fields.stream().filter(f -> f.key().equals(fieldKey)).findFirst();
    }
}
