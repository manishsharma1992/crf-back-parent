package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.rating.value;

import java.util.Optional;

/**
 * Implemented by the model_specific_data value objects of models that consume a
 * leverage analysis.
 *
 * <p>The point is to avoid an instanceof chain over every model type each time
 * something needs to ask "did this rating use a leverage analysis, and which
 * one?". Two callers need that today - traceability reporting, and any future
 * check of whether an analysis has been consumed - and neither should have to
 * know the list of leverage-backed models.
 *
 * <p>Models that do not use leverage simply do not implement this, so the
 * question is answered by the type system rather than by a null check.
 */
public interface LeverageBackedModelData {

    /**
     * Empty when the rating was produced before the leverage module existed, or
     * when the model is leverage-backed but this particular rating predates the
     * analysis being available.
     */
    Optional<LeverageAnalysisReference> leverageAnalysis();
}
