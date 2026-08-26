Two design corrections surfaced while implementing. Flagging them before the code.Two things I got wrong earlier and have now fixed:

**`CompletenessInput` was flat.** It held one `TraversalState` — which can't represent an analysis routed to both ECB and FED. Same modelling mistake as the `formType` column you caught. It's now a list of `FormCompletenessInput`, one per applicable form, derived from the definition-id columns exactly as the BR03 snapshot does. Analysis is complete only when every applicable form is.

**`FormCompleteness` didn't say which form blocked.** "3 mandatory fields missing" isn't actionable when both ECB and FED are open. It now carries `blockingForm`, and that flows through to the payload.

**Justifications needed their own source.** They hang off financial table rows, not the tree, so walking the traversal alone would report a form complete with an empty justification dialog. Hence `RequiredJustificationsProvider` as a separate port, appended after the tree questions so the UI scrolls to the earliest gap.

The read and write paths share one assembler bean and one domain service call. That symmetry is the point — if the read said yes and the write said no, the analyst gets an enabled button that throws.

Three things to confirm:

1. **Which alerts block validation?** This one's for Sushmitha. Treating all alerts as blocking makes the button unreachable on forms she'd consider finished; treating none as blocking lets a known-bad analysis into a rating. My guess is `ECB_ADJUSTED_EBITDA_ZERO` blocks and the immediate messages don't, but that's a business call.

2. **`LeverageTraversalPort` is probably unnecessary.** The engine already exists and likely answers both questions. If it does, delete the port and call it directly rather than adding a wrapper for its own sake.

3. **Cost.** This runs the traversal on every form-state read, not just the validate click. Probably free once the definition is cached, but worth measuring. If it isn't, cache the assembled input through the existing `CacheManager` — don't move the computation to Angular.

That completes the module end to end: domain, infrastructure, application. Want the JIRA sub-task breakdown and design deck next, or the Angular side first?