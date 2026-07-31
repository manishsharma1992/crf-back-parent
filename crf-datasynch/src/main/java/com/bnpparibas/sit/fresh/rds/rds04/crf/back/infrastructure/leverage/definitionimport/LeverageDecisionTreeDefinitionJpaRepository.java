package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data access to {@code leverage_decision_tree_definition}.
 *
 * <p>Deliberately thin and query-only. Everything about WHEN a version is superseded and WHAT
 * version comes next lives in {@link JpaDecisionTreeDefinitionStore}, so the lifecycle is readable
 * in one place instead of spread across derived query names.
 */
public interface LeverageDecisionTreeDefinitionJpaRepository
        extends JpaRepository<LeverageDecisionTreeDefinitionEntity, Long> {

    @Query("select coalesce(max(d.version), 0) from LeverageDecisionTreeDefinitionEntity d "
            + "where d.formType = :formType")
    int maxVersion(@Param("formType") LeverageFormType formType);

    /** Rows still open for a form. Normally at most one; a list makes a broken state visible. */
    List<LeverageDecisionTreeDefinitionEntity> findByFormTypeAndValidToIsNull(LeverageFormType formType);

    @Query("select d from LeverageDecisionTreeDefinitionEntity d "
            + "where d.formType = :formType "
            + "and d.status = com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value"
            + ".DefinitionStatus.PUBLISHED "
            + "and d.validFrom <= :at "
            + "and (d.validTo is null or d.validTo > :at)")
    Optional<LeverageDecisionTreeDefinitionEntity> findInForce(@Param("formType") LeverageFormType formType,
                                                              @Param("at") Instant at);
}
