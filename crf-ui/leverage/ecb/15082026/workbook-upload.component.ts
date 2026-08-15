import { Component, computed, inject, signal } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { MaterialModule } from '@shared/modules/MaterialModule';
import {
  DecisionTreeImportService,
  ImportMode,
  ImportResponse,
  ImportStatus,
} from '@lazyloaded/counterparty/service/leverage-lending/decision-tree-import.service';
import {
  ImportReportDialogComponent,
  ImportReportDialogData,
} from './import-report-dialog/import-report-dialog.component';

/** What the strip under the picker is currently saying. */
interface UploadOutcome {
  status: ImportStatus | 'UNREADABLE';
  summary: string;
  /** Present only when there is a report worth opening. */
  response: ImportResponse | null;
}

/**
 * Uploads an authoring workbook and reports what the server made of it.
 *
 * <p>One file picker, two actions. **Check** validates and reports without writing; **Publish**
 * supersedes what analysts are using right now. They are separate buttons rather than a mode
 * dropdown plus a submit, because the difference between them is the whole risk of the screen and
 * a dropdown lets someone publish while believing they are rehearsing.
 *
 * <p>The two are also styled apart for the same reason — see the `--publish` modifier — and
 * Publish stays disabled until a file is chosen, so the only way to reach it is deliberately.
 */
@Component({
  selector: 'bnpp-workbook-upload',
  templateUrl: './workbook-upload.component.html',
  styleUrls: ['./workbook-upload.component.scss'],
  imports: [MaterialModule],
  standalone: true,
})
export class WorkbookUploadComponent {
  private readonly importService = inject(DecisionTreeImportService);
  private readonly dialog = inject(MatDialog);

  readonly ImportStatus = ImportStatus;

  readonly file = signal<File | null>(null);
  readonly busy = signal(false);
  readonly outcome = signal<UploadOutcome | null>(null);

  readonly fileName = computed(() => this.file()?.name ?? '');
  readonly canSubmit = computed(() => this.file() !== null && !this.busy());

  /** True when the last attempt left something to read. */
  readonly hasReport = computed(() => (this.outcome()?.response?.lines?.length ?? 0) > 0);

  /**
   * Published versions as a readable line — "PRELIMINARY v13, ECB v13, FED v13".
   *
   * <p>Shown because it is the only confirmation that the thing an analyst will see has actually
   * changed. A bare "published" leaves the BA wondering whether their edit made it in.
   */
  readonly publishedSummary = computed(() => {
    const versions = this.outcome()?.response?.publishedVersions ?? {};
    const parts = Object.entries(versions).map(([form, version]) => `${form} v${version}`);
    return parts.join(', ');
  });

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.file.set(input.files?.[0] ?? null);
    // The previous report described the previous file, so it stops being true the moment another
    // is chosen.
    this.outcome.set(null);
  }

  clearFile(input: HTMLInputElement): void {
    this.file.set(null);
    this.outcome.set(null);
    // The native input keeps its value after a programmatic clear, and would then refuse to fire
    // `change` if the same file were picked again.
    input.value = '';
  }

  check(): void {
    this.submit(ImportMode.DRY_RUN);
  }

  publish(): void {
    this.submit(ImportMode.PUBLISH);
  }

  private submit(mode: ImportMode): void {
    const file = this.file();
    if (!file || this.busy()) {
      return;
    }
    this.busy.set(true);
    this.outcome.set(null);

    this.importService.importWorkbook(file, mode).subscribe({
      next: result => {
        this.busy.set(false);
        if (result.kind === 'unreadable') {
          this.outcome.set({
            status: 'UNREADABLE',
            summary: result.error.message,
            response: null,
          });
          return;
        }
        this.outcome.set({
          status: result.response.status,
          summary: result.response.summary,
          response: result.response,
        });
        // A rejection is the case where the report IS the answer, so it opens itself rather than
        // waiting behind a link nobody notices.
        if (result.response.status === ImportStatus.REJECTED) {
          this.openReport();
        }
      },
      error: () => {
        this.busy.set(false);
        this.outcome.set({
          status: 'UNREADABLE',
          summary: 'The import could not be completed. Please try again.',
          response: null,
        });
      },
    });
  }

  openReport(): void {
    const response = this.outcome()?.response;
    if (!response) {
      return;
    }
    const data: ImportReportDialogData = {
      status: response.status,
      summary: response.summary,
      lines: response.lines ?? [],
    };
    this.dialog.open(ImportReportDialogComponent, { data, width: '900px', autoFocus: true });
  }
}
