import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  AnalysisStatusChangeView,
  AnalysisValidationState,
} from './analysis-validation-state.model';

@Injectable({ providedIn: 'root' })
export class LeverageValidationApi {
  private readonly http = inject(HttpClient);

  validationState(analysisUid: string): Observable<AnalysisValidationState> {
    return this.http.get<AnalysisValidationState>(
      `/leverage-analyses/${analysisUid}/validation-state`,
    );
  }

  validate(analysisUid: string): Observable<AnalysisStatusChangeView> {
    return this.http.post<AnalysisStatusChangeView>(
      `/leverage-analyses/${analysisUid}/validate`,
      {},
    );
  }
}
