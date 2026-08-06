/**
 * Validation members for EcbQuestionsComponent. Merge into the existing class.
 *
 * <p>Messages are split by scope rather than dumped in one banner: a rule naming a field belongs
 * beside that field, where the analyst is looking, and only the form-wide ones head the section.
 */

  /** Set from the parent — [messages]="ecbMessages()". Empty until an explicit save. */
  @Input() messages: ValidationMessageView[] = [];

  readonly Severity = Severity;

  /** Authored with blank keys, so it speaks for the section rather than for one question. */
  get formWideMessages(): ValidationMessageView[] {
    return this.messages.filter(message => !message.questionKey && !message.fieldKey);
  }

  messagesFor(questionKey: string): ValidationMessageView[] {
    return this.messages.filter(message => message.questionKey === questionKey && !message.fieldKey);
  }

  messagesForField(questionKey: string, fieldKey: string): ValidationMessageView[] {
    return this.messages.filter(
      message => message.questionKey === questionKey && message.fieldKey === fieldKey);
  }

  /** ERROR blocks the save; WARNING is advisory and styled differently. */
  isBlocking(message: ValidationMessageView): boolean {
    return message.severity === Severity.ERROR;
  }
