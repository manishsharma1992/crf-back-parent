import { animate, style, transition, trigger } from '@angular/animations';
import { NgTemplateOutlet } from '@angular/common';
import { afterNextRender, Component, DestroyRef, ElementRef, inject, Injector, Input, input, output, signal } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import {
    Bullet,
    CHECKLIST_ANSWER_LABELS,
    FormState,
    LabelDetails,
    LevelOfLeveraged,
    LeverageFormType,
    LocalizedLabel,
    LocalizedQuestionLabel,
    LookupOption,
    QuestionType,
    QuestionView,
    Severity,
    ValidationMessageView,
} from '@lazyloaded/counterparty/model/leverage-lending/form-state.model';
import { LeverageLendingService } from '@lazyloaded/counterparty/service/leverage-lending/leverage-lending.service';
import { QuestionOptionsCacheService } from '@lazyloaded/counterparty/service/leverage-lending/question-options-cache.service';
import { ProgressFieldSpinnerComponent } from '@shared/components/progress-field-spinner/progress-field-spinner.component';
import { IItemList, WorkflowAutoCompleteComponent } from '@shared/components/workflow-autocomplete/workflow-autocomplete.component';
import { ISelectionOption, WorkflowSelectionComponent } from '@shared/components/workflow-selection/workflow-selection.component';
import { HoverIconDirective } from '@shared/directives/hover-icon.directive';
import { SvgIconDirective } from '@shared/directives/svg-icon.directive';
import { MaterialModule } from '@shared/modules/MaterialModule';
import { debounceTime, distinctUntilChanged, finalize, map, switchMap, takeUntilDestroyed } from 'rxjs';

import { FinancialTableComponent } from '../financial-table/financial-table.component';

/**
 * Renders one form's questions from its definition.
 *
 * <p><b>This was EcbQuestionsComponent, and the rename IS the change.</b> Nothing in it was ever
 * ECB-specific: it switches on {@link QuestionType}, reads {@code state.visibleQuestions}, and
 * posts dotted keys upward. The only ECB in the file was the class name, the form-group input and
 * a hardcoded form type on the lookup search.
 *
 * <p>Cloning it for FED would have meant two copies of the lookup debounce, the scroll-and-focus
 * advance, the itemList identity caching and the checklist NOT_APPLICABLE rule — and the DATE and
 * TEXT cases FED needs would have been written twice on the first day. Both forms render from the
 * same definition shape, so one component is the honest model of that.
 *
 * <p>What is NOT here, deliberately: which form opens when, the save ordering, whether ECB waits
 * for FED. That is sequencing between forms and it belongs to the parent, where it already lives.
 */
@Component({
    selector: 'bnpp-form-questions',
    templateUrl: './form-questions.component.html',
    styleUrls: ['./form-questions.component.scss'],
    imports: [
        NgTemplateOutlet,
        MaterialModule,
        ReactiveFormsModule,
        SvgIconDirective,
        HoverIconDirective,
        WorkflowSelectionComponent,
        WorkflowAutoCompleteComponent,
        ProgressFieldSpinnerComponent,
        FinancialTableComponent,
    ],
    animations: [
        trigger('questionEnter', [
            transition(':enter', [
                style({ opacity: 0, transform: 'translateY(12px)' }),
                animate('200ms cubic-bezier(0.4, 0, 0.2, 1)', style({ opacity: 1, transform: 'none' })),
            ]),
        ]),
        trigger('fadeInOut', [
            transition(':enter', [style({ opacity: 0 }), animate('1s ease-out', style({ opacity: 1 }))]),
            transition(':leave', [animate('2s ease-out', style({ opacity: 0 }))]),
        ]),
    ],
    standalone: true,
})
export class FormQuestionsComponent {
    @Input({ required: true })
    set state(value: FormState | null) {
        this.currentState = value;
        // Was never assigned, so hasBlockingMessage() always answered false and no question could
        // ever be marked. The messages arrive on the state; this is where they land.
        this.currentMessages = value?.validationMessages ?? [];
        this.scheduleAdvance(value);
    }

    get state(): FormState | null {
        return this.currentState;
    }

    locale = input.required<string>();
    analysisUid = input.required<string>();

    /** The form group this section's controls live on — ecbForm or fedForm, the parent decides. */
    form = input.required<FormGroup>();

    /** Which form this is. Read by the lookup search, which is scoped per form on the backend. */
    formType = input.required<LeverageFormType>();

