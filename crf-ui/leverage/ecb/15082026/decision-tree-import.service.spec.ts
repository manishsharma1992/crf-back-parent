import { HttpErrorResponse, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import {
  DecisionTreeImportService,
  ImportMode,
  ImportResponse,
  ImportStatus,
} from './decision-tree-import.service';

/**
 * The one behaviour worth defending here: a REJECTED workbook comes back as 422 carrying the full
 * report, and that is an ANSWER, not a failure. Letting it reach an error handler would reduce a
 * list of cells to fix into a status code.
 */
describe('DecisionTreeImportService', () => {
  let service: DecisionTreeImportService;
  let http: HttpTestingController;

  const URL = '/api/leverage/decision-trees/import';
  const file = () => new File(['x'], 'leverage.xlsx');

  const response = (status: ImportStatus): ImportResponse => ({
    status,
    summary: 'summary',
    report: ['a'],
    lines: [{ formType: null, location: 'Sheet A', cell: true, code: 'X', message: 'a' }],
    publishedVersions: {},
    importedAt: '2026-08-15T00:00:00Z',
  });

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [DecisionTreeImportService, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(DecisionTreeImportService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posts the file as multipart with the mode as a parameter', () => {
    service.importWorkbook(file(), ImportMode.PUBLISH).subscribe();

    const request = http.expectOne(r => r.url === URL);
    expect(request.request.params.get('mode')).toBe('PUBLISH');
    expect(request.request.body.get('file')).toBeInstanceOf(File);
    request.flush(response(ImportStatus.PUBLISHED));
  });

  it('returns a published report on 200', () => {
    let result: unknown;
    service.importWorkbook(file(), ImportMode.PUBLISH).subscribe(r => (result = r));

    http.expectOne(r => r.url === URL).flush(response(ImportStatus.PUBLISHED));

    expect(result).toEqual({ kind: 'report', response: response(ImportStatus.PUBLISHED) });
  });

  /** A workbook with a typo is an ordinary outcome of asking someone to fill in a spreadsheet. */
  it('unwraps a 422 rejection back onto the ordinary path', () => {
    let result: { kind: string; response?: ImportResponse } | undefined;
    let errored = false;
    service.importWorkbook(file(), ImportMode.DRY_RUN).subscribe({
      next: r => (result = r as never),
      error: () => (errored = true),
    });

    http.expectOne(r => r.url === URL).flush(response(ImportStatus.REJECTED), {
      status: 422,
      statusText: 'Unprocessable Entity',
    });

    expect(errored).toBe(false);
    expect(result?.kind).toBe('report');
    expect(result?.response?.lines).toHaveLength(1);
  });

  /** 400 is different in kind: nothing was read, so there is nothing to tabulate. */
  it('reports a 400 as an unreadable file rather than as a report', () => {
    let result: { kind: string } | undefined;
    service.importWorkbook(file(), ImportMode.DRY_RUN).subscribe(r => (result = r as never));

    http.expectOne(r => r.url === URL).flush(
      { code: 'UNREADABLE_WORKBOOK', message: 'bad zip', at: 'now' },
      { status: 400, statusText: 'Bad Request' },
    );

    expect(result?.kind).toBe('unreadable');
  });

  it('lets a genuine failure through to the error handler', () => {
    let error: HttpErrorResponse | undefined;
    service.importWorkbook(file(), ImportMode.DRY_RUN).subscribe({
      next: () => fail('should not emit'),
      error: e => (error = e),
    });

    http.expectOne(r => r.url === URL).flush('boom', { status: 500, statusText: 'Server Error' });

    expect(error?.status).toBe(500);
  });
});
