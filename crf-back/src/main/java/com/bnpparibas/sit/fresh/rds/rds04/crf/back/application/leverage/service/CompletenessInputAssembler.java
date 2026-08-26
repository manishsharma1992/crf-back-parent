package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import com.bnpparibas.crf.shared.domain.leverage.model.CompletenessInput;
import com.bnpparibas.crf.shared.domain.leverage.model.LeverageAnalysis;

/**
 * Projects a leverage analysis into the shape the completeness rule needs: run the
 * traversal, collect the visible mandatory fields in form order, count blocking
 * validation messages.
 *
 * <p>Declared here so the use case has a stable dependency; the implementation
 * comes next. It sits in the application layer rather than the domain because it
 * orchestrates the traversal engine, which is itself a collaborator - the domain
 * service stays a pure function of its input.
 */
public interface CompletenessInputAssembler {

    CompletenessInput assemble(LeverageAnalysis analysis);
}
