package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.exception;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception.AnalysisNotModifiableException;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception.AnalysisNotValidatableException;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception.ConcurrentValidationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Merge into the existing advice if there is one rather than adding a second.
 *
 * <p>All three are expected outcomes of a race or a stale tab, not faults. Logging
 * them at ERROR would train everyone to ignore the log, so they are mapped and
 * left alone.
 *
 * <p>The statuses differ because the client does different things: 422 means
 * re-fetch availability and show the reason, 409 means reload because the world
 * moved.
 */
@RestControllerAdvice
public class LeverageValidationExceptionHandler {

    @ExceptionHandler(AnalysisNotValidatableException.class)
    public ProblemDetail onNotValidatable(AnalysisNotValidatableException e) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
        problem.setProperty("blocker", e.getBlocker());
        problem.setProperty("missingFieldKeys", e.getMissingFieldKeys());
        return problem;
    }

    @ExceptionHandler(ConcurrentValidationException.class)
    public ProblemDetail onConcurrent(ConcurrentValidationException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }

    @ExceptionHandler(AnalysisNotModifiableException.class)
    public ProblemDetail onNotModifiable(AnalysisNotModifiableException e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, e.getMessage());
    }
}
