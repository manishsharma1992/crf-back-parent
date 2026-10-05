import { Component, computed, DestroyRef, effect, inject, OnInit, signal, ViewEncapsulation } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { LanguageService } from '@core/services/language.service';
import { IAlert } from '@lazyloaded/counterparty/model/alerts.model';
import {
    FormState,
    isEditable,
    isFormComplete,
    JUSTIFICATION_COMMENT,
    JUSTIFICATION_WORDING,
    LeverageFormType,
    QuestionType,
    QuestionView,
    SECTION_PRIORITY,
    Severity,
    Status,
    ValidationMessageView,
} from '@lazyloaded/counterparty/model/leverage-lending/form-state.model';
import { AnalysisStatusChangeView, LeverageSpreadsheet } from '@lazyloaded/counterparty/model/leverage-lending/leverage-lending.model';
import { AlertsBoxService, alertTypes } from '@lazyloaded/counterparty/services/alerts-box.service';
import { LeverageLendingService } from '@lazyloaded/counterparty/services/leverage-lending.service';
import { ValidationStateStoreService } from '@lazyloaded/counterparty/services/leverage-lending/validation-state-store.service';
import { WorkflowService } from '@lazyloaded/counterparty/services/workflow.service';
import { AlertsBoxComponent } from '@shared/components/alerts-box/alerts-box.component';
import { ProgressPulseLoaderComponent } from '@shared/components/progress-pulse-loader/progress-pulse-loader.component';
import { HoverIconDirective } from '@shared/directives/hover-icon.directive';
import { SvgIconDirective } from '@shared/directives/svg-icon.directive';
import { MaterialModule } from '@shared/modules/MaterialModule';
import { concat, debounceTime, distinctUntilChanged, finalize, groupBy, map, mergeMap, Observable, Subject } from 'rxjs';

import { FormQuestionsComponent } from './form-questions/form-questions.component';
import { PreliminaryQuestionsComponent } from './preliminary-questions/preliminary-questions.component';
import { SpreadsheetSelectionComponent } from './spreadsheet-selection/spreadsheet-selection.component';
import { ValidationPanelComponent } from './validation-panel/validation-panel.component';

/**
 * The leverage analysis screen: spreadsheet, preliminary questions, then whichever of FED and ECB
 * the preliminary outcome calls for.
 *
 * <p><b>Nothing here is ECB-specific any more.</b> FED and ECB are the same thing twice — a
 * definition-driven form with a group, a version, an alert anchor and a debounced autosave — so
 * every method that used to be named `*Ecb*` now takes a {@link LeverageFormType}. What stays
 * form-specific is the SEQUENCING between them, which is genuinely different: ECB waits for FED,
 * FED waits for nobody, and FED is saved first because ECB's prefill reads FED's stored answers.
 *
 * <h2>How to read this file</h2>
 * <ol>
 *   <li>Identity and lifecycle — which analysis is open</li>
 *   <li>Section gating — which forms apply, and when each opens</li>
 *   <li>Spreadsheet</li>
 *   <li>Preliminary</li>
 *   <li>FED and ECB — one set of methods, parameterised by form</li>
 *   <li>Alerts</li>
 *   <li>Persistence</li>
 * </ol>
 */
@Component({
    selector: 'bnpp-leverage-analysis',
    templateUrl: './leverage-analysis.component.html',
    styleUrls: ['./leverage-analysis.component.scss'],
    standalone: true,
    encapsulation: ViewEncapsulation.None,
    imports: [
        MaterialModule,
        SvgIconDirective,
        HoverIconDirective,
        SpreadsheetSelectionComponent,
        PreliminaryQuestionsComponent,
        FormQuestionsComponent,
        ValidationPanelComponent,
        ProgressPulseLoaderComponent,
        AlertsBoxComponent,
    ],
})
export class LeverageAnalysisComponent implements OnInit {

    // ================================================================== constants

    private static readonly PRELIMINARY_ANCHOR = 'leverageAnalysisPreliminaryFragment';

