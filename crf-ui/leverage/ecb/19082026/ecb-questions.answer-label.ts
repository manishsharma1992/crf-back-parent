/*
 * ecb-questions.component.ts
 *
 * ADD:    answerLabel()
 * REMOVE: levelOfLeveragedCalculated(), and the LevelOfLeveraged import
 * EDIT:   the COMPUTED case in the template
 */

/**
 * What a stored answer reads as on screen.
 *
 * <p>An answer is a CODE — {@code BUSINESS_GROUP}, {@code YES}, {@code MATERIAL_MODIFICATION}. The
 * words next to it are authored beside the code, in the Options column, in both languages. So the
 * label is a lookup, not a transformation: find the option this answer chose and render its label.
 *
 * <p><b>This replaces {@code levelOfLeveragedCalculated} and the {@code LevelOfLeveraged} enum.</b>
 * That enum spelled BUSINESS GROUP out in TypeScript, which meant the wording existed in two
 * places — the workbook and the client — with nothing keeping them in step. The day the BA edits a
 * display value, the enum disagrees with the database and nothing fails: the screen simply shows
 * last month's words. It also only ever covered ONE question, so Q-Q01 and Q-Q02 rendered raw YES
 * and NO while Q-S04 rendered nicely, for no reason a reader could see.
 *
 * <p>Nothing here names a question or a value, so it works for every COMPUTED question and for any
 * the BA adds later.
 *
 * <p>Falls back to the raw answer, which is the right behaviour for a COMPUTED question that
 * declares no options at all — Q-S05 derives the parent company and its answer is already a
 * rendered string, {@code "12345678 - ACME HOLDING SA"}, with no option to look up.
 */
answerLabel(question: QuestionView): string {
  const answer = question.answer;
  if (!answer) {
    return '-';
  }
  const chosen = (question.options ?? []).find(option => option.value === answer);
  return chosen ? this.labelText(chosen.label) : answer;
}
