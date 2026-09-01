import { DestroyRef, Injectable, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subject, catchError, of, switchMap, tap } from 'rxjs';

import { LeverageValidationApi } from './leverage-validation.api';
import { AnalysisValidationState } from './analysis-validation-state.model';

/**
 * Analysis-level validation state, shared by the ECB and FED form components.
 *
 * Those two are separate components by design, but validation is not per-form —
 * an analysis routed to both is validatable only when both are done. So the state
 * lives here rather than in either component.
 */
@Injectable({ providedIn: 'root' })
export class ValidationStateStore {
  private readonly api = inject(LeverageValidationApi);
  private readonly destroyRef = inject(DestroyRef);

  private readonly refreshRequests = new Subject<string>();

  private readonly validationStateSignal = signal<AnalysisValidationState | null>(null);
  private readonly staleState = signal(false);
  private readonly validatingState = signal(false);

  readonly state = this.validationStateSignal.asReadonly();

  /**
   * The snapshot header binds to this, not to FormState.status. DRAFT until the
   * server says otherwise — an unknown state must never read as validated.
   */
  readonly analysisStatus = computed(() => this.validationStateSignal()?.status ?? 'DRAFT');

  readonly validatedBy = computed(() => this.validationStateSignal()?.validatedBy ?? null);
  readonly validatedAt = computed(() => this.validationStateSignal()?.validatedTimestamp ?? null);
  readonly validating = this.validatingState.asReadonly();

  /**
   * The button is enabled ONLY on a fresh server answer.
   *
   * `stale` closes a real gap: the analyst clears a mandatory box, autosave fires,
   * and until the availability response lands the last answer still says
   * canValidate. Without this the button stays live against state that no longer
   * exists, and the click fails with a 422 the analyst did nothing to deserve.
   */
  readonly canValidate = computed(
    () =>
      !this.staleState() &&
      !this.validatingState() &&
      (this.validationStateSignal()?.canValidate ?? false),
  );

  constructor() {
    this.refreshRequests
      .pipe(
        tap(() => this.staleState.set(true)),
        // switchMap, not mergeMap: a superseded response must never overwrite a
        // newer one. Autosave fires on every change, so out-of-order replies are
        // not hypothetical — the same reconcile-not-accumulate rule as the ECB
        // alert sync.
        switchMap((analysisUid) =>
          this.api.validationState(analysisUid).pipe(
            catchError(() => of(null)),
          ),
        ),
        takeUntilDestroyed(this.destroyRef),
      )
      .subscribe((state) => {
        this.validationStateSignal.set(state);
        this.staleState.set(false);
      });
  }

  /** Call on form load and after every save, from both ECB and FED components. */
  refresh(analysisUid: string): void {
    this.refreshRequests.next(analysisUid);
  }

  /** Marks the current answer stale the moment a save starts, before it returns. */
  markStale(): void {
    this.staleState.set(true);
  }

  setValidating(validating: boolean): void {
    this.validatingState.set(validating);
  }
}
