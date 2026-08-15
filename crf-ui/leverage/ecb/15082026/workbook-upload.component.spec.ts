import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatDialog } from '@angular/material/dialog';
import { of, throwError } from 'rxjs';
import {
  DecisionTreeImportService,
  ImportMode,
  ImportResponse,
  ImportStatus,
} from '@lazyloaded/counterparty/service/leverage-lending/decision-tree-import.service';
import { WorkbookUploadComponent } from './workbook-upload.component';

/**
 * Two actions on one file. The distinction between rehearsing and superseding what analysts are
 * using is the whole risk of this screen, so the tests care mostly about which mode reached the
 * server and when the report is put in front of someone.
 */
describe('WorkbookUploadComponent', () => {
  let fixture: ComponentFixture<WorkbookUploadComponent>;
  let component: WorkbookUploadComponent;
  let importService: { importWorkbook: jest.Mock };
  let dialog: { open: jest.Mock };

  const file = () => new File(['x'], 'leverage-v12.xlsx');

  const response = (
    status: ImportStatus,
    overrides: Partial<ImportResponse> = {},
  ): ImportResponse => ({
    status,
    summary: `summary for ${status}`,
    report: [],
    lines: [],
    publishedVersions: {},
    importedAt: '2026-08-15T00:00:00Z',
    ...overrides,
  });

  function answers(response: ImportResponse): void {
    importService.importWorkbook.mockReturnValue(of({ kind: 'report', response }));
  }

  function choose(): void {
    component.onFileSelected({ target: { files: [file()] } } as unknown as Event);
  }

  beforeEach(async () => {
    importService = { importWorkbook: jest.fn() };
    dialog = { open: jest.fn() };

    await TestBed.configureTestingModule({
      imports: [WorkbookUploadComponent],
      providers: [
        { provide: DecisionTreeImportService, useValue: importService },
        { provide: MatDialog, useValue: dialog },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(WorkbookUploadComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  describe('choosing a file', () => {
    it('shows the chosen name', () => {
      choose();

      expect(component.fileName()).toBe('leverage-v12.xlsx');
      expect(component.canSubmit()).toBe(true);
    });

    /** The previous report described the previous file; it stops being true the moment one changes. */
    it('drops the previous outcome', () => {
      answers(response(ImportStatus.VALIDATED));
      choose();
      component.check();
      expect(component.outcome()).not.toBeNull();

      choose();

      expect(component.outcome()).toBeNull();
    });

    it('refuses to submit with no file', () => {
      component.check();

      expect(importService.importWorkbook).not.toHaveBeenCalled();
      expect(component.canSubmit()).toBe(false);
    });

    /** A native input keeps its value after a clear and would then refuse to re-fire change. */
    it('resets the native input when cleared', () => {
      choose();
      const input = { value: 'C:\\fakepath\\leverage-v12.xlsx' } as HTMLInputElement;

      component.clearFile(input);

      expect(component.fileName()).toBe('');
      expect(input.value).toBe('');
    });
  });

  describe('modes', () => {
    it('Check rehearses', () => {
      answers(response(ImportStatus.VALIDATED));
      choose();

      component.check();

      expect(importService.importWorkbook).toHaveBeenCalledWith(expect.any(File), ImportMode.DRY_RUN);
      expect(component.outcome()?.status).toBe(ImportStatus.VALIDATED);
    });

    it('Publish supersedes', () => {
      answers(response(ImportStatus.PUBLISHED, { publishedVersions: { ECB: 12, FED: 12 } as never }));
      choose();

      component.publish();

      expect(importService.importWorkbook).toHaveBeenCalledWith(expect.any(File), ImportMode.PUBLISH);
      expect(component.publishedSummary()).toBe('ECB v12, FED v12');
    });
  });

  describe('the report', () => {
    const rejected = response(ImportStatus.REJECTED, {
      lines: [
        { formType: null, location: 'ECB Q!C15', cell: true, code: 'UNKNOWN_GOTO', message: 'no such question' },
      ],
    });

    /** When there are problems, the report IS the answer — it should not wait behind a link. */
    it('opens itself on a rejection', () => {
      answers(rejected);
      choose();

      component.publish();

      expect(dialog.open).toHaveBeenCalledTimes(1);
      expect(dialog.open.mock.calls[0][1].data.lines).toHaveLength(1);
    });

    it('stays closed on a clean run', () => {
      answers(response(ImportStatus.VALIDATED));
      choose();

      component.check();

      expect(dialog.open).not.toHaveBeenCalled();
      expect(component.hasReport()).toBe(false);
    });

    it('offers the report when a clean run still had something to say', () => {
      answers(response(ImportStatus.VALIDATED, { lines: rejected.lines }));
      choose();

      component.check();

      expect(component.hasReport()).toBe(true);
      expect(dialog.open).not.toHaveBeenCalled();
    });
  });

  describe('failures', () => {
    it('reports an unreadable file without a report to open', () => {
      importService.importWorkbook.mockReturnValue(
        of({ kind: 'unreadable', error: { code: 'UNREADABLE_WORKBOOK', message: 'bad zip', at: 'now' } }),
      );
      choose();

      component.check();

      expect(component.outcome()?.status).toBe('UNREADABLE');
      expect(component.hasReport()).toBe(false);
      expect(component.busy()).toBe(false);
    });

    /** A spinner that never stops is worse than an error message. */
    it('stops being busy when the request fails outright', () => {
      importService.importWorkbook.mockReturnValue(throwError(() => new Error('offline')));
      choose();

      component.check();

      expect(component.busy()).toBe(false);
      expect(component.outcome()?.status).toBe('UNREADABLE');
    });
  });
});
