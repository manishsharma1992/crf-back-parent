# crf-datasync — decision-tree import, part 1: workbook reader + catalogues

Assumed module root `com.bnpparibas.sit.fresh.rds.rds04.crf.datasync` — say the word and it
renames. The domain model and `DecisionTreeValidator` stay in **crf-shared** under
`...crf.back.domain.leverage.value.tree`; nothing here duplicates them.

## Layering

```
exposition        (later: the import endpoint / batch trigger)
application       definitionimport/  ← everything below except the adapter
   WorkbookSource            PORT: sheet/row/column -> String
   SheetTable, TableRow      header-addressed table reading
   Cells                     ; lists, multi-line cells, header normalisation
   ImportIssue(s)            parse-time problems, located
   SourceLocation            sheet + row + column, for the BA
   FormsSheetParser          form metadata, outcomes, flags, messages, panels
   FlagValuesSheetParser     coded flag dictionary
   FormMetadata, ParsedCatalogues
domain            (crf-shared — untouched)
infrastructure    excel/PoiWorkbookSource   ADAPTER: the only class importing POI
```

`SourceLocation` has **moved** out of `crf-back.application.leverage.definitionimport`. The import
now runs in crf-datasync, so `SourceLocator` and `ValidationReportAssembler` should follow it —
they are unchanged otherwise.

## Three decisions worth knowing

**Tables are located by header signature, never by row number.** The Forms tab stacks five tables
in the same columns. A BA inserting a row would silently shift every fixed offset, so
`SheetTable.locate` scans for a row containing the required headers and reads down from there.
`tables_are_found_by_header_not_by_row_number` pins it.

**A table ends at the first row with fewer than two populated cells.** Banner rows, blank
separators and the italic footnotes all populate only column A; every real data row fills at least
two. One rule, no special-casing of styling.

**Numbers are rendered without a decimal tail.** POI hands back every numeric cell as a double, so
`Stored Value` 0 arrives as `"0.0"` and fails `intValueExact`. `PoiWorkbookSource` strips trailing
zeros. `storedValueZero_survives_poi_style_number_text` guards it.

## Parse issues vs validation errors

Kept deliberately separate. An `ImportIssue` means *I could not build the object* and carries its
own `SourceLocation`. A `ValidationResult.Error` means *I built it and the wiring is wrong* and is
located afterwards through the `SourceLocator` seam. Nothing throws: a BA gets every problem in
one pass.

## New rules this layer adds

| Code | Why |
|---|---|
| `FLAG_VALUE_NUMBER_CLASH` | two codes storing the same integer would be unreadable coming back out of the database |
| `FLAG_ASSIGNMENT_MALFORMED` | catches `ecbCovenantStructure=` — the empty right-hand side we rejected during design |
| `PANEL_TRIGGER_MALFORMED` | `Shown When` must read `<flagKey> is <VALUE>` |
| `FORM_MISSING` / `FORM_DUPLICATE` | all three forms declared exactly once |
| `CELL_REQUIRED` / `CELL_NOT_INTEGER` / `CELL_UNKNOWN_VALUE` | generic, and they name the column |

## Open

**Info panels carry no Form column.** A panel is attached to whichever form owns the flag in its
`Shown When`. Works today because `ecbLeveragedFlag` is ECB-only; add a Form column if a panel
ever triggers on a shared flag.

## Next

Question-tab parser: `Options` / `Items` grammar, then the branch and value-rule expression
parser (`ANY_YES -> END, flags: k=V`, `field a > 4 x field b`), then the Fields tab join, then
`SourceLocator` and the persistence adapter for `leverage_decision_tree_definition`.

---

# Part 2: question tabs

One parser serves all three tabs — `Preliminary Q`, `ECB Q`, `FED Q` share a column layout, so
only the sheet name and form type differ (`QuestionSheetParser.sheetNameFor`).

