package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.DefinitionStatus;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeDefinition;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.repository.LeverageDecisionTreeDefinitionRepository;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * In-memory {@link LeverageDecisionTreeDefinitionRepository} for tests, implementing the same versioning rules
 * as the JPA adapter: append-only, one open row per form, superseding stamps {@code validTo}.
 *
 * <p>It exists so the import service can be tested without a database. The rules are duplicated on
 * purpose — {@code DecisionTreeDefinitionRepositoryContractTest} runs the SAME assertions against this
 * and (in the integration suite) against the JPA adapter, so a divergence shows up as a failing
 * contract rather than as a bug that only appears in production.
 */
public final class InMemoryDecisionTreeDefinitionRepository implements LeverageDecisionTreeDefinitionRepository {

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

    /**
     * Resolved INDEPENDENTLY of {@link #findInForce}, not by delegating to it.
     *
     * <p>The real implementation answers these two through different paths — {@code findActive}
     * goes via the cache, {@code findInForce} straight to the DAO — so a fake that routed one into
     * the other would make the contract's agreement test tautological here and catch nothing.
     */
    @Override
    public DecisionTreeDefinition findActive(LeverageFormType form, Instant now) {
        return rows.stream()
                .filter(r -> r.definition().formType() == form)
                .filter(r -> r.definition().status() == DefinitionStatus.PUBLISHED)
                .filter(r -> !r.validFrom().isAfter(now))
                .filter(r -> r.validTo() == null || r.validTo().isAfter(now))
                .max(java.util.Comparator.comparingInt(r -> r.definition().version()))
                .map(Row::definition)
                .orElse(null);
    }

    @Override
    public DecisionTreeDefinition findByVersion(LeverageFormType form, int version) {
        return rows.stream()
                .map(Row::definition)
                .filter(d -> d.formType() == form && d.version() == version)
                .findFirst()
                .orElse(null);
    }

    /**
     * Not implemented on purpose. This method returns the JPA ENTITY from a domain-layer
     * repository interface, so honouring it here would drag crf-back's infrastructure into a
     * crf-datasync test. Nothing in the import path needs it — see the note in the README about
     * narrowing the port.
     */
    @Override
    public com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage
            .LeverageDecisionTreeDefinition getActiveLeverageDecisionTreeDefinition(
            LeverageFormType form, Instant now) {
        throw new UnsupportedOperationException(
                "getActiveLeverageDecisionTreeDefinition returns a JPA entity and is not needed by the importer");
    }

    @Override
    public Optional<DecisionTreeDefinition> findInForce(LeverageFormType form, Instant at) {
        return rows.stream()
                .filter(r -> r.definition().formType() == form)
                .filter(r -> r.definition().status() == DefinitionStatus.PUBLISHED)
                .filter(r -> !r.validFrom().isAfter(at))
                .filter(r -> r.validTo() == null || r.validTo().isAfter(at))
                .max(java.util.Comparator.comparingInt(r -> r.definition().version()))
                .map(Row::definition);
    }
}
