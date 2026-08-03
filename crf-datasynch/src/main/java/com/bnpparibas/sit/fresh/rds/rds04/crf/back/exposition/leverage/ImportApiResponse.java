package com.bnpparibas.sit.fresh.rds.rds04.crf.back.exposition.leverage;

/**
 * What the import endpoint returns: either the report, or a reason the upload never reached the
 * importer.
 *
 * <p>Sealed rather than {@code ResponseEntity<?>}. The wildcard said "some object", which is both
 * un-Sonar-able and less true than it looks — there are exactly two shapes, and naming them means
 * a reader of the signature knows what a caller has to handle.
 */
public sealed interface ImportApiResponse permits DecisionTreeImportResponse, ApiError {
}
