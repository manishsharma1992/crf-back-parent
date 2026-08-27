package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import java.time.Clock;
import java.time.Instant;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception.AnalysisNotFoundException;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception.ConcurrentValidationException;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.port.AnalysisStatusRepository;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisStatus;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisStatusChange;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.FormCompleteness;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageAnalysis;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * BR02 - moves a leverage analysis from DRAFT to VALIDATED.
 *
 * <h2>Transaction boundary</h2>
 *
 * <p>One transaction spans the status change and the history append. They must be
 * atomic: a VALIDATED row with no audit line is a regulatory gap, and an audit
 * line for a transition that did not happen is worse. Nothing else belongs
 * inside - no notification, no cache eviction, no rating trigger. Those are
 * post-commit concerns, and a failing side effect must not roll back a
 * legitimate validation.
 *
 * <p>The completeness read runs inside the same transaction and is readOnly, so
 * it joins this one rather than opening its own. That matters: evaluated in a
 * separate transaction it could observe a save that this one cannot see.
 *
 * <h2>Why the aggregate is mutated but never saved</h2>
 *
 * <p>{@code analysis.validate(...)} mutates in-memory state and returns the audit
 * row, but this use case never calls save on it. Persistence happens through
 * {@link AnalysisStatusRepository#compareAndSetStatus} instead.
 *
 * <p>That is deliberate, and it is the subtle part of this class. The aggregate is
 * a pure domain object, not a managed entity, so there is no dirty-check flush at
 * commit. If it were ever mapped as an entity, that flush would issue an
 * unconditional {@code update ... set status = 'VALIDATED'} and the losing
 * request in a race would fail the compare-and-set and then overwrite anyway,
 * defeating the whole mechanism. Keep the aggregate unmanaged.
 *
 * <h2>Concurrency</h2>
 *
 * <p>Two requests can both pass the completeness guard - it reads state, it does
 * not lock. Only one will match {@code status = DRAFT} in the conditional UPDATE.
 * The loser gets ConcurrentValidationException and rolls back, so no orphan
 * history row survives. READ_COMMITTED is sufficient; the CAS predicate does the
 * work a version column would.
 */
@Service
@DomainDrivenDesign.ApplicationService
@RequiredArgsConstructor
public class ValidateLeverageAnalysisUseCase {

    private final LeverageAnalysisRepository analyses;
    private final AnalysisStatusRepository statusRepository;
    private final AnalysisCompletenessService completenessService;
    private final Clock clock;

    /**
     * @param validatedBy authenticated user, passed in by the controller from the
     *                    security context rather than read here - which keeps this
     *                    testable and callable from a batch context later
     */
    @Transactional
    public AnalysisStatusChange validate(String analysisUid, String validatedBy) {
        LeverageAnalysis analysis = analyses.findByAnalysisUid(analysisUid)
                .orElseThrow(() -> new AnalysisNotFoundException(analysisUid));

        // Recomputed from stored responses, never taken from the client. The
        // Validate button is a hint; this is the decision. Same service the
        // availability endpoint calls, so the two cannot disagree.
        FormCompleteness completeness = completenessService.evaluate(analysis);

        Instant validatedAt = clock.instant();
        AnalysisStatusChange statusChange = analysis.validate(validatedBy, validatedAt, completeness);

        boolean transitioned = statusRepository.compareAndSetStatus(
                analysisUid, AnalysisStatus.DRAFT, AnalysisStatus.VALIDATED, validatedBy, validatedAt);
        if (!transitioned) {
            throw new ConcurrentValidationException(analysisUid);
        }

        statusRepository.appendHistory(statusChange);
        return statusChange;
    }
}
