/*
 * financial-table.component.ts — replaces applyJustification, and adds one helper.
 * Nothing else in the component changes.
 */

/**
 * Writes what the dialog returned, in ONE round trip.
 *
 * <p><b>Why not emit each key separately.</b> Every emitted answer makes the parent re-traverse,
 * so two emits meant two requests in flight at once: the first carrying the wording alone, the
 * second carrying both. Both responses run through `syncEcbSubGroup`, which writes
 * `supplied[subKey] ?? null` into every control — so whenever the first response landed second,
 * its state (no comment yet) cleared the comment the analyst had just written. The debounced save
 * then read the emptied form and persisted half a justification, which the save-time rule
 * correctly refused.
 *
 * <p>So the halves are written straight onto the group, and a single emit follows to trigger the
 * one traversal. The component already owns this group — the amount input binds `formControlName`
 * against it — so this is the same access, not a new one.
 *
 * <p>The emit carries the BOX's key rather than a justification key. Its value is unchanged, so
 * the parent's patch is a no-op; what matters is that `collectEcbAnswers` then reads a form
 * holding all three values, and one request carries them together.
 */
private applyJustification(fieldKey: string, result: JustificationDialogResult | undefined): void {
  if (!result) {
    return; // CANCEL — nothing was written, so there is nothing to undo
  }

  if (result.action === 'dismiss') {
    // All three together. This is what makes an empty box reachable again: clearing only the
    // figure would leave a wording and comment describing an adjustment that no longer exists.
    this.write(fieldKey, null);
    this.write(`${fieldKey}.${JUSTIFICATION_WORDING}`, null);
    this.write(`${fieldKey}.${JUSTIFICATION_COMMENT}`, null);
  } else {
    this.write(`${fieldKey}.${JUSTIFICATION_WORDING}`, result.wording);
    this.write(`${fieldKey}.${JUSTIFICATION_COMMENT}`, result.comment);
  }

  this.justificationTick.update(tick => tick + 1);

  // One traversal, after every control is in its final state.
  this.emit(fieldKey, this.valueOf(fieldKey) as string | null);
}

/**
 * Sets a control without firing valueChanges.
 *
 * <p>`emitEvent: false` because the traversal is triggered deliberately once at the end — leaving
 * it on would put us back to one request per control.
 */
private write(subKey: string, value: string | null): void {
  this.control(subKey)?.setValue(value, { emitEvent: false });
}
