package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto;

/** How an import ended. */
public enum ImportStatus {
    /** All three forms were read, validated and written. */
    PUBLISHED,
    /** All three forms were read and validated; nothing was written because this was a rehearsal. */
    VALIDATED,
    /** Something was wrong. Nothing was written, whatever the mode. */
    REJECTED
}
