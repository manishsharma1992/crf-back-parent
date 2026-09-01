# form-snapshot.component — showing the analysis status

## What line 10 does today

```html
<div class="progress-label">{{ ecbFormState()?.status || fedFormState()?.status }}</div>
```

`FormState.status` is `IN_PROGRESS` / `COMPLETED` **for one form**. So this reads
as "is the ECB form finished, or failing that the FED one" — which is neither
form's status nor the analysis's. On an analysis routed to both, it reports
whichever happens to be non-null first, and on a FED-only analysis it silently
falls through to the FED value while looking like it meant ECB.

## What the mockups need

Two lines, not one:

| Analysis state | Headline | Sub-line |
|---|---|---|
| DRAFT | ON GOING | DRAFT, NEEDS VALIDATION |
| VALIDATED | VALIDATED | NOT YET USED / (used) |

Plus, when validated: *"Last validation by {validatedBy} on {validatedTimestamp}"*.

## The change

Bind to the analysis-level read, `GET .../validation-state`, which now carries
`status`, `validatedBy` and `validatedTimestamp` alongside `canValidate`. One read
serves both the validation panel and this header — they are two faces of the same
state, and two reads would let them disagree.

### Component

```ts
export class FormSnapshotComponent {
  ecbFormState = input.required<FormState | null>();
  fedFormState = input.required<FormState | null>();
  locale = input.required<string>();

  // NEW — analysis-level, passed by the parent from ValidationStateStore
  analysisState = input.required<AnalysisValidationState | null>();

  readonly isValidated = computed(() => this.analysisState()?.status === 'VALIDATED');
}
```

An input rather than injecting the store, to keep this component presentational
like the rest of it. The parent already has the store for the validation panel.

### Template

```html
<section class="analysis-progress fade-in">
  <div class="progress-details">
    @if (isValidated()) {
      <div class="progress-label" i18n>Validated</div>
      <div class="progress-sub-label" i18n>Not yet used</div>
      <div class="progress-stamp">
        <ng-container i18n>Last validation by</ng-container>
        {{ analysisState()?.validatedBy }}
        <ng-container i18n>on</ng-container>
        {{ analysisState()?.validatedTimestamp | date: 'dd/MM/yyyy' }}
      </div>
    } @else {
      <div class="progress-label" i18n>On going</div>
      <div class="progress-sub-label" i18n>Draft, needs validation</div>
    }
  </div>
</section>
```

### Last-update line (line 5) has the same shape of problem

```html
{{ ecbFormState()?.lastModifiedTimestamp || fedFormState()?.lastModifiedTimestamp | duration }}
```

`||` takes the first non-null, not the most recent. On an analysis with both
forms, editing FED leaves the header showing the older ECB timestamp. Use the
later of the two:

```ts
readonly lastModified = computed(() => {
  const stamps = [this.ecbFormState()?.lastModifiedTimestamp, this.fedFormState()?.lastModifiedTimestamp]
    .filter((value): value is string => !!value)
    .sort();
  return stamps.at(-1) ?? null;
});
```

## Three things in the mockup that are not in scope

Worth confirming with Sushmitha rather than building from the picture — the
Fields tab and written BRs win over mockups.

1. **"NOT YET USED"** requires knowing whether a rating has consumed this
   analysis. The link exists (the rating records which analysis it used), so this
   is buildable — but it is a new read, not covered by BR01–BR03. It also means
   the consumption question now matters for *display today*, not only for the
   parked delete feature.
2. **"ON REVISION" / "SAVE AS REVISION DRAFT"** is a revision flow. Frederic has
   parked edit-after-validation, and the mockup's own dev note says a revision
   must block usage in rating. This contradicts "once validated, cannot be
   edited". Escalate before anyone builds toward it.
3. **COMPLETION 100% / 10%** is the completion percentage, still deferred pending
   Sushmitha's rule. `FormResponses` deliberately stores no completion figure —
   storing 0 everywhere until the rule lands would be worse than storing nothing.

## Minor: the `<dl>` lint warning on line 29

`<dl>` **may** contain `<div>` wrapping `<dt>`/`<dd>` groups — that is valid per
the HTML Living Standard content model, and it is the right way to make each
flag a styleable row. The Angular language service warning is a false positive.
Suppress it rather than restructuring into a flat `dt`/`dd` sequence, which would
cost you the row wrapper.
