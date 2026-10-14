@Injectable({
    providedIn: 'root'
})
export class LeverageLendingService {
    private readonly LEVERAGE_LENDING_URI = `${environment.baseUrlBack}/api/leverage-lending`;

    _persisting = new BehaviourSubject<boolean>(false);
    persisting$ = this._persisting.asObservable();

    private readonly _preliminaryFormState = signal<FormState | null>(null);
    private readonly _fedFormState = signal<FormState | null>(null);
    private readonly _ecbFormState = signal<FormState | null>(null);

    readonly preliminaryFormState = this._preliminaryFormState.asReadonly();
    readonly fedFormState = this._fedFormState.asReadonly();
    readonly ecbFormState = this._ecbFormState.asReadonly();

    httpClient = inject(HttpClient);

    findAllSpreadsheetByRmpmid(rmpmid: string): Observable<LeverageSpreadsheet[]> {
        return this.httpClient.get<LeverageSpreadsheet[]>(`${this.LEVERAGE_LENDING_URI}/find-allspreadsheets-by-rmpmid`, {
            params: new HttpParams().append('rmpmid', rmpmid),
        })
    }

    /**
     * The section as it stands. Also servers its very first load: with nothing stored, the backend 
     * traversal simply returns the entry question. so there is no seperate "start" call. 
     */
    loadFormState(analysisUid: string, formType: LeverageFormType): Observable<FormState> {
        return this.httpClient.get<FormState>(`${this.LEVERAGE_LENDING_URI}/analyses/${analysisUid}/forms/${formType}/state`);
    }

    findState(leverageFormType: LeverageFormType, stateRequest: StateRequest): Observable<FormState> {
        return this.httpClient.post<FormState>(`${this.LEVERAGE_LENDING_URI}/forms/${leverageFormType}/state`, stateRequest);
    }

    /**
     * Choices for one LOOKUP question
     * 
     * <p> Its own call rather than a field on the form state: ten million counterparties cannot ride
     * along. with a form that re-renders on eery answer.
     * 
     * <p>Returns an empty list below three characters, so this may be called on overy keystroke without
     * the caller special-casing short input.
     */
    searchLookupOptions(analysisUid: string, formType: LeverageFormType, questionKey: string, query: string, locale: string): Observable<LookupOption[]> {
        return this.httpClient.get<LookupOption[]>(`${this.LEVERAGE_LENDING_URI}/analyses/${analysisUid}/forms/${formType}/lookups/${questionKey}`, {
            params: new HttpParams().set('query', query).set('locale', locale),
        });
    }

    /**
     * Per-answer traversal for a section of a specific analysis.
     * 
     * Distinct from findState, which is stateless: ECB's first question prefills from FED, and only
     * an analysis-bound call lets the backend read FED's stored answers.
     */
    findFormState(leverageFormType: LEverageFormType, analysisUid: string, stateRequest: StateRequest): Observable<FormState> {
        return this.httpClient.post<FormState>(`${this.LEVERAGE_LENDING_URI}/analyses/${analysisUid}/forms/${leverageFormType}/state`, stateRequest);
    }

    savePreliminary(analysisUid: string, answers: Record<string, string>, locale: string): Observable<FormState> {
        return this.httpClient.post<FormState>(`${this.LEVERAGE_LENDING_URI}/analyses/${analysisUid}/preliminary/answers`, {
            locale: locale,
            answers: answers,
        })
    }

    isAnalysisPresent(archiveId: string): Observable<boolean> {
        return this.httpClient.get<boolean>(`${this.LEVERAGE_LENDING_URI}/analyses/is-analysis-present-for-archive-id`, {
            params: new HttpParams().append('archiveId', archiveId),
        });
    }

    findAnalysisBySpreadsheet(archiveId: string): Observable<LeverageAnalysis> {
        return this.httpClient.get<LeverageAnalysis>(`${this.LEVERAGE_LENDING_URI}/analyses/by-spreadsheet`, {
            params: new HttpParams().append('archiveId', archiveId);
        })
    }

    findLatestAndValidatedAnalysisBySpreadsheet(rmpmid: string): Observable<LeverageAnalysis> {
        return this.httpClient.get<LeverageAnalysis>(`${this.LEVERAGE_LENDING_URI}/analyses/by-spreadsheet/latestAndValidated`, {
            params: new HttpParams().append('rmpmid', rmpmid),
        })
    }

    getAnalysis(analysisUid: string): Observable<LeverageAnalysis> {
        return this.httpClient.get<LeverageAnalysis>(`${this.LEVERAGE_LENDING_URI}/analyses/${analysisUid}`);
    }

    saveSpreadsheet(analysisUid: string, leverageSpreadsheet: LeverageSpreadsheet): Observable<LeverageAnalysis> {
        return this.httpClient.get<LeverageAnalysis>(`${this.LEVERAGE_LENDING_URI}/analyses/spreadsheet`, {
            analysisUid: analysisUid,
            spreadsheet: leverageSpreadsheet,
        });
    }

    saveFormAnswers(analysisUid: string, formType: LeverageFormType, answers: Record<string, string>, locale: string): Observable<FormState> {
        return this.httpClient.post<FormState>(`${this.LEVERAGE_LENDING_URI}/analyses/${analysisUid}/forms/${formType}/answers`, {locale, answers});
    }

    setFormState(form: LeverageFormType, state: FormState | null): void {
        switch(form) {
            case LeverageFormType.PRELIMINARY:
                this._preliminaryFormState.set(state);
                break;
            
            case LeverageFormType.FED:
                this._fedFormState.set(state);
                break;
            
            case LeverageFormType.ECB:
                this._ecbFormState.set(state);
        }
    }

    clearDownstreamFormState(): void {
        this._fedFormState.set(null);
        this._ecbFormState.set(null);
    }

    reset(): void {
        this._preliminaryFormState.set(null);
        this.clearDownstreamFormState();
    }

    clearAllFormState(): void {
        this.setFormState(LeverageFormType.PRELIMINARY, null);
        this.clearDownstreamFormState();
    }

}