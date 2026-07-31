package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * The result of one import attempt: what happened, what the BA needs to fix, and what is now live.
 *
 * <p>A rejection is a RESULT, not an exception. A workbook with a typo is an ordinary outcome of
 * asking someone to fill in a spreadsheet, and the caller needs the whole report to render it —
 * throwing would reduce that to a message and a stack trace. Exceptions stay for what they are
 * for: the file is not a workbook, or the database is gone.
 *
 * @param report            every parse issue and validation error, each pointed at a cell
 * @param publishedVersions the version now in force per form; empty unless status is PUBLISHED
 */
public record ImportOutcome(ImportStatus status,
                            List<String> report,
                            Map<LeverageFormType, Integer> publishedVersions,
                            Instant at) {

    public ImportOutcome {
        report = report == null ? List.of() : List.copyOf(report);
        publishedVersions = publishedVersions == null ? Map.of() : Map.copyOf(publishedVersions);
    }

    public boolean isRejected() {
        return status == ImportStatus.REJECTED;
    }
}
