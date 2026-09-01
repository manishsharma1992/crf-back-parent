# Wiring the panel into EcbQuestionsComponent / FedQuestionsComponent

Both host components do the same three things. They stay separate components —
they will diverge — but the validation wiring is identical.

```ts
private readonly validationState = inject(ValidationStateStore);

// 1. on load
ngOnInit(): void {
  this.validationState.refresh(this.analysisUid);
}

// 2. the moment a save STARTS, before it returns
private save(): void {
  this.validationState.markStale();
  this.saveApi.save(...).subscribe(state => {
    this.formState.set(state);
    this.validationState.refresh(this.analysisUid);   // 3. after it lands
  });
}
```

Step 2 is the one that is easy to skip and the reason the button cannot be driven
by the last response alone. Between the analyst clearing a mandatory box and the
availability call returning, the previous answer still says `canValidate: true`.
`markStale()` closes that window; without it the button stays live against state
that no longer exists.

## Template

```html
<crf-validation-panel
  [analysisUid]="analysisUid"
  (validated)="onValidated($event)"
  (conflicted)="reload()" />

<bnpp-form-snapshot
  [ecbFormState]="ecbFormState()"
  [fedFormState]="fedFormState()"
  [analysisState]="validationState.state()"
  [locale]="locale()" />
```

The panel reads status from the store directly; the snapshot takes it as an input
so it stays presentational.

## Read-only lock

Drive it from the analysis status, not from `FormState.validatedAt`. Inferring
"validated" from a non-null timestamp works today and breaks the moment any other
transition stamps one — and the status is now on the same read the button uses.

```ts
readonly locked = computed(() => this.validationState.analysisStatus() === 'VALIDATED');

constructor() {
  effect(() => (this.locked() ? this.form.disable() : this.form.enable()));
}
```

This is display only. `assertModifiable()` in the save use cases (LV-07) is what
actually prevents the write — a disabled control stops an honest analyst, not a
tab left open since before someone else clicked Validate.

## After a successful validation

```ts
onValidated(change: AnalysisStatusChangeView): void {
  // Refresh rather than patch: the store is the single source for status, and
  // the snapshot header reads the same signal the panel does.
  this.validationState.refresh(this.analysisUid);
}
```
