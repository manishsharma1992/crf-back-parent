package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.List;

/**
 * Result of validating a single {@link DecisionTreeDefinition}.
 *
 * <p>PURE domain value object. Errors describe problems in DOMAIN terms and carry NO Excel
 * coordinates: the validator does not know whether the tree came from a spreadsheet, a UI or a
 * test fixture. The application layer joins {@link Error.Aspect} and question key back to a cell
 * through the SourceLocator seam.
 */
@DomainDrivenDesign.ValueObject
public record ValidationResult(List<Error> errors) {

    public ValidationResult {
        errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public static ValidationResult ok() {
        return new ValidationResult(List.of());
    }

    /**
     * @param subKey      checklist item key, data field key, flag key or message key
     * @param branchIndex 0-based line of the Branches (or Value Rules) cell, else null
     * @param code        stable machine code, never user-facing text
     */
    @DomainDrivenDesign.ValueObject
    public record Error(
            LeverageFormType formType,
            String questionKey,
            String subKey,
            Integer branchIndex,
            Aspect aspect,
            String code,
            String message) {

        /** Logical part of a question — resolves to a column of the authoring template. */
        public enum Aspect {
            FORM,
            KEY,
            TYPE,
            LABEL_EN,
            LABEL_FR,
            OPTIONS,
            ITEMS,
            FIELDS,
            DERIVED_FROM,
            VALUE_RULES,
            PREFILL_FROM,
            BRANCHES,
            FILLS_FLAG,
            FLAGS_CATALOGUE,
            FLAG_VALUES,
            VALIDATION_MESSAGES,
            INFO_PANELS,
            REACHABILITY
        }

        public static Error form(LeverageFormType ft, String code, String message) {
            return new Error(ft, null, null, null, Aspect.FORM, code, message);
        }

        public static Error question(LeverageFormType ft, String key, Aspect aspect, String code, String message) {
            return new Error(ft, key, null, null, aspect, code, message);
        }

        public static Error branch(LeverageFormType ft, String key, int branchIndex, String code, String message) {
            return new Error(ft, key, null, branchIndex, Aspect.BRANCHES, code, message);
        }

        public static Error valueRule(LeverageFormType ft, String key, int ruleIndex, String code, String message) {
            return new Error(ft, key, null, ruleIndex, Aspect.VALUE_RULES, code, message);
        }

        public static Error item(LeverageFormType ft, String key, String itemKey, String code, String message) {
            return new Error(ft, key, itemKey, null, Aspect.ITEMS, code, message);
        }

        public static Error field(LeverageFormType ft, String key, String fieldKey, String code, String message) {
            return new Error(ft, key, fieldKey, null, Aspect.FIELDS, code, message);
        }

        public static Error catalogue(LeverageFormType ft, Aspect aspect, String subKey, String code, String message) {
            return new Error(ft, null, subKey, null, aspect, code, message);
        }
    }
}
