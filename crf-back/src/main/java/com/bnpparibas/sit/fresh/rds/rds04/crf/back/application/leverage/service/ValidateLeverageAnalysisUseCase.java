package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import java.time.Clock;
import java.time.Instant;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception.AnalysisNotFoundException;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception.ConcurrentValidationException;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.port.AnalysisStatusRepository;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.repository.LeverageAnalysisRepository;
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
 * <h2>The aggregate is never mutated here, and that is load-bearing</h2>
 *
 * <p>LeverageAnalysis is a MANAGED entity. If this use case set the status on it,
 * Hibernate would flush that change before the compare-and-set ran - the CAS is
 * annotated {@code flushAutomatically = true} - and the conditional UPDATE's
 * {@code where status = 'DRAFT'} would then match nothing. The request would fail
 * with ConcurrentValidationException having raced against nothing but its own
 * flush, and the log would show two UPDATEs where there should be one.
 *
 * <p>So {@code validationTransition(...)} guards and describes; the CAS is the
 * only write. Nothing in this method makes the entity dirty, which is why
 * {@code analyses.save(...)} is absent and must stay absent.
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
        // Guards the invariant and describes the transition. Deliberately does not
        // apply it - see the class comment on why mutating here breaks the CAS.
        AnalysisStatusChange statusChange =
                analysis.validationTransition(validatedBy, validatedAt, completeness);

        boolean transitioned = statusRepository.compareAndSetStatus(
                analysisUid, AnalysisStatus.DRAFT, AnalysisStatus.VALIDATED, validatedBy, validatedAt);
        if (!transitioned) {
            throw new ConcurrentValidationException(analysisUid);
        }

        statusRepository.appendHistory(statusChange);
        return statusChange;
    }
}
