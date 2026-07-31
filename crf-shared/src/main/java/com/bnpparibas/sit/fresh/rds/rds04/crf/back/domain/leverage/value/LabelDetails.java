package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.List;

/**
 * A block of display text in ONE language: a lead paragraph plus optional bullets.
 *
 * <p>CHANGED: {@code bullets} was {@code List<String>}; it is now {@code List<Bullet>} so that
 * notes can carry sub-bullets. Flat callers migrate with {@code Bullet.of(text)}.
 */
@DomainDrivenDesign.ValueObject
public record LabelDetails(String text, List<Bullet> bullets) {

    public LabelDetails {
        bullets = bullets == null ? List.of() : List.copyOf(bullets);
    }

    public static LabelDetails of(String text) {
        return new LabelDetails(text, List.of());
    }
}
