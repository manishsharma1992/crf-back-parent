# The completion percentage — the one thing that is not concrete

Everything else you answered is settled and applied. This one needs a decision before it can be
built, because **a dynamic tree has no stable denominator.**

The tree branches. Two analysts on the same form answer different numbers of questions: an ANY_YES
on Q-B01A ends the ECB form after two questions, while a full run through the financial table and
the qualitative block takes twelve. "Percentage of questions answered" therefore has no obvious
divisor, and the naive choices are all wrong in a way an analyst will notice.

## What does not work

**answered / total questions in the definition.** Q-B01A and Q-B01B are alternatives — nobody ever
sees both — so a completed form can never reach 100%.

**answered / questions on the path so far.** Always 100%, since the path is by definition what has
been answered.

**A hard-coded expected length.** Wrong for one of the two LBO scenarios, exactly like the `Order`
column we deleted for the same reason.

## Three that do work

**A. Shortest remaining path.** `answered / (answered + minimumStepsToAnyTerminal)`. Computed from
the current node by a breadth-first walk of the definition. Honest and needs no authoring, but it
can go BACKWARDS — answering a question that opens the qualitative block drops the figure from 80%
to 60%, and a progress bar that retreats reads as a bug.

**B. Longest path.** Same but worst case. Monotonic and never retreats, but it undershoots badly:
a form that ends early at Q-B01A shows ~15% when it is genuinely finished, so it needs a special
case for the terminal, and at that point it is not really a measure.

**C. Section completion, not question completion.** The image is the strongest evidence for this:
it shows 20% beside a checklist of SPREADSHEET SELECTION, PRELIMINARY, and the rest. Five sections,
one complete, 20%. Nothing to compute, monotonic, never retreats, and it matches what the analyst
sees on the left-hand rail.

## Recommendation

**C for the headline figure, A inside a form if a per-form bar is wanted later.** It matches the
mock-up arithmetic exactly, needs no graph analysis, and cannot embarrass itself. The cost is that
it is coarse: a half-filled financial table shows the same 20% as an untouched ECB form.

Worth confirming with the BA which of the two they actually meant — "percentage of questions
answered" and a five-section progress ring are different features, and the ring is the one already
drawn.

## Either way

`FormResponses.completion` is now on the record, so the number is FROZEN with the snapshot rather
than recomputed on read. That matters for the same reason the panels are frozen: a definition
published next month may add a question, and last month's finished analysis must not silently drop
to 90%.
