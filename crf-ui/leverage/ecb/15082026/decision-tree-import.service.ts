import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, catchError, of, throwError } from 'rxjs';
import { LeverageFormType } from '@lazyloaded/counterparty/model/leverage-lending/form-state.model';

export enum ImportMode {
  DRY_RUN = 'DRY_RUN',
  PUBLISH = 'PUBLISH',
}

export enum ImportStatus {
  /** All three forms were read, validated and written. */
  PUBLISHED = 'PUBLISHED',
  /** Valid, but nothing was written — this was a rehearsal. */
  VALIDATED = 'VALIDATED',
  /** Something was wrong. Nothing was written, whatever the mode. */
  REJECTED = 'REJECTED',
}

/** One problem, in columns. */
export interface ReportLine {
  formType: LeverageFormType | null;
  location: string | null;
  /** True when `location` is a real cell reference rather than a logical path. */
  cell: boolean;
  code: string | null;
  message: string;
}

export interface ImportResponse {
  status: ImportStatus;
  summary: string;
  report: string[];
  lines: ReportLine[];
  publishedVersions: Partial<Record<LeverageFormType, number>>;
  importedAt: string;
}

/** The 400 shape: the file was not a workbook, so there is no report to render. */
export interface ImportApiError {
  code: string;
  message: string;
  at: string;
}

export type ImportResult =
  | { kind: 'report'; response: ImportResponse }
  | { kind: 'unreadable'; error: ImportApiError };

@Injectable({ providedIn: 'root' })
export class DecisionTreeImportService {
  private readonly http = inject(HttpClient);
  private readonly url = '/api/leverage/decision-trees/import';

  /**
   * Uploads a workbook.
   *
   * <p><b>A rejection is a normal answer, not an error.</b> The server returns 422 with the full
   * report — the same body as a success — because a workbook with a typo is an ordinary outcome of
   * asking someone to fill in a spreadsheet. Letting that reach an error handler would reduce the
   * report to a status code, so 422 is unwrapped back into the ordinary path here.
   *
   * <p>400 is different in kind: the file was not a workbook, nothing could be read, and there is
   * nothing to put in a table.
   */
  importWorkbook(file: File, mode: ImportMode): Observable<ImportResult> {
    const body = new FormData();
    body.append('file', file, file.name);

    return this.http
      .post<ImportResponse>(this.url, body, { params: new HttpParams().set('mode', mode) })
      .pipe(
        catchError((error: HttpErrorResponse) => {
          if (error.status === 422 && error.error) {
            return of(error.error as ImportResponse);
          }
          if (error.status === 400 && error.error) {
            return of({ kind: 'unreadable' as const, error: error.error as ImportApiError });
          }
          return throwError(() => error);
        }),
        // A 422 body arrives here as an ImportResponse; anything already tagged passes through.
        map(result =>
          'kind' in result ? result : ({ kind: 'report' as const, response: result }),
        ),
      );
  }
}
