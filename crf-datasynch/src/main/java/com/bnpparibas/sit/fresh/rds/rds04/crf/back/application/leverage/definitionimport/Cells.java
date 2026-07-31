package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import java.util.Arrays;
import java.util.List;

/** Small shared helpers for cell text. */
public final class Cells {

    private Cells() {
    }

    /** Splits on {@code ;}, trims, drops blanks. Used for Locales, Forms To Show, Fields. */
    public static List<String> splitList(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split(";")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** Splits a multi-line cell into trimmed, non-blank lines, preserving order. */
    public static List<String> lines(String raw) {
        if (raw == null || raw.isBlank()) return List.of();
        return Arrays.stream(raw.split("\\R")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    /** Normalises a header for comparison: lower case, collapsed whitespace. */
    public static String normaliseHeader(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase().replaceAll("\\s+", " ");
    }
}
