import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AnalysisStatusChangeView,
  ValidationAvailability,
} from './validation-availability.model';

@Injectable({ providedIn: 'root' })
export class LeverageValidationApi {
  private readonly http = inject(HttpClient);

  availability(analysisUid: string): Observable<ValidationAvailability> {
    return this.http.get<ValidationAvailability>(
      `/leverage-analyses/${analysisUid}/validation-availability`,
    );
  }

  validate(analysisUid: string): Observable<AnalysisStatusChangeView> {
    return this.http.post<AnalysisStatusChangeView>(
      `/leverage-analyses/${analysisUid}/validate`,
      {},
    );
  }
}
