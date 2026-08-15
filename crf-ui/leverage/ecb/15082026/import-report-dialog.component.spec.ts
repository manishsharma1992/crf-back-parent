import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import {
  ImportStatus,
  ReportLine,
} from '@lazyloaded/counterparty/service/leverage-lending/decision-tree-import.service';
import {
  ImportReportDialogComponent,
  ImportReportDialogData,
} from './import-report-dialog.component';

describe('ImportReportDialogComponent', () => {
  let fixture: ComponentFixture<ImportReportDialogComponent>;
  let component: ImportReportDialogComponent;

  const line = (overrides: Partial<ReportLine> = {}): ReportLine => ({
    formType: 'ECB' as never,
    location: "Sheet 'ECB Q', row 15",
    cell: true,
    code: 'UNKNOWN_GOTO',
    message: 'no such question',
    ...overrides,
  });

  async function open(lines: ReportLine[]): Promise<void> {
    const data: ImportReportDialogData = {
      status: ImportStatus.REJECTED,
      summary: `${lines.length} problem(s) found.`,
      lines,
    };
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [ImportReportDialogComponent],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: data },
        { provide: MatDialogRef, useValue: { close: jest.fn() } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ImportReportDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  /**
   * The server's order is causal — a sheet that could not be read produces errors in every tree
   * below it, so sorting would scatter a cause among its consequences.
   */
  it('keeps the order the server sent', async () => {
    await open([line({ code: 'Z_LAST' }), line({ code: 'A_FIRST' })]);

    expect(component.rows().map(r => r.code)).toEqual(['Z_LAST', 'A_FIRST']);
  });

  it('counts problems and distinct codes separately', async () => {
    await open([line({ code: 'A' }), line({ code: 'A' }), line({ code: 'B' })]);

    expect(component.total()).toBe(3);
    expect(component.distinctCodes()).toBe(2);
  });

  describe('filtering', () => {
    it('matches on code, location, form and wording alike', async () => {
      await open([
        line({ code: 'UNKNOWN_GOTO', message: 'no such question' }),
        line({ code: 'MISSING_LABEL_FR', message: 'FR label text is missing', location: 'Fields!C7' }),
      ]);

      component.onFilterInput('label');
      expect(component.rows()).toHaveLength(1);

      component.onFilterInput('Fields');
      expect(component.rows()).toHaveLength(1);

      component.onFilterInput('unknown_goto');
      expect(component.rows()[0].code).toBe('UNKNOWN_GOTO');
    });

    it('shows everything again when the filter is cleared', async () => {
      await open([line(), line({ code: 'OTHER' })]);
      component.onFilterInput('OTHER');

      component.onFilterInput('  ');

      expect(component.rows()).toHaveLength(2);
    });

    it('survives a row with no code or location', async () => {
      await open([line({ code: null, location: null, formType: null })]);

      component.onFilterInput('no such');

      expect(component.rows()).toHaveLength(1);
    });
  });

  /** Someone who has filtered to one code wants that list, not the whole thing. */
  it('copies only the visible rows', async () => {
    const writeText = jest.fn();
    Object.assign(navigator, { clipboard: { writeText } });
    await open([line({ code: 'A' }), line({ code: 'B' })]);
    component.onFilterInput('A');

    component.copyToClipboard();

    expect(writeText).toHaveBeenCalledTimes(1);
    expect(writeText.mock.calls[0][0]).toContain('A');
    expect(writeText.mock.calls[0][0]).not.toContain('B');
  });
});