    /** Panel heading and icon, so the one component can present itself as either section. */
    title = input.required<string>();
    icon = input.required<string>();

    answered = output<{ value: string; questionKey: string }>();

    private static readonly NOT_APPLICABLE = 'NOT_APPLICABLE';

    private readonly host = inject(ElementRef);
    private readonly injector = inject(Injector);
    private readonly leverageAnalysisService = inject(LeverageLendingService);
    private readonly questionOptions = inject(QuestionOptionsCacheService);
    private readonly destroyRef = inject(DestroyRef);

    private currentState: FormState | null = null;
    private lastQuestionKey: string | null = null;
    private firstApply = true;
    private checklistCache: ISelectionOption[] | null = null;
    private currentMessages: ValidationMessageView[] = [];

    private readonly openedNotes = new Set<string>();
    private readonly searching = signal<ReadonlySet<string>>(new Set());

    /**
     * A search box per LOOKUP question, SEPARATE from the answer control.
     *
     * <p>They cannot be the same control. While the analyst types "ACM" the box holds partial text,
     * and since every answer re-traverses, the engine would route on a half-typed word. Only a
     * selection writes to the answer.
     */
    private readonly searchControls = new Map<string, FormControl<string | null>>();
    private readonly results = new Map<string, LookupOption[]>();

    /** The built list plus the answer it was built for, so staleness is checkable rather than encoded in a key. */
    private readonly itemListCache = new Map<string, { answer: string; itemList: IItemList[] }>();

    private readonly renderTick = signal(0);

    readonly Severity = Severity;
    readonly QuestionType = QuestionType;
    readonly loading = signal<boolean>(false);
    readonly localizedNotApplicableLabel = $localize`:@@notApplicableDataLabel: Non Applicable`;

    get questions(): QuestionView[] {
        return this.currentState?.visibleQuestions ?? [];
    }

    /** ERROR blocks the save; WARNING is advisory and styled differently. */
    isBlocking(message: ValidationMessageView): boolean {
        return message.severity === Severity.ERROR;
    }

    hasBlockingMessage(questionKey: string): boolean {
        return this.currentMessages.some(message => message.questionKey === questionKey && this.isBlocking(message));
    }

    onAnswer(questionKey: string, value: string | null): void {
        this.answered.emit({ value: value ?? '', questionKey });
    }

    // ------------------------------------------------------------------ DATE

    /**
     * What the picker should show: the stored ISO string as a Date, or null.
     *
     * <p>Parsed from the PARTS rather than handed to {@code new Date('2026-03-31')}, which the
     * spec reads as UTC midnight — so in any zone behind UTC the picker opens on the previous day.
     */
    dateValue(questionKey: string): Date | null {
        const stored = this.form().get(questionKey)?.value as string | null;
        if (!stored) {
            return null;
        }
        const [year, month, day] = stored.split('-').map(Number);
        return Number.isNaN(year) ? null : new Date(year, month - 1, day);
    }

    /**
     * A date was picked.
     *
     * <p><b>Formatted from the LOCAL parts, never {@code toISOString()}.</b> The picker returns
     * local midnight; converting that to UTC in Mumbai subtracts five and a half hours and yields
     * the day before. The analyst picks the 31st and the form stores the 30th — and the backend
     * would accept it, because it is a perfectly well-formed date.
     *
     * <p>The backend refuses anything that is not {@code yyyy-MM-dd}, and a snapshot read back
     * years later has to mean the same day, so this is the one format that may be posted.
     */
    onDateChanged(questionKey: string, picked: Date | null): void {
        this.onAnswer(questionKey, picked ? FormQuestionsComponent.toIsoDate(picked) : null);
    }

    private static toIsoDate(date: Date): string {
        const month = `${date.getMonth() + 1}`.padStart(2, '0');
        const day = `${date.getDate()}`.padStart(2, '0');
        return `${date.getFullYear()}-${month}-${day}`;
    }

    // ------------------------------------------------------------------ notes

    isNoteOpen(questionKey: string): boolean {
        return this.openedNotes.has(questionKey);
    }

    onDisplayNoteClick(questionKey: string, open: boolean): void {
        this.openedNotes.clear(); // only one note at a time
        if (open) {
            this.openedNotes.add(questionKey);
        }
    }

