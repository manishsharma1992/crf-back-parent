package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeDefinition;
import com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.application.leverage.definitionimport
        .DecisionTreeDefinitionRepository;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * ADAPTER for {@link DecisionTreeDefinitionRepository} over JPA.
 *
 * <p>Holds the whole versioning lifecycle so it reads as one story: what the current version is,
 * how the open one is closed, and how the next is appended. Nothing here converts JSON — the
 * entity's {@code @JdbcTypeCode(SqlTypes.JSON)} field does that, and this class only ever handles
 * domain objects.
 *
 * <p>No {@code @Transactional} here. The import publishes three forms and they must land together
 * or not at all, so the boundary belongs to the calling service, not to a single-form write.
 */
@DomainDrivenDesign.InfrastructureService
public final class LeverageDecisionTreeDefinitionRepositoryImpl implements DecisionTreeDefinitionRepository {

    private final LeverageDecisionTreeDefinitionDao dao;

    public LeverageDecisionTreeDefinitionRepositoryImpl(LeverageDecisionTreeDefinitionDao dao) {
        this.dao = dao;
    }

    @Override
    public int currentVersion(LeverageFormType form) {
        return dao.maxVersion(form);
    }

    @Override
    public void supersede(LeverageFormType form, Instant at) {
        List<LeverageDecisionTreeDefinition> open = dao.findByFormTypeAndValidToIsNull(form);
        // More than one open row would mean an earlier import half-committed. Closing them all is
        // the repair: the new version takes over regardless, and nothing is left ambiguous.
        open.forEach(entity -> entity.close(at));
        dao.saveAll(open);
    }

    @Override
    public void save(DecisionTreeDefinition definition, Instant validFrom) {
        dao.save(LeverageDecisionTreeDefinition.newVersion(definition, validFrom));
    }

    @Override
    public Optional<DecisionTreeDefinition> findInForce(LeverageFormType form, Instant at) {
        return dao.findInForce(form, at)
                .map(LeverageDecisionTreeDefinition::getDefinition);
    }
}
