package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.exception;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;

/**
 * A walk could not continue: no branch matched, or one pointed at a question that is not there.
 *
 * <p>An exception rather than a {@code FormState}, because there is nothing for an analyst to do
 * about it. The published validator makes it unreachable, so it means a definition was published
 * around the validator — an operational fault to surface loudly, not a screen to render.
 */
public class StrandedTraversalException extends RuntimeException {

    private final transient LeverageFormType formType;
    private final transient int version;

    public StrandedTraversalException(LeverageFormType formType, int version) {
        super("Traversal of " + formType + " definition v" + version
                + " could not continue: no branch matched, or a branch pointed at a missing question");
        this.formType = formType;
        this.version = version;
    }

    public LeverageFormType formType() {
        return formType;
    }

    public int version() {
        return version;
    }
}
