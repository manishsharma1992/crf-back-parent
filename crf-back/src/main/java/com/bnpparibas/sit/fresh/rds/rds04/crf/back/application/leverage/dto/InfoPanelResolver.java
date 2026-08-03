package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.formstate;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.responses.PanelSnapshot;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.InfoPanel;

import java.util.List;

/**
 * PORT. Fills the panels a form is showing with what RMPM currently holds.
 *
 * <p>Deliberately narrow: WHICH panels are triggered is decided upstream by
 * {@code InfoPanelSelector}, a pure domain service. All that is left here is the read, which is the
 * only part that needs a database — so this is the only thing an ECB test has to fake.
 *
 * <p>Implementations return snapshots ALREADY RENDERED: an integer leveraged flag has been decoded
 * through its value set before it lands in a {@link PanelSnapshot}, because the record has to stay
 * readable after a later import rewords that set.
 */
public interface InfoPanelResolver {

    /**
     * @param triggered      panels whose flag condition already holds; never null, often empty
     * @param counterpartyId the counterparty under analysis, whose RMPM row is displayed
     * @param locale         the language the analyst is working in — frozen into the snapshot
     */
    List<PanelSnapshot> resolve(List<InfoPanel> triggered, String counterpartyId, String locale);

    /** For PRELIMINARY, which declares no panels, and for tests that do not care. */
    static InfoPanelResolver none() {
        return (triggered, counterpartyId, locale) -> List.of();
    }
}
