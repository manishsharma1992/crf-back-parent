/*
 * form-snapshot.component.ts — the panel half. sections()/sectionFor keep their shape; toPanel is
 * replaced and two constants are added.
 */

/** Where a panel field lands in the designed card. */
type PanelSlot = 'date' | 'headline' | 'ratio';

/**
 * Which authored field fills which slot.
 *
 * <p><b>Yes, this names fields — and it is the only place that does.</b> A designed card has named
 * positions: "since «date»", a headline, "ratio: «value»". Nothing in {@code PanelSnapshot} says
 * which field is a date, because the workbook has no column for it, so somewhere has to decide.
 * Better here, in one readable map, than spread through a template as
 * {@code panel.values['leverageDate']}.
 *
 * <p><b>The `other` fallback is what makes this safe.</b> A field not claimed here still renders,
 * as a plain label and value beneath the card. So if the BA adds a panel field, it appears —
 * unstyled, but visible — instead of being silently dropped, which is the failure mode that would
 * otherwise take months to notice.
 *
 * <p>Panel field names do not match flag keys — {@code leveragedFlag} here versus
 * {@code ecbLeveragedFlag} in the flags catalogue — which is the same mismatch the adapter's
 * DECODED_BY map exists for. Aligning them in the workbook would remove both.
 */
const PANEL_SLOTS: Readonly<Record<string, PanelSlot>> = {
  leverageDate: 'date',
  covenantStructure: 'headline',
  leveragedFlag: 'headline',
  leverageRatio: 'ratio',
  ecbLeverageRatio: 'ratio',
  fedLeverageRatio: 'ratio',
};

export interface SnapshotPanel {
  key: string;
  title: string;
  /** Rendered as "since «date»". Absent when the panel does not carry one. */
  date: string | null;
  /** The bold conclusion lines, in the order the workbook declared them. */
  headlines: string[];
  /** Rendered as "ratio: «value»". */
  ratio: string | null;
  /** Everything the card has no designed position for. */
  other: { label: string; value: string }[];
}

/**
 * Sorts a panel's fields into the card's positions.
 *
 * <p>Driven by {@code fieldOrder}, not by the keys of {@code values}: the order is authored and the
 * map is only a lookup. That matters for {@code headlines}, where two fields sit on one line and
 * the workbook decides which comes first.
 *
 * <p>An absent or empty value is dropped rather than rendered as a dash. A dash is right for a
 * TABLE, where the row exists and is empty; here the row does not exist at all, and "since —" reads
 * as a fault.
 */
private toPanel(panel: PanelSnapshot): SnapshotPanel {
  const built: SnapshotPanel = {
    key: panel.panelKey,
    title: this.labelText(panel.title),
    date: null,
    headlines: [],
    ratio: null,
    other: [],
  };

  for (const field of panel.fieldOrder ?? []) {
    const raw = panel.values?.[field];
    if (raw === null || raw === undefined || raw === '') {
      continue;
    }
    const value = formatDisplayValue(raw, this.locale());

    switch (PANEL_SLOTS[field]) {
      case 'date':
        built.date = value;
        break;
      case 'headline':
        built.headlines.push(value);
        break;
      case 'ratio':
        built.ratio = value;
        break;
      default:
        built.other.push({ label: field, value });
    }
  }
  return built;
}

/*
 * ============================================================ sectionFor
 *
 * One change: a section is now kept when it has EITHER flags or panels, and both are rendered.
 * The old "panels OR flags" rule came from the first reading of the requirement; the designed
 * screen shows FED with its NOT REQUIRED flag AND its RMPM card, so they are not alternatives.
 *
 *     if (!panels.length && !flags.length) {
 *       return null;
 *     }
 *     return { formType, panels, flags };
 *
 * unchanged — it already allowed both. Only the TEMPLATE treated them as exclusive.
 */
