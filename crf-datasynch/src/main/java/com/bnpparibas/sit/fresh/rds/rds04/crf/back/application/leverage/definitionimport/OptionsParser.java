package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.ChecklistItem;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.Option;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LocalizedLabel;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads the {@code Options} and {@code Items} cells, which share one grammar:
 * {@code CODE|English|French} entries separated by {@code ;}.
 *
 * <p>A LOOKUP question is the exception: its list comes from a service at runtime, so its Options
 * cell names the SOURCE ({@code LOOKUP/COUNTERPARTY}) instead of spelling values out. That is kept
 * as a single valueless option so the rest of the model does not need a special case.
 */
@DomainDrivenDesign.ApplicationService
public final class OptionsParser {

    static final String LOOKUP_PREFIX = "LOOKUP/";

    public List<Option> parseOptions(String cell, SourceLocation where, ImportIssues issues) {
        if (cell != null && cell.trim().startsWith(LOOKUP_PREFIX)) {
            return List.of(new Option(cell.trim(), null));
        }
        List<Option> options = new ArrayList<>();
        for (Entry e : entries(cell, where, "option", issues)) {
            options.add(new Option(e.code, new LocalizedLabel(e.en, e.fr)));
        }
        return options;
    }

    public List<ChecklistItem> parseItems(String cell, SourceLocation where, ImportIssues issues) {
        List<ChecklistItem> items = new ArrayList<>();
        for (Entry e : entries(cell, where, "checklist item", issues)) {
            items.add(new ChecklistItem(e.code, new LocalizedLabel(e.en, e.fr)));
        }
        return items;
    }

    private List<Entry> entries(String cell, SourceLocation where, String what, ImportIssues issues) {
        List<Entry> entries = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (String token : Expressions.splitTopLevel(cell == null ? "" : cell, ';')) {
            String[] parts = token.split("\\|", -1);
            if (parts.length < 3) {
                issues.add(where, "ENTRY_MALFORMED",
                        "Each " + what + " must read CODE|English|French; got '" + token + "'");
                continue;
            }
            String code = parts[0].trim();
            if (code.isEmpty()) {
                issues.add(where, "ENTRY_BLANK_CODE", "An " + what + " has a blank code: '" + token + "'");
                continue;
            }
            if (!seen.add(code)) {
                issues.add(where, "ENTRY_DUPLICATE", "Duplicate " + what + " code '" + code + "'");
                continue;
            }
            entries.add(new Entry(code, blankToNull(parts[1]), blankToNull(parts[2])));
        }
        return entries;
    }

    private static String blankToNull(String s) {
        String t = s == null ? null : s.trim();
        return t == null || t.isEmpty() ? null : t;
    }

    private record Entry(String code, String en, String fr) {
    }
}
