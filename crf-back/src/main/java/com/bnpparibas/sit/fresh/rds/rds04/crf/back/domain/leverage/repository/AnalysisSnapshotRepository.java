package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.repository;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisSnapshotView;
import java.util.List;
import java.util.Optional;

/**
 * BR03 read port - projects a validated analysis and its history row into the
 * snapshot shown to the user.
 *
 * <p>Nothing is copied at validation time. Because a validated analysis can never
 * return to DRAFT or be edited, its row is frozen from the moment of transition,
 * so projecting over it IS the snapshot. That premise is what makes a projection
 * safe here where it normally would not be.
 *
 * <p>If the parked edit/delete ticket ever lands, the premise breaks and this port
 * must switch to a materialised copy. It is a port precisely so that only the
 * adapter changes.
 */
public interface AnalysisSnapshotRepository {

    Optional<AnalysisSnapshotView> findLatest(String analysisUid);

    /** Every validation of this analysis, newest first. */
    List<AnalysisSnapshotView> findHistory(String analysisUid);
}