    levelOfLeveragedCalculated(answer: string | undefined | null): string {
        if (!answer) {
            return '';
        }
        // Was Object.hasOwn(this.levelOfLeveragedCalculated, ...) — the method, not the enum, so
        // the lookup never matched and every value fell through to the raw code.
        if (Object.hasOwn(LevelOfLeveraged, answer)) {
            return LevelOfLeveraged[answer as keyof typeof LevelOfLeveraged];
        }
        return answer;
    }

    /**
     * Cached for reference stability — the chip listbox tracks by object identity, so a fresh array
     * per change-detection pass loops. NOT_APPLICABLE is absent on purpose: the backend assigns it
     * when a YES settles the block, and it must never be selectable.
     */
    checklistOptions(): ISelectionOption[] {
        this.checklistCache ??= CHECKLIST_ANSWER_LABELS.map(answer => ({
            value: answer.value,
            label: this.labelText(answer.label),
        }));
        return this.checklistCache;
    }

    /**
     * Scrolls to wherever the walk now stops, once the new question has rendered.
     *
     * <p>Driven by nextQuestionKey rather than "the one after what I answered": a branch may skip
     * several questions, and only the backend knows where it landed. Silent on the first state so
     * opening a resumed analysis does not yank the page.
     */
    private scheduleAdvance(state: FormState | null): void {
        const target = state?.nextQuestionKey ?? null;
        const moved = target !== null && target !== this.lastQuestionKey;
        this.lastQuestionKey = target;

        if (this.firstApply) {
            this.firstApply = false;
            return;
        }
        if (moved) {
            afterNextRender(() => this.advanceTo(target!), { injector: this.injector });
        }
    }

    private advanceTo(questionKey: string): void {
        const block = (this.host.nativeElement as HTMLElement)
            .querySelector<HTMLElement>(`[data-question-key="${CSS.escape(questionKey)}"]`);
        if (!block) {
            return;
        }
        block.scrollIntoView({
            behavior: this.prefersReducedMotion() ? 'auto' : 'smooth',
            block: 'center',
        });
        // Focus follows the scroll: without it a keyboard user reads one question and types into
        // another.
        block.querySelector<HTMLElement>('input, select, textarea, button')?.focus({ preventScroll: true });
    }

    private prefersReducedMotion(): boolean {
        return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
    }

    /**
     * The item's control on the PARENT's group — the generic component binds [formControl] straight
     * to whatever it is handed, so this instance is what receives the value.
     */
    itemControl(questionKey: string, itemKey: string): FormControl {
        return (this.form().get(questionKey) as FormGroup)?.get(itemKey) as FormControl;
    }

    /**
     * The control first, the state second: the control is what will be posted, and the state is the
     * fallback for the instant between a response arriving and the parent reconciling the group.
     */
    itemValue(question: QuestionView, itemKey: string): string | null {
        const group = this.form().get(question.key) as FormGroup | null;
        return group?.get(itemKey)?.value ?? question.subAnswers?.[itemKey] ?? null;
    }

    /**
     * Assigned by the backend when a YES settled the block — never chosen by the analyst, so it is
     * shown as a statement rather than offered as a third chip.
     */
    isNotApplicable(question: QuestionView, itemKey: string): boolean {
        return this.itemValue(question, itemKey) === FormQuestionsComponent.NOT_APPLICABLE;
    }

    /** Question labels carry bullets: LocalizedQuestionLabel -> LabelDetails.text */
    questionText(label: LocalizedQuestionLabel | null | undefined): string {
        if (!label) {
            return '';
        }
        return (this.isFrench() ? label.fr : label.en)?.text ?? '';
    }

    /** Option and checklist-item labels are plain LocalizedLabel — a different shape. */
    labelText(label: LocalizedLabel | null | undefined): string {
        if (!label) {
            return '';
        }
        return (this.isFrench() ? label.fr : label.en) ?? '';
    }

    optionsOf(question: QuestionView): ISelectionOption[] {
        return this.questionOptions.optionsOf(question, this.locale(), this.currentState?.definitionVersion);
    }

    bulletsOf(label: LocalizedQuestionLabel | null | undefined): Bullet[] {
        return this.details(label)?.bullets ?? [];
    }

    hasContent(label: LocalizedQuestionLabel | null | undefined): boolean {
        return !!this.questionText(label) || this.bulletsOf(label).length > 0;
    }

    // ------------------------------------------------------------------ lookup

