package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.routing;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.math.BigDecimal;

/**
 * A numeric band, authored as {@code range [4..6]}, {@code range [<0 | >6]} or the half-open
 * {@code range [0 .. <4]}. At most one lower and one upper bound; a null bound is unbounded.
 */
@DomainDrivenDesign.ValueObject
public record Range(BigDecimal gte, BigDecimal gt, BigDecimal lte, BigDecimal lt) {

    public boolean contains(BigDecimal v) {
        if (v == null) return false;
        if (gte != null && v.compareTo(gte) < 0) return false;
        if (gt != null && v.compareTo(gt) <= 0) return false;
        if (lte != null && v.compareTo(lte) > 0) return false;
        return lt == null || v.compareTo(lt) < 0;
    }
}
