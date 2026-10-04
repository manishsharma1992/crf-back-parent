/**
 * leverage-analysis.component.ts — the members that change.
 *
 * Paste these over their ECB-named originals. Everything NOT here stays as it is: the section
 * gating (ecbOpen, fedRequired), the preliminary handling, the spreadsheet flow and the save
 * ordering are genuinely about sequencing BETWEEN forms, and generalising them would buy nothing.
 *
 * The rename is mechanical. The two things worth reading are the alert bookkeeping and the
 * debounce, both of which were ECB-scoped in a way that would have gone wrong quietly rather than
 * loudly once a second form used them.
 */

// ====================================================================== 1. the form groups

/**
 * fedForm was commented out while FED waited. Both groups now exist from the start: an empty one
 * costs nothing, and a group created lazily on first response would have to be added to the parent
 * mid-flight, which is where "cannot find control" comes from.
 */
initLeverageLendingForm(): void {
    this.leverageLendingForm = this.formBuilder.group({
        spreadsheet: [null, [Validators.required]],
        preliminaryForm: this.formBuilder.group({}),
        ecbForm: this.formBuilder.group({}),
        fedForm: this.formBuilder.group({}),
    });
}

get ecbForm(): FormGroup {
    return this.leverageLendingForm.get('ecbForm') as FormGroup;
}

get fedForm(): FormGroup {
    return this.leverageLendingForm.get('fedForm') as FormGroup;
}

/** The group a form's controls live on. The one place that maps a form type to its group. */
private formOf(formType: LeverageFormType): FormGroup {
    return formType === LeverageFormType.FED ? this.fedForm : this.ecbForm;
}

/**
 * The definition version last seen for a form, echoed back so the backend pins the session.
 *
 * <p>Per form, not shared: FED and ECB are separate definitions with separate version numbers, and
 * sending ECB's version on a FED traversal would pin the wrong tree.
 */
private readonly versions = new Map<LeverageFormType, number | null>();

// ====================================================================== 2. answering

/**
 * Child emits (value, questionKey); parent re-traverses. THE only traversal caller for either form.
 *
 * <p>`questionKey` may be dotted for a checklist item (`Q-B01A.sovereign`), a data-entry box
 * (`Q-F01.ebitda`), or a justification half (`Q-F01.ebitda.wording`).
 */
onFormAnswer(formType: LeverageFormType, value: string, questionKey: string): void {
    this.clearValidation(formType);
    this.controlFor(formType, questionKey)?.patchValue(value, { emitEvent: false });

    // ALWAYS traverse. Routing is not gated by whether the answers may be saved: changing Q-B01A
    // from ALL_NO to ANY_YES has to retract Q-T01 at once.
    this.leverageLendingService
        .findFormState(formType, this.analysisUid!, {
            version: this.versions.get(formType) ?? null,
            answers: this.collectFormAnswers(formType),
            locale: this.locale,
        })
        .subscribe(state => {
            this.applyFormState(formType, state);
            this.answered$.next(formType); // debounced; persist decides whether it may write
        });
}

/** Template keeps two thin entry points, so the HTML reads as what it is. */
onEcbAnswer(value: string, questionKey: string): void {
    this.onFormAnswer(LeverageFormType.ECB, value, questionKey);
}

onFedAnswer(value: string, questionKey: string): void {
    this.onFormAnswer(LeverageFormType.FED, value, questionKey);
}

/**
 * Resolves a possibly-dotted answer key to its control.
 *
 * <p>`FormGroup.get` cannot do this: it treats every dot as a path separator, so it would look for
 * `wording` inside a group `ebitda` inside `Q-F01` — three levels, where the form has two. The
 * first segment is always a question key; whatever follows is a control NAME, dots and all.
 */
private controlFor(formType: LeverageFormType, answerKey: string): AbstractControl | null {
    const form = this.formOf(formType);
    const firstDot = answerKey.indexOf('.');
    if (firstDot < 0) {
        return form.get(answerKey);
    }
    const group = form.get(answerKey.slice(0, firstDot)) as FormGroup | null;
    return group?.controls[answerKey.slice(firstDot + 1)] ?? null;
}

// ====================================================================== 3. the debounce

