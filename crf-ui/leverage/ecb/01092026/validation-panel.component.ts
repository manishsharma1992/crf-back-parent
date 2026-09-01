import { ChangeDetectionStrategy, Component, computed, inject, input, output } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';

import { AnalysisStatusChangeView, CompletenessBlocker } from './analysis-validation-state.model';
import { LeverageValidationApi } from './leverage-validation.api';
import { ValidationStateStore } from './validation-state.store';

/**
 * The always-visible validation panel.
 *
 * Three states, not one. The UX brief describes the DRAFT case; an analysis that
 * has already been validated needs different words, because "Ready for validation"
 * on a locked analysis is simply wrong — and the panel is specified to stay
 * visible, so it will be read in that state.
 */
@Component({
  selector: 'crf-validation-panel',
  standalone: true,
  imports: [MatButtonModule, MatProgressSpinnerModule],
  changeDetection: ChangeDetectionStrategy.OnPush,
  templateUrl: './validation-panel.component.html',
  styleUrl: './validation-panel.component.scss',
})
export class ValidationPanelComponent {
  private readonly api = inject(LeverageValidationApi);
  private readonly store = inject(ValidationStateStore);

  readonly analysisUid = input.required<string>();

  readonly validated = output<AnalysisStatusChangeView>();
  readonly conflicted = output<void>();

  /**
   * Status comes from the analysis-level read, not from a form's payload. An
   * earlier version inferred it from FormState.validatedAt being non-null, which
   * works today and stops working the moment any other transition stamps a
   * timestamp.
   */
  readonly isValidated = computed(() => this.store.analysisStatus() === 'VALIDATED');
  readonly validatedBy = this.store.validatedBy;
  readonly validatedAt = this.store.validatedAt;
  readonly canValidate = this.store.canValidate;
  readonly validating = this.store.validating;

  /**
   * Derived from the blocker, not from the message codes.
   *
   * The codes are message KEYS, not text — the analyst already sees the localised
   * messages on the form itself. Repeating them here would say the same thing
   * twice and in a worse place. What the panel adds is which form to go to, which
   * the form's own alerts cannot tell them.
   */
  readonly reason = computed<string | null>(() => {
    if (this.isValidated() || this.canValidate()) {
      return null;
    }
    const state = this.store.state();
    if (!state) {
      return $localize`Checking whether this analysis can be validated.`;
    }
    return this.messageFor(state.blocker, state.blockingForm);
  });

  validate(): void {
    if (!this.canValidate()) {
      return;
    }
    this.store.setValidating(true);
    this.api.validate(this.analysisUid()).subscribe({
      next: (change) => {
        this.store.setValidating(false);
        this.validated.emit(change);
      },
      error: (error: { status?: number }) => {
        this.store.setValidating(false);
        if (error.status === 409) {
          // Someone else validated while this tab was open, or a double click
          // lost the race. Reload rather than explain — the screen is out of date.
          this.conflicted.emit();
          return;
        }
        // 422 means the server disagreed with the button. Re-ask rather than
        // guess: the analysis moved between the last refresh and the click.
        this.store.refresh(this.analysisUid());
      },
    });
  }

  private messageFor(
    blocker: CompletenessBlocker,
    form: string | null,
  ): string | null {
    switch (blocker) {
      case 'FORM_INCOMPLETE':
        return form
          ? $localize`Some questions on the ${form} form still need an answer.`
          : $localize`Some questions still need an answer.`;
      case 'BLOCKING_ERRORS':
        return form
          ? $localize`The ${form} form has errors that must be resolved first.`
          : $localize`Some errors must be resolved first.`;
      case 'DEFINITION_STRANDED':
        // Not the analyst's fault and not fixable by them. Say so, rather than
        // sending them to look for a field that does not exist.
        return form
          ? $localize`The ${form} decision tree is incomplete. Contact support — this cannot be fixed from the form.`
          : $localize`The decision tree is incomplete. Contact support.`;
      case 'NOT_IN_DRAFT':
        return $localize`This analysis is no longer a draft.`;
      default:
        return null;
    }
  }
}
