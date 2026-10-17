import { NgClass } from '@angular/common';
import { Component, computed, effect, input, OnDestroy, OnInit, output, signal, untracked } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { SvgIconDirective } from '@shared/directives/svg-icon.directive';
import { TooltipDirective } from '@shared/directives/tooltip.directive';
import { MaterialModule } from '@shared/modules/MaterialModule';
import { debounceTime, distinctUntilChanged, Subject, takeUntil } from 'rxjs';

import { ProgressFieldSpinnerComponent } from '../progress-field-spinner/progress-field-spinner.component';

export interface IItemList {
    label: string;
    value: any;
    selected?: boolean;
}

@Component({
    selector: 'bnpp-workflow-autocomplete',
    templateUrl: './workflow-autocomplete.component.html',
    styleUrls: ['./workflow-autocomplete.component.scss'],
    standalone: true,
    imports: [ NgClass, MaterialModule, ReactiveFormsModule, SvgIconDirective, TooltipDirective, ProgressFieldSpinnerComponent ]
})
export class WorkflowAutocompleteComponent implements OnInit, OnDestroy {
    // Input Signals
    placeholder = input.required<string>();
    itemsList = input.required<IItemList[]>();
    autocompleteControl = input.required<FormControl<string | null>>();
    showProgressFieldSpinner = input<boolean>(false);
    initialValue = input<IItemList | null>(null);
    colorTheme = input<'RATING_THEME' | 'FACILITY_THEME'>('RATING_THEME');
    debounceTimeMs = input<number>(300);
    isCountrySearch =  input<boolean>(false);

    // Output Signals
    itemSelectedEvent = output<IItemList | null>();
    inputChangedValueEvent = output<string>();
    advanceSearchEvent = output<void>();

    // Internal state using signals
    isPanelOpen = signal<boolean>(false);
    itemSelected = signal<IItemList | null>(null);

    private userCleared = signal(false);
    private hasPreselected = signal<boolean>(false);

    //Computed signal for display value
    displayValue = computed(() => {
        const selected = this.itemSelected();
        if(!selected) return '';
        return selected.value !== selected.label
         ? this.isCountrySearch()
            ? `${selected.value} - ${selected.label}`
                : `${selected.label} - ${selected.value}`
            : selected.value;
    });

    // For cleanup
    private destory$ = new Subject<void>();
    private searchSubject$ = new Subject<string>();

    constructor() {
        // Effect to sync when initialValue is provided directly as IItemList
        effect(
            () => {
                const externalValue = this.initialValue();
                const currentSelected = this.itemSelected();
                const cleared = this.userCleared();

                if(cleared) {
                    // user cleared -> do nothing, keep it cleared
                    return;
                }

                if(externalValue && typeof externalValue === 'object') {
                    this.hasPreselected.set(true);
                    if(externalValue.value !== currentSelected?.value) {
                        this.selectItem(externalValue, false);
                        this.userCleared.set(false);
                    }
                }

                if(externalValue === null && currentSelected !== null && this.hasPreselected()) {
                    this.clearSelection();
                }
            },
            { allowSignalWrites: true },
        );

        // Effect to sync when itemsList load/changes - try to match with existing control value
        effect(() => {
            const items = this.itemsList();
            if(items?.length > 0) {
                untracked(() => this.tryMatchControlValueToItem());
            }
        });
    }

    ngOnInit(): void {
        this.setupSearchDebounce();
        this.setupControlValueSync();
    }

    ngOnDestroy(): void {
        this.destory$.next();
        this.destory$.complete();
    }

    private setupSearchDebounce(): void {
        this.searchSubject$.pipe(debounceTime(this.debounceTimeMs(), distinctUntilChanged(), takeUntil(this.destory$))
            .subscribe(searchValue => this.inputChangedValueEvent.emit(searchValue)))
    }

    private setupControlValueSync(): void {
        this.autocompleteControl()
            .valueChanges.pipe(takeUntil(this.destory$))
            .subscribe(() => {
                //Skip if panel is open (user is typing)
                if(this.isPanelOpen()) {
                    return;
                }
                this.tryMatchControlValueToItem();
            });

        //Also check on init in case value is already set
        this.tryMatchControlValueToItem();
    }

    private tryMatchControlValueToItem(): void {
        const controlValue = this.autocompleteControl().value;
        const currentSelected = this.itemSelected();

        //Already matched
        if(currentSelected?.value === controlValue) {
            return;
        }

        // No value to match
        if(!controlValue) {
            return;
        }
        // Try to find matching item in list
        const items = this.itemsList();
        if(items?.length > 0) {
            const matchingItem = items.find(item => item.value === controlValue);
            if(matchingItem) {
                //this.itemSelected.set(matchingItem);
            }
        }
    }

    openPanel(): void {
        const control = this.autocompleteControl();
        if (control.disabled || this.isPanelOpen()) {
            return;
        }

        this.isPanelOpen.set(true);
        // clear display to allow typing, but dont reset during typing
        control.setValue('', { emitEvent: false });
    }

    closePanel(): void {
        // Delay to allow click events on panel items to register
        setTimeout(() => {
            this.isPanelOpen.set(false);
            const selected = this.itemSelected();
            const control = this.autocompleteControl();

            if(selected) {
                control.setValue(selected.value, { emitEvent: false });
            } else {
                control.setValue(null, { emitEvent: false })
            }
        }, 200);
    }

    selectItem(item: IItemList, emitEvent: boolean = true): void {
        this.itemSelected.set(item);
        const control = this.autocompleteControl();
        control.setValue(item?.value ?? null);
        control.markAsTouched();
        this.isPanelOpen.set(false);
        this.userCleared.set(false);

        if(emitEvent) {
            this.itemSelectedEvent.emit(item);
        }
    }

    onSearchInput(event: Event): void {
        const inputElement = event.target as HTMLInputElement;
        const searchValue = inputElement.value;
        this.searchSubject$.next(searchValue);
    }

    clearSelection(event?: Event): void {
        event?.stopPropagation();
        const control = this.autocompleteControl();
        control.setValue(null);
        this.itemSelected.set(null);
        this.userCleared.set(true);
        this.hasPreselected.set(false);
        this.itemSelectedEvent.emit(null);
    }

    advanceSearch(event: Event): void {
        event.stopPropagation();
        this.advanceSearchEvent.emit();
    }

    onInputFocus(): void {
        this.openPanel();
    }

    onInputBlur(): void {
        this.closePanel();
    }

    onPanelItemKeydown(event: KeyboardEvent, item: IItemList): void {
        if(event.key === 'Enter' || event.key === ' ') {
            event.preventDefault();
            this.selectItem(item);
        }
    }

    trackByValue(_index: number, item: IItemList): any {
        return item.value;
    }

    get hasError(): boolean {
        const ctrl = this.autocompleteControl?.();
        return !!ctrl && ctrl.invalid && (ctrl.touched || ctrl.dirty);
    }
}