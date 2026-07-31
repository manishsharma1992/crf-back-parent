package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto;

/**
 * Whether an import is being rehearsed or published.
 *
 * <p>DRY_RUN exists because the report is the point. A BA wants to know what is wrong with a
 * workbook long before anyone is ready to change what analysts see, and rehearsing costs nothing:
 * the whole pipeline runs, the report is identical, and only the two write calls are skipped.
 */
public enum ImportMode {
    DRY_RUN,
    PUBLISH
}
