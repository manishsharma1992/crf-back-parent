package com.bnpparibas.sit.fresh.rds.rds04.crf.back.exposition.leverage;

import java.time.Instant;

/** A failure that never reached the importer — a bad upload rather than a bad workbook. */
public record ApiError(String code, String message, Instant at) {
}
