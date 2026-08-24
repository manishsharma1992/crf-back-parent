package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.validation;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BR03 - the snapshot line rendered for a validated analysis.
 *
 * @param analysisUid         business identifier of the analysis
 * @param financialId         FINSTAR archive identifier the analysis was built on
 * @param formType            ECB or FED
 * @param flags               resolved flags for the form, in definition order
 * @param validatedBy         from leverage_analysis
 * @param validatedTimestamp  from leverage_analysis
 * @param changedBy           from leverage_analysis_history
 * @param changedTimestamp    from leverage_analysis_history
 * @param fromStatus          from leverage_analysis_history
 * @param toStatus            from leverage_analysis_history
 *
 * <p>Flags are a LinkedHashMap, not a typed record: different tree versions carry
 * different flag sets, and a fixed record would break replay of analyses
 * validated under an earlier workbook. Insertion order is load-bearing for
 * display, so this must never become Map.copyOf.
 */
public record AnalysisSnapshotView(String analysisUid,
                                   String financialId,
                                   LeverageFormType formType,
                                   Map<String, String> flags,
                                   String validatedBy,
                                   Instant validatedTimestamp,
                                   String changedBy,
                                   Instant changedTimestamp,
                                   LeverageAnalysisStatus fromStatus,
                                   LeverageAnalysisStatus toStatus) {

    public AnalysisSnapshotView {
        flags = new LinkedHashMap<>(flags);
    }

    @Override
    public Map<String, String> flags() {
        return new LinkedHashMap<>(flags);
    }
}