/**
 * One subject for both forms, split by form type before debouncing.
 *
 * <p><b>A single debounced subject would have been wrong.</b> Answer FED then ECB inside the
 * window and only the second persists — the first form's answers sit unsaved until something else
 * happens to trigger it. groupBy gives each form its own 300ms, which is what the two separate
 * subjects would have done without the duplication.
 */
private readonly answered$ = new Subject<LeverageFormType>();

constructor() {
    this.answered$
        .pipe(
            groupBy(formType => formType),
            mergeMap(group => group.pipe(debounceTime(300))),
            takeUntilDestroyed(this.destroyRef),
        )
        .subscribe(formType => this.persistForm(formType));

    // ... the rest of the existing constructor is unchanged ...
}

// ====================================================================== 4. applying a state

/**
 * Reconciles a form group against the state the backend returned.
 *
 * <p>Was applyEcbState. Identical work for either form — the questions arrive in the same shape
 * and the group is reconciled the same way.
 */
private applyFormState(formType: LeverageFormType, state: FormState): void {
    this.versions.set(formType, state.definitionVersion);
    this.leverageLendingService.setFormState(formType, state);

    const group = this.formOf(formType);
    const incoming = new Set(state.visibleQuestions.map(question => question.key));

    for (const question of state.visibleQuestions) {
        this.syncQuestion(group, question);
    }
    for (const key of Object.keys(group.controls)) {
        if (!incoming.has(key)) {
            group.removeControl(key, { emitEvent: false });
        }
    }

    this.syncAlerts(formType, state.validationMessages ?? []);
}

private syncQuestion(group: FormGroup, question: QuestionView): void {
    const existing = group.get(question.key);
    if (!existing) {
        group.addControl(question.key, this.buildControl(question), { emitEvent: false });
        return;
    }
    if (existing instanceof FormGroup) {
        this.syncSubGroup(existing, question);
        return;
    }
    // Hydrated value wins over the server echo, so a keystroke in flight is not overwritten.
    if (question.answer != null && existing.value == null) {
        existing.setValue(question.answer, { emitEvent: false });
    }
}

/**
 * Sub-answers are reconciled BOTH ways: values the backend supplies are written in, and controls
 * it no longer names are cleared. Without the clearing, an item settled to NOT_APPLICABLE by a YES
 * keeps that value after the YES is withdrawn, and the analyst can never answer it again.
 */
private syncSubGroup(group: FormGroup, question: QuestionView): void {
    const supplied = question.subAnswers ?? {};

    for (const [subKey, control] of Object.entries(group.controls)) {
        const value = supplied[subKey] ?? null;
        if (control.value !== value) {
            control.setValue(value, { emitEvent: false });
        }
    }
}

/**
 * A DATA_ENTRY question is a sub-group like a CHECKLIST, but its boxes do not share one read-only
 * state and the editable ones carry two more controls each — so it gets its own builder.
 */
private buildControl(question: QuestionView): FormGroup | FormControl {
    if (question.type === QuestionType.CHECKLIST) {
        return this.buildSubGroup(question, (question.items ?? []).map(item => item.key));
    }
    if (question.type === QuestionType.DATA_ENTRY) {
        return this.buildDataEntryGroup(question);
    }
    return this.formBuilder.control(
        { value: question.answer ?? null, disabled: this.isReadOnly(question) },
        question.mandatory ? [Validators.required] : [],
    );
}

private buildSubGroup(question: QuestionView, subKeys: string[]): FormGroup {
    const readOnly = this.isReadOnly(question);
    return this.formBuilder.group(
        Object.fromEntries(
            subKeys.map(subKey => [
                subKey,
                this.formBuilder.control({
                    value: question.subAnswers?.[subKey] ?? null,
                    disabled: readOnly,
                }),
            ]),
        ),
    );
}

/**
 * Read-only when the value came from somewhere else.
 *
 * <p>Deliberately not keyed on prefillFrom or editable: both are static declarations from the
 * definition, saying the question CAN be copied. Whether it WAS is only known after the walk, and
 * `derived` is the projection of exactly that.
 *
 * <p>Covers FED's three rating questions for free — they are COMPUTED with a derivedFrom, so the
 * walk reports them derived and the control is disabled without this method naming them.
 */
private isReadOnly(question: QuestionView): boolean {
    return question.derived;
}

