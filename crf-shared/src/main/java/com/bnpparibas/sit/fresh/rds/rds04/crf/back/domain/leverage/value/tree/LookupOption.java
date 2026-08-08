package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

/**
 * One choice offered by a LOOKUP question, resolved from reference data.
 *
 * <p>Not in {@code value.tree}: everything there is what the BA authored, and this is not authored
 * — a LOOKUP declares only WHERE its choices come from. Not in {@code value.responses} either,
 * since nothing here is frozen; what the analyst picks is stored as an ordinary answer.
 *
 * @param value what is stored and routed on — the RMPM id, stable across a re-import
 * @param label what the analyst reads, already rendered in their language
 */
@DomainDrivenDesign.ValueObject
public record LookupOption(String value, String label) {
}
