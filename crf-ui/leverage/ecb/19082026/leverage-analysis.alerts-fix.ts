/*
 * LeverageAnalysisComponent — the ECB alert path, reconciled instead of accumulated.
 *
 * Replaces: applyEcbState's last line, raiseImmediateEcbMessages, onEcbValidationRaised,
 *           clearEcbValidation. Adds one field and one helper.
 */

// ============================================================ state

/**
 * The anchors this section currently has alerts filed under.
 *
 * <p>Needed because alerts are cleared BY ANCHOR, and a field-scoped message anchors to its own
 * question rather than to the section — so the section anchor alone cannot clear them. Without
 * this, a MUST_BE_POSITIVE filed under `Q-F01` stayed on screen after the analyst removed the
 * minus sign, because nothing ever asked for `Q-F01` to be cleared.
 */
private readonly raisedEcbAnchors = new Set<string>();

// ============================================================ applyEcbState — last line only

private applyEcbState(state: FormState): void {
  // ... everything above unchanged: version, setFormState, syncEcbQuestion, removeControl ...

  this.syncEcbAlerts(state.validationMessages ?? []);
}

// ============================================================ reconciliation

/**
 * Makes the alert box show exactly what the latest response says, and nothing else.
 *
 * <p><b>Clear first, then add — unconditionally.</b> The previous alerts describe the previous
 * answers, so they stop being true the moment a new response arrives, whether the new one has
 * anything to say or not. The old code only added, and only when there was something to add, which
 * meant an alert could outlive the problem that caused it: fix the number, get a clean response,
 * and the stale message stayed because nothing asked for it to go.
 *
 * <p><b>Which messages show is a separate question from whether they are current.</b> The FINSTAR
 * blockers show as soon as they arrive, because they are about a figure the analyst cannot fix from
 * this screen and they stop every question below the table. Everything else waits for an explicit
 * save — firing "your checklist is incomplete" on item two of three is what teaches people to
 * ignore alerts.
 */
private syncEcbAlerts(messages: ValidationMessageView[]): void {
  this.clearRaisedEcbAlerts();

  const showAll = this.ecbValidationVisible();
  const visible = messages.filter(
    message => showAll || LeverageAnalysisComponent.IMMEDIATE_ECB_MESSAGES.has(message.messageKey),
  );
  if (!visible.length) {
    return;
  }

  const alerts = visible
    .map(message =>
      this.alertBoxService.buildErrorAlert(
        message.questionKey ?? LeverageAnalysisComponent.ECB_ANCHOR,
        { [message.text]: true },
        message.fieldKey ?? undefined,
      ),
    )
    .filter((alert): alert is IAlert => alert !== null);

  // Recorded BEFORE the add, and taken from the messages rather than from the alerts, so an alert
  // the catalogue could not resolve still leaves its anchor clearable.
  visible.forEach(message =>
    this.raisedEcbAnchors.add(message.questionKey ?? LeverageAnalysisComponent.ECB_ANCHOR),
  );

  if (alerts.length) {
    this.alertBoxService.addAlerts('errors', alerts);
  }
}

/** Every anchor this section filed under, not just the section's own. */
private clearRaisedEcbAlerts(): void {
  for (const anchorId of this.raisedEcbAnchors) {
    this.alertBoxService.clearAlertsByAnchorId('errors', anchorId);
  }
  this.raisedEcbAnchors.clear();
}

// ============================================================ clearEcbValidation

/**
 * Called from onEcbAnswer. Answering is the analyst acting on the feedback, so the save-time
 * messages stand down and only reappear if they ask to save again with the problem unfixed.
 *
 * <p>It no longer clears the alerts itself. The traversal that follows will land in
 * {@link syncEcbAlerts}, which reconciles the whole box against the fresh response — so clearing
 * here as well would blank the FINSTAR blockers for the length of one round trip and make them
 * flicker on every keystroke.
 */
private clearEcbValidation(): void {
  this.ecbValidationVisible.set(false);
}