| Class | Job |
|---|---|
| `Expressions` | bracket-aware splitting — package-private, the grammar's foundation |
| `ConditionExpressionParser` | one condition atom or an `AND` composite |
| `BranchExpressionParser` | a whole Branches cell, order preserved |
| `ValueRuleExpressionParser` | a whole Value Rules cell |
| `LabelParser` | text + nested bullets, EN and FR |
| `OptionsParser` | `CODE\|EN\|FR` for both Options and Items |
| `FieldsSheetParser` | Fields tab, grouped by form then question |
| `QuestionSheetParser` | assembles a `Question` and joins its boxes |

## Why `Expressions` exists

Every separator in the grammar also appears inside a bracketed list:

```
Q-C02 in [ORIGINATION, MATERIAL_MODIFICATION] AND field ecbLeverageRatio range [<0 | >6] -> Q-Q04
```

Splitting on `,` tears the `in` list apart; splitting on `|` tears the range apart. All splitting
happens at bracket depth zero. `and_splits_only_outside_brackets` pins it.

## Range semantics

A bare bound inside a band is INCLUSIVE, so `[4..6]` is 4 ≤ r ≤ 6 and `[0 .. <4]` is 0 ≤ r < 4 —
the band that ends the ECB form. A `|` separates alternative terms: `[<0 | >6]` becomes two
`Range`s, matching if either holds.

## Decisions worth reviewing

**A bare token is the question's own answer.** `JUST_BELOW_CCDG` needs no keyword; `Q01 is NO`
does. A bare token containing a space is an error rather than a guess — free prose in a Branches
cell should fail loudly.

**Flags are allowed on a continuing branch.** `Effect(null, flags, terminal=false)`. `Fills Flag`
covers the common case, but nothing is gained by forbidding this and it keeps `outcome=` as the
only terminal-only clause.

**An orphan sub-bullet is promoted, not dropped.** Losing regulatory tooltip wording silently is
worse than showing it at the wrong indent; the issue is recorded either way.

**Sheet row order is preserved but means nothing.** There is no Order column — screen order comes
from routing. Rows stay in sheet order only so reports read the way the BA typed them.

**The template has no sections.** `QuestionSheetParser.singleSection` wraps a form's questions in
one synthetic `Section` to keep the aggregate's shape. `Section` is now vestigial; worth deciding
whether to drop it from the model.

## Tests

`ExpressionParserTest`, `QuestionSheetParserTest`, `FormsSheetParserTest` — all
`@TestInstance(Lifecycle.PER_CLASS)`, all against `InMemoryWorkbookSource`. Every expression case
is a real cell from the workbook. Note the annotation constant is `TestInstance.Lifecycle.PER_CLASS`.

## Next

`DecisionTreeAssembler` (catalogues + questions → three `DecisionTreeDefinition`s), the
`SourceLocator` Excel implementation, the JSONB serialiser and the
`leverage_decision_tree_definition` persistence adapter — then the management-layer
`@SpringBootTest` covering workbook in → three rows out.

---

# Part 3: assembler

`DecisionTreeAssembler` + `AssembledWorkbook`. One workbook in, three
`DecisionTreeDefinition`s out.

## Why one pass over the whole workbook

The catalogues are genuinely shared: the `LEVERAGED_FLAG` value set is written by ECB *and* FED,
and code 2 (INR) belongs to both. Parsing per form would either duplicate the sets or leave each
form blind to the other's codes — and reading once is what makes "publish all three or none"
possible one layer up.

## Boundaries held

**Assembly is not validation.** The assembler builds objects and records what it could not build.
Reachability, cycles, unknown flags, uncovered options all need the assembled aggregate, so they
stay in `DecisionTreeValidator`. The two run in sequence; their outputs are joined for the BA.

**No database.** Version and status are supplied by the caller through `VersionPolicy`
(`form -> int`). The orchestrating service reads `MAX(version)` per form and hands it in, so the
assembler stays a pure function of the workbook — which is what makes the whole test suite run in
memory.

