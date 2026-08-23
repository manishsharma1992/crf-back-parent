package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.routing;

/**
 * How a CHECKLIST's item answers collapse into a routing decision.
 *
 * <p>These are strict complements, so exactly one always matches and a checklist can never
 * strand the walk. NOT_APPLICABLE is system-assigned and never triggers.
 */
public enum Aggregate {
    /** At least one item is YES. */
    ANY_YES,
    /** No item is YES — NO and NOT_APPLICABLE both count as non-triggering. */
    ALL_NO
}
