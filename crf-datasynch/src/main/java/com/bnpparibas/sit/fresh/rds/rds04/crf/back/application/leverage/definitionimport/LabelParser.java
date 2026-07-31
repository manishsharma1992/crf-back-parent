package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.Bullet;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LabelDetails;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LocalizedQuestionLabel;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds a bilingual label block from a text cell and its bullets cell.
 *
 * <p>Bullets are one per line, {@code -} for a top-level bullet and {@code --} for a sub-bullet,
 * which attaches to the bullet above it. The Support-Entity tooltip on Q-S02 is the reference
 * case: four bullets, the last carrying two children.
 *
 * <p>A sub-bullet with no parent is PROMOTED to top level rather than dropped: losing regulatory
 * wording silently is worse than showing it at the wrong indent, and the issue is recorded either
 * way.
 */
@DomainDrivenDesign.ApplicationService
public final class LabelParser {

    public LocalizedQuestionLabel parse(TableRow row, String textEn, String textFr,
                                        String bulletsEn, String bulletsFr, ImportIssues issues) {
        LabelDetails en = details(row, textEn, bulletsEn, issues);
        LabelDetails fr = details(row, textFr, bulletsFr, issues);
        return en == null && fr == null ? null : new LocalizedQuestionLabel(en, fr);
    }

    /** Null when both the text and the bullets are absent, so callers can leave the column empty. */
    public LabelDetails details(TableRow row, String textHeader, String bulletsHeader, ImportIssues issues) {
        String text = row.get(textHeader).orElse(null);
        List<Bullet> bullets = bulletsHeader == null
                ? List.of()
                : parseBullets(row.get(bulletsHeader).orElse(null), row.at(bulletsHeader), issues);
        if (text == null && bullets.isEmpty()) return null;
        return new LabelDetails(text, bullets);
    }

    public List<Bullet> parseBullets(String cell, SourceLocation where, ImportIssues issues) {
        List<String> lines = Cells.lines(cell);
        if (lines.isEmpty()) return List.of();

        List<Bullet> top = new ArrayList<>();
        List<Bullet> children = new ArrayList<>();
        String pendingParentText = null;

        for (String line : lines) {
            int level = level(line);
            String text = strip(line, level);
            if (text.isEmpty()) {
                issues.add(where, "BULLET_EMPTY", "A bullet line has no text");
                continue;
            }
            if (level == 2) {
                if (pendingParentText == null) {
                    issues.add(where, "BULLET_ORPHAN_SUB",
                            "Sub-bullet '" + text + "' has no bullet above it; shown at top level");
                    top.add(Bullet.of(text));
                } else {
                    children.add(Bullet.of(text));
                }
                continue;
            }
            flush(top, pendingParentText, children);
            pendingParentText = text;
            children = new ArrayList<>();
            if (level == 0) {
                issues.add(where, "BULLET_NO_MARKER",
                        "Bullet '" + text + "' does not start with '-'; treated as a top-level bullet");
            }
        }
        flush(top, pendingParentText, children);
        return List.copyOf(top);
    }

    private void flush(List<Bullet> top, String parentText, List<Bullet> children) {
        if (parentText != null) {
            top.add(new Bullet(parentText, List.copyOf(children)));
        }
    }

    /** 2 for {@code --}, 1 for {@code -}, 0 when the author forgot the marker. */
    private int level(String line) {
        if (line.startsWith("--")) return 2;
        if (line.startsWith("-")) return 1;
        return 0;
    }

    private String strip(String line, int level) {
        return line.substring(Math.min(level, line.length())).trim();
    }
}
