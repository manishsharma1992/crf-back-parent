import { AnalysisStatus } from './leverage-lending.model';

/**
 * The two havles of an adjustment's justification, as sub-answer keys
 * 
 * <p> Kept as one place because the backend treats a box as either fully absent or fully complete:
 * value, wording and comment are written together and cleared together, and a wording without a
 * comment is not a justification
 */
export const JUSTIFICATION_WORDING = 'wording';
export const JUSTIFICATION_COMMENT = 'comment';

/** Server-side limits, mirrored so the counters agree with what will be accepted */
export const JUSTIFICATION_WORDING_MAX = 40;
export const JUSTIFICATION_COMMENT_MAX = 100;

export const CHECKLIST_ANSWER_LABELS: ReadonlyArray<{ value: string; label: LocalizedLabel}> = [
    { value: 'YES', label: {en : 'Yes', fr: 'Oui'}},
    { value: 'NO', label: {en: 'No', fr: 'Non'}}
];

/** Two decimals on screen. The stored value keeps all twenty-eight, and the routing reads that. */
const DISPLAY_DECIMALS = 2;

/** What an absent value reads as - the convention the info panels set. */
export const ABSENT = '-';

/**
 * Additions to form-state.model.ts, mirroring the widened Java FormState.
 */

export enum Severity {
    ERROR = 'ERROR',
    WARNING = 'WARNING',
    INFO = 'INFO',
}

export enum LevelOfLeveraged {
    BUSINESS_GROUP = 'BUSINESS GROUP',
    BORROWER = 'BORROWER',
}

/**
 * A rule that CURRENTLY FIRES, already worded in the analyst's language.
 * 
 * <p>The text arrives rendered rather than as a key to look up, so a re-wording in the authoring
 * workbook reaches the screen through an import with no UI change and nothing duplicated in a
 * translation file.
 * 
 * @param questionKey null when the rule speaks for the whole form - the generic checklist message
 * is authored with blank keys precisely because it covers every checklist
 * 
 * @param fieldKey null unless the rule is about one data-entry box
 */
export class ValidationMessageView {
    messageKey: string;
    severity: Severity;
    questionKey: string | null;
    fieldKey: string | null;
    text: string;
}

export class Result {
    analysisUid: string;
    formState: FormState;
}

export class FormState {
    formType: LeverageFormType;
    definitionVersion: number;
    status: Status;
    visibleQuestions: QuestionView[];
    nextQuestionKey: string;
    flags: Record<string, string>;
    flagViews: FlagView[];
    infoPanels: PanelSnapshot[];
    outcome: OutcomeView;
    validationMessages: ValidationMessageView[];
    lastModifiedTimestamp: string | null;
    validatedAt: string | null;
    validatedBy: string | null;
    analysisStatus: AnalysisStatus | null;
}

export type Section = 'PRELIMINARY' | 'FED' | 'ECB' | 'DONE';

export enum LeverageFormType {
    PRELIMINARY = 'PRELIMINARY',
    ECB = 'ECB',
    FED = 'FED'
}

export type Phase = LeverageFormType | 'DONE';

export const SECTION_PRIORITY: readonly LeverageFormType[] = [LeverageFormType.FED, LeverageFormType.ECB];

export enum Status {
    IN_PROGRESS = 'IN_PROGRESS',
    COMPLETED = 'COMPLETED',
}

export class QuestionView {
    key: string;
    type: QuestionType;
    mandatory: boolean;
    computed: boolean;
    editable: boolean;
    prefillFrom: string;
    fillsFlag: string;
    label: LocalizedQuestionLabel;
    subtitle: LocalizedQuestionLabel;
    note: LocalizedQuestionLabel;
    /**
     * Heading drawn ABOVE the question - "Rule for escalation to CCDG". Null when the workbook's
     * Section column is blank, which is almost every row. There is no `hidden` on this class: a
     * hidden question is never sent, so there is nothing for the client to filter.
     */
    section?: LocalizedQuestionLabel | null;
    options: Option[];
    items: ChecklistItem[];
    fields: DataField[];
    answer: string;
    derived: boolean;
    subAnswers: Record<string, string>;
    current: boolean;
}

export enum QuestionType {
    SINGLE_CHOICE = 'SINGLE_CHOICE',
    NUMERIC = 'NUMERIC',
    TEXT = 'TEXT',
    CHECKLIST = 'CHECKLIST',
    DATA_ENTRY = 'DATA_ENTRY',
    COMPUTED = 'COMPUTED',
    LOOKUP = 'LOOKUP',
    DATE = 'DATE',
}

export enum DataTypeField {
    NUMERIC = 'NUMERIC',
    DATE = 'DATE',
    TEXT = 'TEXT',
    LOOKUP = 'LOOKUP'
}

