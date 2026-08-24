/*
 * ---------------------------------------------------------------------------
 * FRAGMENT, NOT A COMPLETE FILE.
 *
 * These members are to be added to the existing LeverageAnalysis aggregate.
 * The surrounding class is unchanged; only the fields referenced below
 * (analysisUid, status, validatedBy, validatedTimestamp) are assumed to exist
 * already. Confirm the exact accessor names before merging.
 * ---------------------------------------------------------------------------
 */

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
        if (status != LeverageAnalysisStatus.DRAFT) {
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
            throw new AnalysisNotValidatableException(
                    analysisUid, completeness.blocker(), completeness.missingFieldKeys());
        }
        LeverageAnalysisStatus previousStatus = this.status;
        this.status = LeverageAnalysisStatus.VALIDATED;
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
        return status == LeverageAnalysisStatus.VALIDATED;
    }
