import { LeverageFormType } from '../shared/leverage-form-type.model';

/** Mirrors AnalysisSnapshotView / FormSnapshot (BR03). */
export interface AnalysisSnapshotView {
  readonly analysisUid: string;
  readonly archiveId: string | null;
  readonly companyName: string | null;
  readonly recommendedOutcome: string | null;
  readonly forms: readonly FormSnapshot[];
  readonly validatedBy: string;
  readonly validatedTimestamp: string;
  readonly changedBy: string;
  readonly changedTimestamp: string;
  readonly fromStatus: string;
  readonly toStatus: string;
}

export interface FormSnapshot {
  readonly formType: LeverageFormType;
  /** The workbook version this form was answered against — what makes it replayable. */
  readonly definitionVersion: number;
  readonly locale: string;
  /**
   * Insertion-ordered by the server. Iterate with keyvalue:originalOrder, never
   * the default comparator — definition order is what the analyst expects to see.
   */
  readonly flags: Readonly<Record<string, string>>;
}
