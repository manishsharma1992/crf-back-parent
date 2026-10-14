import { CommonModule } from '@angular/common';
import { Component, computed, DestroyRef, inject, OnInit, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router } from '@angular/router';
import { LeverageAnalysis, ValidatedAnalysisViewModel } from '@lazyloaded/counterparty/model/leverage-lending/leverage-lending.model';
import { LeverageLendingService } from '@lazyloaded/counterparty/service/leverage-lending/leverage-lending.service';
import { IItemList } from '@shared/components/workflow-autocomplete/workflow-autocomplete.component';
import { SvgIconDirective } from '@shared/directives/svg-icon.directive';
import { MaterialModule } from '@shared/modules/MaterialModule';

@Component({
    selector: 'bnpp-leverage-history',
    templateUrl: 'leverage-history.component.html',
    styleUrls: ['leverage-history.component.scss'],
    standalone: true,
    imports: [ CommonModule, SvgIconDirective, MaterialModule ],
})
export class LeverageHistoryComponent implements OnInit {
    private readonly router = inject(Router);
    private readonly route = inject(ActivatedRoute);
    private readonly leverageLendingService = inject(LeverageLendingService);

    readonly rmpmid = signal('');
    readonly analyses = signal<LeverageAnalysis[]>([]);

    readonly draftAnalysis = computed(() => this.analyses().find(a => a.status === 'DRAFT') ?? null);
    readonly hasDraft = computed(() => this.draftAnalysis() !== null);

    private readonly validatedAnalyses = computed(() => this.analyses().filter(a => a.status === 'VALIDATED'));
    private readonly analysisUrl = computed(() => `/counterparty/detail/${this.rmpmid()}/leverage/analysis`);

    readonly validatedAnalysesVm = computed<ValidatedAnalysisViewModel[]>(() =>
        this.validatedAnalyses().map(a => {
            const ecbFlags = a.responses?.ecbForm?.flags;
            const fedFlags = a.responses?.fedForm?.flags;
            const ecbRatioVal = ecbFlags?.['ecbLeverageRatio'] != null ? Number(ecbFlags['ecbLeverageRatio']) : null;
            const fedRatioVal = fedFlags?.['fedLeverageRatio'] != null ? Number(fedFlags['fedLeverageRatio']) : null;
            const covenantRaw: string = ecbFlags?.['ecbCovenantStructure'];
            return {
                analysis: a,
                noAnalysisRequired: a.responses?.ecbForm === null && a.responses?.fedForm == null,
                isLbo: ecbFlags?.['ecbLboFlag'] === 'YES',
                ecbRatio: ecbRatioVal != null && !Number.isNaN(ecbRatioVal) ? ecbRatioVal.toFixed(2) : '',
                fedRatio: fedRatioVal != null && !Number.isNaN(fedRatioVal) ? fedRatioVal.toFixed(2) : '',
                ecbFlag: this.ecbLeveragedFlagList.find(f => f.value === ecbFlags?.['ecbLeveragedFlag'])?.label,
                fedFlag: this.fedLeveragedFlagList.find(f => f.value === fedFlags?.['fedLeveragedFlag'])?.label,
                isEscalated: ecbFlags?.['escalatedTransactions'] === 'YES',
                isFedEscalated: fedFlags?.['escalatedTransactions'] === 'YES',
                covenant: covenantRaw === 'NONE' ? 'NO' : covenantRaw,
            }
        }),
    );

    destroyRef = inject(DestroyRef);

    ecbLeveragedFlagList: IItemList[] = [
        { label: 'LEVERAGED', value: 'ECB_LEVERAGED' },
        { label: 'NOT LEVERAGED', value: 'NOT_LEVERAGED' },
        { label: 'INR', value: 'INR' },
        { label: 'CALCULATION AT BG OR OTHER LE', value: 'CALCULATION_AT_BG_OR_OTHER_LE' },
    ];

    ecbCovenantStructure: IItemList[] = [
        { label: 'Covenant Loose', value: 'LOOSE' },
        { label: 'Covenant Lite', value: 'LITE' },
        { label: 'No Covenant', value: 'NONE' },
        { label: 'Full Covenant', value: 'FULL' }
    ];

    fedLeveragedFlagList: IItemList[] = [
        { label: 'NOT LEVERAGED', value: 'FED_NOT_LEVERAGED' },
        { label: 'INR', value: 'INR' },
        { label: 'LEVERAGED', value: 'FED_LEVERAGED' },
        { label: 'LEVERAGED HIGH RISK', value: 'FED_LEVERAGED_HIGH_RISK' },
        { label: 'LEVERAGED LOW RISK', value: 'FED_LEVERAGED_LOW_RISK' },
    ];

    ngOnInit(): void {
        const rmpmid = this.router.url.split('/')[3] ?? '';
        this.rmpmid.set(rmpmid);
        this.leverageLendingService
            .getAnalysesByRmpmid(rmpmid)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: list => this.analyses.set(list);
            })
    }

    initAnalysis(): void {
        this.router.navigate([this.analysisUrl()], {
          relativeTo: this.route,
          queryParams: { analysisUid: null },
          queryParamsHandling: 'merge',
        })
    }

    navigateToDraft(uid: string): void {
        this.router.navigate([this.analysisUrl()], {
            relativeTo: this.route,
            queryParams: { analysisUid: uid },
            queryParamsHandling: 'merge',
        });
    }

    navigateToValidated(uid: string): void {
        this.router.navigate([this.analysisUrl()], {
            relativeTo: this.route,
            queryParams: { analysisUid: uid, readonly: true },
            queryParamsHandling: 'merge',
        });
    }


}