package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.aggregate;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception.AnalysisNotModifiableException;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception.AnalysisNotValidatableException;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.exception.AnalysisNotValidatedException;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.FormResponses;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.LeverageResponses;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.validation.AnalysisStatusChange;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.validation.FormCompleteness;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.validation.ValidatedAnswer;

import java.time.Instant;
import java.util.Optional;

@Entity
@Table(name = "leverage_analysis", uniqueConstraints = @UniqueConstraints(name = "uk_leverage_analysis", columnNames = "analysisUid"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LeverageAnalysis extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "leverage_analysis_seq_gen")
    @SequenceGenerator(name = "leverage_analysis_seq_gen", sequenceName = "leveage_analysis_seq", allocationSize = 1)
    private Long id;

    @Column(name = "analysis_uid", nullable = false)
    private String analysisUid;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "financial_id", nullable = false)
    private Financials financials;

    @Enumerated(EnumType.STRING)
    @Column(name = "recommended_outcome")
    private RecommendationOutcome recommendationOutcome;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preliminary_definition_id")
    private LeverageDecisionTreeDefinition preliminaryDecisionTree;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ecb_definition_id")
    private LeverageDecisionTreeDefinition ecbDecisionTree;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fed_definition_id")
    private LeverageDecisionTreeDefinition fedDecisionTree;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "responses", columnDefinition = "jsonb")
    private LeverageResponses responses;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private AnalysisStatus status;

    @Column(name = "validated_by")
    private String validationBy;

    @Column(name = "validated_timestamp")
    private Instant validatedTimestamp;

    // Manual builder pattern definition

    public void changeSpreadsheet(Financials financials) { this.financials = financials; }

    public static LeverageAnalysis start(String analysisUid, Financials financials, SpreadsheetSelection spreadsheetSelection, LeverageDecisionTreeDefinition preliminaryDecisionTree) {
        return LeverageAnalysis.builder()
                .analysisUid(analysisUid)
                .financials(financials)
                .responses(LeverageResponses.initial(spreadsheetSelection))
                .preliminiaryDecisionTree(preliminaryDecisionTree)
                .status(AnalysisStatus.DRAFT)
                .build();
    }

    public LeverageDecisionTreeDefinition decisionTreeFor(LeverageFormType formType) {
        return switch(formType) {
            case PRELIMINARY -> preliminaryDecisionTree;
            case ECB -> ecbDecisionTree;
            case FED -> fedDecisionTree;
        };
    }

    /**
     * Freezes this analysis onto a definition version for the given form. Idempotent bu design:
     * once pinned, a later call is ignored so a session cannot drift onto a newer version
     * mid-analysis - the same guarantee {@code SavePreliminaryFormUseCase} already relies on
     */
    public void pinDecisionTree(LeverageFormType formType, LeverageDecisionTreeDefinition definition) {
        switch(formType) {
            case PRELIMINARY -> {
                if(preliminaryDecisionTree == null) {
                    preliminaryDecisionTree = definition;
                }
            }
            case ECB -> {
                if(ecbDecisionTree == null) {
                    ecbDecisionTree = definition;
                }
            }
            case FED -> {
                if(fedDecisionTree == null) {
                    fedDecisionTree = definition;
                }
            }
        }
    }

    public com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.FormResponses responsesFor(LeverageFormType formType) {
        if(responses == null) {
            return null;
        }

        return switch (formType) {
            case PRELIMINARY -> responses.preliminary();
            case ECB -> responses.ecbForm();
            case FED -> responses.fedForm();
        };
    }

    public void recordPreliminary(com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.FormResponses snapshot, RecommendationOutcome outcome) {
        this.responses = this.responses.withPreliminary(snapshot);
        this.recommendationOutcome = outcome;
    }

    public void recordEcb(com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.FormResponses snapshot) {
        this.responses = this.responses.withEcbForm(snapshot);
    }

    public void recordFed(com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.FormResponses snapshot) {
        this.responses = this.responses.withFedForm(snapshot);
    }

    /**
     * NOTE - deliberately no {@code outcome} parameter, unlike {@code recordPreliminary}
     *
     * <p>{@code recommendationOutcome} is a single column, and preliminary already writes to it. If
     * ECB and FED also wrote there, the last section saved would silently win, and an analysis
     * requiring both forms has no defined answer as to which verdict is "the" recommendation.
     * That is the open completion-metric question - resolve it with the BA before wiring outcome
     * writes into these two.
     * </p>
     */
    public void recordSection(LeverageFormType formType, com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.FormResponses snapshot) {
        switch (formType) {
            case PRELIMINARY -> throw new IllegalArgumentException("preliminary is recorded via recordPreliminary, which also sets the outcome");
            case ECB -> recordEcb(snapshot);
            case FED -> recordFed(snapshot);
        }
    }

    /**
     * The one place that answers "may this analysis be changed?".
     *
     * <p>Every mutating operation must route through this method - SaveLeverageForm
     * today, delete tomorrow - rather than testing the status inline. The BA has
     * confirmed the current rule is absolute: once validated, an analysis can
     * neither be edited nor returned to draft.
     *
     * <p>The relaxation Frederic has parked (allow edit/delete of a VALIDATED
     * analysis not yet consumed by a rating) becomes:
     *
     * <pre>
     *   if (status != DRAFT &amp;&amp; !(status == VALIDATED &amp;&amp; !usedInRating)) { throw ... }
     * </pre>
     *
     * one method body and one extra argument. That is the entire cost of being
     * ready for it, and it is why nothing speculative is being built now.
     */
    public void assertModifiable() {
        if (status != AnalysisStatus.DRAFT) {
            throw new AnalysisNotModifiableException(analysisUid, status);
        }
    }

    /**
     * BR02 - moves the analysis from DRAFT to VALIDATED.
     *
     * <p>Guards the invariant inside the aggregate rather than in the use case, so
     * the transition cannot be performed by any future caller that forgets to
     * check. Returns the audit row for the caller to persist; the aggregate does
     * not reach for a repository.
     *
     * <p>Note this validates the in-memory state only. The atomic guarantee against
     * a concurrent second validation is the compare-and-set in the repository -
     * two requests can both pass this guard, but only one will update a row.
     */
    public AnalysisStatusChange validate(String validatedBy,
                                         Instant validatedAt,
                                         FormCompleteness completeness) {
        assertModifiable();
        if (!completeness.canValidate()) {
            throw new AnalysisNotValidatableException(analysisUid, completeness);
        }
        AnalysisStatus previousStatus = this.status;
        this.status = AnalysisStatus.VALIDATED;
        this.validatedBy = validatedBy;
        this.validatedTimestamp = validatedAt;
        return new AnalysisStatusChange(
                analysisUid, previousStatus, this.status, validatedBy, validatedAt);
    }

    /**
     * True once the analysis is available to the rating engine. The rating
     * integration already selects the most recent validated analysis; this
     * accessor exists so that selection does not have to compare status enums.
     */
    public boolean isAvailableForRating() {
        return status == AnalysisStatus.VALIDATED;
    }

    /**
     * What the analyst answered to one question, readable only once the analysis
     * is validated.
     *
     * <p><b>The status guard is the point of this method.</b> A DRAFT analysis is
     * still being edited: its answers can change, and a rating built on one would
     * be built on a figure that no longer exists by the time anyone checks. The
     * rating side has no business seeing provisional answers, and making that
     * impossible here is cheaper than trusting every future caller to remember.
     *
     * <p>Reads from the frozen responses, so it never re-walks the tree - the
     * answer returned is the answer that was given, not the answer today's
     * definition would produce from the same inputs.
     *
     * @throws AnalysisNotValidatedException if the analysis has not been validated
     */
    public Optional<ValidatedAnswer> validatedAnswerTo(LeverageFormType formType, String questionKey) {
        if (status != AnalysisStatus.VALIDATED) {
            throw new AnalysisNotValidatedException(analysisUid, status);
        }
        FormResponses form = responsesFor(formType);
        if (form == null) {
            return Optional.empty();   // the analysis was never routed to this form
        }
        return form.answerTo(questionKey)
                .map(answer -> new ValidatedAnswer(formType, form.definitionVersion(),
                        questionKey, answer.value(), answer.provenance()));
    }

    /**
     * A flag from a validated analysis. Prefer this over
     * {@link #validatedAnswerTo} wherever the value is catalogued as a flag.
     *
     * <p>Flags survive a re-import; question keys do not. A rating that reads
     * "Q-S06" breaks silently the day the workbook renumbers it, whereas one that
     * reads ECB_LEVERAGED keeps working - which is exactly why the ECB and FED
     * forms express their whole conclusion as flags in the first place.
     */
    public Optional<String> validatedFlag(LeverageFormType formType, String flagName) {
        if (status != AnalysisStatus.VALIDATED) {
            throw new AnalysisNotValidatedException(analysisUid, status);
        }
        FormResponses form = responsesFor(formType);
        return form == null ? Optional.empty() : Optional.ofNullable(form.flags().get(flagName));
    }
}
