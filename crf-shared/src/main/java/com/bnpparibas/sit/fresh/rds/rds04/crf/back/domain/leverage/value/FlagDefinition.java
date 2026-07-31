package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LocalizedLabel;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

/**
 * A row of the Flags catalogue: an output flag the form can show.
 *
 * <p>Declaring a flag here is what makes "remains empty" meaningful. Every catalogued flag is
 * ALWAYS rendered; it holds no value until some branch, {@code fillsFlag} or field sets one.
 * Nothing ever writes an explicit empty.
 *
 * @param valueSet for {@link FlagStorage#CODE} only — the set naming its integer codes. NOTE the
 *                 ECB and FED leveraged flags share ONE set, so 2 (INR) is written by both.
 */
@DomainDrivenDesign.ValueObject
public record FlagDefinition(String key, LocalizedLabel display, FlagStorage storage, String valueSet) {
}
