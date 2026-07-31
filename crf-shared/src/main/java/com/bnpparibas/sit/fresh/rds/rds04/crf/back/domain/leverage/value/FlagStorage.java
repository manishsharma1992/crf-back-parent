package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

/** How a flag's value is persisted in {@code counterparty_characteristics}. */
public enum FlagStorage {
    /** An integer resolved through a {@link FlagValue} set. */
    CODE,
    BOOLEAN,
    NUMBER
}
