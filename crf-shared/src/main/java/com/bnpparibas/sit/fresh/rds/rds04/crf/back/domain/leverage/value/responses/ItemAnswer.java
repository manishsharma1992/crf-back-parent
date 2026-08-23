package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses;

/**
 * An analyst's answer to ONE checklist item.
 *
 * <p>Tri-state, and the third state is not the analyst's to choose: {@code NOT_APPLICABLE} is
 * assigned by the system when a YES elsewhere in the block settles it. The UI offers Yes and No
 * only.
 */
public enum ItemAnswer {
    YES,
    NO,
    /** System-assigned. Never triggers a routing aggregate. */
    NOT_APPLICABLE
}