export class Option {
    value: string;
    label: LocalizedLabel;
}

export class ChecklistItem {
    key: string;
    label: LocalizedLabel;
}

/**
 * One box inside a DATA_ENTRY question -  a row of the workbook's Fields tab.
 * 
 * <p>`editable` and `visible` were missing from this class while being present on the wire, so the
 * client could not tell a typed adjustment from a calculate total. Inferring it from 
 * `derivedFrom` happens to give the right answer for all eighteen rows of the current workbook -
 * every non-editable box has a source and every editable one does not - but that is a property of
 * this workbook, not a rule, and it would break the first time a FINANCIALS/ box is made editable
 * 
 * @param editable true only for the ten adjustments the analyst types into. The two ratios, the three totals and 
 *                  three totals and the three FINSTAR figures are false
 * 
 * @param visible false for a box that is part of the record and frozen with the answer but
 *                  never rendered - `netDebt`, which feeds Total Net Funded Debt. Optional on 
 *                  the wire: a definition published before the column existed omits it, and 
 *                  absent means visible
 * @param derivedFrom `CALC/x` computed in the domain layer, `FINANCIALS/x` read from FINSTAR, or
 *                      absent when the analyst types it.
 * 
 * @param formula documentation only. NOTHING evaluates this, on either side - the arithmetic lives
 *                  in the backend domain layer, anad the client renders what it is sent
 */
export class DataField {
    key: string;
    group: string;
    label: LocalizedLabel;
    note: LocalizedLabel;
    type: DataTypeField;
    mandatory: boolean;
    editable: boolean;
    visible?: boolean;
    derivedFrom: string;
    formula: string;
    fillsFlag: string;
}

/** Absent means visible - see the note on `DataField.visible`. */
export function isFieldVisible(field: DataField): boolean {
    return field.visible !== false;
}

export class LocalizedQuestionLabel {
    en: LocalizedDetails;
    fr: LocalizedDetails;
}

export class LabelDetails {
    text: string;
    bullets: Bullet[];
}

export class LocalizedLabel {
    en: string;
    fr: string;
}

export class Bullet {
    text: string;
    children: Bullet[];
}

export class OutcomeView {
    code: string;
    displayValue: string;
    formsToShow: LeverageFormType[];
    flags: Record<string, string>;
}

export class Answer {
    questionKey: string;
    questionLabel: LocalizedQuestionLabel;
    type: string;
    value: string;
    valueLabel: LocalizedLabel;
    computed: boolean;
}

export class FormResponses {
    definitionVersion: number;
    locale: string;
    flags;
    answers: Answer[];
}

export class LookupOption {
    value: string;
    label: string;
}

export class PanelSnapshot {
    panelKey: string;
    title: LocalizedLabel;
    fieldOrder: string[];
    values: Record<string, string>;
}

export interface FinancialRow {
    field: DataField;
    label: string;
    note: string;
    editable: boolean;
    /** Formatted for the screen. NEver read back - see `displayValue`. */
    display: string;
    justified: boolean;
    needsJustification: boolean;
}

/** A heading from the Fields tab's Group column, with the rows that sit under it. */
export interface FinancialGroup {
    name: string;
    rows: FinancialRow[];
}

/** What the table hands the dialog when it opens. */
export interface JustificationDialogData {
    /** The adjustment's label, so the dialog can say which box it is about. */
    fieldLabel: string;
    wording: string | null;
    comment: string | null;
    /** True when the box currently holds a figure - DISMISS has nothing to clear otherwise. */
    hasValue: boolean;
}

/**
 * What the dialog hands back. `undefined` means CANCEL: the analyst changed nothing and the box is
 * left exactly as it was.
 */
export type JustificationDialogResult = { action: 'validate'; wording: string; comment: string } | { action: 'dismiss' };

export interface FlagView {
    key: string;
    label: string;
    value: string;
    displayValue: string;
}

export interface SnapshotSection {
    formType: LeverageFormType;
    panels: SnapshotPanel[];
    flags: FlagView[];
}

export interface SnapshotPanel {
    key: string;
    title: string;
    /** Rendered as "since <<date>>". Absent when the panel does not carry one. */
    date: string | null;
    /** The bold conclusion lines, in the order the workbook declared them. */
    headlines: string[];
    /** Rendered as "ratio: <<value>>" */
    ratio: string | null;
    /** Everything the card has no designed position for */
    other: { label: string; value: string } [];
}

export type PanelSlot = 'date' | 'headline' | 'ratio';

