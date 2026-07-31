package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import java.util.ArrayList;
import java.util.List;

/**
 * Splitting helpers for the authoring expression grammar.
 *
 * <p>Every separator in the grammar can also appear INSIDE a bracketed list, so a naive
 * {@code String.split} corrupts real cells:
 * <pre>
 *   Q-C02 in [ORIGINATION, MATERIAL_MODIFICATION] AND field r range [&lt;0 | &gt;6] -&gt; Q-Q04
 * </pre>
 * splitting on {@code ,} would tear the {@code in} list apart, and splitting on {@code |} would
 * tear the range apart. These helpers only split at BRACKET DEPTH ZERO.
 */
final class Expressions {

    private Expressions() {
    }

    /** Splits on a single character, ignoring anything inside {@code [ ]}. */
    static List<String> splitTopLevel(String text, char delimiter) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        for (char c : text.toCharArray()) {
            if (c == '[') depth++;
            if (c == ']') depth = Math.max(0, depth - 1);
            if (c == delimiter && depth == 0) {
                parts.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString().trim());
        parts.removeIf(String::isEmpty);
        return parts;
    }

    /**
     * Splits on a whole word (used for {@code AND}), ignoring bracketed regions and matching
     * case-insensitively so {@code and} authored in lower case still parses.
     */
    static List<String> splitOnKeyword(String text, String keyword) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int depth = 0;
        int i = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '[') depth++;
            if (c == ']') depth = Math.max(0, depth - 1);
            if (depth == 0 && matchesWordAt(text, i, keyword)) {
                parts.add(current.toString().trim());
                current.setLength(0);
                i += keyword.length();
                continue;
            }
            current.append(c);
            i++;
        }
        parts.add(current.toString().trim());
        parts.removeIf(String::isEmpty);
        return parts;
    }

    private static boolean matchesWordAt(String text, int index, String keyword) {
        int end = index + keyword.length();
        if (end > text.length()) return false;
        if (!text.regionMatches(true, index, keyword, 0, keyword.length())) return false;
        boolean leftFree = index == 0 || !Character.isLetterOrDigit(text.charAt(index - 1));
        boolean rightFree = end == text.length() || !Character.isLetterOrDigit(text.charAt(end));
        return leftFree && rightFree;
    }

    /** Returns the text between the first {@code [} and the last {@code ]}, or null. */
    static String bracketContent(String text) {
        int open = text.indexOf('[');
        int close = text.lastIndexOf(']');
        return open < 0 || close < open ? null : text.substring(open + 1, close).trim();
    }

    /** Splits at the FIRST occurrence of a separator at depth zero; null when absent. */
    static String[] splitOnceTopLevel(String text, String separator) {
        int depth = 0;
        for (int i = 0; i + separator.length() <= text.length(); i++) {
            char c = text.charAt(i);
            if (c == '[') depth++;
            if (c == ']') depth = Math.max(0, depth - 1);
            if (depth == 0 && text.startsWith(separator, i)) {
                return new String[]{text.substring(0, i).trim(), text.substring(i + separator.length()).trim()};
            }
        }
        return null;
    }
}
