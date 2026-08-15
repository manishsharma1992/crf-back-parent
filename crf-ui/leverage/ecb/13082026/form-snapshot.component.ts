import { Component, computed, input } from '@angular/core';
import {
  FlagView,
  FormState,
  LeverageFormType,
  LocalizedLabel,
  PanelSnapshot,
} from '@lazyloaded/counterparty/model/leverage-lending/form-state.model';
import { MaterialModule } from '@shared/modules/MaterialModule';

/** One form's conclusions, ready to render. */
export interface SnapshotSection {
  formType: LeverageFormType;
  /** Resolved panels, when the walk triggered any. */
  panels: SnapshotPanel[];
  /** The flags the walk set. Rendered when there are no panels. */
  flags: FlagView[];
}

export interface SnapshotPanel {
  key: string;
  title: string;
  rows: { label: string; value: string }[];
}

/**
 * The read-only summary beside the form: what the walk has concluded so far.
 *
 * <p>Serves both sections. FED and ECB differ in their questions, not in the shape of their
 * conclusions — each sets flags, and each may trigger info panels — so one component covers both
 * and a third form would need nothing new.
 *
 * <p><b>Panels or flags, decided by what arrived.</b> An info panel is authored with a trigger
 * ({@code whenFlagKey} / {@code whenFlagValue}), and the backend has already evaluated that:
 * {@code state.infoPanels} contains ONLY the panels whose trigger matched. So the presence of a
 * panel IS the condition, and the component needs no rule of its own.
 *
 * <p>That is why nothing here names {@code ecbLeveragedFlag} or {@code INR}. Writing
 * "if the flag is INR, show panels" would re-implement in TypeScript a rule the workbook already
 * authors — and it would be wrong for FED, which sets a different flag key and triggers different
 * panels. When Sushmitha adds a panel on a new trigger, this file does not change.
 */
@Component({
  selector: 'bnpp-form-snapshot',
  templateUrl: './form-snapshot.component.html',
  styleUrls: ['./form-snapshot.component.scss'],
  imports: [MaterialModule],
  standalone: true,
})
export class FormSnapshotComponent {
  ecbFormState = input<FormState | null>(null);
  fedFormState = input<FormState | null>(null);
  locale = input.required<string>();

  /** What an absent panel value reads as — the convention the financial table uses too. */
  private static readonly ABSENT = '-';

  /**
   * FED first, then ECB — the order the analyst filled them, and the order
   * {@code SECTION_PRIORITY} declares.
   *
   * <p>A section with neither panels nor flags is dropped rather than rendered empty: before the
   * walk reaches a terminal branch there is genuinely nothing to summarise, and an empty card
   * reads as a section that failed to load.
   */
  readonly sections = computed<SnapshotSection[]>(() =>
    [
      this.sectionFor(LeverageFormType.FED, this.fedFormState()),
      this.sectionFor(LeverageFormType.ECB, this.ecbFormState()),
    ].filter((section): section is SnapshotSection => section !== null),
  );

  private sectionFor(formType: LeverageFormType, state: FormState | null): SnapshotSection | null {
    if (!state) {
      return null;
    }
    const panels = (state.infoPanels ?? []).map(panel => this.toPanel(panel));
    const flags = state.flagViews ?? [];

    if (!panels.length && !flags.length) {
      return null;
    }
    return { formType, panels, flags };
  }

  /**
   * `fieldOrder` drives the rows, not the keys of `values`.
   *
   * <p>The order is authored in the workbook and the map is only a lookup — iterating the map
   * would put the rows in whatever order they were resolved in, which is not a decision anyone
   * made.
   */
  private toPanel(panel: PanelSnapshot): SnapshotPanel {
    return {
      key: panel.panelKey,
      title: this.labelText(panel.title),
      rows: (panel.fieldOrder ?? []).map(field => ({
        label: field,
        value: this.valueOf(panel, field),
      })),
    };
  }

  private valueOf(panel: PanelSnapshot, field: string): string {
    const value = panel.values?.[field];
    return value === null || value === undefined || value === ''
      ? FormSnapshotComponent.ABSENT
      : value;
  }

  labelText(label: LocalizedLabel | null | undefined): string {
    if (!label) {
      return '';
    }
    return (this.isFrench() ? label.fr : label.en) ?? '';
  }

  private isFrench(): boolean {
    return this.locale()?.toUpperCase() === 'FR';
  }
}