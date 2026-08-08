Before the code, one thing that will bite: **the autocomplete's control can't be the answer control.** While the analyst types "ACM", that control holds partial text — and since every answer triggers a traversal, the engine would see `Q-S06 = "ACM"` and route on garbage. So the search needs its own control, and only a *selection* writes to the answer.

Searching is neither traversal nor persistence, so it stays in the child rather than the parent.Parent template needs one more binding:

```html
<app-ecb-questions
  [state]="leverageLendingService.ecbFormState()"
  [ecbForm]="ecbForm"
  [messages]="ecbMessages()"
  [analysisUid]="analysisUid!"
  [locale]="locale"
  (answered)="onEcbAnswer($event.value, $event.questionKey)" />
```

**The one thing I've guessed is the selection output.** I've written `(itemSelected)="...$event.value"`. You confirmed the inputs (`autocompleteControl`, `placeholder`, `itemList`) but not the output — and given how `selectionChange` vs `fieldValueChanged` went with the chip component, this is exactly the spot where a wrong guess produces the `Property 'value' does not exist on type 'Event'` error again. Check `bnpp-workflow-autocomplete`'s `output<...>()` declaration and correct that line; if it emits a plain string, it becomes `onLookupSelected(question.key, $event)`.

Three deliberate choices worth flagging:

**`renderTick`** exists because results arrive from a subscription, not from an input, and `OnPush` won't re-render on a `Map` mutation. Reading the signal inside `itemListFor` makes the template re-evaluate when a response lands. If the component ever moves off `OnPush`, that can go.

**No 3-character check in the client.** The backend already returns `[]` below three, so duplicating it would give two places to change one rule. Costs one wasted HTTP call per short term, which is cheap and won't touch the database.

**`lookupDisplay` falls back to the raw stored value** on reload, since the results map is empty until the analyst searches again. That means a reloaded Q-S06 shows the RMPM id rather than the company name. If that's not acceptable, the fix is backend-side: have `QuestionView` carry a resolved display label for LOOKUP answers, the same way `single()` already carries `valueLabel` for a chosen option.