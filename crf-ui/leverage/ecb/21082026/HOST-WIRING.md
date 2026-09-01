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
  [validatedAt]="formState().validatedAt"
  [validatedBy]="formState().validatedBy"
  (validated)="onValidated($event)"
  (conflicted)="reload()" />
```

## Read-only lock

No payload change needed — `FormState.validatedAt` is already there.

```ts
readonly locked = computed(() => this.formState().validatedAt !== null);

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
  // The response carries the stamp, so no refetch is needed to lock the form.
  this.formState.update(state => ({
    ...state,
    validatedAt: change.changedTimestamp,
    validatedBy: change.changedBy,
  }));
}
```
