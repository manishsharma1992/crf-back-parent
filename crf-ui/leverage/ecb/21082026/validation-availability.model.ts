import { LeverageFormType } from '../shared/leverage-form-type.model';

/**
 * Mirrors the ValidationAvailability record returned by
 * GET /leverage-analyses/{uid}/validation-availability.
 *
 * Analysis-level, not form-level. FormState.status says whether THIS form is
 * finished; this says whether the ANALYSIS is, and the two differ the moment a
 * preliminary outcome asks for both ECB and FED.
 */
export interface ValidationAvailability {
  readonly canValidate: boolean;
  readonly blocker: CompletenessBlocker;
  readonly blockingForm: LeverageFormType | null;
  /** Message keys, for telemetry and highlighting. Not for display — see validationReason(). */
  readonly blockingMessageCodes: readonly string[];
}

export type CompletenessBlocker =
  | 'NOT_IN_DRAFT'
  | 'FORM_INCOMPLETE'
  | 'DEFINITION_STRANDED'
  | 'BLOCKING_ERRORS'
  | 'NONE';

export interface AnalysisStatusChangeView {
  readonly analysisUid: string;
  readonly fromStatus: string;
  readonly toStatus: string;
  readonly changedBy: string;
  readonly changedTimestamp: string;
}
