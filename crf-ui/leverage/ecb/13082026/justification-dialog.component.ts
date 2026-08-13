import { Component, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import {
  JUSTIFICATION_COMMENT_MAX,
  JUSTIFICATION_WORDING_MAX,
} from '@lazyloaded/counterparty/model/leverage-lending/form-state.model';
import { MaterialModule } from '@shared/modules/MaterialModule';

/** What the table hands the dialog when it opens. */
export interface JustificationDialogData {
  /** The adjustment's label, so the dialog can say which box it is about. */
  fieldLabel: string;
  wording: string | null;
  comment: string | null;
  /** True when the box currently holds a figure — DISMISS has nothing to clear otherwise. */
  hasValue: boolean;
}

/**
 * What the dialog hands back. `undefined` means CANCEL: the analyst changed nothing and the box is
 * left exactly as it was.
 */
export type JustificationDialogResult =
  | { action: 'validate'; wording: string; comment: string }
  | { action: 'dismiss' };

/**
 * Names and explains one adjustment.
 *
 * <p><b>Its controls are its own, not the table's.</b> The two halves live on the parent form
 * group as `ebitda.wording` and `ebitda.comment`, but binding those directly here would mean the
 * analyst's half-typed text was already on the form — and since CANCEL must leave the box
 * untouched, there would be nothing to cancel back to. The dialog edits copies and returns them;
 * only VALIDATE and DISMISS write.
 *
 * <p>That is also what lets VALIDATE require both halves. The backend treats a box as either fully
 * absent or fully complete, so a wording without a comment is a state the save refuses; refusing
 * it here means the analyst is told while they are still looking at the field.
 */
@Component({
  selector: 'bnpp-justification-dialog',
  templateUrl: './justification-dialog.component.html',
  styleUrls: ['./justification-dialog.component.scss'],
  imports: [MaterialModule, ReactiveFormsModule],
  standalone: true,
})
export class JustificationDialogComponent {
  readonly data = inject<JustificationDialogData>(MAT_DIALOG_DATA);

  private readonly dialogRef =
    inject<MatDialogRef<JustificationDialogComponent, JustificationDialogResult>>(MatDialogRef);

  readonly wordingMax = JUSTIFICATION_WORDING_MAX;
  readonly commentMax = JUSTIFICATION_COMMENT_MAX;

  readonly form = new FormGroup({
    wording: new FormControl<string>(this.data.wording ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(JUSTIFICATION_WORDING_MAX)],
    }),
    comment: new FormControl<string>(this.data.comment ?? '', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(JUSTIFICATION_COMMENT_MAX)],
    }),
  });

  /** Live counts for the two counters, so they agree with what the server will accept. */
  get wordingLength(): number {
    return this.form.controls.wording.value.length;
  }

  get commentLength(): number {
    return this.form.controls.comment.value.length;
  }

  /** Whitespace is not a reason, so the check is on trimmed text rather than on validity alone. */
  get canValidate(): boolean {
    return (
      this.form.controls.wording.value.trim() !== '' && this.form.controls.comment.value.trim() !== ''
    );
  }

  onValidate(): void {
    if (!this.canValidate) {
      return;
    }
    this.dialogRef.close({
      action: 'validate',
      wording: this.form.controls.wording.value.trim(),
      comment: this.form.controls.comment.value.trim(),
    });
  }

  /** Clears the figure and both halves together — the only way back to an empty box. */
  onDismiss(): void {
    this.dialogRef.close({ action: 'dismiss' });
  }

  onCancel(): void {
    this.dialogRef.close();
  }
}
