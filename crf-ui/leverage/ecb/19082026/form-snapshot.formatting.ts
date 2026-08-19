/*
 * form-snapshot.component.ts — three methods change, plus the import.
 * The template is unchanged: it already renders flag.displayValue and row.value.
 */

import {
  ABSENT,
  formatDisplayValue,
} from '@lazyloaded/counterparty/service/leverage-lending/display-format';

// ============================================================ sectionFor

/**
 * One addition: flag values are formatted before they reach the template.
 *
 * <p>`ecbLeverageRatio` is a NUMBER flag, so it has no value set and no authored label — the
 * backend hands back the stored figure verbatim, all twenty-eight decimals of it. Coded flags are
 * untouched by this, because `formatDisplayValue` leaves anything non-numeric alone: INR stays INR
 * and BUSINESS GROUP stays BUSINESS GROUP.
 */
private sectionFor(formType: LeverageFormType, state: FormState | null): SnapshotSection | null {
  if (!state) {
    return null;
  }
  const panels = (state.infoPanels ?? []).map(panel => this.toPanel(panel));
  const flags = (state.flagViews ?? []).map(flag => ({
    ...flag,
    displayValue: formatDisplayValue(flag.displayValue, this.locale()),
  }));

  if (!panels.length && !flags.length) {
    return null;
  }
  return { formType, panels, flags };
}

// ============================================================ valueOf

/**
 * Panel values get the same treatment, for the same reason: a panel can display the leverage
 * ratio, and it would otherwise disagree with the financial table three inches away.
 */
private valueOf(panel: PanelSnapshot, field: string): string {
  return formatDisplayValue(panel.values?.[field], this.locale());
}

/*
 * ============================================================ where else to use this
 *
 * formatStatus(state.status)
 *   Wherever IN_PROGRESS is currently rendered — the status chip, and the form snapshot if you
 *   decide to show it there. Do not use humanise() for this: the label map is localised and
 *   "In progress" is not a mechanical transformation of the code in French.
 *
 * BUSINESS_GROUP
 *   CHECK THE WORKBOOK FIRST. If this reaches the screen through a flag, its Flag Values row
 *   should carry Display EN "Business group" and a French equivalent, and the backend resolves it
 *   with no code change at all.
 *
 *   If it reaches the screen through Q-S04's `question.answer` instead — that question is COMPUTED
 *   with options BUSINESS_GROUP and BORROWER — then the value IS the option code, and the option's
 *   own label on the Options column is what should be shown. `ecb-questions` currently solves this
 *   with `levelOfLeveragedCalculated()` and a `LevelOfLeveraged` enum, which is the thing to
 *   retire: it hardcodes in TypeScript a wording the BA can edit in the sheet, and when she does,
 *   nothing fails — the screen just shows the old words.
 *
 *   The durable fix is for the COMPUTED renderer to look the answer up in `question.options` and
 *   render that option's localised label, exactly as the SINGLE_CHOICE renderer already does.
 *   Then no code names BUSINESS_GROUP anywhere.
 */
