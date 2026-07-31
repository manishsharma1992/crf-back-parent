package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.List;

/**
 * One bullet of a label or a note, with optional sub-bullets.
 *
 * <p>Authored in the workbook as one line per bullet: {@code - } for a top-level bullet and
 * {@code -- } for a sub-bullet, the sub-bullets attaching to the bullet above them. The
 * Support-Entity tooltip on Q-S02 is the reference case.
 */
@DomainDrivenDesign.ValueObject
public record Bullet(String text, List<Bullet> children) {

    public Bullet {
        children = children == null ? List.of() : List.copyOf(children);
    }

    public static Bullet of(String text) {
        return new Bullet(text, List.of());
    }
}
