package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LocalizedLabel;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

/**
 * One box inside a DATA_ENTRY question — a row of the Fields tab.
 *
 * @param key         stable code stored in the answer, e.g. {@code adjustedEbitda}. Two boxes may
 *                    share a LABEL but never a key: "IFRS 16 adjustment" appears under both EBITDA
 *                    and Gross Debt.
 * @param group       heading the box sits under, e.g. "ECB Leverage Ratio"; display only
 * @param label       EN / FR caption
 * @param note        EN / FR tooltip, or null
 * @param type        NUMERIC for the whole financial table
 * @param mandatory   must hold a value when the question is saved
 * @param editable    false for anything the system calculates. A {@code FINANCIALS/} field is
 *                    prefilled AND editable — the analyst may overwrite it.
 * @param derivedFrom {@code CALC/x} computed in the domain layer, {@code FINANCIALS/x} read from
 *                    the financials table, or null when the analyst types it
 * @param formula     documentation only; NOTHING evaluates this. The arithmetic lives in a domain
 *                    service so the sheet never becomes an expression language.
 * @param fillsFlag   flag key this value becomes, e.g. {@code ecbLeverageRatio}; else null
 */
@DomainDrivenDesign.ValueObject
public record DataField(
        String key,
        String group,
        LocalizedLabel label,
        LocalizedLabel note,
        DataFieldType type,
        boolean mandatory,
        boolean editable,
        String derivedFrom,
        String formula,
        String fillsFlag) {

    /** True when the analyst can type into this box — prefilled-but-editable counts. */
    public boolean isAnalystInput() {
        return editable;
    }

    /** True when the domain layer computes this box on save. */
    public boolean isCalculated() {
        return derivedFrom != null && derivedFrom.startsWith("CALC/");
    }
}
