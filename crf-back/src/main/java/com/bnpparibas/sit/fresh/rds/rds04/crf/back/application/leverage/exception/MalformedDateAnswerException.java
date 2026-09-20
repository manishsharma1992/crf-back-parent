package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.exception;

public class MalformedDateAnswerException extends RuntimeException {

    private final transient List<String> keys;

    public MalformedDateAnswerException(List<String> keys) {
        super("Not a valid date (expected yyyy-MM-dd): " + String.join(", ", keys));
        this.keys = List.copyOf(keys);
    }

    public List<String> keys() {
        return keys;
    }
}