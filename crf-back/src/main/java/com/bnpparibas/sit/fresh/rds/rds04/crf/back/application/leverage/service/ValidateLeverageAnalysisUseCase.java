package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import java.time.Clock;
import java.time.Instant;

import com.bnpparibas.crf.shared.domain.leverage.exception.AnalysisNotFoundException;
import com.bnpparibas.crf.shared.domain.leverage.exception.ConcurrentValidationException;
import com.bnpparibas.crf.shared.domain.leverage.model.AnalysisStatus;
import com.bnpparibas.crf.shared.domain.leverage.model.AnalysisStatusChange;
import com.bnpparibas.crf.shared.domain.leverage.model.CompletenessInput;
import com.bnpparibas.crf.shared.domain.leverage.model.FormCompleteness;
import com.bnpparibas.crf.shared.domain.leverage.model.LeverageAnalysis;
import com.bnpparibas.crf.shared.domain.leverage.port.AnalysisStatusRepository;
import com.bnpparibas.crf.shared.domain.leverage.port.LeverageAnalysisRepository;
import com.bnpparibas.crf.shared.domain.leverage.service.FormCompletenessDomainService;
import org.springframework.transaction.annotation.Transactional;

/**
 * BR02 - moves a leverage analysis from DRAFT to VALIDATED.
 *
 * <h2>Transaction boundary</h2>
 *
 * <p>One transaction spans the status change and the history append. They must be
 * atomic: a VALIDATED row with no audit line is a regulatory gap, and an audit
 * line for a transition that did not happen is worse. Nothing else belongs inside
 * this boundary - no notification, no rating trigger, no cache eviction. Those
 * are post-commit concerns and adding them here would mean a failing side effect
 * could roll back a legitimate validation.
 *
 * <h2>Why the aggregate is mutated but never saved</h2>
 *
 * <p>{@code analysis.validate(...)} mutates in-memory state and returns the audit
 * row, but this use case never calls save on it. The persistence happens through
 * {@link AnalysisStatusRepository#compareAndSetStatus} instead.
 *
 * <p>That is deliberate and it is the subtle part of this class. The aggregate is
 * a pure domain object in crf-shared with no JPA annotations, so it is not a
 * managed entity and there is no dirty-check flush at commit. If it were ever
 * mapped as an entity, the flush would issue an unconditional
 * {@code update ... set status = 'VALIDATED'} that would silently undo the whole
 * point of the compare-and-set - the second concurrent request would fail the CAS
 * and then overwrite anyway. Keep the aggregate unmanaged.
 *
 * <p>The in-memory mutation is retained because the aggregate is where the
 * invariant lives; it guards, then reports what changed.
 *
 * <h2>Concurrency</h2>
 *
 * <p>Two requests can both pass the completeness guard - it reads state, it does
 * not lock. Only one will match {@code status = DRAFT} in the conditional UPDATE.
 * The loser gets ConcurrentValidationException and its transaction rolls back, so
 * no orphan history row is left behind. Default READ_COMMITTED isolation is
 * sufficient; the CAS predicate is doing the work a version column would.
 */
public class ValidateLeverageAnalysisUseCase {

    private final LeverageAnalysisRepository analysisRepository;
    private final AnalysisStatusRepository statusRepository;
    private final FormCompletenessDomainService completenessService;
    private final CompletenessInputAssembler completenessInputAssembler;
    private final Clock clock;

    public ValidateLeverageAnalysisUseCase(LeverageAnalysisRepository analysisRepository,
                                           AnalysisStatusRepository statusRepository,
                                           FormCompletenessDomainService completenessService,
                                           CompletenessInputAssembler completenessInputAssembler,
                                           Clock clock) {
        this.analysisRepository = analysisRepository;
        this.statusRepository = statusRepository;
        this.completenessService = completenessService;
        this.completenessInputAssembler = completenessInputAssembler;
        this.clock = clock;
    }

    /**
     * @param analysisUid analysis to validate
     * @param validatedBy authenticated user, supplied by the controller from the
     *                    security context - this layer does not reach for it,
     *                    which keeps the use case testable and callable from a
     *                    batch context later
     */
    @Transactional
    public AnalysisStatusChange validate(String analysisUid, String validatedBy) {
        LeverageAnalysis analysis = analysisRepository.findByUid(analysisUid)
                .orElseThrow(() -> new AnalysisNotFoundException(analysisUid));

        // Completeness is recomputed from stored responses, never taken from the
        // client. The Validate button is a hint; this is the decision.
        CompletenessInput input = completenessInputAssembler.assemble(analysis);
        FormCompleteness completeness = completenessService.evaluate(analysis.getStatus(), input);

        Instant validatedAt = clock.instant();
        AnalysisStatusChange statusChange = analysis.validate(validatedBy, validatedAt, completeness);

        boolean transitioned = statusRepository.compareAndSetStatus(
                analysisUid,
                AnalysisStatus.DRAFT,
                AnalysisStatus.VALIDATED,
                validatedBy,
                validatedAt);
        if (!transitioned) {
            throw new ConcurrentValidationException(analysisUid);
        }

        statusRepository.appendHistory(statusChange);
        return statusChange;
    }
}
