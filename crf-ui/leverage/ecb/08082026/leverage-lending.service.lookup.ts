/**
 * Lookup search. Merge into LeverageLendingService.
 */
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';

export class LookupOption {
  value: string;
  label: string;
}

export class LeverageLendingServiceLookup {

  constructor(private readonly httpClient: HttpClient) {}

  /**
   * Choices for one LOOKUP question.
   *
   * <p>Its own call rather than a field on the form state: ten million counterparties cannot ride
   * along with a form that re-renders on every answer.
   *
   * <p>Returns an empty list below three characters, so this may be called on every keystroke
   * without the caller special-casing short input.
   */
  searchLookupOptions(analysisUid: string, formType: LeverageFormType, questionKey: string,
                      query: string, locale: string): Observable<LookupOption[]> {
    return this.httpClient.get<LookupOption[]>(
      `${this.LEVERAGE_LENDING_URI}/analyses/${analysisUid}/forms/${formType}/lookups/${questionKey}`,
      { params: new HttpParams().set('query', query).set('locale', locale) });
  }
}
