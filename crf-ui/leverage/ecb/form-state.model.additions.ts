/**
 * Additions to form-state.model.ts, mirroring the widened Java FormState.
 */

export enum Severity {
  ERROR = 'ERROR',
  WARNING = 'WARNING',
}

/**
 * A rule that CURRENTLY FIRES, already worded in the analyst's language.
 *
 * <p>The text arrives rendered rather than as a key to look up, so a re-wording in the authoring
 * workbook reaches the screen through an import with no UI change and nothing duplicated in a
 * translation file.
 *
 * @param questionKey null when the rule speaks for the whole form — the generic checklist message
 *                    is authored with blank keys precisely because it covers every checklist
 * @param fieldKey    null unless the rule is about one data-entry box
 */
export class ValidationMessageView {
  messageKey: string;
  severity: Severity;
  questionKey: string | null;
  fieldKey: string | null;
  text: string;
}

/**
 * Add to FormState:
 *
 *   validationMessages: ValidationMessageView[];   // empty, never null
 *   lastModifiedTimestamp: string | null;          // ISO-8601, null on the stateless path
 *   validatedAt: string | null;                    // null while still a draft
 *   validatedBy: string | null;
 *
 * The three timestamps are Instants on the Java side. They serialise to ISO-8601 strings only if
 * JavaTimeModule is registered with WRITE_DATES_AS_TIMESTAMPS off — otherwise they arrive as epoch
 * numbers and these types are wrong. Worth checking the first payload before trusting the type.
 */
