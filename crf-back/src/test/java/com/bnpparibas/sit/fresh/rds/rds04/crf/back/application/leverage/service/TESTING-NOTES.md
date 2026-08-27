# Test suite notes

## Two files you must adapt before anything compiles

| File | Why |
|---|---|
| `fixture/FormStateFixture` | `FormState` has grown — `FormStateAssembler` passes `FlagView` and `panels` that the record I was given does not declare. Every test that needs a `FormState` comes through here, so it is one edit. |
| `service/LeverageAnalysisTestBuilder` | The aggregate's construction is the one thing I could not write for you. If there is no public builder, add a package-private test factory on the aggregate rather than reaching for reflection — reflection would keep the suite green through a refactor that ought to break it. |

## What is deliberately not mocked

`AnalysisCompletenessServiceTest` uses the **real** `AnalysisCompletenessDomainService`.
Stubbing it would leave the interesting behaviour — which forms get gathered, what
happens when one of them throws — asserted against nothing.

## The tests that earn their place

Coverage is a floor, not the goal. If you keep only five of these:

1. **`neverSavesTheAggregate`** — the tripwire for someone "fixing" the missing
   save. That flush would issue an unconditional status update and silently defeat
   the compare-and-set.
2. **`failsAndAppendsNoHistoryWhenAnotherRequestWonTheRace`** — a history row for
   a transition that did not happen is worse than no row.
3. **`turnsAStrandedDownstreamDefinitionIntoABlockerRatherThanAFailure`** — without
   the catch, a broken FED definition takes the Validate button away from an
   analyst working in ECB.
4. **`ignoresWarningsAndCountsOnlyErrors`** — the BA's rule, and the one most
   likely to be quietly widened later.
5. **`takesTheArchiveIdFromTheFrozenSelection`** — pins BR03's whole premise: the
   snapshot reports what was concluded, not what is true now.

## What 80% will not catch

- **The JPQL actually running.** `AnalysisSnapshotDao`'s fetch join and
  `compareAndSetStatus`'s bulk update are both mocked out here. A `@DataJpaTest`
  against Testcontainers Postgres is the only thing that proves the query parses
  and that `@JdbcTypeCode(SqlTypes.JSON)` round-trips `LeverageResponses`. Worth
  one integration test per query.
- **The concurrency guarantee itself.** `compareAndSetStatus` returning `false` is
  stubbed, never raced. A two-thread test against a real database is the only way
  to prove the conditional UPDATE does what the design claims.
- **`assertModifiable()` in the save paths.** The guard belongs in both
  `SaveLeverageFormUseCase` and `SavePreliminaryFormUseCase`; nothing here proves
  either calls it. Add one test per save use case asserting a validated analysis
  is refused.

## Dependencies

All from `spring-boot-starter-test`: JUnit 5, Mockito (`mockito-junit-jupiter`),
AssertJ. `junit-jupiter-params` for the `@ParameterizedTest`.
