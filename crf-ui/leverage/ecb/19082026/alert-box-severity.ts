/*
 * alerts-box.service.ts — proposed additions.
 *
 * ASSUMPTIONS, both easy to correct:
 *   1. IAlert carries a severity discriminator that the box reads for styling. If it does not,
 *      see the note at the bottom — the fix is different and slightly larger.
 *   2. buildErrorAlert currently resolves wording from the static alerts-messages catalogue and
 *      returns null when the key is absent. The private builder below preserves that exactly.
 */

export enum AlertSeverity {
  ERROR = 'ERROR',
  WARNING = 'WARNING',
  INFO = 'INFO',
}

// ------------------------------------------------------------------ builders

/**
 * Blocks. The analyst cannot complete the analysis until it is resolved.
 *
 * <p>Unchanged in behaviour — it now delegates, so the three severities cannot drift in how they
 * resolve wording or anchor themselves.
 */
buildErrorAlert(
  anchorId: string,
  wording: Record<string, boolean>,
  fragmentId?: string,
): IAlert | null {
  return this.buildAlert(AlertSeverity.ERROR, anchorId, wording, fragmentId);
}

/**
 * Advises. Something is unusual and worth a second look, but the analyst may proceed.
 */
buildWarningAlert(
  anchorId: string,
  wording: Record<string, boolean>,
  fragmentId?: string,
): IAlert | null {
  return this.buildAlert(AlertSeverity.WARNING, anchorId, wording, fragmentId);
}

/**
 * Informs. Nothing is wrong; the analyst is being told something they would otherwise have to go
 * and look up.
 *
 * <p>The case this exists for: PARENT_NAME_DIFFERS. The parent the analyst chose is eligible, but
 * it is not the one RMPM currently holds. They may carry on — and they should also know, because
 * the right follow-up is a correction in RMPM rather than a different choice here.
 */
buildInfoAlert(
  anchorId: string,
  wording: Record<string, boolean>,
  fragmentId?: string,
): IAlert | null {
  return this.buildAlert(AlertSeverity.INFO, anchorId, wording, fragmentId);
}

/**
 * The single place an alert is assembled.
 *
 * <p>Severity is the ONLY thing that varies between the three. Everything else — how the wording is
 * resolved, what happens when the key is missing, how the anchor and fragment are attached — has to
 * be identical, or an INFO will behave subtly differently from an ERROR in a way nobody tests for.
 *
 * <p>Returning null on an unresolved key is deliberate and inherited: a missing catalogue entry
 * drops the alert rather than rendering an empty box. That is a real cost — see the note below —
 * but silently dropping is better than showing an alert with no text.
 */
private buildAlert(
  severity: AlertSeverity,
  anchorId: string,
  wording: Record<string, boolean>,
  fragmentId?: string,
): IAlert | null {
  const alertTextId = Object.keys(wording).find(key => wording[key]);
  if (!alertTextId) {
    return null;
  }
  return {
    alertTextId,
    anchorId,
    fragmentId: fragmentId ?? '',
    severity,
  };
}
