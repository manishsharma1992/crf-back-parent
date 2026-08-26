package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.rating.value;

import java.time.Instant;

/**
 * The leverage analysis a rating consumed, stored inside
 * {@code counterparty_rating.model_specific_data}.
 *
 * <p>Lives in the jsonb rather than as a column because only some models are
 * leverage-backed; a column would be null for the majority of rows and would
 * imply every model has the concept.
 *
 * <p>Deliberately denormalised. The obvious minimal design is to store only
 * analysisUid and join for the rest, but two things argue against it:
 *
 * <ul>
 *   <li>An audit answer should be self-contained. "This rating was produced from
 *       the ECB-leveraged analysis validated on 12-Mar against workbook v12" is a
 *       statement the rating row can make alone, without a live join to a system
 *       that may have been reorganised by the time anyone asks.</li>
 *   <li>Denormalisation is only dangerous when the source can drift. A validated
 *       analysis is frozen, so these copies can never disagree with the source.
 *       This is the same premise that makes the BR03 projection safe.</li>
 * </ul>
 *
 * <p>Note there is no referential integrity here - jsonb cannot carry a foreign
 * key. Nothing in the database will stop a leverage analysis from being deleted
 * out from under a rating that cites it. That is not a problem today, since
 * validated analyses are immutable and undeletable, but it is a constraint the
 * parked edit/delete ticket must respect rather than discover.
 *
 * @param analysisUid        business key of the consumed analysis
 * @param analysisId         surrogate key, for joins; the uid remains the durable
 *                           identifier if ids are ever remapped
 * @param financialArchiveId FINSTAR archive the analysis was built on
 * @param recommendedOutcome leveraged / non-leveraged, as decided at validation
 * @param ecbDefinitionId    tree definition version answered, null if not routed to ECB
 * @param fedDefinitionId    tree definition version answered, null if not routed to FED
 * @param validatedTimestamp when the analysis was validated
 * @param consumedTimestamp  when this rating read it; differs from
 *                           validatedTimestamp whenever a rating is re-run
 */
public record LeverageAnalysisReference(String analysisUid,
                                        Long analysisId,
                                        String financialArchiveId,
                                        String recommendedOutcome,
                                        Long ecbDefinitionId,
                                        Long fedDefinitionId,
                                        Instant validatedTimestamp,
                                        Instant consumedTimestamp) {
}