    /** Where a form-wide message anchors when the rule names no question. */
    private static readonly SECTION_ANCHORS: ReadonlyMap<LeverageFormType, string> = new Map([
        [LeverageFormType.FED, 'leverageAnalysisFedFragment'],
        [LeverageFormType.ECB, 'leverageAnalysisEcbFragment'],
    ]);

    /**
     * Rules that must not wait for an explicit save, per form.
     *
     * <p>Chosen by message key rather than by severity: plenty of ERRORs are about something the
     * analyst is still filling in. These name a source figure that arrived wrong from FINSTAR and
     * that NOTHING on this screen can fix, so making someone fill ten adjustment boxes before
     * telling them EBITDA was never delivered is the worst version of waiting.
     *
     * <p>{@code ECB_ADJUSTED_EBITDA_ZERO} is deliberately absent: it is caused by what the analyst
     * typed — five adjustments cancelling the base out — so it belongs with the save-time messages.
     *
     * <p><b>FED's set is empty, and that is correct rather than unfinished.</b> Its two prefilled
     * figures, aplcTotalBSDebt and aplcBookValueOfEquity, are Editable = Yes, so an analyst can
     * type over a bad one. A message about something they can fix belongs with the rest.
     */
    private static readonly IMMEDIATE_MESSAGES: ReadonlyMap<LeverageFormType, ReadonlySet<string>> = new Map([
        [LeverageFormType.ECB, new Set(['ECB_EBITDA_EMPTY', 'ECB_EBITDA_ZERO', 'ECB_GROSS_DEBT_ZERO'])],
        [LeverageFormType.FED, new Set<string>()],
    ]);

    private static readonly ALERT_CHANNELS: readonly alertTypes[] = ['errors', 'warnings', 'informations'];

    /** FED then ECB — the order they are filled, and the order the prefill depends on. */
    private static readonly SAVE_ORDER: readonly LeverageFormType[] = [LeverageFormType.FED, LeverageFormType.ECB];

    // ================================================================== services

    formBuilder = inject(FormBuilder);
    leverageLendingService = inject(LeverageLendingService);
    languageService = inject(LanguageService);
    router = inject(Router);
    route = inject(ActivatedRoute);
    workflowService = inject(WorkflowService);
    alertBoxService = inject(AlertsBoxService);

    readonly validationState = inject(ValidationStateStoreService);
    private readonly destroyRef = inject(DestroyRef);

    // ================================================================== 1. identity and lifecycle

    analysisUid: string | null = null;
    rmpmid: string;
    leverageLendingForm: FormGroup;
    leverageSpreadSheet: LeverageSpreadsheet;
    formState?: FormState;

    persisting: boolean;
    hydrating = false;
    resolvingAnalysis = false;
    panelOpened = signal<boolean>(false);
    panelOpenedStatus = false;

    /** Preliminary's definition version. The two downstream forms keep their own — see `versions`. */
    version: number | null = null;

    private spreadsheetSaveInFlight = false;

    readonly LeverageFormType = LeverageFormType;

