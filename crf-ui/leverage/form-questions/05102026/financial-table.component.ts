import { Component, computed, inject, input, output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import {
    DataField,
    DataTypeField,
    FinancialGroup,
    FinancialRow,
    formatFieldValue,
    JUSTIFICATION_COMMENT,
    JUSTIFICATION_WORDING,
    JustificationDialogData,
    JustificationDialogResult,
    LocalizedLabel,
    QuestionView,
    isFieldVisible,
} from '@lazyloaded/counterparty/model/leverage-lending/form-state.model';
import { HoverIconDirective } from '@shared/directives/hover-icon.directive';
import { SvgIconDirective } from '@shared/directives/svg-icon.directive';
import { MaterialModule } from '@shared/modules/MaterialModule';

import { JustificationDialogComponent } from '../justification-dialog/justification-dialog.component';

/**
 * One DATA_ENTRY question: the financial table.
 *
 * <p>Renders whatever boxes the definition declares, so ECB's Q-F01, APLC's and REIT's are the same
 * component with different rows. What it now also does is render them by TYPE — ECB's eighteen
 * boxes are all numeric, FED's are not, and formatting every box to two decimals turned a
 * sixty-per-cent threshold into "0.60" and an ISO date into itself.
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

    /** Same contract as every other renderer: a dotted key and its new value. */
    answered = output<{ value: string; questionKey: string }>();

    private readonly dialog = inject(MatDialog);

    /** Bumped when a justification is written, so the derived rows re-evaluate. */
    private readonly justificationTick = signal(0);

    readonly DataTypeField = DataTypeField;

    /**
     * Rows grouped by the Group column, in Fields-tab order.
     *
     * <p>Hidden boxes are dropped here rather than in the template: `netDebt` is part of the record
     * and part of the form group — posted and frozen like any other — but it has no row on screen,
     * and a template-level `@if` would leave an empty slot in the group it belongs to.
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
        const hasValue = this.hasText(this.valueOf(field.key));
        return {
            field,
            label: this.labelText(field.label),
            note: this.labelText(field.note),
            editable: field.editable,
            display: formatFieldValue(field, this.valueOf(field.key), this.locale()),
            justified,
            needsJustification: this.needsJustification(field) && hasValue && !justified,
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
     * What the picker should show for a DATE box: the stored ISO string as a Date, or null.
     *
     * <p>Built from the PARTS rather than {@code new Date('2024-06-30')}, which the spec reads as
     * UTC midnight — so in any zone behind UTC the picker opens on the previous day.
     */
    dateValue(fieldKey: string): Date | null {
        const stored = this.valueOf(fieldKey) as string | null;
        if (!stored) {
            return null;
        }
        const [year, month, day] = String(stored).split('-').map(Number);
        return Number.isNaN(year) ? null : new Date(year, month - 1, day);
    }

    // ------------------------------------------------------------------ answering

    /**
     * An adjustment changed.
     *
     * <p>Emitted on change rather than on every keystroke: each answer re-traverses and the backend
     * recomputes the table, so per-character would put the analyst's typing in a race with the
     * response that overwrites the calculated rows.
     */
    onAmountChanged(field: DataField, value: string | null): void {
        this.emit(field.key, value);
    }

    /**
     * A date was picked.
     *
     * <p><b>Formatted from the LOCAL parts, never {@code toISOString()}.</b> The picker returns
     * local midnight; converting that to UTC in Mumbai subtracts five and a half hours and yields
     * the day before. The analyst picks the 30th, the form stores the 29th — and the backend
     * accepts it, because it is a perfectly well-formed date.
     */
    onDateChanged(field: DataField, picked: Date | null): void {
        this.emit(field.key, picked ? FinancialTableComponent.toIsoDate(picked) : null);
    }

    private static toIsoDate(date: Date): string {
        const month = `${date.getMonth() + 1}`.padStart(2, '0');
        const day = `${date.getDate()}`.padStart(2, '0');
        return `${date.getFullYear()}-${month}-${day}`;
    }

    private emit(subKey: string, value: string | null): void {
        this.answered.emit({ value: value ?? '', questionKey: `${this.question().key}.${subKey}` });
    }

    // ------------------------------------------------------------------ justification

    /**
     * Which boxes owe a reason when they hold a figure.
     *
     * <p>Not every editable box: a justification explains why an ADJUSTMENT moved a regulatory
     * figure, and a date or a code is not an adjustment. {@code reitOriginationDate} is editable and
     * numeric-adjacent, and offering a Justify button beside it would ask the analyst to explain
     * when a quarter ended.
     *
     * <p><b>Approximate on purpose, and worth knowing.</b> The authoritative answer is whether the
     * Forms tab carries a JUSTIFICATION_REQUIRED row for the box, and the client is never told. So
     * this errs towards showing the button: a spurious one is a pop-in the analyst closes, a
     * missing one is a save they cannot complete and no way on screen to see why.
     */
    private needsJustification(field: DataField): boolean {
        return field.editable && field.type === DataTypeField.NUMERIC;
    }

    /**
     * Both halves present, or the box is unjustified.
     *
     * <p>A wording with no comment names the adjustment without explaining it, which is not an audit
     * trail — and the backend refuses it, so accepting it here would only move the failure later.
     */
    isJustified(fieldKey: string): boolean {
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
                { data, autoFocus: true, restoreFocus: true, width: '800px' },
            )
            .afterClosed()
            .subscribe(result => this.applyJustification(fieldKey, result));
    }

    /**
     * Writes what the dialog returned, in ONE round trip.
     *
     * <p><b>Why not emit each key separately.</b> Every emitted answer makes the parent re-traverse,
     * so two emits meant two requests in flight: the first carrying the wording alone, the second
     * carrying both. Both responses run through syncSubGroup, which writes {@code supplied[subKey]
     * ?? null} into every control — so whenever the first landed second, its state (no comment yet)
     * cleared the comment the analyst had just written. The debounced save then read the emptied
     * form and persisted half a justification, which the save-time rule correctly refused.
     */
    private applyJustification(fieldKey: string, result: JustificationDialogResult | undefined): void {
        if (!result) {
            return; // CANCEL — nothing was written, so there is nothing to undo
        }

        if (result.action === 'dismiss') {
            // All three together. This is what makes an empty box reachable again: clearing only
            // the figure would leave a wording and comment describing an adjustment that no longer
            // exists.
            this.write(fieldKey, null);
            this.write(`${fieldKey}.${JUSTIFICATION_WORDING}`, null);
            this.write(`${fieldKey}.${JUSTIFICATION_COMMENT}`, null);
        } else {
            this.write(`${fieldKey}.${JUSTIFICATION_WORDING}`, result.wording);
            this.write(`${fieldKey}.${JUSTIFICATION_COMMENT}`, result.comment);
        }

        this.justificationTick.update(tick => tick + 1);

        // One traversal, after every control is in its final state.
        this.emit(fieldKey, this.valueOf(fieldKey) as string | null);
    }

    /**
     * Sets a control without firing valueChanges.
     *
     * <p>{@code emitEvent: false} because the traversal is triggered deliberately once at the end —
     * leaving it on would put us back to one request per control.
     */
    private write(subKey: string, value: string | null): void {
        this.control(subKey)?.setValue(value, { emitEvent: false });
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
