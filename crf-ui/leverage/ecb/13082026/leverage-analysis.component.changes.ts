/*
 * LeverageAnalysisComponent — the ECB members that change for the financial table.
 *
 * Five edits. Everything else in the class is untouched, including shouldPersistEcb.
 *   1. onEcbAnswer            — resolve dotted keys without Angular's path splitting
 *   2. controlFor (new)       — the resolver itself
 *   3. buildEcbControl        — DATA_ENTRY gets its own builder
 *   4. buildDataEntryGroup    — per-field disabled state, plus the justification controls
 *   5. applyEcbState          — raise the FINSTAR blockers without waiting for a save
 */

import { AbstractControl, FormControl, FormGroup, Validators } from '@angular/forms';
import {
  FormState,
  JUSTIFICATION_COMMENT,
  JUSTIFICATION_WORDING,
  LeverageFormType,
  QuestionType,
  QuestionView,
  ValidationMessageView,
} from '@lazyloaded/counterparty/model/leverage-lending/form-state.model';

// ============================================================ 1. onEcbAnswer

/**
 * Child emits (value, questionKey); parent re-traverses. THE only ECB traversal caller.
 *
 * <p>`questionKey` may be dotted for a checklist item (`Q-B01A.sovereign`), a data-entry box
 * (`Q-F01.ebitda`), or a justification half (`Q-F01.ebitda.wording`).
 *
 * <p>`FormGroup.get` can no longer resolve these. It treats every dot as a path separator, so it
 * would look for a control named `wording` inside a group named `ebitda` inside `Q-F01` — three
 * levels, where the form has two. `controlFor` splits on the FIRST dot only and looks the
 * remainder up by name, which resolves all three shapes with one rule.
 */
onEcbAnswer(value: string, questionKey: string): void {
  this.clearEcbValidation();
  this.controlFor(questionKey)?.patchValue(value, { emitEvent: false });

  // ALWAYS traverses. Routing is not gated by whether the answers may be saved: changing Q-B01A
  // from ALL_NO to ANY_YES has to retract Q-T01 at once, and gating this behind shouldPersistEcb
  // left the retracted question on screen because Q-T01 itself was half-filled.
  this.leverageLendingService
    .findFormState(LeverageFormType.ECB, this.analysisUid!, {
      version: this.ecbVersion,
      answers: this.collectEcbAnswers(),
      locale: this.locale,
    })
    .subscribe(state => {
      this.applyEcbState(state);
      this.ecbAnswered.next(); // debounced; persistEcb decides whether it may write
    });
}

// ============================================================ 2. controlFor

/**
 * Resolves a possibly-dotted answer key to its control.
 *
 * <p>The first segment is always a question key; whatever follows is a control NAME inside that
 * question's sub-group, dots and all. A question key never contains a dot; a sub-key may.
 */
private controlFor(answerKey: string): AbstractControl | null {
  const firstDot = answerKey.indexOf('.');
  if (firstDot < 0) {
    return this.ecbForm.get(answerKey);
  }
  const questionKey = answerKey.slice(0, firstDot);
  const subKey = answerKey.slice(firstDot + 1);
  const group = this.ecbForm.get(questionKey) as FormGroup | null;
  return group?.controls[subKey] ?? null;
}

// ============================================================ 3. buildEcbControl

/**
 * A DATA_ENTRY question is a sub-group like a CHECKLIST, but its boxes do not share one read-only
 * state and the editable ones carry two more controls each — so it gets its own builder rather
 * than another parameter on buildEcbSubGroup.
 */
private buildEcbControl(question: QuestionView): FormGroup | FormControl {
  if (question.type === QuestionType.CHECKLIST) {
    return this.buildEcbSubGroup(
      question,
      (question.items ?? []).map(item => item.key),
    );
  }
  if (question.type === QuestionType.DATA_ENTRY) {
    return this.buildDataEntryGroup(question);
  }
  return this.formBuilder.control(
    { value: question.answer ?? null, disabled: this.isEcbReadOnly(question) },
    question.mandatory ? [Validators.required] : [],
  );
}

// ============================================================ 4. buildDataEntryGroup

