import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, input } '@angular/core';
import {
    formatDisplayValue,
    FormState,
    LeverageFormType,
    LocalizedLabel,
    PANEL_SLOTS,
    PanelSnapshot,
    SnapshotPanel,
    SnapshotSection
} from "@lazyloaded/counterparty/model/leverage-lending/form-state.model";
import { AnalysisValidationState } from '@lazyloaded/counterparty/model/leverage-lending/leverage-lending.model';
import { MaterialModule } from '@shared/modules/MaterialModule';
import { DurationPipe } from '@shared/pipes/duration/duration.pipe';

@Component({
    selector: 'bnpp-form-snapshot',
    templateUrl: './form-snapshot.component.html',
    styleUrls: ['./form-snapshot.component.scss'],
    imports: [ DurationPipe, NgTemplateOutlet, MaterialModule ],
    standalone: true,
})
export class FormSnapshotComponent {
    ecbFormState = input.required<FormState | null>();
    fedFormState = input.required<FormState | null>();
    locale = input.required<string>();
    analysisState = input.required<AnalysisValidationState | null>();

    /**
     * FED first, then ECB - the order the analyst filled them, and the order
     * {@code SECTION_PRIORITY} declares.
     *
     * <p> A section with neither panels nor flags is dropped rather than rendered empty: before the
     * walk reaches a terminal branch there is genuinely nothing to summarise, and an empty card
     * reads as a section that failed to load.
     */
    readonly sections = computed<SnapshotSection[]>(() =>
        [this.sectionFor(LeverageFormType.FED, this.fedFormState()), this.sectionFor(LeverageFormType.ECB, this.ecbFormState())].filter(
            (section): section is SnapshotSection => section !== null,
        ),
    );

    readonly isValidated = computed(() => this.analysisState()?.status === 'VALIDATED');

    /**
     * One addition: flag values are formatted before they reach the template.
     *
     * <p>`ecbLeverageRatio` is a NUMBER flag, so it has no value set and no authored label - the
     * backend hands back the stored figure verbatim, all twenty-eight decimals of it. Coded flags are
     * untouched by this, because `formatDisplayValue` leaves anything non-numeric alone: INR stays INR
     * and BUSINESS GROUP status BUSINESS GROUP.
     */
    private sectionFor(formType: LeverageFormType, state: FormState | null): SnapshotSection | null {
        if(!state) {
            return null;
        }
        const panels = (state.infoPanels ?? []).map(panel => this.toPanel(panel));
        const flags = (state.flagViews ?? []).map(flag => ({
            ...flag,
            displayValue: formatDisplayValue(flag.displayValue, this.locale()),
        }));

        if(!panels.length && !flags.length) {
            return null;
        }
        return { formType, panels, flags };
    }

    /**
     * Sorts a panel's fields into the card's positions.
     *
     * <p>Driven by {@code fieldOrder}, not by the keys of {@code values}: the order is authored and the
     * map is only lookup. That matters for {@code headlines}, where two fields sit on one line and
     * the workbook decides which comes first.
     *
     * <p> An absent or empty value is dropped rather than rendered as a dash. A dash is right for a TABLE,
     * where the row exists and is empty; here the row does not exist at all, and "since -" reads
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
        }

        for (const field of panel.fieldOrder ?? []) {
            const raw = panel.values?.[field];
            if(raw === null || raw === undefined || raw === '') {
                continue;
            }
            const value = formatDisplayValue(raw, this.locale());
            switch(PANEL_SLOTS[field]) {
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

    valueOf(panel: PanelSnapshot, field: string): string {
        return formatDisplayValue(panel.values?.[field], this.locale());
    }

    labelText(label: LocalizedLabel | null | undefined): string {
        if(!label) {
            return '';
        }
        return (this.isFrench() ? label.fr : label.en) ?? '';
    }

    private isFrench(): boolean {
        return this.locale()?.toUpperCase() == 'FR';
    }

    get lboFlag(): string {
        return `LBO: ${this.ecbFormState()?.flags['ecbLboFlag'] ?? '-'}`;
    }

    get ecbLeveragedFlag(): string {
        return this.ecbFormState()?.flags['ecbLeveragedFlag'] ?? '-';
    }
}