    /**
     * Created on first render of the question and kept, so subscription is set up once rather
     * than per change-detection pass.
     */
    searchControl(questionKey: string): FormControl<string | null> {
        let control = this.searchControls.get(questionKey);
        if (!control) {
            control = new FormControl<string | null>(null);
            this.searchControls.set(questionKey, control);
            this.watchSearch(questionKey, control);
        }
        return control;
    }

    /**
     * The list as the autocomplete wants it, with a stable array identity.
     *
     * <p>Rebuilt only when the results or the selected answer change. A fresh array per
     * change-detection pass makes a component that tracks by identity re-render, which schedules
     * another pass — the same loop the chip options hit.
     */
    itemListFor(question: QuestionView): IItemList[] {
        this.renderTick(); // re-evaluates when a response lands

        const answer = this.form().get(question.key)?.value ?? '';
        const cached = this.itemListCache.get(question.key);
        if (cached && cached.answer === answer) {
            return cached.itemList;
        }

        const itemList = (this.results.get(question.key) ?? []).map(option => ({
            label: option.label,
            value: option.value,
            selected: option.value === answer,
        }));
        this.itemListCache.set(question.key, { answer, itemList });
        return itemList;
    }

    isSearching(questionKey: string): boolean {
        return this.searching().has(questionKey);
    }

    /**
     * A pick is the only thing that becomes an answer.
     *
     * <p>Emitted upward like any other answer, so the parent re-traverses and persists exactly as it
     * does for a radio — the child stays the component that knows nothing about either.
     */
    onLookupSelected(questionKey: string, value: string): void {
        if (!value) {
            this.clearLookup(questionKey);
            return;
        }
        this.onAnswer(questionKey, value);
    }

    /** What the box shows once a choice is made, and after a reload. */
    lookupDisplay(question: QuestionView): string {
        const answer = this.form().get(question.key)?.value ?? '';
        const matched = (this.results.get(question.key) ?? []).find(option => option.value === answer);
        return matched?.label ?? answer;
    }

    answerLabel(question: QuestionView): string {
        const answer = question.answer;
        if (!answer) {
            return '-';
        }
        const chosen = (question.options ?? []).find(option => option.value === answer);
        return chosen ? this.labelText(chosen.label) : answer;
    }

    /**
     * Puts the question back to genuinely unanswered.
     *
     * <p>Emitting the empty answer is what makes the rest of it true: the parent patches the control,
     * re-traverses, and an unanswered question matches nothing — so the walk stops there and every
     * question that depended on it retracts by itself. No code here needs to know which those were.
     */
    private clearLookup(questionKey: string): void {
        this.searchControls.get(questionKey)?.setValue(null, { emitEvent: false });
        this.results.delete(questionKey);
        this.itemListCache.delete(questionKey);
        this.renderTick.update(tick => tick + 1);

        this.onAnswer(questionKey, null);
    }

    private markSearching(questionKey: string, active: boolean): void {
        this.searching.update(current => {
            const next = new Set(current);
            if (active) {
                next.add(questionKey);
            } else {
                next.delete(questionKey);
            }
            return next;
        });
    }

    /**
     * Debounced because the analyst types faster than a trigram search over ten million rows
     * answers; distinct because arrow keys and re-focus re-emit the same text; switchMap because a
     * slower earlier response must not overwrite a faster later one.
     *
     * <p>The form type comes from the input rather than a constant — the lookup endpoint is scoped
     * per form, and this is the one line that stopped the component being form-agnostic.
     */
    private watchSearch(questionKey: string, control: FormControl<string | null>): void {
        control.valueChanges
            .pipe(
                map(value => (value ?? '').trim()),
                debounceTime(300),
                distinctUntilChanged(),
                tap(() => this.markSearching(questionKey, true)),
                switchMap(query =>
                    this.leverageAnalysisService
                        .searchLookupOptions(this.analysisUid(), this.formType(), questionKey, query, this.locale())
                        .pipe(finalize(() => this.markSearching(questionKey, false))),
                ),
                takeUntilDestroyed(this.destroyRef),
            )
            .subscribe(options => {
                this.results.set(questionKey, options);
                this.itemListCache.delete(questionKey);
                this.renderTick.update(tick => tick + 1);
            });
    }

    private details(label: LocalizedQuestionLabel | null | undefined): LabelDetails | null {
        if (!label) {
            return null;
        }
        return (this.isFrench() ? label.fr : label.en) ?? null;
    }

    private isFrench(): boolean {
        return this.locale()?.toUpperCase() === 'FR';
    }
}
