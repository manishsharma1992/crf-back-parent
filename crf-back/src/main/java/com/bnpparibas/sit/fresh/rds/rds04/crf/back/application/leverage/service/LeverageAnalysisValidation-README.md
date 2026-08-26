Two parts. Rating storage first, since it shapes what the use case doesn't need to do.## Rating storage

Your instinct is right — it belongs in `model_specific_data`, not as a column. A column would be null for most models and would imply every model has the concept.

Composition rather than a wrapper type: `LeverageAnalysisReference` sits as a field inside whichever model-specific value objects need it, and those implement `LeverageBackedModelData`. That marker means "which ratings used a leverage analysis" is answered by the type system rather than an `instanceof` chain over every model.

**Store more than the uid.** I'd denormalise `recommended_outcome`, the ECB/FED definition ids, and `validated_timestamp` alongside it. Normally that's a drift risk — here it isn't, because a validated analysis is frozen, so the copies can never disagree. The gain is that the rating row makes a complete audit statement on its own: *"produced from the ECB-leveraged analysis validated 12-Mar against workbook v12."* No live join to a system that may have been reorganised by the time anyone asks.

Also worth capturing `consumed_timestamp` separately from `validated_timestamp` — they differ whenever a rating is re-run against an older analysis.

One caveat: jsonb carries no foreign key, so nothing in the database stops an analysis from being deleted out from under a rating that cites it. Harmless today, but it's a constraint the parked ticket needs to respect rather than discover. If you query "has this analysis been consumed?", that needs a GIN index on `model_specific_data`.

A small check on the earlier answer — is the linkage already live in `counterparty_rating`, or is this the design for putting it there? If it's the latter, ratings produced before it lands won't have it, which is the gap I flagged. Worth knowing which.

## Transaction boundary

One transaction, two writes: the CAS and the history append. Atomic — a VALIDATED row with no audit line is a regulatory gap. Nothing else inside: no notification, no cache eviction, no rating trigger, or a failing side effect could roll back a legitimate validation.

**The part worth your review:** `analysis.validate(...)` mutates the aggregate but the use case never saves it. Persistence goes through `compareAndSetStatus` instead. That works only because the aggregate is a pure domain object with no JPA annotations — no dirty-check flush at commit. If it were ever a managed entity, the flush would issue an unconditional `set status = 'VALIDATED'` and the losing request would overwrite anyway, defeating the CAS entirely. Worth a comment in the code, which is there, but flagging it explicitly since it's the kind of thing a later refactor breaks silently.

Two requests can both pass the completeness guard — it reads, it doesn't lock. Only one matches `status = DRAFT`. The loser rolls back, so no orphan history row. READ_COMMITTED is enough.

Also: `@Transactional` on a class with no interface means a CGLIB proxy, so `validate` must stay non-final or the annotation is silently ignored — showing up as a missing rollback, not an error.

Next up is the assembler implementation, then `canValidate` on the form-state payload.