import { LeverageFormType } from '../shared/leverage-form-type.model';

/**
 * Mirrors AnalysisValidationState from
 * GET /leverage-analyses/{uid}/validation-state.
 *
 * Analysis-level. The snapshot header binds to `status` here, never to
 * FormState.status — that one is IN_PROGRESS / COMPLETED for a single form.
 */
export interface AnalysisValidationState {
  readonly status: AnalysisStatus;
  readonly validatedBy: string | null;
  readonly validatedTimestamp: string | null;
  readonly canValidate: boolean;
  readonly blocker: CompletenessBlocker;
  readonly blockingForm: LeverageFormType | null;
  readonly blockingMessageCodes: readonly string[];
}

export type AnalysisStatus = 'DRAFT' | 'VALIDATED';

export type CompletenessBlocker =
  | 'NOT_IN_DRAFT'
  | 'FORM_INCOMPLETE'
  | 'DEFINITION_STRANDED'
  | 'BLOCKING_ERRORS'
  | 'NONE';

export interface AnalysisStatusChangeView {
  readonly analysisUid: string;
  readonly fromStatus: AnalysisStatus;
  readonly toStatus: AnalysisStatus;
  readonly changedBy: string;
  readonly changedTimestamp: string;
}
