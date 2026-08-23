package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

/**
 * Where a frozen answer came from.
 *
 * <p>Replaces the {@code boolean computed} flag, which could only say "the analyst typed it" or
 * "the system worked it out". There are now four origins, and a regulator asking "who decided
 * this?" needs them distinguished:
 *
 * <ul>
 *   <li>{@code TYPED} — the analyst chose or entered it.</li>
 *   <li>{@code COMPUTED} — derived from earlier answers by the tree's own rules, e.g. Q-S04's
 *       level of calculation, or Q-Q01 from the debt multiple.</li>
 *   <li>{@code CALCULATED} — arithmetic over the financial table, e.g. the ECB leverage ratio.</li>
 *   <li>{@code PREFILLED} — copied from another form the analyst already completed
 *       ({@code FED/Q01}). Nobody answered it HERE, and that matters when the two forms are
 *       reconciled.</li>
 *   <li>{@code SYSTEM_ASSIGNED} — the analyst was never offered the choice. Only checklist items
 *       forced to NOT_APPLICABLE once a YES settled the block.</li>
 * </ul>
 */
@DomainDrivenDesign.ValueObject
public enum AnswerProvenance {
    TYPED,
    COMPUTED,
    CALCULATED,
    PREFILLED,
    SYSTEM_ASSIGNED
}
