package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports;

/**
 * PORT. Fills the panels a form is showing with what RMPM currently holds.
 *
 * <p>Deliberately narrow: WHICH panels are triggered is decided upstream by {@code InfoPanelSelector},
 * a pure domain service. All that is left here is the read.
 *
 * <p>The definition comes in because a stored value is meaningless without the value set that
 * decodes it — {@code leveraged = 2} is "INR" only by reference to the LEVERAGED_FLAG set, and that
 * set lives in the pinned definition. Decoding here rather than on the client is what lets the
 * snapshot stay readable after a later import rewords it.
 */
public interface InfoPanelResolver {

    /**
     * @param definition the pinned tree, for the value sets a coded field is rendered through
     * @param triggered  panels whose flag condition already holds; never null, often empty
     * @param subject    the counterparty under analysis, whose RMPM row is displayed
     * @param locale     the language the analyst is working in — frozen into the snapshot
     */
    List<PanelSnapshot> resolve(DecisionTreeDefinition definition, List<InfoPanel> triggered,
                                AnalysisSubject subject, String locale);

    /** For PRELIMINARY, which declares no panels, and for tests that do not care. */
    static InfoPanelResolver none() {
        return (definition, triggered, subject, locale) -> List.of();
    }
}
