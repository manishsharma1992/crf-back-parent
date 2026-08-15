import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import {
  JustificationDialogComponent,
  JustificationDialogData,
} from './justification-dialog.component';

/**
 * The pop-in edits COPIES and returns them. That is the whole design: it lets Cancel mean
 * untouched, and it lets Validate refuse a half-filled justification while the analyst is still
 * looking at the fields rather than at a failed save.
 */
describe('JustificationDialogComponent', () => {
  let fixture: ComponentFixture<JustificationDialogComponent>;
  let component: JustificationDialogComponent;
  let dialogRef: { close: jest.Mock };

  const DEFAULT: JustificationDialogData = {
    fieldLabel: 'Reported LTM adjustment',
    wording: null,
    comment: null,
    hasValue: false,
  };

  async function open(data: Partial<JustificationDialogData> = {}): Promise<void> {
    dialogRef = { close: jest.fn() };
    TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [JustificationDialogComponent],
      providers: [
        { provide: MAT_DIALOG_DATA, useValue: { ...DEFAULT, ...data } },
        { provide: MatDialogRef, useValue: dialogRef },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(JustificationDialogComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  }

  describe('opening', () => {
    it('starts empty for a box that has never been justified', async () => {
      await open();

      expect(component.form.controls.wording.value).toBe('');
      expect(component.form.controls.comment.value).toBe('');
    });

    /** Re-opening must show what was written, or the analyst cannot correct a typo. */
    it('shows what the box already holds', async () => {
      await open({ wording: 'Perimeter change', comment: 'Acquisition completed in March' });

      expect(component.form.controls.wording.value).toBe('Perimeter change');
      expect(component.form.controls.comment.value).toBe('Acquisition completed in March');
    });
  });

  describe('validate', () => {
    it('is refused until both halves are filled', async () => {
      await open({ wording: 'Perimeter change' });

      expect(component.canValidate).toBe(false);
    });

    /** Whitespace names nothing and explains nothing. */
    it('is refused when a half holds only whitespace', async () => {
      await open({ wording: '   ', comment: 'Acquisition' });

      expect(component.canValidate).toBe(false);
    });

    it('closes with both halves trimmed', async () => {
      await open({ wording: '  Perimeter change  ', comment: '  Acquisition  ' });

      component.onValidate();

      expect(dialogRef.close).toHaveBeenCalledWith({
        action: 'validate',
        wording: 'Perimeter change',
        comment: 'Acquisition',
      });
    });

    it('does nothing when called while incomplete', async () => {
      await open({ wording: 'Perimeter change' });

      component.onValidate();

      expect(dialogRef.close).not.toHaveBeenCalled();
    });
  });

  describe('dismiss and cancel', () => {
    it('dismiss asks the caller to clear everything', async () => {
      await open({ wording: 'Perimeter change', comment: 'Acquisition', hasValue: true });

      component.onDismiss();

      expect(dialogRef.close).toHaveBeenCalledWith({ action: 'dismiss' });
    });

    /**
     * Undefined, not an action: the caller reads that as "nothing happened". Because the dialog
     * edits copies, there is genuinely nothing to roll back.
     */
    it('cancel closes with no result', async () => {
      await open({ wording: 'Perimeter change' });

      component.onCancel();

      expect(dialogRef.close).toHaveBeenCalledWith();
    });
  });

  describe('counters', () => {
    it('report the length the server will measure', async () => {
      await open({ wording: 'abc', comment: 'de' });

      expect(component.wordingLength).toBe(3);
      expect(component.commentLength).toBe(2);
      expect(component.wordingMax).toBe(40);
      expect(component.commentMax).toBe(100);
    });
  });
});
