package com.bnpparibas.sit.fresh.rds.rds04.crf.back.exposition.leverage;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.ReportLine;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.management.leverage.definitionimport.ImportOutcome;
import com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.management.leverage.definitionimport.ImportStatus;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * What the caller gets back.
 *
 * <p>Both shapes travel, and here that duplication is deliberate rather than a smell: {@code lines}
 * is what the report table renders, {@code report} is what a human pastes into a ticket or reads in
 * a log. They cannot disagree, because {@code report} is derived from {@code lines} in
 * {@link ImportOutcome} rather than assembled separately.
 */
public record DecisionTreeImportResponse(ImportStatus status,
                                         String summary,
                                         List<String> report,
                                         List<ReportLine> lines,
                                         Map<LeverageFormType, Integer> publishedVersions,
                                         Instant importedAt) implements ImportApiResponse {

    public static DecisionTreeImportResponse from(ImportOutcome outcome) {
        return new DecisionTreeImportResponse(
                outcome.status(),
                summarise(outcome),
                outcome.report(),
                outcome.lines(),
                outcome.publishedVersions(),
                outcome.at());
    }

    private static String summarise(ImportOutcome outcome) {
        return switch (outcome.status()) {
            case PUBLISHED -> "All three forms were published.";
            case VALIDATED -> "The workbook is valid. Nothing was published — this was a dry run.";
            case REJECTED -> outcome.lines().size() + " problem(s) found. Nothing was published.";
        };
    }
}
