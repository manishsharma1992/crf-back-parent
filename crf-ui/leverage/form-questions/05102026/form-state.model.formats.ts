/**
 * ADDITIONS to form-state.model.ts — paste beside formatDisplayValue.
 */

import { DataField, DataTypeField, ABSENT } from './form-state.model';

/** Two decimals on screen. The stored value keeps all twenty-eight, and the routing reads that. */
const DISPLAY_DECIMALS = 2;

/** A percentage needs no more than this on screen; 60%, 8.25%, never 60.0000%. */
const PERCENT_DECIMALS = 2;

export enum FieldFormat {
    /** A sum of money, or any plain figure. The default. */
    AMOUNT = 'AMOUNT',
    /** A fraction stored 0..1 and READ as a percentage: 0.60 is sixty per cent. */
    PERCENT = 'PERCENT',
    /** A multiple, written with an x: 5 is "5x", 2.6 is "2.6x". */
    MULTIPLE = 'MULTIPLE',
}

/**
 * How a box's figure should be read, where the figure alone does not say.
 *
 * <p><b>Yes, this names fields — and like PANEL_SLOTS it is the only place that does.</b> Every box
 * here is {@code DataTypeField.NUMERIC}, because that is what it is; the type says how the value is
 * STORED, not how it is READ. {@code reitThresholdForExclusionOfHighlySecuredDebt} holds 0.60 and
 * means sixty per cent, and nothing on the wire distinguishes it from an amount of 0.60.
 *
 * <p><b>Why this one matters more than the panel slots.</b> An unclaimed panel field renders
 * unstyled and someone eventually notices. An unclaimed PERCENT renders as a perfectly plausible
 * number that is wrong by a factor of a hundred, on a threshold an analyst reads to decide whether
 * a carve-out applies. A silent wrong number beats a visibly ugly one every time, which is why the
 * entries below are worth keeping in step by hand until the workbook carries the answer.
 *
 * <p><b>Where this belongs eventually: a Format column on the Fields tab.</b> The BA knows 60% is a
 * percentage — they wrote "displayed as 60%" in the Note. One column, read by FieldsSheetParser
 * into DataField, and this map disappears. Worth doing when the next workbook change lands rather
 * than on its own.
 *
 * <p>No ECB key appears below, deliberately. ECB's figures are amounts and its two ratios already
 * render as plain two-decimal numbers that analysts have been reading for months; changing that is
 * not this ticket.
 */
export const FIELD_FORMATS: Readonly<Record<string, FieldFormat>> = {
    // --- APLC: thresholds and the ratio are multiples, written with an x
    aplcDebtToEquityRatio: FieldFormat.MULTIPLE,
    aplcDebtToEquityLeverageThreshold: FieldFormat.MULTIPLE,
    aplcDebtToEquityEscalationThreshold: FieldFormat.MULTIPLE,
    aplcImpliedDebtToEquity: FieldFormat.MULTIPLE,

    // --- REIT: fractions stored 0..1, every one of them read as a percentage
    reitPctHighlySecuredPortionCommittedTotalDebt: FieldFormat.PERCENT,
    reitThresholdForExclusionOfHighlySecuredDebt: FieldFormat.PERCENT,
    reitCommittedDebtYield: FieldFormat.PERCENT,
    reitCommittedDebtYieldThreshold: FieldFormat.PERCENT,
    reitDebtToMarketCap: FieldFormat.PERCENT,
    reitDebtToMarketValueAssetsLeverageThreshold: FieldFormat.PERCENT,
};

/**
 * One box's value, as it should be read.
 *
 * <p><b>Display only. Never fed back into a comparison.</b> A true ratio of 3.9994 renders as 4.00,
 * and a predicate reading that would skip a {@code [0 .. <4]} termination while {@code > 4x} stayed
 * false — ending the analysis leveraged on a sub-4 ratio. What the backend stores keeps all
 * twenty-eight decimals and the routing reads that.
 *
 * <p>Absent renders as a dash rather than as blank, so an empty row reads as "there is nothing
 * here" instead of as a rendering failure. A blocked analysis withholds every calculated figure, so
 * the whole lower half of the table shows dashes — which is the intended signal, not a fault.
 */
export function formatFieldValue(field: DataField, raw: unknown, locale: string): string {
    if (raw === null || raw === undefined || String(raw).trim() === '') {
        return ABSENT;
    }
    const text = String(raw).trim();

    switch (field.type) {
        case DataTypeField.DATE:
            return formatIsoDate(text, locale);

        // A code (YES / NO / NOT_APPLICABLE) or free text. Written as it arrived: Number('YES') is
        // NaN, and the numeric path below would otherwise hand it back untouched by accident
        // rather than on purpose.
        case DataTypeField.TEXT:
        case DataTypeField.LOOKUP:
            return text;

        case DataTypeField.NUMERIC:
        default:
            return formatNumeric(field, text, locale);
    }
}

function formatNumeric(field: DataField, text: string, locale: string): string {
    const parsed = Number(text);
    if (Number.isNaN(parsed)) {
        return text; // never silently blank something the backend sent
    }

    switch (FIELD_FORMATS[field.key]) {
        case FieldFormat.PERCENT:
            return `${trimZeros(parsed * 100, PERCENT_DECIMALS, locale)}%`;
        case FieldFormat.MULTIPLE:
            return `${trimZeros(parsed, DISPLAY_DECIMALS, locale)}x`;
        default:
            return parsed.toLocaleString(locale, {
                minimumFractionDigits: DISPLAY_DECIMALS,
                maximumFractionDigits: DISPLAY_DECIMALS,
            });
    }
}

/**
 * Percentages and multiples drop trailing zeros; amounts keep them.
 *
 * <p>A threshold reads "60%" and "5x", not "60.00%" and "5.00x" — the BA writes them that way in
 * the Note and the analyst says them that way. An amount keeps two decimals because a column of
 * figures that do not line up is harder to scan than one with redundant zeros.
 */
function trimZeros(value: number, maxDecimals: number, locale: string): string {
    return value.toLocaleString(locale, { minimumFractionDigits: 0, maximumFractionDigits: maxDecimals });
}

/**
 * An ISO date, in the analyst's locale.
 *
 * <p>Parsed from the PARTS, never {@code new Date('2024-06-30')} — the spec reads a bare ISO date
 * as UTC midnight, so in any zone behind UTC the rendered day is the one before the stored one.
 *
 * <p>Anything that will not parse is written as it arrived. The backend refuses a malformed date on
 * save, so if one reaches the screen the useful thing is to show what is actually stored rather
 * than a dash that hides it.
 */
function formatIsoDate(text: string, locale: string): string {
    const [year, month, day] = text.split('-').map(Number);
    if (Number.isNaN(year) || Number.isNaN(month) || Number.isNaN(day)) {
        return text;
    }
    return new Date(year, month - 1, day).toLocaleDateString(locale);
}
