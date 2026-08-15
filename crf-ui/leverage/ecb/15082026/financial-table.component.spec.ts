import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormBuilder, FormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { of } from 'rxjs';
import {
  DataField,
  DataTypeField,
  QuestionType,
  QuestionView,
} from '@lazyloaded/counterparty/model/leverage-lending/form-state.model';
import { FinancialTableComponent } from './financial-table.component';
import { JustificationDialogResult } from './justification-dialog/justification-dialog.component';

/**
 * The financial table renders eighteen boxes from the workbook and writes three answer keys per
 * adjustment. The cases worth pinning are the ones where getting it wrong is invisible: a
 * calculated box that becomes editable, a hidden box that disappears from the record rather than
 * from the screen, and a justification that costs two round trips instead of one.
 */
describe('FinancialTableComponent', () => {
  let fixture: ComponentFixture<FinancialTableComponent>;
  let component: FinancialTableComponent;
  let dialog: { open: jest.Mock };
  let group: FormGroup;

  // ------------------------------------------------------------------ fixtures

  const label = (text: string) => ({ en: text, fr: text });

  function field(key: string, overrides: Partial<DataField> = {}): DataField {
    return {
      key,
      group: 'ECB Leverage Ratio',
      label: label(key),
      note: null as never,
      type: DataTypeField.NUMERIC,
      mandatory: false,
      editable: false,
      derivedFrom: null as never,
      formula: null as never,
      fillsFlag: null as never,
      ...overrides,
    } as DataField;
  }

  const EDITABLE = field('reportedLtmAdjustment', { editable: true });
  const SOURCE = field('ebitda', { derivedFrom: 'FINANCIALS/ebitda' });
  const CALCULATED = field('adjustedEbitda', { derivedFrom: 'CALC/adjustedEbitda' });
  const HIDDEN = field('netDebt', { derivedFrom: 'FINANCIALS/netDebt', visible: false });
  const OTHER_GROUP = field('committedUndrawnDebt', { editable: true, group: 'Gross Debt' });

  function question(fields: DataField[], subAnswers: Record<string, string> = {}): QuestionView {
    return {
      key: 'Q-F01',
      type: QuestionType.DATA_ENTRY,
      fields,
      subAnswers,
      items: [],
      options: [],
    } as unknown as QuestionView;
  }

  /** Mirrors what the parent's buildDataEntryGroup produces, including the dotted sub-keys. */
  function groupFor(view: QuestionView): FormGroup {
    const fb = new FormBuilder();
    const controls: Record<string, unknown> = {};
    for (const f of view.fields ?? []) {
      controls[f.key] = fb.control({
        value: view.subAnswers?.[f.key] ?? null,
        disabled: !f.editable,
      });
      if (f.editable) {
        controls[`${f.key}.wording`] = fb.control(view.subAnswers?.[`${f.key}.wording`] ?? null);
        controls[`${f.key}.comment`] = fb.control(view.subAnswers?.[`${f.key}.comment`] ?? null);
      }
    }
    return fb.group(controls);
  }

  function render(view: QuestionView): void {
    group = groupFor(view);
    fixture.componentRef.setInput('question', view);
    fixture.componentRef.setInput('group', group);
    fixture.componentRef.setInput('locale', 'en');
    fixture.detectChanges();
  }

  beforeEach(async () => {
    dialog = { open: jest.fn() };
    await TestBed.configureTestingModule({
      imports: [FinancialTableComponent],
      providers: [{ provide: MatDialog, useValue: dialog }],
    }).compileComponents();

    fixture = TestBed.createComponent(FinancialTableComponent);
    component = fixture.componentInstance;
  });

  // ================================================================ rows

  describe('rows', () => {
    it('keeps the order the workbook declared', () => {
      render(question([SOURCE, EDITABLE, CALCULATED]));

      const keys = component.groups().flatMap(g => g.rows.map(r => r.field.key));
      expect(keys).toEqual(['ebitda', 'reportedLtmAdjustment', 'adjustedEbitda']);
    });

    it('collects rows under the heading from the Group column', () => {
      render(question([SOURCE, OTHER_GROUP]));

      expect(component.groups().map(g => g.name)).toEqual(['ECB Leverage Ratio', 'Gross Debt']);
    });

    /**
     * netDebt feeds Total Net Funded Debt and is frozen with the answer, but has no row. Dropping
     * it in the template instead would leave a gap in the group it belongs to.
     */
    it('omits a hidden box from the screen while leaving it in the form', () => {
      render(question([SOURCE, HIDDEN]));

      const keys = component.groups().flatMap(g => g.rows.map(r => r.field.key));
      expect(keys).not.toContain('netDebt');
      expect(group.controls['netDebt']).toBeDefined();
    });

    it('marks only the editable boxes as editable', () => {
      render(question([SOURCE, EDITABLE, CALCULATED]));

      const rows = component.groups().flatMap(g => g.rows);
      expect(rows.filter(r => r.editable).map(r => r.field.key)).toEqual(['reportedLtmAdjustment']);
    });
  });

  // ================================================================ display

  describe('display', () => {
    it('shows a calculated value at two decimals', () => {
      render(question([CALCULATED], { adjustedEbitda: '3528.0' }));

      expect(component.groups()[0].rows[0].display).toBe('3,528.00');
    });

    /**
     * The stored value keeps twenty-eight decimals and the routing reads that. Rounding here is
     * presentation only — a ratio that displays as 4.00 must still be below four where it counts.
     */
    it('rounds for the screen without touching what is stored', () => {
      render(question([CALCULATED], { adjustedEbitda: '3.9994' }));

      expect(component.groups()[0].rows[0].display).toBe('4.00');
      expect(group.controls['adjustedEbitda'].value).toBe('3.9994');
    });

    /** A blocked analysis withholds every calculated figure; dashes are the intended signal. */
    it('shows a dash for an absent value rather than a blank', () => {
      render(question([CALCULATED]));

      expect(component.groups()[0].rows[0].display).toBe('-');
    });

    it('shows unparseable text rather than silently blanking it', () => {
      render(question([CALCULATED], { adjustedEbitda: 'n/a' }));

      expect(component.groups()[0].rows[0].display).toBe('n/a');
    });
  });

  // ================================================================ answering

  describe('answering', () => {
    it('emits the dotted key for an amount', () => {
      render(question([EDITABLE]));
      const emitted: unknown[] = [];
      component.answered.subscribe(event => emitted.push(event));

      component.onAmountChanged(EDITABLE, '123');

      expect(emitted).toEqual([{ value: '123', questionKey: 'Q-F01.reportedLtmAdjustment' }]);
    });

    it('emits an empty string for a cleared amount, so the key is treated as unanswered', () => {
      render(question([EDITABLE]));
      const emitted: { value: string }[] = [];
      component.answered.subscribe(event => emitted.push(event));

      component.onAmountChanged(EDITABLE, null);

      expect(emitted[0].value).toBe('');
    });
  });

  // ================================================================ justification

  describe('justification', () => {
    function dialogReturns(result: JustificationDialogResult | undefined): void {
      dialog.open.mockReturnValue({ afterClosed: () => of(result) });
    }

    function openFirstRow(): void {
      component.openJustification(component.groups()[0].rows[0]);
    }

    it('flags a figure that has no justification', () => {
      render(question([EDITABLE], { reportedLtmAdjustment: '123' }));

      expect(component.groups()[0].rows[0].needsJustification).toBe(true);
    });

    it('does not flag an untouched box — absent is a legal state', () => {
      render(question([EDITABLE]));

      expect(component.groups()[0].rows[0].needsJustification).toBe(false);
    });

    it('treats a wording without a comment as unjustified', () => {
      render(question([EDITABLE], {
        reportedLtmAdjustment: '123',
        'reportedLtmAdjustment.wording': 'Perimeter',
      }));

      expect(component.groups()[0].rows[0].justified).toBe(false);
      expect(component.groups()[0].rows[0].needsJustification).toBe(true);
    });

    it('hands the dialog what the box currently holds', () => {
      render(question([EDITABLE], {
        reportedLtmAdjustment: '123',
        'reportedLtmAdjustment.wording': 'Perimeter',
        'reportedLtmAdjustment.comment': 'Acquisition',
      }));
      dialogReturns(undefined);

      openFirstRow();

      expect(dialog.open.mock.calls[0][1].data).toEqual({
        fieldLabel: 'reportedLtmAdjustment',
        wording: 'Perimeter',
        comment: 'Acquisition',
        hasValue: true,
      });
    });

    /**
     * THE bug this shape exists to prevent. Emitting each half separately put two traversals in
     * flight, and whenever the first response landed second it cleared the half the second had
     * just written — the analyst then saved a wording with no comment.
     */
    it('writes both halves and emits ONCE', () => {
      render(question([EDITABLE], { reportedLtmAdjustment: '123' }));
      dialogReturns({ action: 'validate', wording: 'Perimeter', comment: 'Acquisition' });
      const emitted: unknown[] = [];
      component.answered.subscribe(event => emitted.push(event));

      openFirstRow();

      expect(group.controls['reportedLtmAdjustment.wording'].value).toBe('Perimeter');
      expect(group.controls['reportedLtmAdjustment.comment'].value).toBe('Acquisition');
      expect(emitted).toEqual([{ value: '123', questionKey: 'Q-F01.reportedLtmAdjustment' }]);
    });

    /** Dismiss is the only route back to an empty box once one has been typed into. */
    it('clears the figure and both halves on dismiss', () => {
      render(question([EDITABLE], {
        reportedLtmAdjustment: '123',
        'reportedLtmAdjustment.wording': 'Perimeter',
        'reportedLtmAdjustment.comment': 'Acquisition',
      }));
      dialogReturns({ action: 'dismiss' });

      openFirstRow();

      expect(group.controls['reportedLtmAdjustment'].value).toBeNull();
      expect(group.controls['reportedLtmAdjustment.wording'].value).toBeNull();
      expect(group.controls['reportedLtmAdjustment.comment'].value).toBeNull();
    });

    it('writes nothing on cancel', () => {
      render(question([EDITABLE], {
        reportedLtmAdjustment: '123',
        'reportedLtmAdjustment.wording': 'Perimeter',
      }));
      dialogReturns(undefined);
      const emitted: unknown[] = [];
      component.answered.subscribe(event => emitted.push(event));

      openFirstRow();

      expect(group.controls['reportedLtmAdjustment.wording'].value).toBe('Perimeter');
      expect(emitted).toEqual([]);
    });

    it('tells the dialog there is nothing to dismiss when the box is empty', () => {
      render(question([EDITABLE]));
      dialogReturns(undefined);

      openFirstRow();

      expect(dialog.open.mock.calls[0][1].data.hasValue).toBe(false);
    });
  });
});
