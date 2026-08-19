import { Status } from '@lazyloaded/counterparty/model/leverage-lending/form-state.model';

/**
 * Turning stored values into readable ones.
 *
 * <p>Three rules, in order of preference — the point of the order is that the first two keep the
 * wording where the person who owns it can change it, and the third is only a safety net.
 *
 * <ol>
 *   <li><b>An authored label wins.</b> Every coded flag has Display EN and Display FR on the Flag
 *       Values tab, and the backend already resolves them into {@code FlagView.displayValue}. If a
 *       code reaches the screen raw, the fix is usually a blank cell in the workbook, not code
 *       here.</li>
 *   <li><b>A system enum gets a label map.</b> {@code Status} is ours, not the BA's — it will never
 *       be in the workbook, so it needs one here.</li>
 *   <li><b>Anything else gets {@link humanise}</b>, which is mechanical and deliberately dumb.</li>
 * </ol>
 *
 * <p><b>What NOT to add here: an enum mirroring workbook values.</b> There is already one —
 * {@code LevelOfLeveraged}, with BUSINESS_GROUP spelled out in TypeScript — and it is a liability,
 * not a pattern. The day the BA edits a Display EN, that enum disagrees with the database and
 * nothing fails; the screen just quietly shows the old wording. Every value the workbook authors
 * should arrive already localised.
 */

/** Two decimals on screen. The stored value keeps all twenty-eight, and the routing reads that. */
const DISPLAY_DECIMALS = 2;

/** What an absent value reads as — the convention the info panels set. */
export const ABSENT = '-';

/**
 * Formats a stored value for display.
 *
 * <p>Numeric-looking strings are formatted to two decimals; everything else is returned untouched.
 * That is what keeps the ECB leverage ratio from arriving on screen with twenty-eight decimal
 * places while the same figure in the financial table shows 3.17.
 *
 * <p><b>Display only.</b> Never feed the result back into a comparison. A true ratio of 3.9994
 * renders as 4.00, and a predicate reading that would skip the `[0 .. <4]` termination while the
 * `> 4x` line stayed false — ending the analysis ECB_LEVERAGED on a sub-4 ratio.
 */
export function formatDisplayValue(value: string | null | undefined, locale: string): string {
  if (value === null || value === undefined || value.trim() === '') {
    return ABSENT;
  }
  const parsed = Number(value);
  // Number('') is 0 and Number('  ') is 0, both already excluded above. A code like 'INR' or
  // 'BUSINESS_GROUP' parses as NaN and falls through untouched, which is what we want.
  if (Number.isNaN(parsed)) {
    return value;
  }
  return parsed.toLocaleString(locale, {
    minimumFractionDigits: DISPLAY_DECIMALS,
    maximumFractionDigits: DISPLAY_DECIMALS,
  });
}

/**
 * Wording for the statuses the application defines.
 *
 * <p>Localised rather than a `replace('_', ' ')`, because these are read by analysts and "In
 * progress" is not a mechanical transformation of IN_PROGRESS in every language.
 */
const STATUS_LABELS: Readonly<Record<Status, string>> = {
  [Status.IN_PROGRESS]: $localize`:@@statusInProgress:In progress`,
  [Status.COMPLETED]: $localize`:@@statusCompleted:Completed`,
};

export function formatStatus(status: Status | null | undefined): string {
  if (!status) {
    return ABSENT;
  }
  return STATUS_LABELS[status] ?? humanise(status);
}

/**
 * Last resort: SCREAMING_SNAKE to Title Case.
 *
 * <p>For a code that should have had a label and does not — so the screen degrades to something
 * readable instead of shouting. It is NOT a substitute for authoring the label: if you find
 * yourself relying on this for a value the workbook owns, the workbook has a blank cell.
 */
export function humanise(code: string | null | undefined): string {
  if (!code) {
    return ABSENT;
  }
  return code
    .toLowerCase()
    .split(/[_\s]+/)
    .filter(Boolean)
    .map(word => word.charAt(0).toUpperCase() + word.slice(1))
    .join(' ');
}