/**
 * Which authored field fills which slot.
 * 
 * <p><b> Yes, this names fields - and it is the only place that does. </b> A desinged card has named
 * positions: since <<date>>, a headline, "ratio: <<value>>", Nothing in {@code PanelSnapshot} says
 * which field is a date, because the workbook has no column for it, so somewhere has to decide.
 * {@code panel.values['leverageDate']}.
 * 
 * <p><b>The `other` fallback is what this safe. <b> A field not claimed here still renders,
 * as a plain label and value beneath the card. So if the BA adds a panel field, it appears -
 * unstyled, but visible - instead of being silently dropped, which is the failure mode that would
 * otherwise take months to notice.
 * 
 * <p>Panel field names do not match flag keys - {@code leveragedFlag} here versus
 * {@code ecbLeveragedFlag} in the flags catalogue - which is the same mismatch the adapter's
 * DECODED_BY map exists for. Aligning them in the workbook would remove both.
 */
export const PANEL_SLOTS: Readonly<Record<string, PanelSlot>> = {
    leverageDate: 'date',
    covenantStructure: 'headline',
    leveragedFlag: 'headline',
    leverageRatio: 'ratio',
    ecbLeverageRatio: 'ratio',
    fedLeverageRatio: 'ratio'
}

/**
 * Formats a stored value for display.
 * 
 * <p>Numeric-looking strings are formatted to two decimals: everything else is written untouched.
 * That is what keeps the ECB Leverage Ratio from arriving on screen with 28 decimal 
 * places while the same figure in the financial table shows 3.17.
 * 
 * <p><b>Display only. </b> Never feed the result back into a comparison. A true ratio of 3.9994
 * renders as 4.00, and a predicate reading that would skip the `[0 .. <4]` temination while the 
 * `> 4x` line stayed false - ending the analysis ECB_LEVERAGED on a sub-4 ratio
 */
export function formatDisplayValue(value: string | null | undefined, locale: string): string {
    if (value === null || value === undefined || value.trim() === '') {
        return ABSENT;
    }
    const parsed = Number(value);
    // Number('') is 0 and Number(' ') is 0, both already excluded above. A code like 'INR' or
    // BUSINESS_GROUP parses as NaN and falls through untouched, which is want we want
    if(Number.isNaN(parsed)) {
        return value;
    }
    return parsed.toLocaleString(locale, {
        minimumFractionDigits: DISPLAY_DECIMALS,
        maximumFractionDigits: DISPLAY_DECIMALS,
    });
}

/** Wording for the statuses the application defines
 * 
 * <p> Localized rather than a `replace('_', ' ')`, because these are read by analysts and "In
 * progress" is not mechanical transformation of IN_PROGRESS in every language.
 */
const STATUS_LABELS: Readonly<Record<Status, string>> = {
    [Status.IN_PROGRESS]: $localize`:@@statusInProgress: En cours`,
    [Status.COMPLETED]: $localize`:@@statusCompleted:Cpmplete`,
};

export function isFormComplete(state: FormState | null): boolean {
    return state?.status === Status.COMPLETED;
}

export function formatStatus(status: Status | null | undefined): string {
    if(!status) {
        return ABSENT;
    }
    return STATUS_LABELS[status] ?? humanise(status);
}

/**
 * Last resort: SCREAMING_SNAKE to Title Case.
 * 
 * <p> For a code that should have had a label and does not - so the screen degrades to something
 * readable instead of shouting. It is NOT a substitute for authoring the label: if you find
 * yourself relying on this for a value the workbook owns, the workbook has a blank cell.
 */
export function humanise(code: string | null | undefined): string {
    if(!code) {
        return ABSENT;
    }
    return code
        .toLowerCase()
        .split(/[_\s]+/)
        .filter(Boolean)
        .map(word => word.charAt(0).toUpperCase() + word.slice(1))
        .join(' ');
}

/**
 * Whether the analysis behind this form state may still be written to.
 *
 * <p>Null state OR null status both mean "no analysis is being described". The stateless traversal
 * (findState) has no analysis row to report on and correctly sends FormAudit.NONE, so the
 * preliminary's every-answer response carries no status — and treating that as not-editable
 * silently disabled its autosave entirely.
 *
 * <p>The server's assertModifiable() is the real guard; this only stops pointless requests.
 */
export function isEditable(state: FormState | null | undefined): boolean {
    return state?.analysisStatus == null || state.analysisStatus === 'DRAFT';
}

/**
 * Fallback if FormState.analysisStatus is not added.
 * 
 * Correct today only because validation is the only transition that stamps
 * validatedAt. Nothing fails loudly the day a second one does, which is why the 
 * explicit status is worth the one-line payload change.
 */
export function isEditableByStamp(state: FormState | null): boolean {
    return state != null && state.validatedAt == null;
}