    ngOnInit(): void {
        this.route.parent?.parent?.paramMap.subscribe(params => {
            this.rmpmid = params.get('rmpmid') ?? null;
        });

        this.initLeverageLendingForm();
        this.wireSpreadsheetAutosave();
        this.route.queryParamMap
            .pipe(
                map(params => params.get('analysisUid')),
                distinctUntilChanged(),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe(uid => {
                if (uid === this.analysisUid) {
                    return; // our own setAnalysisUidInUrl() write — not a navigation
                }
                uid ? this.reload(uid) : this.discardAnalysisIdentity();
            });

        this.handleCounterpartyHeaderOpenedStatus();
        this.leverageLendingService.persisting$.subscribe(persisting => (this.persisting = persisting));
    }

    constructor() {
        // One subject for both forms, SPLIT BY FORM before debouncing. A single debounced stream
        // would drop a save: answer FED then ECB inside the window and only the second persists,
        // leaving the first form's answers unsaved until something else happens to trigger it.
        this.answered$
            .pipe(
                groupBy(formType => formType),
                mergeMap(stream => stream.pipe(debounceTime(300))),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe(formType => this.persistForm(formType));
    }

    initLeverageLendingForm(): void {
        this.leverageLendingForm = this.formBuilder.group({
            spreadsheet: [null, [Validators.required]],
            preliminaryForm: this.formBuilder.group({}),
            fedForm: this.formBuilder.group({}),
            ecbForm: this.formBuilder.group({}),
        });
    }

    get preliminaryForm(): FormGroup {
        return this.leverageLendingForm.get('preliminaryForm') as FormGroup;
    }

    get fedForm(): FormGroup {
        return this.leverageLendingForm.get('fedForm') as FormGroup;
    }

    get ecbForm(): FormGroup {
        return this.leverageLendingForm.get('ecbForm') as FormGroup;
    }

    get locale(): string {
        return this.languageService.getCurrentLang();
    }

    /** Everything that identifies the open analysis, forgotten together. */
    private discardAnalysisIdentity(): void {
        this.analysisUid = null;
        this.formState = undefined;
        this.version = null;
        this.versions.clear();
        this.loadStarted.clear();
        this.validationVisible.clear();
        this.leverageLendingService.clearAllFormState();
        this.validationState.reset();
        this.clearLeverageAlerts();
    }

    handleCounterpartyHeaderOpenedStatus(): void {
        this.workflowService.counterpartyHeaderOpened$
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((opened: boolean) => this.panelOpened.set(opened));
    }

    setPanelOpenedStatus(status: boolean): void {
        this.panelOpenedStatus = status;
    }

    onValidated(change: AnalysisStatusChangeView): void {
        // Refresh rather than patch: the store is the single source for status, and the snapshot
        // header reads the same signal the panel does.
        this.validationState.refresh(this.analysisUid);
    }

    // ================================================================== 2. section gating

    readonly requiredSections = computed<LeverageFormType[]>(() => {
        const required = this.leverageLendingService.preliminaryFormState()?.outcome?.formsToShow ?? [];
        return SECTION_PRIORITY.filter(section => required.includes(section));
    });

    readonly fedRequired = computed<boolean>(() => this.requiredSections().includes(LeverageFormType.FED));
    readonly ecbRequired = computed<boolean>(() => this.requiredSections().includes(LeverageFormType.ECB));
    readonly fedComplete = computed<boolean>(() => isFormComplete(this.leverageLendingService.fedFormState()));

    /** ECB opens once FED is done, or immediately when FED does not apply. */
    readonly ecbOpen = computed<boolean>(() => this.ecbRequired() && (!this.fedRequired() || this.fedComplete()));

    readonly locked = computed<boolean>(() => this.validationState.analysisStatus() === 'VALIDATED');

    /** Guards re-entry while a form's first request is in flight. */
    private readonly loadStarted = new Set<LeverageFormType>();

    /** FED opens as soon as the preliminary outcome names it — nothing to wait for. */
    private readonly openFedWhenReady = effect(() => {
        if (this.fedRequired()) {
            this.openWhenReady(LeverageFormType.FED);
        }
    });

    /**
     * ECB opens on a CONDITION, not at a moment: preliminary may complete first, or FED may finish
     * later. Watching the condition covers both without hanging the load off one call site.
     */
    private readonly openEcbWhenReady = effect(() => {
        if (this.ecbOpen()) {
            this.openWhenReady(LeverageFormType.ECB);
        }
    });

    private openWhenReady(formType: LeverageFormType): void {
        if (this.loadStarted.has(formType) || !this.analysisUid) {
            return;
        }
        if (this.stateOf(formType)) {
            return; // reload() already brought it in
        }
        this.loadStarted.add(formType);
        this.leverageLendingService.loadFormState(this.analysisUid, formType).subscribe({
            next: state => this.applyFormState(formType, state),
            error: () => this.loadStarted.delete(formType),
        });
    }

    // ================================================================== 3. spreadsheet

    onSpreadsheetSelected(spreadsheet: LeverageSpreadsheet): void {
        this.leverageSpreadSheet = spreadsheet;
        this.resolvingAnalysis = true; // block autosave while we look
        this.leverageLendingService.findAnalysisBySpreadsheet(this.leverageSpreadSheet.archiveId).subscribe({
            next: dto => {
                if (dto?.status === 'DRAFT') {
                    this.reload(dto.analysisUid);
                } else {
                    this.analysisUid = null;
                    this.formState = undefined;
                    this.clearLeverageAlerts();
                    this.saveSpreadsheetAndLoadPreliminary(this.leverageSpreadSheet);
                }
                this.resolvingAnalysis = false;
            },
            // 204 comes back as empty; handle in `next` if your client maps 204 -> null
            error: () => {
                this.analysisUid = null;
                this.resolvingAnalysis = false;
            },
        });
    }

    onSpreadsheetSelectionError(alertDetails: IAlert): void {
        this.alertBoxService.addAlerts('errors', [alertDetails]);
    }

    private saveSpreadsheetAndLoadPreliminary(spreadsheet: LeverageSpreadsheet): void {
        if (!this.analysisUid && this.spreadsheetSaveInFlight) {
            return;
        }
        this.spreadsheetSaveInFlight = true;
        const isFirstSave = !this.analysisUid;

        this.leverageLendingService
            .saveSpreadsheet(this.analysisUid, spreadsheet)
            .pipe(finalize(() => (this.spreadsheetSaveInFlight = false)))
            .subscribe({
                next: dto => {
                    // Set BEFORE navigating: the queryParamMap subscription compares against
                    // this.analysisUid to tell our own URL write from a real navigation.
                    this.analysisUid = dto.analysisUid;
                    if (!isFirstSave) {
                        return;
                    }
                    this.setAnalysisUidInUrl(dto.analysisUid);
                    this.leverageLendingService
                        .loadFormState(dto.analysisUid, LeverageFormType.PRELIMINARY)
                        .subscribe(state => this.applyPreliminaryState(state));
                },
                error: () => {
                    /* surface the failure so the analyst can retry */
                },
            });
    }

    // ================================================================== 4. preliminary

    /**
     * Reload the whole analysis.
     *
     * <p><b>FED before ECB, and not cosmetically.</b> ECB's Q01 and Q-S06 prefill from FED's
     * STORED answers. Load ECB first and its Q01 comes back unanswered, so the analyst is asked a
     * question they already answered next door.
     */
    reload(analysisUid: string): void {
        this.hydrating = true;
        this.leverageLendingService.getAnalysis(analysisUid).subscribe(dto => {
            this.analysisUid = dto.analysisUid; // FIRST
            this.leverageLendingForm.get('spreadsheet')!.setValue(dto.finstarArchiveId, { emitEvent: false });
            this.hydratePreliminary(dto.response?.preliminary?.answers ?? []);
            this.leverageLendingService.loadFormState(dto.analysisUid, LeverageFormType.PRELIMINARY).subscribe(state => {
                this.applyPreliminaryState(state);
                this.reloadForm(LeverageFormType.FED);
                this.reloadForm(LeverageFormType.ECB);
                this.hydrating = false;
                this.validationState.refresh(this.analysisUid);
            });
        });
    }

    /** Child emits (value, questionKey); parent re-traverses. THE only preliminary traversal caller. */
    onAnswer(value: string, questionKey: string): void {
        this.alertBoxService.clearAlertsByAnchorId('errors', LeverageAnalysisComponent.PRELIMINARY_ANCHOR);
        this.preliminaryForm.get(questionKey)?.patchValue(value, { emitEvent: false });
        this.leverageLendingService
            .findState(LeverageFormType.PRELIMINARY, {
                version: this.version,
                answers: this.collectPreliminaryAnswers(),
                locale: this.locale,
            })
            .subscribe(state => this.applyPreliminaryState(state));
    }

    private applyPreliminaryState(state: FormState): void {
        this.formState = state;
        this.version = state.definitionVersion;
        this.leverageLendingService.setFormState(LeverageFormType.PRELIMINARY, state);

        const group = this.preliminaryForm;
        const incoming = new Set(state.visibleQuestions.map(question => question.key));

        for (const question of state.visibleQuestions) {
            const existing = group.get(question.key);
            if (!existing) {
                const value = question.answer ?? null; // hydrated value wins over the server echo
                group.addControl(
                    question.key,
                    this.formBuilder.control(value, question.mandatory ? [Validators.required] : []),
                    { emitEvent: false },
                );
            } else if (question.answer != null && existing.value == null) {
                existing.setValue(question.answer, { emitEvent: false }); // only fill if empty
            }
        }
        for (const key of Object.keys(group.controls)) {
            if (!incoming.has(key)) {
                group.removeControl(key, { emitEvent: false });
            }
        }

        if (state.status === Status.COMPLETED && isEditable(state) && !this.hydrating) {
            this.persistPreliminary();
        }
    }

    /**
     * Build the answer payload from the current form, dropping null and blank values. A blank
     * answer means "unanswered", which the engine treats as an absent key — so Q05 goes away once
     * Q04 flips it off-path.
     */
    private collectPreliminaryAnswers(): Record<string, string> {
        const raw = this.preliminaryForm.value as Record<string, string | null>;
        const cleaned: Record<string, string> = {};
        for (const [key, value] of Object.entries(raw)) {
            if (value != null && value !== '') {
                cleaned[key] = value;
            }
        }
        return cleaned;
    }

    private hydratePreliminary(answers: { questionKey: string; value: string }[]): void {
        const group = this.preliminaryForm;
        for (const answer of answers) {
            if (group.contains(answer.questionKey)) {
                group.get(answer.questionKey)!.setValue(answer.value, { emitEvent: false });
            } else {
                group.addControl(answer.questionKey, this.formBuilder.control(answer.value), { emitEvent: false });
            }
        }
    }

    persistPreliminary(): void {
        if (!this.analysisUid || this.persisting) {
            return;
        }
        this.leverageLendingService._persisting.next(true);
        this.leverageLendingService
            .savePreliminary(this.analysisUid, this.preliminaryForm.value, this.locale)
            .pipe(finalize(() => this.leverageLendingService._persisting.next(false)))
            .subscribe({
                next: (formState: FormState) => this.onPreliminarySaved(formState),
                error: () => {
                    /* surface the failure so the analyst can retry */
                },
            });
    }

    onPreliminarySaved(formState: FormState): void {
        const previous = this.leverageLendingService.preliminaryFormState();
        this.leverageLendingService.setFormState(LeverageFormType.PRELIMINARY, formState);

        if (this.verdictChanged(previous, formState)) {
            this.leverageLendingService.clearDownstreamFormState();
        }
    }

    verdictChanged(previous: FormState | null, next: FormState): boolean {
        const before: LeverageFormType[] = previous?.outcome?.formsToShow ?? [];
        const after: LeverageFormType[] = next?.outcome?.formsToShow ?? [];
        return before.length !== after.length || before.some(form => !after.includes(form));
    }

    // ================================================================== 5. FED and ECB

    /**
     * Each form's definition version, echoed back so the backend pins the session.
     *
     * <p>Per form, not shared: FED and ECB are separate definitions with separate numbers, and
     * sending one on the other's traversal would pin the wrong tree.
     */
    private readonly versions = new Map<LeverageFormType, number | null>();

    private readonly answered$ = new Subject<LeverageFormType>();

    private formOf(formType: LeverageFormType): FormGroup {
        return formType === LeverageFormType.FED ? this.fedForm : this.ecbForm;
    }

    private stateOf(formType: LeverageFormType): FormState | null {
        return formType === LeverageFormType.FED
            ? this.leverageLendingService.fedFormState()
            : this.leverageLendingService.ecbFormState();
    }

    /** Thin template entry points, so the HTML reads as the section it is in. */
    onFedAnswer(value: string, questionKey: string): void {
        this.onFormAnswer(LeverageFormType.FED, value, questionKey);
    }

    onEcbAnswer(value: string, questionKey: string): void {
        this.onFormAnswer(LeverageFormType.ECB, value, questionKey);
    }

    /**
     * Child emits (value, questionKey); parent re-traverses. THE only traversal caller for either.
     *
     * <p>`questionKey` may be dotted for a checklist item (`Q-B01A.sovereign`), a data-entry box
     * (`Q-F01.ebitda`) or a justification half (`Q-F01.ebitda.wording`).
     *
     * <p><b>Always traverse.</b> Routing is not gated by whether the answers may be saved: changing
     * Q-B01A from ALL_NO to ANY_YES has to retract Q-T01 at once, and gating this behind
     * `shouldPersist` left retracted questions on screen because Q-T01 itself was half-filled.
     */
    private onFormAnswer(formType: LeverageFormType, value: string, questionKey: string): void {
        this.validationVisible.set(formType, false);
        this.controlFor(formType, questionKey)?.patchValue(value, { emitEvent: false });

        this.leverageLendingService
            .findFormState(formType, this.analysisUid!, {
                version: this.versions.get(formType) ?? null,
                answers: this.collectFormAnswers(formType),
                locale: this.locale,
            })
            .subscribe(state => {
                this.applyFormState(formType, state);
                this.answered$.next(formType); // debounced; persistForm decides whether it may write
            });
    }

    /**
     * Resolves a possibly-dotted answer key to its control.
     *
     * <p>`FormGroup.get` cannot: it treats every dot as a path separator, so it would look for
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

    private reloadForm(formType: LeverageFormType): void {
        const required = formType === LeverageFormType.FED ? this.fedRequired() : this.ecbRequired();
        if (!required || !this.analysisUid) {
            return;
        }
        this.leverageLendingService
            .loadFormState(this.analysisUid, formType)
            .subscribe(state => this.applyFormState(formType, state));
    }

    /** Reconciles a form group against the state the backend returned. */
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
     * Sub-answers are reconciled BOTH ways: values the backend supplies are written in, and
     * controls it no longer names are cleared. Without the clearing, an item settled to
     * NOT_APPLICABLE by a YES keeps that value after the YES is withdrawn, and the analyst can
     * never answer it again.
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
                    this.formBuilder.control({ value: question.subAnswers?.[subKey] ?? null, disabled: readOnly }),
                ]),
            ),
        );
    }

    /**
     * One control per box, plus a wording and a comment for each editable one.
     *
     * <p><b>Read-only is per FIELD here, not per question.</b> `isReadOnly` answers
     * `question.derived`, a statement about the question as a whole — false for a financial table,
     * since the analyst does type into it. Applying that to every box would leave the ratios and
     * the totals editable, and an analyst could type a leverage ratio the arithmetic never produced.
     *
     * <p><b>The justification controls are named with a dot on purpose.</b> `ebitda.wording` is one
     * control NAME, not a path: `collectFormAnswers` flattens into `${questionKey}.${subKey}`,
     * which yields exactly the `Q-F01.ebitda.wording` the backend rules read. A third level of
     * FormGroup would flatten to `[object Object]`.
     *
     * <p>Both halves are created up front, even though the analyst may never open the pop-in, so
     * the group's shape does not change under `syncSubGroup` between two responses.
     */
    private buildDataEntryGroup(question: QuestionView): FormGroup {
        const supplied = question.subAnswers ?? {};
        const controls: Record<string, FormControl> = {};

        for (const field of question.fields ?? []) {
            controls[field.key] = this.formBuilder.control({
                value: supplied[field.key] ?? null,
                disabled: !field.editable,
            });

            if (!field.editable) {
                continue;
            }
            // Absent or complete, never half: the pop-in writes all three together and DISMISS
            // clears all three, which is why they are siblings rather than a nested group.
            for (const half of [JUSTIFICATION_WORDING, JUSTIFICATION_COMMENT]) {
                const subKey = `${field.key}.${half}`;
                controls[subKey] = this.formBuilder.control(supplied[subKey] ?? null);
            }
        }
        return this.formBuilder.group(controls);
    }

    /**
     * Read-only when the value came from somewhere else.
     *
     * <p>Deliberately not keyed on prefillFrom or editable: both are static declarations saying the
     * question CAN be copied. Whether it WAS is only known after the walk, and `derived` is the
     * projection of exactly that. Covers FED's three rating questions without naming them.
     */
    private isReadOnly(question: QuestionView): boolean {
        return question.derived;
    }

    /**
     * Flattens to the dotted wire format, dropping null and blank values.
     *
     * <p>getRawValue rather than value: a prefilled or computed control is disabled, and it still
     * has to round-trip rather than vanish from the payload.
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

    /**
     * NOT_APPLICABLE is never posted back.
     *
     * <p>It is assigned by the backend when a YES settles a block, never typed. Sending it would
     * make a system-assigned value indistinguishable from an answer — and worse, it would count
     * towards "every item answered", so a half-filled checklist would report ALL_NO once a YES had
     * been set and cleared.
     */
    private putAnswer(target: Record<string, string>, key: string, value: unknown): void {
        const text = value === null || value === undefined ? '' : String(value).trim();
        if (text !== '' && text !== 'NOT_APPLICABLE') {
            target[key] = text;
        }
    }

    // ================================================================== 6. alerts

    /** Which forms are showing their full message set, rather than only the immediate ones. */
    private readonly validationVisible = new Map<LeverageFormType, boolean>();

    /**
     * Anchors raised PER FORM.
     *
     * <p>One shared Set would be a cross-form bug: `clearRaisedAlerts` walks every anchor in it, so
     * a FED traversal would clear ECB's alerts and FED's response has nothing to re-raise them
     * with. The analyst would watch ECB's errors vanish while typing in FED.
     */
    private readonly raisedAnchors = new Map<LeverageFormType, Set<string>>();

    /**
     * Everything the response says, in the register it was authored in.
     *
     * <p><b>Displaying is not blocking.</b> Only ERROR stops the analysis being validated; WARNING
     * and INFO are things the analyst should know while carrying on. Alerts are batched per channel
     * so three errors are one render rather than three.
     */
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
        const byChannel = new Map<alertTypes, IAlert[]>();

        for (const message of visible) {
            // Recorded from the MESSAGE, before the alert is built and regardless of whether it
            // builds: alertFor returns null for a key the static catalogue does not know, and an
            // alert we failed to build must still leave a clearable anchor behind.
            raised.add(message.questionKey ?? sectionAnchor);

            const alert = this.alertFor(message, sectionAnchor);
            if (!alert) {
                continue;
            }
            const channel = this.channelFor(message.severity);
            const existing = byChannel.get(channel);
            if (existing) {
                existing.push(alert);
            } else {
                byChannel.set(channel, [alert]);
            }
        }

        byChannel.forEach((alerts, channel) => this.alertBoxService.addAlerts(channel, alerts));
    }

    /**
     * Every anchor this form filed under, across every channel.
     *
     * <p>Both loops are necessary. A field-scoped message anchors to its own question rather than to
     * the section, so the section anchor alone will not clear it; and a message may have been filed
     * in any of the three channels. Clearing a channel that holds nothing for an anchor is a no-op,
     * which is cheaper than tracking the pairs.
     */
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

    /**
     * Builds the alert that MATCHES the severity.
     *
     * <p>An ERROR filter used to stand here, discarding two thirds of what the BA wrote. It was
     * covering for `buildErrorAlert` styling everything as a blocker; the fix is to build the right
     * alert, not to drop the message.
     */
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

    private channelFor(severity: Severity): alertTypes {
        switch (severity) {
            case Severity.WARNING:
                return 'warnings';
            case Severity.INFO:
                return 'informations';
            case Severity.ERROR:
            default:
                return 'errors';
        }
    }

    private clearLeverageAlerts(): void {
        for (const formType of LeverageAnalysisComponent.SAVE_ORDER) {
            this.clearRaisedAlerts(formType);
        }
        this.alertBoxService.clearAlertsByAnchorId('errors', LeverageAnalysisComponent.PRELIMINARY_ANCHOR);
    }

    // ================================================================== 7. persistence

    /**
     * The save button: show every message on every open form, then write.
     *
     * <p>This is where `validationVisible` flips. Until it does, only the immediate messages are on
     * screen — firing "your checklist is incomplete" on item two of three is the noise that teaches
     * people to ignore alerts.
     */
    saveLeverageAnalysisForm(): void {
        if (!this.analysisUid) {
            return;
        }
        for (const formType of this.openSections()) {
            this.validationVisible.set(formType, true);
            this.syncAlerts(formType, this.stateOf(formType)?.validationMessages ?? []);
        }

        if (!this.preliminaryForm.valid) {
            this.alertBoxService.addAlerts('errors', [
                {
                    alertTextId: 'levAnalysisFormValidationErrorMsg',
                    anchorId: LeverageAnalysisComponent.PRELIMINARY_ANCHOR,
                    fragmentId: '',
                },
            ]);
            return;
        }
        this.alertBoxService.clearAlertsByAnchorId('errors', LeverageAnalysisComponent.PRELIMINARY_ANCHOR);
        this.persistOpenSections();
    }

    /** FED when it applies, ECB when it has opened — in the order they are saved. */
    private openSections(): LeverageFormType[] {
        return LeverageAnalysisComponent.SAVE_ORDER.filter(formType =>
            formType === LeverageFormType.FED ? this.fedRequired() : this.ecbOpen(),
        );
    }

    /**
     * Preliminary first, then FED, then ECB — the order the analyst filled them, and the order the
     * prefill depends on: ECB's snapshot copies FED/Q01, so FED has to be recorded before ECB
     * reads it back.
     */
    private persistOpenSections(): void {
        this.leverageLendingService._persisting.next(true);

        const saves: Observable<FormState>[] = [
            this.leverageLendingService.savePreliminary(this.analysisUid!, this.collectPreliminaryAnswers(), this.locale),
        ];
        for (const formType of this.openSections()) {
            saves.push(
                this.leverageLendingService.saveFormAnswers(
                    this.analysisUid!,
                    formType,
                    this.collectFormAnswers(formType),
                    this.locale,
                ),
            );
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

    /** Autosave for one form, debounced upstream by `answered$`. */
    private persistForm(formType: LeverageFormType): void {
        // hydrating guards the reload path: applying a stored state must not echo straight back as
        // a save, which would rewrite the snapshot on every page open.
        if (!this.analysisUid || this.hydrating) {
            return;
        }
        if (!this.shouldPersist(formType)) {
            return;
        }

        this.leverageLendingService._persisting.next(true);
        this.leverageLendingService
            .saveFormAnswers(this.analysisUid, formType, this.collectFormAnswers(formType), this.locale)
            .pipe(finalize(() => this.leverageLendingService._persisting.next(false)))
            .subscribe({
                next: state => this.applyFormState(formType, state),
                error: () => {
                    /* surface the failure so the analyst can retry */
                },
            });
    }

    /**
     * Every checklist on screen must be settled before anything autosaves: a YES settles the block,
     * or every item is answered. Blanks with no YES are a block still being filled, which the
     * backend refuses — so autosaving one would mean a failed request on every keystroke.
     *
     * <p>Checks ALL checklists rather than only the current one: `current` comes from the last
     * state the backend returned, which is one save behind, so trusting it can let a half-filled
     * block through on the response after it stopped being current.
     *
     * <p><b>FED declares no checklists, so this is vacuously true there — and that is correct.</b>
     * The guard exists for the one shape the backend refuses mid-edit; FED's half-finished states
     * are all simply unanswered questions, which save cleanly.
     */
    private shouldPersist(formType: LeverageFormType): boolean {
        return (this.stateOf(formType)?.visibleQuestions ?? [])
            .filter(question => question.type === QuestionType.CHECKLIST)
            .every(question => this.isChecklistSettled(formType, question));
    }

    private isChecklistSettled(formType: LeverageFormType, question: QuestionView): boolean {
        const group = this.formOf(formType).get(question.key) as FormGroup | null;
        const items = question.items ?? [];

        // No group or no declared items: nothing to settle — false, never vacuously true. An
        // `every` over an empty array returns true, which is what let a half-filled block save.
        if (!group || items.length === 0) {
            return false;
        }
        const values = items.map(item => this.answerToken(group.get(item.key)?.value));

        return values.includes('YES') || values.every(value => value !== null);
    }

    /** Tolerates the chip handing back an option object rather than its value. */
    private answerToken(raw: unknown): string | null {
        const value = raw !== null && typeof raw === 'object' ? (raw as { value?: unknown }).value : raw;
        if (value === null || value === undefined) {
            return null;
        }
        const text = String(value).trim();
        return text === '' ? null : text.toUpperCase();
    }

    // ================================================================== unchanged

    // wireSpreadsheetAutosave(), setAnalysisUidInUrl() and the maxHeight getter are not reproduced
    // here — I have not seen their bodies. Keep yours as they are.
}
