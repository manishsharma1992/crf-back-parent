import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import {
  DataField,
  JUSTIFICATION_COMMENT,
  JUSTIFICATION_WORDING,
  LocalizedLabel,
  QuestionView,
  isFieldVisible,
} from '@lazyloaded/counterparty/model/leverage-lending/form-state.model';
import { HoverIconDirective } from '@shared/directives/hover-icon.directive';
import { SvgIconDirective } from '@shared/directives/svg-icon.directive';
import { MaterialModule } from '@shared/modules/MaterialModule';
import {
  JustificationDialogComponent,
  JustificationDialogData,
  JustificationDialogResult,
} from './justification-dialog/justification-dialog.component';

/** A box as the template renders it: the authored field plus what this build decided about it. */
export interface FinancialRow {
  field: DataField;
  label: string;
  note: string;
  editable: boolean;
  /** Formatted for the screen. Never read back — see `displayValue`. */
  display: string;
  justified: boolean;
  needsJustification: boolean;
}

/** A heading from the Fields tab's Group column, with the rows that sit under it. */
export interface FinancialGroup {
  name: string;
  rows: FinancialRow[];
}

/**
 * The ECB financial table — the DATA_ENTRY renderer for Q-F01.
 *
 * <p>Presentational, like the rest of the ECB renderers: it owns no state, calls no endpoint, and
 * emits `answered` for the parent to traverse and persist. The eighteen boxes and their order come
 * from the workbook via `question.fields`; nothing about the table is named here, so a row added on
 * the Fields tab appears without a code change.
 *
 * <p><b>It does not calculate.</b> Every total and both ratios arrive already computed from the
 * backend, in `subAnswers`. Reimplementing the arithmetic here to save a round trip would give two
 * implementations of a regulated calculation, and they would drift — at which point the screen and
 * the frozen record disagree about what the analyst was shown.
 */
@Component({
  selector: 'bnpp-financial-table',
  templateUrl: './financial-table.component.html',
  styleUrls: ['./financial-table.component.scss'],
  imports: [MaterialModule, ReactiveFormsModule, SvgIconDirective, HoverIconDirective],
  standalone: true,
})
export class FinancialTableComponent {
  question = input.required<QuestionView>();
  group = input.required<FormGroup>();
  locale = input.required<string>();

  /** Same contract as every other ECB renderer: a dotted key and its new value. */
  answered = output<{ value: string; questionKey: string }>();

  private readonly dialog = inject(MatDialog);

  /**
   * Bumped when a justification is written.
   *
   * <p>The two halves live on controls this component never binds to, so nothing else would tell
   * the computed rows that a marker has changed.
   */
  private readonly justificationTick = signal(0);

  /** Two decimals on screen; the stored value keeps all twenty-eight. */
  private static readonly DISPLAY_DECIMALS = 2;

  /** What an absent value reads as. Matches the info-panel convention the BA set. */
  private static readonly ABSENT = '-';

  /**
   * Rows grouped by the Group column, in Fields-tab order.
   *
   * <p>Hidden boxes are dropped here rather than in the template: `netDebt` is part of the record
   * and part of the form group — posted and frozen like any other box — but it has no row on
   * screen, and a template-level `@if` would leave an empty slot in its group.
   */
  readonly groups = computed<FinancialGroup[]>(() => {
    this.justificationTick();

    const grouped: FinancialGroup[] = [];
    for (const field of this.question().fields ?? []) {
      if (!isFieldVisible(field)) {
        continue;
      }
      const name = field.group ?? '';
      let target = grouped.find(candidate => candidate.name === name);
      if (!target) {
        target = { name, rows: [] };
        grouped.push(target);
      }
      target.rows.push(this.toRow(field));
    }
    return grouped;
  });

  private toRow(field: DataField): FinancialRow {
    const justified = this.isJustified(field.key);
    return {
      field,
      label: this.labelText(field.label),
      note: this.labelText(field.note),
      editable: field.editable,
      display: this.displayValue(field),
      justified,
      needsJustification: field.editable && this.hasText(this.valueOf(field.key)) && !justified,
    };
  }

  // ------------------------------------------------------------------ values

  control(subKey: string): FormControl | null {
    return (this.group().controls[subKey] as FormControl) ?? null;
  }

  private valueOf(subKey: string): unknown {
    return this.control(subKey)?.value;
  }

