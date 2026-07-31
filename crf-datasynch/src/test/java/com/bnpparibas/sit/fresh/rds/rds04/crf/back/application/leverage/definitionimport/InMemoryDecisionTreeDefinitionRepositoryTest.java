package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.repository.LeverageDecisionTreeDefinitionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.TestInstance;

/**
 * Runs the store contract against the in-memory fake. The JPA adapter runs the SAME class in the
 * integration suite against a real PostgreSQL — that is what keeps the fake trustworthy.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InMemoryDecisionTreeDefinitionRepositoryTest extends DecisionTreeDefinitionRepositoryContractTest {

    private InMemoryDecisionTreeDefinitionRepository repository;

    @BeforeEach
    void reset() {
        repository = new InMemoryDecisionTreeDefinitionRepository();
    }

    @Override
    protected LeverageDecisionTreeDefinitionRepository repository() {
        return repository;
    }
}
