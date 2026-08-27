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

## 3. STRANDED — answered, and it fails closed

`FormStateAssembler.assemble()` throws `StrandedTraversalException` before it
maps anything, so STRANDED never reaches `FormState.Status` at all. The dangerous
case — a broken definition arriving as `COMPLETED` and becoming validatable — is
ruled out.

The cost is that the exception would have propagated out of the availability
endpoint as a 500, **including when the stranded form is not the one the analyst
is looking at**: an analyst on ECB would lose the Validate button entirely
because FED's definition was published with a gap.

`AnalysisCompletenessService.readOrNull` now catches it per form and maps it to
`DEFINITION_STRANDED`. Validation is refused either way; the difference is a
disabled button with a reason instead of an error page. That blocker is now
reachable, so it earns its place in the enum.

`FormState.Status` needs no third value after all.

---

## 4. Also worth a look

- `GetLeverageFormStateUseCase` calls `LeverageFormType.valueOf(formType)` five
  times per request across two methods. Converting once at the top removes the
  repetition and gives one place to throw a sensible error on a bad value.
- `SaveLeverageFormUseCase` throws `IllegalArgumentException` for PRELIMINARY,
  which will surface as a 500 unless the advice maps it. A dedicated exception
  mapped to 400 says the same thing without the noise.


---

## 5. Bugs in `FormStateAssembler`

### a. `panels` is dropped

```java
return state(definition, result, views, currentKey, localise(violations, locale), audit, locale);
```

Seven arguments into an eight-parameter method — `panels` is missing. Every
FormState would be built without its info panels.

### b. Missing comma

```java
FlagView.from(definition, result.flags(), locale) null,   // IN_PROGRESS branch
```

### c. `ValidationMessage` accessors do not match

The assembler calls `message.textEn()` and `message.textFr()`, but the record you
sent earlier declares a single `LocalizedLabel text`. One of the two is out of
date — worth resolving before wiring, since `localise()` is what feeds the ERROR
messages that BR01 reads.

### d. `FormState` has grown

The assembler passes `FlagView` and `panels`, which the record you sent earlier
does not declare. No impact on this work — `ValidationAvailability` is not going
onto `FormState` — but the copy I was given is stale.
