/**
 * The ECB members of LeverageAnalysisComponent. Merge into the existing class.
 *
 * Mirrors the preliminary flow exactly: the child emits an answer, the PARENT re-traverses, the
 * parent reconciles its form group, and the parent persists once the section completes. The child
 * owns no state and calls no endpoint — same contract preliminary-questions already has.
 *
 * ECB differs from preliminary in two ways that show up here:
 *  - it has CHECKLIST and DATA_ENTRY questions, so a control may be a nested FormGroup keyed by
 *    sub-key, and the payload has to flatten back to dotted keys;
 *  - its traversal is analysis-bound rather than stateless, because Q01 prefills from FED and only
 *    the backend can read FED's stored answers.
 */

  // ------------------------------------------------------------------ form group

  /** Sibling of preliminaryForm inside leverageLendingForm — add it in initLeverageLendingForm. */
  get ecbForm(): FormGroup {
    return this.leverageLendingForm.get('ecbForm') as FormGroup;
  }

  // ------------------------------------------------------------------ traversal

  /**
   * Child emits (value, questionKey); parent re-traverses. THE only ECB traversal caller.
   *
   * <p>`questionKey` may be dotted for a checklist item or a data-entry box — `FormGroup.get`
   * resolves a dotted path natively, so one patch handles both flat and nested.
   */
  onEcbAnswer(value: string, questionKey: string): void {
    this.alertBoxService.clearAlertsByAnchorId('errors', 'leverageAnalysisEcbFragment');
    this.ecbForm.get(questionKey)?.patchValue(value, { emitEvent: false });

    this.leverageLendingService
      .findFormState(LeverageFormType.ECB, this.analysisUid!, {
        version: this.ecbVersion,
        answers: this.collectEcbAnswers(),
      })
      .subscribe(state => this.applyEcbState(state));
  }

  // ------------------------------------------------------------------ state

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

    // Persist only once the section is finished — same rule preliminary follows. An in-progress
    // walk is recomputed from answers on every call, so there is nothing to lose by not saving it.
    if (state.status === Status.COMPLETED) {
      this.persistEcb();
    }
  }

  private syncEcbQuestion(group: FormGroup, question: QuestionView): void {
    const existing = group.get(question.key);
    if (!existing) {
      group.addControl(question.key, this.buildEcbControl(question), { emitEvent: false });
      return;
    }
    if (existing instanceof FormGroup) {
      this.syncEcbSubGroup(existing, question);
      return;
    }
    // Hydrated value wins over the server echo, so a keystroke in flight is not overwritten.
    if (question.answer != null && existing.value == null) {
      existing.setValue(question.answer, { emitEvent: false });
    }
  }

  /**
   * Sub-answers are reconciled rather than rebuilt, because the backend sends back values the
   * analyst never typed — a checklist item settled to NOT_APPLICABLE by a YES elsewhere — and the
   * screen has to show what was decided, not what was entered.
   */
  private syncEcbSubGroup(group: FormGroup, question: QuestionView): void {
    for (const [subKey, value] of Object.entries(question.subAnswers ?? {})) {
      const control = group.get(subKey);
      if (control && control.value !== value) {
        control.setValue(value, { emitEvent: false });
      }
    }
  }

  private buildEcbControl(question: QuestionView): FormGroup | FormControl {
    if (question.type === QuestionType.CHECKLIST) {
      return this.buildEcbSubGroup(question, (question.items ?? []).map(item => item.key));
    }
    if (question.type === QuestionType.DATA_ENTRY) {
      return this.buildEcbSubGroup(question, (question.fields ?? []).map(field => field.key));
    }
    return this.formBuilder.control(
      { value: question.answer ?? null, disabled: this.isEcbReadOnly(question) },
      question.mandatory ? [Validators.required] : [],
    );
  }

  private buildEcbSubGroup(question: QuestionView, subKeys: string[]): FormGroup {
    const readOnly = this.isEcbReadOnly(question);
    return this.formBuilder.group(
      Object.fromEntries(subKeys.map(subKey => [
        subKey,
        this.formBuilder.control({
          value: question.subAnswers?.[subKey] ?? null,
          disabled: readOnly,
        }),
      ])),
    );
  }

  /** A computed value belongs to the tree; a prefilled one belongs to the FED form. */
  private isEcbReadOnly(question: QuestionView): boolean {
    return question.computed || !!question.prefillFrom || question.editable === false;
  }

  // ------------------------------------------------------------------ payload

  /**
   * Flattens to the dotted wire format — `Q-B01A.sovereign` — dropping null/blank values, since a
   * blank answer means "unanswered" and the engine treats an absent key that way.
   *
   * <p>getRawValue rather than value: a prefilled or computed control is disabled, and it still has
   * to round trip rather than vanish from the payload.
   */
  private collectEcbAnswers(): Record<string, string> {
    const cleaned: Record<string, string> = {};
    const raw = this.ecbForm.getRawValue() as Record<string, unknown>;

    for (const [questionKey, value] of Object.entries(raw)) {
      if (value !== null && typeof value === 'object') {
        for (const [subKey, subValue] of Object.entries(value as Record<string, unknown>)) {
          this.putEcbAnswer(cleaned, `${questionKey}.${subKey}`, subValue);
        }
      } else {
        this.putEcbAnswer(cleaned, questionKey, value);
      }
    }
    return cleaned;
  }

  private putEcbAnswer(target: Record<string, string>, key: string, value: unknown): void {
    const text = value === null || value === undefined ? '' : String(value).trim();
    if (text !== '') {
      target[key] = text;
    }
  }

  // ------------------------------------------------------------------ persistence

  persistEcb(): void {
    if (!this.analysisUid || this.persisting) {
      return;
    }
    this.leverageLendingService._persisting.next(true);
    this.leverageLendingService
      .saveFormAnswers(LeverageFormType.ECB, this.analysisUid, this.collectEcbAnswers(), this.locale)
      .pipe(finalize(() => this.leverageLendingService._persisting.next(false)))
      .subscribe({
        next: state => this.leverageLendingService.setFormState(LeverageFormType.ECB, state),
        error: () => { /* surface the failure so the analyst can retry */ },
      });
  }

  // ------------------------------------------------------------------ reload

  /** Call from reload(), after the preliminary state has been applied. */
  private reloadEcb(analysisUid: string): void {
    if (!this.ecbRequired()) {
      return;
    }
    this.leverageLendingService
      .getFormState(LeverageFormType.ECB, analysisUid)
      .subscribe(state => this.applyEcbState(state));
  }
