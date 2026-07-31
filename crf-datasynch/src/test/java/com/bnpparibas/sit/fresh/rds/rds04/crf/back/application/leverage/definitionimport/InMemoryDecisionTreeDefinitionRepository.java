package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.DefinitionStatus;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeDefinition;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * In-memory {@link DecisionTreeDefinitionRepository} for tests, implementing the same versioning rules
 * as the JPA adapter: append-only, one open row per form, superseding stamps {@code validTo}.
 *
 * <p>It exists so the import service can be tested without a database. The rules are duplicated on
 * purpose — {@code DecisionTreeDefinitionRepositoryContractTest} runs the SAME assertions against this
 * and (in the integration suite) against the JPA adapter, so a divergence shows up as a failing
 * contract rather than as a bug that only appears in production.
 */
public final class InMemoryDecisionTreeDefinitionRepository implements DecisionTreeDefinitionRepository {

    /** One stored row. */
    public record Row(DecisionTreeDefinition definition, Instant validFrom, Instant validTo) {
    }

    private final List<Row> rows = new ArrayList<>();

    public List<Row> rows() {
        return List.copyOf(rows);
    }

    @Override
    public int currentVersion(LeverageFormType form) {
        return rows.stream()
                .filter(r -> r.definition().formType() == form)
                .mapToInt(r -> r.definition().version())
                .max()
                .orElse(0);
    }

    @Override
    public void supersede(LeverageFormType form, Instant at) {
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            if (row.definition().formType() == form && row.validTo() == null) {
                rows.set(i, new Row(row.definition(), row.validFrom(), at));
            }
        }
    }

    @Override
    public void save(DecisionTreeDefinition definition, Instant validFrom) {
        rows.add(new Row(definition, validFrom, null));
    }

    @Override
    public Optional<DecisionTreeDefinition> findInForce(LeverageFormType form, Instant at) {
        return rows.stream()
                .filter(r -> r.definition().formType() == form)
                .filter(r -> r.definition().status() == DefinitionStatus.PUBLISHED)
                .filter(r -> !r.validFrom().isAfter(at))
                .filter(r -> r.validTo() == null || r.validTo().isAfter(at))
                .map(Row::definition)
                .findFirst();
    }
}
