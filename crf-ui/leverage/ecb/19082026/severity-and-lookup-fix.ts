/*
 * TWO FIXES
 *   1. leverage-analysis.component.ts — build the alert that matches the severity
 *   2. ecb-questions.component.ts + .html — clearing a lookup clears the ANSWER, not just the box
 */

// ================================================================================
// 1. SEVERITY — form-state.model.ts
// ================================================================================

/**
 * The TS enum was missing INFO, which is why nothing could render one.
 *
 * <p>The workbook has authored INFO for a while — PARENT_NAME_DIFFERS is the case that matters: the
 * chosen parent is eligible but is not the one RMPM holds, and the analyst may proceed. It has been
 * reaching the client and being dropped on the floor ever since.
 */
export enum Severity {
  ERROR = 'ERROR',
  WARNING = 'WARNING',
  INFO = 'INFO',
}

// ================================================================================
// 1. SEVERITY — leverage-analysis.component.ts
// ================================================================================

/**
 * Everything the response says, in the register it was authored in.
 *
 * <p>The ERROR filter that was here is gone, and it should never have been a filter. It was
 * standing in for a real problem — {@code buildErrorAlert} styles whatever it is handed as a
 * blocker — and the way to fix that is to build the alert that matches the severity, not to
 * discard two thirds of what the BA wrote.
 *
 * <p><b>Displaying is not blocking.</b> Only ERROR stops the analysis being validated; WARNING and
 * INFO are things the analyst should know while carrying on. Those are two different questions and
 * they now have two different pieces of code — {@code isBlocking} answers the second and is
 * untouched.
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
    .map(message => this.alertFor(message))
    .filter((alert): alert is IAlert => alert !== null);

  visible.forEach(message =>
    this.raisedEcbAnchors.add(message.questionKey ?? LeverageAnalysisComponent.ECB_ANCHOR),
  );

  if (alerts.length) {
    this.alertBoxService.addAlerts('errors', alerts);
  }
}

/**
 * One builder per severity.
 *
 * <p>If the shared service only exposes {@code buildErrorAlert}, this is where the two siblings are
 * needed — an INFO presented in red says the opposite of what it means, and an analyst who is told
 * to stop when they may proceed will stop.
 */
private alertFor(message: ValidationMessageView): IAlert | null {
  const anchorId = message.questionKey ?? LeverageAnalysisComponent.ECB_ANCHOR;
  const wording = { [message.text]: true };
  const fragmentId = message.fieldKey ?? undefined;

  switch (message.severity) {
    case Severity.WARNING:
      return this.alertBoxService.buildWarningAlert(anchorId, wording, fragmentId);
    case Severity.INFO:
      return this.alertBoxService.buildInfoAlert(anchorId, wording, fragmentId);
    case Severity.ERROR:
    default:
      return this.alertBoxService.buildErrorAlert(anchorId, wording, fragmentId);
  }
}

// ================================================================================
// 2. LOOKUP — ecb-questions.component.html
// ================================================================================

/*
 *   (itemSelectedEvent)="onLookupSelected(question.key, $event?.value ?? null)"
 *
 * The clear button emits null, and `$event.value` on a null event throws inside the template —
 * where the error surfaces as a broken render rather than as anything that names the cause.
 */

// ================================================================================
// 2. LOOKUP — ecb-questions.component.ts
// ================================================================================

/**
 * A pick is the only thing that becomes an answer — and clearing is the only thing that un-becomes
 * one.
 *
 * <p><b>What was wrong.</b> The clear button emptied the SEARCH box, which is a separate control
 * from the answer by design. The answer control kept the old counterparty. So the screen showed an
 * empty field while the form still held a parent, the walk still ran on it, and the questions below
 * Q-S06 still stood on a choice the analyst believed they had withdrawn.
 *
 * <p><b>The validation message Sushmitha saw is correct.</b> A cleared mandatory lookup IS
 * unanswered, and saying so on save is the rule working. What was wrong is that it fired while the
 * form still held the old answer — the message and the state disagreed, and only one of them was
 * visible.
 */
onLookupSelected(questionKey: string, value: string | null): void {
  if (!value) {
    this.clearLookup(questionKey);
    return;
  }
  this.onAnswer(questionKey, value);
}

/**
 * Puts the question back to genuinely unanswered.
 *
 * <p>Emitting the empty answer is what makes the rest of it true: the parent patches the control,
 * re-traverses, and semantic 1 does the work — an unanswered question matches nothing, so the walk
 * stops at Q-S06 and every question that depended on the parent retracts by itself. No code here
 * needs to know which those were.
 *
 * <p>The search control is reset with {@code emitEvent: false} so the debounced watcher does not
 * treat the clearing as a new search and fire a request for an empty string.
 */
private clearLookup(questionKey: string): void {
  this.searchControls.get(questionKey)?.setValue(null, { emitEvent: false });
  this.results.delete(questionKey);
  this.itemListCache.delete(questionKey);
  this.renderTick.update(tick => tick + 1);

  this.onAnswer(questionKey, null);
}