  /**
   * A read-only box's value, formatted.
   *
   * <p><b>Display only.</b> The rounded string is never posted and never compared: a true ratio of
   * 3.9994 shows here as 4.00, and a predicate reading that would skip the `[0 .. <4]` termination
   * while `> 4x` stayed false, ending the analysis ECB_LEVERAGED on a sub-4 ratio. What the
   * backend stores keeps all twenty-eight decimals, and the routing reads that.
   *
   * <p>An absent value renders as a dash rather than as blank, so an empty row reads as "there is
   * nothing here" instead of as a rendering failure — the same convention the info panels use. A
   * blocked analysis withholds every calculated figure, so the lower half of the table shows
   * dashes, which is the intended signal rather than a fault.
   */
  private displayValue(field: DataField): string {
    const raw = this.valueOf(field.key);
    if (raw === null || raw === undefined || raw === '') {
      return FinancialTableComponent.ABSENT;
    }
    const parsed = Number(raw);
    if (Number.isNaN(parsed)) {
      return String(raw); // never silently blank something the backend sent
    }
    return parsed.toLocaleString(this.locale(), {
      minimumFractionDigits: FinancialTableComponent.DISPLAY_DECIMALS,
      maximumFractionDigits: FinancialTableComponent.DISPLAY_DECIMALS,
    });
  }

  // ------------------------------------------------------------------ answering

  /**
   * An adjustment changed.
   *
   * <p>On change rather than on every keystroke: each answer re-traverses and the backend recomputes
   * five figures, so per-character would put the analyst's typing in a race with the response that
   * rewrites the calculated rows underneath them.
   */
  onAmountChanged(field: DataField, value: string | null): void {
    this.emit(field.key, value);
  }

  private emit(subKey: string, value: string | null): void {
    this.answered.emit({
      value: value ?? '',
      questionKey: `${this.question().key}.${subKey}`,
    });
  }

  // ------------------------------------------------------------------ justification

  /**
   * Both halves present, or the box is unjustified.
   *
   * <p>A wording with no comment names the adjustment without explaining it, which is not an audit
   * trail — and the backend refuses it, so treating it as justified here would only move the
   * failure to the save.
   */
  private isJustified(fieldKey: string): boolean {
    return (
      this.hasText(this.valueOf(`${fieldKey}.${JUSTIFICATION_WORDING}`)) &&
      this.hasText(this.valueOf(`${fieldKey}.${JUSTIFICATION_COMMENT}`))
    );
  }

  /**
   * Opens the pop-in for one box.
   *
   * <p>The dialog edits copies and returns them, so CANCEL genuinely leaves the box untouched —
   * nothing is written until VALIDATE or DISMISS comes back.
   */
  openJustification(row: FinancialRow): void {
    const fieldKey = row.field.key;
    const data: JustificationDialogData = {
      fieldLabel: row.label,
      wording: (this.valueOf(`${fieldKey}.${JUSTIFICATION_WORDING}`) as string) ?? null,
      comment: (this.valueOf(`${fieldKey}.${JUSTIFICATION_COMMENT}`) as string) ?? null,
      hasValue: this.hasText(this.valueOf(fieldKey)),
    };

    this.dialog
      .open<JustificationDialogComponent, JustificationDialogData, JustificationDialogResult>(
        JustificationDialogComponent,
        { data, autoFocus: true, restoreFocus: true },
      )
      .afterClosed()
      .subscribe(result => this.applyJustification(fieldKey, result));
  }

  /**
   * Writes what the dialog returned.
   *
   * <p>Each half is emitted separately because each is its own answer key, and the parent's
   * `controlFor` resolves them one at a time. That means two or three traversals for one dialog —
   * redundant work, but it keeps this component on the identical contract every other renderer
   * uses, which is worth more than the saved round trips.
   *
   * <p>DISMISS clears the figure and both halves together. That is what makes an empty box
   * reachable again: without it, a box that has ever been typed into could only be emptied by
   * deleting the digits, leaving a wording and comment describing a figure that no longer exists.
   */
  private applyJustification(fieldKey: string, result: JustificationDialogResult | undefined): void {
    if (!result) {
      return; // CANCEL
    }
    if (result.action === 'dismiss') {
      this.emit(fieldKey, null);
      this.emit(`${fieldKey}.${JUSTIFICATION_WORDING}`, null);
      this.emit(`${fieldKey}.${JUSTIFICATION_COMMENT}`, null);
    } else {
      this.emit(`${fieldKey}.${JUSTIFICATION_WORDING}`, result.wording);
      this.emit(`${fieldKey}.${JUSTIFICATION_COMMENT}`, result.comment);
    }
    this.justificationTick.update(tick => tick + 1);
  }

  // ------------------------------------------------------------------ labels

  /** Field labels are plain LocalizedLabel — a different shape from a question's. */
  labelText(label: LocalizedLabel | null | undefined): string {
    if (!label) {
      return '';
    }
    return (this.isFrench() ? label.fr : label.en) ?? '';
  }

  private hasText(value: unknown): boolean {
    return value !== null && value !== undefined && String(value).trim() !== '';
  }

  private isFrench(): boolean {
    return this.locale()?.toUpperCase() === 'FR';
  }
}
