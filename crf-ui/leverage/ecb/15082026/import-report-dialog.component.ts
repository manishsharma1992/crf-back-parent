import { Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { MaterialModule } from '@shared/modules/MaterialModule';
import {
  ImportStatus,
  ReportLine,
} from '@lazyloaded/counterparty/service/leverage-lending/decision-tree-import.service';

export interface ImportReportDialogData {
  status: ImportStatus;
  summary: string;
  lines: ReportLine[];
}

/**
 * Every problem the importer found, as a table.
 *
 * <p>Read-only and disposable — it exists so a BA can work through a list with the workbook open
 * beside them, fix the cells, and upload again.
 *
 * <p><b>Rows keep the server's order.</b> That order is parse issues first, then validation errors
 * form by form, which is causal: a sheet that could not be read produces errors in every tree
 * below it, and sorting by code or by location would scatter the cause among its consequences.
 */
@Component({
  selector: 'bnpp-import-report-dialog',
  templateUrl: './import-report-dialog.component.html',
  styleUrls: ['./import-report-dialog.component.scss'],
  imports: [MaterialModule, FormsModule],
  standalone: true,
})
export class ImportReportDialogComponent {
  readonly data = inject<ImportReportDialogData>(MAT_DIALOG_DATA);
  private readonly dialogRef = inject<MatDialogRef<ImportReportDialogComponent>>(MatDialogRef);

  /** Free-text narrowing. A hundred errors from one missing column is a common shape. */
  readonly filter = signal('');

  readonly total = computed(() => this.data.lines.length);

  readonly rows = computed<ReportLine[]>(() => {
    const needle = this.filter().trim().toLowerCase();
    if (!needle) {
      return this.data.lines;
    }
    return this.data.lines.filter(line =>
      [line.formType, line.location, line.code, line.message]
        .filter((part): part is string => !!part)
        .some(part => part.toLowerCase().includes(needle)),
    );
  });

  /**
   * How many DISTINCT codes are present.
   *
   * <p>Shown next to the total because the two together say something neither says alone: eighty
   * problems across three codes is one missing column, and eighty across forty codes is a workbook
   * in trouble.
   */
  readonly distinctCodes = computed(
    () => new Set(this.data.lines.map(line => line.code).filter(Boolean)).size,
  );

  onFilterInput(value: string): void {
    this.filter.set(value);
  }

  /**
   * Copies the whole report as text.
   *
   * <p>The visible rows, not all of them — someone who has filtered to one code wants that list to
   * paste into an email or a ticket, and the unfiltered list is one click away.
   */
  copyToClipboard(): void {
    const text = this.rows()
      .map(line =>
        [line.formType ?? '', line.location ?? '', line.code ?? '', line.message]
          .filter(part => part !== '')
          .join('\t'),
      )
      .join('\n');
    navigator.clipboard?.writeText(text);
  }

  close(): void {
    this.dialogRef.close();
  }
}
