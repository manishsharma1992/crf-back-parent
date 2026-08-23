package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses;

/**
 * Where a walk stopped.
 *
 * <p>Two states, not three. The old {@code AWAITING_EXTERNAL} is gone with the rating-motor call:
 * every calculation now happens in the domain layer, so a walk never pauses on another service and
 * never has to be resumed by a callback.
 */
public enum TraversalState {
    /** Stopped on a question the analyst must answer. */
    PENDING_INPUT,
    /** The form is finished. */
    TERMINAL,
    /**
     * The walk could not continue: no branch matched, or a branch pointed nowhere. The published
     * validator makes this unreachable, so it means a definition was published around it.
     */
    STRANDED
}
