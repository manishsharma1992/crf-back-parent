package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.port.AnalysisSnapshotRepository;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisSnapshotView;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.FormResponses;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.FormSnapshot;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageResponses;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.SpreadsheetSelection;

/**
 * BR03 adapter.
 *
 * <p><b>No Jackson, no JsonNode.</b> The jsonb column is mapped to LeverageResponses
 * by Hibernate, so this class only ever handles domain types. Parsing a JsonNode
 * here would have put a second, hand-written reading of the payload alongside the
 * one Hibernate already does - and the two would disagree the first time
 * FormResponses gained a component.
 *
 * <p><b>Which forms apply is read from the responses, not from the definition-id
 * columns.</b> A pinned definition only records that a form was opened; a present
 * FormResponses records that it was answered. For a snapshot of what was
 * concluded, the latter is the question being asked.
 */
public class AnalysisSnapshotRepositoryImpl implements AnalysisSnapshotRepository {

    private final AnalysisSnapshotDao snapshotDao;

    public AnalysisSnapshotRepositoryImpl(AnalysisSnapshotDao snapshotDao) {
        this.snapshotDao = snapshotDao;
    }

    @Override
    public Optional<AnalysisSnapshotView> findLatest(String analysisUid) {
        return findHistory(analysisUid).stream().findFirst();
    }

    @Override
    public List<AnalysisSnapshotView> findHistory(String analysisUid) {
        return snapshotDao.findValidatedSnapshots(analysisUid).stream()
                .map(this::toView)
                .toList();
    }

    private AnalysisSnapshotView toView(LeverageAnalysisHistory history) {
        LeverageAnalysis analysis = history.getAnalysis();
        LeverageResponses responses = analysis.getResponses();
        SpreadsheetSelection selection = selectionOf(responses);

        return new AnalysisSnapshotView(
                analysis.getAnalysisUid(),
                selection == null ? null : selection.archiveId(),
                selection == null ? null : selection.companyName(),
                analysis.getRecommendedOutcome(),
                toForms(responses),
                analysis.getValidatedBy(),
                analysis.getValidatedTimestamp(),
                history.getChangedBy(),
                history.getChangedTimestamp(),
                history.getFromStatus(),
                history.getToStatus());
    }

    /**
     * One entry per form that was actually answered, in display order. A null
     * FormResponses means the analysis was never routed there.
     */
    private List<FormSnapshot> toForms(LeverageResponses responses) {
        List<FormSnapshot> forms = new ArrayList<>();
        if (responses == null) {
            return forms;
        }
        addIfPresent(forms, LeverageFormType.PRELIMINARY, responses.preliminary());
        addIfPresent(forms, LeverageFormType.ECB, responses.ecbForm());
        addIfPresent(forms, LeverageFormType.FED, responses.fedForm());
        return forms;
    }

    /**
     * Flags are taken verbatim from the frozen FormResponses. An absent flag stays
     * absent rather than becoming blank - the same rule the grammar rests on, and
     * the reason this map is not defaulted or padded here.
     */
    private void addIfPresent(List<FormSnapshot> forms,
                              LeverageFormType formType,
                              FormResponses formResponses) {
        if (formResponses == null) {
            return;
        }
        forms.add(new FormSnapshot(
                formType,
                formResponses.definitionVersion(),
                formResponses.locale(),
                formResponses.flags()));
    }

    private SpreadsheetSelection selectionOf(LeverageResponses responses) {
        return responses == null ? null : responses.spreadsheetSelection();
    }
}