/**
 * Flattens to the dotted wire format — `Q-B01A.sovereign` — dropping null and blank values, since
 * a blank answer means "unanswered" and the engine treats an absent key that way.
 *
 * <p>getRawValue rather than value: a prefilled or computed control is disabled, and it still has
 * to round-trip rather than vanish from the payload.
 */
private collectFormAnswers(formType: LeverageFormType): Record<string, string> {
    const cleaned: Record<string, string> = {};
    const raw = this.formOf(formType).getRawValue() as Record<string, unknown>;

    for (const [questionKey, value] of Object.entries(raw)) {
        if (value !== null && typeof value === 'object') {
            for (const [subKey, subValue] of Object.entries(value as Record<string, unknown>)) {
                this.putAnswer(cleaned, `${questionKey}.${subKey}`, subValue);
            }
        } else {
            this.putAnswer(cleaned, questionKey, value);
        }
    }
    return cleaned;
}

private putAnswer(target: Record<string, string>, key: string, value: unknown): void {
    const text = value === null || value === undefined ? '' : String(value).trim();
    if (text !== '' && text !== 'NOT_APPLICABLE') {
        target[key] = text;
    }
}

// ====================================================================== 5. alerts, per form

/**
 * Messages raised as soon as they arrive, rather than waiting for an explicit save.
 *
 * <p>Keyed by form because the two have different sets, and the reason is not symmetry: these name
 * a figure that arrived wrong from FINSTAR and that NOTHING on the screen can fix. ECB's three
 * sources are read-only, so all three qualify.
 *
 * <p><b>FED's set is empty, and that is correct.</b> Its two prefilled figures —
 * aplcTotalBSDebt and aplcBookValueOfEquity — are Editable = Yes, so an analyst CAN type over a
 * bad one. A message about something they can fix belongs with the rest at save time.
 */
private static readonly IMMEDIATE_MESSAGES: ReadonlyMap<LeverageFormType, ReadonlySet<string>> = new Map([
    [LeverageFormType.ECB, new Set(['ECB_EBITDA_EMPTY', 'ECB_EBITDA_ZERO', 'ECB_GROSS_DEBT_ZERO'])],
    [LeverageFormType.FED, new Set<string>()],
]);

private static readonly SECTION_ANCHORS: ReadonlyMap<LeverageFormType, string> = new Map([
    [LeverageFormType.ECB, 'leverageAnalysisEcbFragment'],
    [LeverageFormType.FED, 'leverageAnalysisFedFragment'],
]);

/**
 * Anchors raised PER FORM.
 *
 * <p>Was one shared Set. With two forms that is a cross-form bug waiting: a FED traversal would
 * clear ECB's alerts, because clearRaisedAlerts walks every anchor in the set and FED's response
 * has nothing to re-raise ECB's with. The analyst would watch ECB's errors vanish while typing in
 * FED and reappear on the next ECB answer.
 */
private readonly raisedAnchors = new Map<LeverageFormType, Set<string>>();

/** Which forms are showing their full message set — set by the save button, per form. */
private readonly validationVisible = new Map<LeverageFormType, boolean>();

readonly ecbValidationVisible = signal<boolean>(false);

private syncAlerts(formType: LeverageFormType, messages: ValidationMessageView[]): void {
    this.clearRaisedAlerts(formType);

    const showAll = this.validationVisible.get(formType) ?? false;
    const immediate = LeverageAnalysisComponent.IMMEDIATE_MESSAGES.get(formType) ?? new Set<string>();
    const visible = messages.filter(message => showAll || immediate.has(message.messageKey));
    if (!visible.length) {
        return;
    }

    const sectionAnchor = LeverageAnalysisComponent.SECTION_ANCHORS.get(formType)!;
    const raised = this.anchorsFor(formType);

    for (const message of visible) {
        // Recorded from the MESSAGE, before the alert is built and regardless of whether it builds:
        // alertFor returns null for a key the static catalogue does not know, and an alert we failed
        // to build must still leave a clearable anchor behind.
        raised.add(message.questionKey ?? sectionAnchor);

        const alert = this.alertFor(message, sectionAnchor);
        if (!alert) {
            continue;
        }
        // ... existing channel dispatch, unchanged ...
    }
}

