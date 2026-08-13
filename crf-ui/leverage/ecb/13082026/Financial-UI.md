Both files above. Class hooks are `financial-table`, `financial-group`, `financial-group-header`, `financial-row` (with `--editable` / `--calculated` modifiers), `financial-label`, `financial-input`, `financial-value`, `financial-justify-btn` (with `--missing`), and `financial-justification-*` for the pop-in.

Four decisions worth your eye.

**Nothing is hard-coded.** Rows, order and groups all come from `question.fields`, so a row added on the Fields tab appears with no code change. That's also why hidden fields are filtered in `groups()` rather than with a template `@if` — `netDebt` stays in the form group and gets posted and frozen like any other box, it just never becomes a row.

**The read-only rows have no control binding at all** — they render `row.display` as text. Binding a disabled control would work, but then the two-decimal formatting would have to live in a pipe over a control whose value is 28 decimals, and it'd be one refactor away from someone reading the formatted value back. Keeping display as a computed string makes that impossible by construction.

**`(change)`, not `(input)`.** Every answer re-traverses and the backend recomputes five figures; per-keystroke would put the analyst's typing in a race with the response that rewrites the calculated rows underneath them.

**Four things I need to flag rather than assume:**

1. The template references `wordingKey`, `commentKey`, `wordingMax`, `commentMax` — I need to expose those as public readonly members on the class re-exporting the model constants. I left them out of the TS by oversight; four one-liners.
2. `*ngTemplateOutlet` needs `NgTemplateOutlet` in the component's `imports` array.
3. The icon name `comment_16dp_...` is a guess — the mock shows a comment glyph beside each editable box, but I don't know your icon set's actual name.
4. The pop-in binds `formControlName` on inputs that sit inside an `ng-template` outside the `[formGroup]` div. That will throw. It needs to be rendered *inside* the `financial-table` div, or the template needs its own `[formGroup]="group()"` wrapper.

Point 4 is a real bug, not a style note — want me to fix it and add the missing members, or would you rather restructure the pop-in as a dialog first and have me write it that way?