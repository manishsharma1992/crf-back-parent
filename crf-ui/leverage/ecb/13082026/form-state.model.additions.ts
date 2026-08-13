/*
 * Additions to form-state.model.ts. Replace the existing DataField class with this one; the rest
 * of the file is unchanged.
 */

/**
 * One box inside a DATA_ENTRY question — a row of the workbook's Fields tab.
 *
 * <p>`editable` and `visible` were missing from this class while being present on the wire, so the
 * client could not tell a typed adjustment from a calculated total. Inferring it from
 * `derivedFrom` happens to give the right answer for all eighteen rows of the current workbook —
 * every non-editable box has a source and every editable one does not — but that is a property of
 * this workbook, not a rule, and it would break the first time a FINANCIALS/ box is made editable.
 *
 * @param editable    true only for the ten adjustments the analyst types into. The two ratios, the
 *                    three totals and the three FINSTAR figures are false.
 * @param visible     false for a box that is part of the record and frozen with the answer but
 *                    never rendered — `netDebt`, which feeds Total Net Funded Debt. Optional on
 *                    the wire: a definition published before the column existed omits it, and
 *                    absent means visible.
 * @param derivedFrom `CALC/x` computed in the domain layer, `FINANCIALS/x` read from FINSTAR, or
 *                    absent when the analyst types it.
 * @param formula     documentation only. NOTHING evaluates this, on either side — the arithmetic
 *                    lives in the backend domain layer, and the client renders what it is sent.
 */
export class DataField {
  key: string;
  group: string;
  label: LocalizedLabel;
  note: LocalizedLabel;
  type: DataTypeField;
  mandatory: boolean;
  editable: boolean;
  visible?: boolean;
  derivedFrom: string;
  formula: string;
  fillsFlag: string;
}

/** Absent means visible — see the note on `DataField.visible`. */
export function isFieldVisible(field: DataField): boolean {
  return field.visible !== false;
}

/**
 * The two halves of an adjustment's justification, as sub-answer keys.
 *
 * <p>Kept as one place because the backend treats a box as either fully absent or fully complete:
 * value, wording and comment are written together and cleared together, and a wording without a
 * comment is not a justification.
 */
export const JUSTIFICATION_WORDING = 'wording';
export const JUSTIFICATION_COMMENT = 'comment';

/** Server-side limits, mirrored so the counters agree with what will be accepted. */
export const JUSTIFICATION_WORDING_MAX = 40;
export const JUSTIFICATION_COMMENT_MAX = 100;