## Two allocation decisions

**Flags are sliced per form; value sets are not.** `flagsFor(ECB)` gives ECB its four flags, but
every definition receives the complete `flagValueSets` map, because ECB must be able to READ a
FED-written code to render an info panel. `Set By` governs writing, not reading.

**Outcomes go to PRELIMINARY only.** ECB and FED express results as flags. Handing them an empty
catalogue is deliberate: an `outcome=` clause mistakenly authored on an ECB branch then fails with
`OUTCOME_NOT_DECLARED` instead of importing quietly and doing nothing at runtime.

## The test that matters

`all_three_assembled_definitions_are_structurally_valid` runs the real `DecisionTreeValidator`
over the assembler's output. Parser and validator were written against the same template from
opposite ends; nothing else in the suite would catch them disagreeing. Two negative cases sit
beside it — a dangling `goTo` (parses fine, only the validator sees it) and ECB writing a FED-only
code.

## Next

`SourceLocator` (question key -> row, so validation errors get a cell), the JSONB serialiser, the
`leverage_decision_tree_definition` persistence adapter, and the management-layer
`@SpringBootTest`.

---

# Part 4: SourceLocator

`SourceIndex` (recorder) + `SourceLocator` (port) + `ExcelSourceLocator` (adapter) +
`ValidationReportAssembler`.

## The split

Two lookups combine to make a cell reference. The **column** comes from a fixed `Aspect` table,
because an aspect maps one-to-one onto a template column — that is a property of the template, not
of any workbook. Only the **row** varies, and only the parser ever knew it, so only the row is
recorded.

`SourceIndex` is threaded through the four sheet parsers as an explicit parameter. The assembler
creates it, so `AssembledWorkbook` can hand back a ready `locator()`. Tests that do not care pass
`SourceIndex.discarding()`.

## Decisions

**Empty rather than a guess.** An unrecorded question yields no location, and the report falls back
to `ECB / Q-S04 / branch 2`. A half-right cell reference sends a BA to the wrong row, which is
worse than being told the logical position.

**A branch index becomes a line number.** `branchIndex 1` renders as
`row 15, column 'Branches' (line 2)` — 0-based inside, 1-based for the reader. That precision
matters most exactly where the grammar is densest.

**Parse issues print before validation errors.** An unreadable Branches cell leaves the question
with no branches, so it is also reported as a dead end: one cause, two symptoms, and the cause
should be read first.

## ArchUnit: 25 classes in one package

Over the 20 limit. Suggested split, all under `application.leverage.definitionimport`:

| Sub-package | Classes |
|---|---|
| `.workbook` | `WorkbookSource`, `SheetTable`, `TableRow`, `Cells`, `Expressions` (5) |
| `.grammar` | `ConditionExpressionParser`, `BranchExpressionParser`, `ValueRuleExpressionParser`, `LabelParser`, `OptionsParser` (5) |
| `.sheet` | `FormsSheetParser`, `FlagValuesSheetParser`, `FieldsSheetParser`, `QuestionSheetParser`, `FormMetadata`, `ParsedCatalogues` (6) |
| *(root)* | `DecisionTreeAssembler`, `AssembledWorkbook`, `ImportIssue`, `ImportIssues`, `SourceLocation`, `SourceIndex`, `SourceLocator`, `ExcelSourceLocator`, `ValidationReportAssembler` (9) |

`Expressions` and `TableRow` are package-private today — moving them means widening them or
keeping their collaborators together. Worth doing in one pass at the end, as agreed.

Also worth folding at that point: `ImportIssues` + `SourceIndex` are both per-import scratchpads
threaded side by side. One `ImportContext` holding both would cut a parameter from every parse
signature.

## Next

JSONB serialiser, `leverage_decision_tree_definition` persistence adapter, then the
management-layer `@SpringBootTest`.
