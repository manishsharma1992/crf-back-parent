package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.port;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisSnapshotView;
import java.util.List;
import java.util.Optional;

/**
 * BR03 read port - projects a validated analysis and its history row into the
 * snapshot shown to the user.
 *
 * <p>No snapshot is copied at validation time. Because a validated analysis can
 * never return to DRAFT or be edited, the {@code leverage_analysis} row is frozen
 * from the moment of transition, so projecting over it IS the snapshot. This is
 * what makes the JPA projection safe here where it normally would not be.
 *
 * <p>The day the planned relaxation lands (edit/delete of a validated analysis not
 * yet used in a rating), that premise breaks and this port must switch to a
 * materialised copy. The seam is deliberately a port so only the adapter changes.
 */
public interface AnalysisSnapshotResolver {

    Optional<AnalysisSnapshotView> findByAnalysisUid(String analysisUid);

    /** History for one counterparty analysis chain, newest validation first. */
    List<AnalysisSnapshotView> findHistory(String analysisUid);
}
