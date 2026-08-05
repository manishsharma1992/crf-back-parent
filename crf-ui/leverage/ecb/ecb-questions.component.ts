import {
  ChangeDetectionStrategy, Component, ElementRef, EventEmitter, Injector, Input, Output,
  afterNextRender, inject,
} from '@angular/core';
import { NgTemplateOutlet } from '@angular/common';
import { FormGroup, ReactiveFormsModule } from '@angular/forms';
import { animate, style, transition, trigger } from '@angular/animations';

import {
  Bullet, FormState, LabelDetails, LocalizedLabel, LocalizedQuestionLabel,
  Option, QuestionType, QuestionView,
} from '../../model/leverage-lending/form-state.model';

/**
 * Presentational only. Renders the ECB section and emits answers.
 *
 * <p>Owns no state and calls no endpoint: the parent re-traverses, reconciles the form group and
 * persists, exactly as it already does for preliminary. That keeps one traversal caller in the
 * application rather than one per section.
 */
@Component({
  selector: 'app-ecb-questions',
  standalone: true,
  imports: [ReactiveFormsModule, NgTemplateOutlet],
  templateUrl: './ecb-questions.component.html',
  styleUrl: './ecb-questions.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  animations: [
    // Only :enter. A question never leaves mid-session — the walk only reveals more.
    trigger('questionEnter', [
      transition(':enter', [
        style({ opacity: 0, transform: 'translateY(12px)' }),
        animate('220ms cubic-bezier(0.4, 0, 0.2, 1)', style({ opacity: 1, transform: 'none' })),
      ]),
    ]),
  ],
})
export class EcbQuestionsComponent {

  @Input({ required: true }) ecbForm!: FormGroup;
  @Input() locale = 'EN';

  /** (value, questionKey) — dotted for a checklist item or a data-entry box. */
  @Output() answered = new EventEmitter<{ value: string; questionKey: string }>();

  private readonly host = inject(ElementRef);
  private readonly injector = inject(Injector);

  readonly QuestionType = QuestionType;

  private currentState: FormState | null = null;
  private lastQuestionKey: string | null = null;
  private firstApply = true;

  /**
   * Setter rather than a plain input: the scroll has to fire when the walk moves on, and that is
   * exactly the moment a new state arrives.
   */
  @Input({ required: true })
  set state(value: FormState | null) {
    this.currentState = value;
    this.scheduleAdvance(value);
  }
  get state(): FormState | null {
    return this.currentState;
  }

  get questions(): QuestionView[] {
    return this.currentState?.visibleQuestions ?? [];
  }

  onAnswer(questionKey: string, value: string | null): void {
    this.answered.emit({ value: value ?? '', questionKey });
  }

  // ------------------------------------------------------------------ auto-advance

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
    block.querySelector<HTMLElement>('input, select, textarea, button')
      ?.focus({ preventScroll: true });
  }

  private prefersReducedMotion(): boolean {
    return window.matchMedia?.('(prefers-reduced-motion: reduce)').matches ?? false;
  }

  // ------------------------------------------------------------------ labels and bullets

  /** Question, subtitle and note are LocalizedQuestionLabel — text plus optional nested bullets. */
  questionText(label: LocalizedQuestionLabel | null | undefined): string {
    return this.details(label)?.text ?? '';
  }

  bulletsOf(label: LocalizedQuestionLabel | null | undefined): Bullet[] {
    return this.details(label)?.bullets ?? [];
  }

  /** A note may be bullets only, with no lead text — emptiness is not just a blank string. */
  hasContent(label: LocalizedQuestionLabel | null | undefined): boolean {
    return !!this.questionText(label) || this.bulletsOf(label).length > 0;
  }

  private details(label: LocalizedQuestionLabel | null | undefined): LabelDetails | null {
    if (!label) {
      return null;
    }
    return (this.isFrench() ? label.fr : label.en) ?? null;
  }

  /** Option and checklist-item labels are plain LocalizedLabel — a different shape, no bullets. */
  labelText(label: LocalizedLabel | null | undefined): string {
    if (!label) {
      return '';
    }
    return (this.isFrench() ? label.fr : label.en) ?? '';
  }

  optionsOf(question: QuestionView): Option[] {
    return question.options ?? [];
  }

  private isFrench(): boolean {
    return this.locale?.toUpperCase() === 'FR';
  }
}
