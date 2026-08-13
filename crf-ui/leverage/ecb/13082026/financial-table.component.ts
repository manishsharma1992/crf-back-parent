import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
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

/** A box as the template renders it: the authored field plus what this build decided about it. */
export interface FinancialRow {
  field: DataField;
  label: string;
  note: string;
  editable: boolean;
  /** Formatted for the screen. Never read back — see `displayValue`. */
  display: string;
  justified: boolean;
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
 * from the workbook via `question.fields`; nothing about the table is hard-coded here, so a row
 * added on the Fields tab appears without a code change.
 *
 * <p><b>It does not calculate.</b> Every total and both ratios arrive already computed from the
 * backend, in `subAnswers`. Reimplementing the arithmetic here to avoid a round trip would give
 * two implementations of a regulated calculation that drift, and the screen would eventually
 * disagree with the frozen record.
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

  /** Which box has its justification pop-in open, or null. Never more than one. */
  readonly openJustification = signal<string | null>(null);

  /** Bumped when a justification is written, so the derived rows re-evaluate. */
  private readonly justificationTick = signal(0);

  /** Two decimals on screen; the stored value keeps all twenty-eight. */
  private static readonly DISPLAY_DECIMALS = 2;

  /** What an absent value reads as. Matches the info-panel convention the BA set. */
  private static readonly ABSENT = '-';

  /**
   * Rows grouped by the Group column, in Fields-tab order.
   *
   * <p>Hidden boxes are dropped here rather than in the template: `netDebt` is part of the record
   * and part of the form group — it is posted and frozen like any other — but it has no row on
   * screen, and a template-level `@if` would leave an empty slot in the group it belongs to.
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
    return {
      field,
      label: this.labelText(field.label),
      note: this.labelText(field.note),
      editable: field.editable,
      display: this.displayValue(field),
      justified: this.isJustified(field.key),
    };
  }

  // ------------------------------------------------------------------ values

  /**
   * A read-only box's value, formatted.
   *
   * <p><b>Display only.</b> The rounded string is never posted and never compared against
   * anything: a true ratio of 3.9994 shows here as 4.00, and a predicate reading that would skip
   * the `[0 .. <4]` termination while `> 4x` stayed false, ending the analysis ECB_LEVERAGED on a
   * sub-4 ratio. What the backend stores keeps all twenty-eight decimals, and the routing reads
   * that.
   *
   * <p>An absent value renders as a dash rather than as blank, so an empty row reads as "there is
   * nothing here" instead of as a rendering failure — the same convention the info panels use.
   * A blocked analysis withholds every calculated figure, so the whole lower half of the table
   * shows dashes, which is the intended signal.
   */
  private displayValue(field: DataField): string {
    const raw = this.control(field.key)?.value;
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

  control(subKey: string): FormControl | null {
    return (this.group().controls[subKey] as FormControl) ?? null;
  }

  // ------------------------------------------------------------------ answering

  /**
   * An adjustment changed.
   *
   * <p>Emitted on change rather than on every keystroke: each answer re-traverses and the backend
   * recomputes five figures, so per-character would put the analyst's typing in a race with the
   * response that overwrites the calculated rows.
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
   * trail — and the backend refuses it, so accepting it here would only move the failure later.
   */
  isJustified(fieldKey: string): boolean {
    this.justificationTick();
    return (
      this.hasText(this.control(`${fieldKey}.${JUSTIFICATION_WORDING}`)?.value) &&
      this.hasText(this.control(`${fieldKey}.${JUSTIFICATION_COMMENT}`)?.value)
    );
  }

  /**
   * True when the box holds a figure but no justification — the state the save will refuse.
   *
   * <p>Shown as a marker on the row rather than as an alert. The alert box is for things the
   * analyst must act on now; this is a reminder attached to the thing that needs it.
   */
  needsJustification(field: DataField): boolean {
    return field.editable && this.hasText(this.control(field.key)?.value) && !this.isJustified(field.key);
  }

  openJustificationFor(fieldKey: string): void {
    this.openJustification.set(fieldKey);
  }

  closeJustification(): void {
    this.openJustification.set(null);
  }

  isJustificationOpen(fieldKey: string): boolean {
    return this.openJustification() === fieldKey;
  }

  /**
   * VALIDATE in the pop-in: write both halves and close.
   *
   * <p>Two emissions, not one — each is a separate answer key, and the parent's `controlFor`
   * resolves each on its own. The second traversal is redundant work but it keeps this component
   * on the same contract as every other renderer, which is worth more than the saved round trip.
   */
  onJustificationValidated(fieldKey: string, wording: string, comment: string): void {
    this.emit(`${fieldKey}.${JUSTIFICATION_WORDING}`, wording);
    this.emit(`${fieldKey}.${JUSTIFICATION_COMMENT}`, comment);
    this.justificationTick.update(tick => tick + 1);
    this.closeJustification();
  }

  /**
   * DISMISS ADJUSTMENT: clear the value and both halves together.
   *
   * <p>This is what makes "absent" a reachable state. Without it a box that has ever been typed
   * into could only be emptied by deleting the digits, leaving the wording and comment behind for
   * a figure that no longer exists.
   */
  onJustificationDismissed(fieldKey: string): void {
    this.emit(fieldKey, null);
    this.emit(`${fieldKey}.${JUSTIFICATION_WORDING}`, null);
    this.emit(`${fieldKey}.${JUSTIFICATION_COMMENT}`, null);
    this.justificationTick.update(tick => tick + 1);
    this.closeJustification();
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
