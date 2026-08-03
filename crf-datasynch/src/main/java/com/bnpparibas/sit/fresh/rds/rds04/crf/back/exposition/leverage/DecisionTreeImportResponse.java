package com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.exposition.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.management.leverage.definitionimport.ImportOutcome;
import com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.management.leverage.definitionimport.ImportStatus;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * What the caller gets back from an import.
 *
 * <p>The REPORT is the payload that matters. On a rejection it is the entire value of the
 * response — a BA needs every cell reference at once, not the first one — and on success it is
 * empty, which is itself the confirmation.
 *
 * @param report            one line per problem, each naming sheet, row and column
 * @param publishedVersions version now in force per form; empty unless something was published
 */
public record DecisionTreeImportResponse(ImportStatus status,
                                         String summary,
                                         List<String> report,
                                         Map<LeverageFormType, Integer> publishedVersions,
                                         Instant importedAt) implements ImportApiResponse {

    public static DecisionTreeImportResponse from(ImportOutcome outcome) {
        return new DecisionTreeImportResponse(
                outcome.status(),
                summarise(outcome),
                outcome.report(),
                outcome.publishedVersions(),
                outcome.at());
    }

    private static String summarise(ImportOutcome outcome) {
        return switch (outcome.status()) {
            case PUBLISHED -> "All three forms were published.";
            case VALIDATED -> "The workbook is valid. Nothing was published — this was a dry run.";
            case REJECTED -> outcome.report().size() + " problem(s) found. Nothing was published.";
        };
    }
}
