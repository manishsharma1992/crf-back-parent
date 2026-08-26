/*
 * ---------------------------------------------------------------------------
 * ILLUSTRATIVE, NOT FOR MERGE. Shows how LeverageAnalysisReference composes into
 * the existing model_specific_data types. Adapt to whatever those types are
 * actually called.
 * ---------------------------------------------------------------------------
 */
package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.rating.value;

import java.util.Optional;

/** Sealed so the jsonb polymorphism is exhaustive and Jackson can be told the shape. */
public sealed interface ModelSpecificData permits PlacmModelData, PprfmModelData {
}

/** A leverage-backed model: composes the reference rather than inheriting from it. */
record PlacmModelData(String someExistingField,
                      LeverageAnalysisReference leverageAnalysisReference)
        implements ModelSpecificData, LeverageBackedModelData {

    @Override
    public Optional<LeverageAnalysisReference> leverageAnalysis() {
        return Optional.ofNullable(leverageAnalysisReference);
    }
}

/** A model that does not use leverage: does not implement LeverageBackedModelData. */
record PprfmModelData(String someOtherField) implements ModelSpecificData {
}