private clearRaisedAlerts(formType: LeverageFormType): void {
    const raised = this.anchorsFor(formType);
    for (const anchorId of raised) {
        for (const channel of LeverageAnalysisComponent.ALERT_CHANNELS) {
            this.alertBoxService.clearAlertsByAnchorId(channel, anchorId);
        }
    }
    raised.clear();
}

private anchorsFor(formType: LeverageFormType): Set<string> {
    let raised = this.raisedAnchors.get(formType);
    if (!raised) {
        raised = new Set<string>();
        this.raisedAnchors.set(formType, raised);
    }
    return raised;
}

private alertFor(message: ValidationMessageView, sectionAnchor: string): IAlert | null {
    const anchorId = message.questionKey ?? sectionAnchor;
    const wording = { [message.text]: true };
    const fragmentId = message.fieldKey ?? undefined;

    switch (message.severity) {
        case Severity.WARNING:
            return this.alertBoxService.buildWarning(anchorId, wording, fragmentId);
        case Severity.INFO:
            return this.alertBoxService.buildInfoAlert(anchorId, wording, fragmentId);
        case Severity.ERROR:
        default:
            return this.alertBoxService.buildErrorAlert(anchorId, wording, fragmentId);
    }
}

private clearValidation(formType: LeverageFormType): void {
    this.validationVisible.set(formType, false);
    if (formType === LeverageFormType.ECB) {
        this.ecbValidationVisible.set(false);
    }
}

// ====================================================================== 6. loading

/**
 * FED's first load. Mirrors the ECB effect, minus the gating — FED opens as soon as the
 * preliminary outcome names it, with nothing to wait for.
 */
private fedLoadStarted = false;

private readonly openFedWhenReady = effect(() => {
    if (!this.fedRequired() || this.fedLoadStarted || !this.analysisUid) {
        return;
    }
    if (this.leverageLendingService.fedFormState()) {
        return; // reload() already brought it in
    }
    this.fedLoadStarted = true;
    this.leverageLendingService.loadFormState(this.analysisUid, LeverageFormType.FED).subscribe({
        next: state => this.applyFormState(LeverageFormType.FED, state),
        error: () => {
            this.fedLoadStarted = false;
        },
    });
});

/**
 * In reload(), beside reloadEcb. FED FIRST.
 *
 * <p>Not cosmetic: ECB's Q01 and Q-S06 prefill from FED, and the prefill is read from FED's STORED
 * answers. Loading ECB before FED has been recorded gives an ECB state whose Q01 is unanswered,
 * and the analyst is asked a question they already answered next door.
 */
private reloadFed(analysisUid: string): void {
    this.leverageLendingService
        .loadFormState(analysisUid, LeverageFormType.FED)
        .subscribe(state => this.applyFormState(LeverageFormType.FED, state));
}

// in reload(...), replacing the TODO:
//      this.reloadFed(analysisUid);
//      this.reloadEcb(analysisUid);

// ====================================================================== 7. one existing bug

/**
 * persistOpenSections had FED saving under ECB's form type:
 *
 *      saves.push(... saveFormAnswers(uid, LeverageFormType.ECB, this.collectFedAnswers(), ...))
 *
 * so FED's answers would have been written into ECB's section — and because the ECB save follows
 * it in the same concat, the second would have overwritten the first and the symptom would have
 * been "FED never saves" rather than anything pointing at the wrong form.
 */
private persistOpenSections(): void {
    this.leverageLendingService._persisting.next(true);

    const saves: Observable<FormState>[] = [
        this.leverageLendingService.savePreliminary(this.analysisUid!, this.collectAnswers(), this.locale),
    ];
    if (this.fedRequired()) {
        saves.push(this.leverageLendingService.saveFormAnswers(
            this.analysisUid!, LeverageFormType.FED, this.collectFormAnswers(LeverageFormType.FED), this.locale));
    }
    if (this.ecbOpen()) {
        saves.push(this.leverageLendingService.saveFormAnswers(
            this.analysisUid!, LeverageFormType.ECB, this.collectFormAnswers(LeverageFormType.ECB), this.locale));
    }

    concat(...saves)
        .pipe(finalize(() => this.leverageLendingService._persisting.next(false)))
        .subscribe({
            complete: () => this.reload(this.analysisUid!),
            error: () => {
                /* surface the failure so the analyst can retry */
            },
        });
}
