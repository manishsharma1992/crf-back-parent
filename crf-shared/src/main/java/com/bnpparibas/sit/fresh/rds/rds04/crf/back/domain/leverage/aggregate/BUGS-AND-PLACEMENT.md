# Review notes

## 1. `assertModifiable()` — TWO places, not one

`SaveLeverageFormUseCase.save()` **rejects PRELIMINARY** and delegates it to
`SavePreliminaryFormUseCase`. Putting the guard in only one of them leaves the
preliminary form writable on a VALIDATED analysis — the exact hole BR02 exists
to close.

```java
// SaveLeverageFormUseCase.save()
LeverageAnalysis analysis = analyses.findByAnalysisUid(analysisUid)
        .orElseThrow(() -> new AnalysisNotFoundException(analysisUid));
analysis.assertModifiable();          // <-- here, before pinnedDefinition(...)

// SavePreliminaryFormUseCase — same position, immediately after the load
```

It must sit **before `pinnedDefinition(...)`**, because that method mutates the
aggregate (`analysis.pinDecisionTree(...)`). Guarding after it would let a
validated analysis have a definition pinned onto it before the refusal.

Anything else that mutates the aggregate needs the same line — worth a grep for
`analyses.save(` to catch use cases neither of us has looked at.

---

## 2. Real bugs

### a. `derivedSources` reads the wrong accessor — Get only

```java
// GetLeverageFormStateUseCase
definition.question()      // singular
// SaveLeverageFormUseCase
definition.questions()     // plural
```

Two copies of the same method, one of them wrong. This is the argument for the
extraction you declined — I still think declining was right for now, but the
duplication has already produced a defect.

### b. `LOOKUP_QUESTION = "Q-S06"` is wrong for FED

You flagged this yourself. It is a live bug, not a future concern: on the FED
form `resolved.get("Q-S06")` returns null, so
`entityEligibility.resolve(null, subject)` runs and whatever it returns is fed
into `validation.violations(...)`. FED eligibility is therefore being evaluated
against nothing.

The key belongs on `DecisionTreeDefinition` — each tree naming its own lookup
question — rather than as a constant duplicated across two use cases.

### c. Transcription-level (may be paste artefacts)

- `@Transational` → `@Transactional` (both classes, every method)
- `analysis.reponsesFor(...)` → `responsesFor` (Get)
- `souce != target` → `source` (Save, `crossFormAnswers`)
- `infoPanels.resolve(defintion, ...)` → `definition` (Save)
- `assemble(definition, resolved, result,, violations, ...)` — double comma (Save)
- `PreliminaryResponesAssembler` → `Responses` (Save field)

---

## 3. STRANDED — still open

Not answerable from these two classes. The mapping from `TraversalState`
(PENDING_INPUT / TERMINAL / STRANDED) to `FormState.Status` (IN_PROGRESS /
COMPLETED) happens inside **`FormStateAssembler`**, which I have not seen.

Three cases, and they are not equivalent:

| If the assembler maps STRANDED to… | Consequence for BR01 |
|---|---|
| `IN_PROGRESS` | validation correctly refused; analyst told "incomplete" and goes looking for a field that does not exist |
| `COMPLETED` | **a broken definition would be validatable** — the ERROR filter is the only thing standing in the way, and a stranded walk may raise none |
| throws | never reaches completeness at all |

The middle row is the one to rule out. Send me `FormStateAssembler` and I can
confirm which it is.

---

## 4. Also worth a look

- `GetLeverageFormStateUseCase` calls `LeverageFormType.valueOf(formType)` five
  times per request across two methods. Converting once at the top removes the
  repetition and gives one place to throw a sensible error on a bad value.
- `SaveLeverageFormUseCase` throws `IllegalArgumentException` for PRELIMINARY,
  which will surface as a 500 unless the advice maps it. A dedicated exception
  mapped to 400 says the same thing without the noise.
