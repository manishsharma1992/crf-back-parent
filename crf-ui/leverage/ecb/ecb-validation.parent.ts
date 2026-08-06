/**
 * Validation members for LeverageAnalysisComponent. Merge into the existing class.
 *
 * <p>The hardcoded ECB_CHECKLIST_MANDATORY constant goes: the backend now says which rules fire and
 * in what words, so the client renders whatever it is handed rather than naming a key it has to
 * keep in step with the workbook.
 *
 * <p>Messages are computed on EVERY response, including autosaves, but only DISPLAYED after an
 * explicit save. Nagging an analyst mid-checklist about a checklist they are still filling is
 * exactly the noise that trains people to ignore alerts.
 */

  /** Whether the analyst has asked to save and therefore earned the right to be told what is wrong. */
  readonly ecbValidationVisible = signal<boolean>(false);

  readonly ecbMessages = computed<ValidationMessageView[]>(() =>
    this.ecbValidationVisible()
      ? this.leverageLendingService.ecbFormState()?.validationMessages ?? []
      : []);

  /** Explicit save. Unlike autosave, this reports rather than declining silently. */
  saveEcbSection(): void {
    this.ecbValidationVisible.set(true);

    if (!this.shouldPersistEcb()) {
      // The state in hand already carries the fired rules — the last traversal computed them, so
      // there is nothing to ask the backend for before showing them.
      return;
    }
    this.persistEcb();
  }

  /**
   * Called from onEcbAnswer. Answering is the analyst acting on the feedback, so the errors clear
   * and only reappear if they ask to save again with the problem unfixed.
   */
  private clearEcbValidation(): void {
    this.ecbValidationVisible.set(false);
    this.alertBoxService.clearAlertsByAnchorId('errors', 'leverageAnalysisEcbFragment');
  }

  onEcbAnswer(value: string, questionKey: string): void {
    this.clearEcbValidation();
    this.ecbForm.get(questionKey)?.patchValue(value, { emitEvent: false });

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
        this.ecbAnswered.next();   // debounced; persistEcb decides whether it may write
      });
  }