/**
 * One control per box, plus a wording and a comment for each editable one.
 *
 * <p><b>Read-only is per FIELD here, not per question.</b> `isEcbReadOnly` answers
 * `question.derived`, a statement about the question as a whole — false for Q-F01, since the
 * analyst does type into it. Applying that to all eighteen boxes would leave the two ratios and
 * the three totals editable, and an analyst could type a leverage ratio the arithmetic never
 * produced.
 *
 * <p><b>The justification controls are named with a dot on purpose.</b> `ebitda.wording` is one
 * control NAME, not a path: `collectEcbAnswers` flattens `Object.entries(group.controls)` into
 * `${questionKey}.${subKey}`, which yields exactly the `Q-F01.ebitda.wording` the backend rules
 * read, with no change to that method. A third level of FormGroup would flatten to
 * `[object Object]` and would leave the box's own value with nowhere to live.
 *
 * <p>Both halves are created up front, even though the analyst may never open the pop-in, so the
 * group's shape does not change under `syncEcbSubGroup` between two responses.
 */
private buildDataEntryGroup(question: QuestionView): FormGroup {
  const supplied = question.subAnswers ?? {};
  const controls: Record<string, FormControl> = {};

  for (const field of question.fields ?? []) {
    controls[field.key] = this.formBuilder.control({
      value: supplied[field.key] ?? null,
      disabled: !field.editable,
    });

    if (!field.editable) {
      continue;
    }
    // Absent or complete, never half: the pop-in writes all three together and DISMISS clears all
    // three, which is why they are siblings rather than a nested group.
    for (const half of [JUSTIFICATION_WORDING, JUSTIFICATION_COMMENT]) {
      const subKey = `${field.key}.${half}`;
      controls[subKey] = this.formBuilder.control(supplied[subKey] ?? null);
    }
  }
  return this.formBuilder.group(controls);
}

// ============================================================ 5. applyEcbState

/**
 * Reconciles the form group against the state the save returned.
 *
 * <p>One addition at the end: blocking FINSTAR messages are raised as soon as they arrive.
 *
 * <p>Every other ECB message waits for `saveLeverageAnalysisForm` to set `ecbValidationVisible`,
 * and that is right — firing "your checklist is incomplete" on item two of three is the noise that
 * teaches people to ignore alerts. The FINSTAR rules are the opposite case: they are not about
 * something the analyst is part-way through, they are about a figure they cannot fix from this
 * screen at all, and they block every question below the table. Making someone fill ten adjustment
 * boxes before telling them EBITDA was never delivered is the worst version of waiting.
 */
private applyEcbState(state: FormState): void {
  this.ecbVersion = state.definitionVersion;
  this.leverageLendingService.setFormState(LeverageFormType.ECB, state);

  const group = this.ecbForm;
  const incoming = new Set(state.visibleQuestions.map(q => q.key));

  for (const question of state.visibleQuestions) {
    this.syncEcbQuestion(group, question);
  }
  for (const key of Object.keys(group.controls)) {
    if (!incoming.has(key)) {
      group.removeControl(key, { emitEvent: false });
    }
  }

  this.raiseImmediateEcbMessages(state.validationMessages ?? []);
}

/**
 * The rules that must not wait for an explicit save.
 *
 * <p>Chosen by message key rather than by severity: plenty of ERRORs are about something the
 * analyst is still filling in. These three name a source figure that arrived wrong from FINSTAR,
 * and nothing on this screen can make them go away.
 *
 * <p>`ECB_ADJUSTED_EBITDA_ZERO` is deliberately NOT here. That one IS caused by what the analyst
 * typed — five adjustments cancelling the base out — so it belongs with the save-time messages,
 * where they can see it alongside whatever else is outstanding.
 */
private static readonly IMMEDIATE_ECB_MESSAGES: ReadonlySet<string> = new Set([
  'ECB_EBITDA_EMPTY',
  'ECB_EBITDA_ZERO',
  'ECB_GROSS_DEBT_ZERO',
]);

private raiseImmediateEcbMessages(messages: ValidationMessageView[]): void {
  const immediate = messages.filter(message =>
    LeverageAnalysisComponent.IMMEDIATE_ECB_MESSAGES.has(message.messageKey),
  );
  if (immediate.length) {
    this.onEcbValidationRaised(immediate);
  }
}
