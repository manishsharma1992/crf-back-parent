All three confirmed. Let me read the Flag Values tab and the panel declarations before writing the adapter.Everything's there. Two mismatches worth naming before the code:

**Panel field names don't match flag keys.** The panel declares `leveragedFlag` and `covenantStructure`; the flags catalogue calls them `ecbLeveragedFlag` and `ecbCovenantStructure`. `InfoPanelSelector.display(...)` looks up by *flag key* to find the value set, so the adapter needs a small map between them.

**The port can't decode without the definition.** `display(definition, flagKey, storedValue, locale)` needs `definition.flags()` and `definition.flagValueSets()`, but `resolve(triggered, counterpartyId, locale)` doesn't have it. So the signature grows one argument — both call sites already hold it.Call site in `SaveLeverageFormUseCase` gains the definition and the subject:

```java
List<PanelSnapshot> panels = infoPanels.resolve(definition,
        panelSelector.triggeredBy(definition, result.flags()), subject, locale);
```

**Two things I've guessed and you should check:**

1. **`PanelSnapshot` and `PanelField`'s shapes.** I've assumed `PanelSnapshot(panelKey, title, List<PanelField>)` and `PanelField(name, value)`. If your records differ, only the `snapshots`/`fields` methods change. Worth also confirming `InfoPanel` exposes `key()`, `titleEn()`, `titleFr()`, `fields()` — I inferred those from the sheet's columns and `whenFlagKey()`/`whenFlagValue()` which I've seen used.

2. **`ecbCovenantStructure` as the flag key** for `covenantStructure`. The catalogue rows I could read cover `ecbLeveragedFlag`, `ecbLboFlag` and `fedLeveragedFlag`; the covenant flag is in the rows I didn't print. If it's named differently, fix the `DECODED_BY` entry.

**One thing that's still not wired:** these panels only reach the *stored snapshot*, via `responseAssembler`. `FormState` has no panels field, so the analyst never sees them on screen. If the INR panel is meant to display while filling the form — and "read-only blocks pulled from RMPM and shown when a flag takes a given value" reads like it is — then `FormState` needs `infoPanels`, the assembler needs the argument, and both read use cases need to call the resolver too. Same shape as the validation-messages change, maybe twenty minutes.

Want that now, or straight to the financial table? The panels are currently recorded correctly for export either way, so it's a display gap rather than a data one.