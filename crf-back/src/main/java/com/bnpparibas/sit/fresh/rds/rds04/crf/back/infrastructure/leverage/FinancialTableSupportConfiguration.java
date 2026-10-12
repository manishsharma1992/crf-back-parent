package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.FinancialTableSupport;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.FinancialsResolver;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.ecb.EcbFinancialTableSupport;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.fed.FedFinancialSourcesResolver;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.fed.FedFinancialTableSupport;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.ecb.EcbFinancialCalculator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.fed.AplcFinancialCalculator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.fed.GoCommittedDebtCalculator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.fed.GoNetFundedDebtCalculator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.fed.ReitFinancialCalculator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service.FinancialCalculationDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the financial table supports.
 *
 * <p><b>Why these are @Bean methods and not component scanning.</b> There are FIVE calculators
 * and they are not interchangeable — a support holds one specific calculator, and FED holds two
 * supports per industry path where it declares one table each, and General Obligor / Utilities
 * declares TWO on the same path. Component-scanning {@code FedFinancialTableSupport}
 * would ask Spring to choose a single {@code FinancialTableCalculator} by type, which is the
 * "expected single matching bean but found 5" failure. Naming each pairing here is also what makes
 * the registry readable: one line per financial table in the system.
 *
 * <p><b>The calculators are constructed, not injected.</b> They are pure domain services — no I/O,
 * no state, no Spring — and putting {@code @Service} on them contradicts that, the same way
 * {@link FinancialCalculationDomainService} carries only the DDD marker. Keeping the {@code new}
 * here also means a test can build one without a context, which is how every calculator test runs.
 *
 * <p><b>Order does not matter.</b> {@code FinancialTableResolver} receives every support as a list and
 * resolves each declared table on its own; since Clara's correction of 2026-10-12 no table reads
 * another, so there is no ordering to get wrong here.
 */
@Configuration
public class FinancialTableSupportConfiguration {

    @Bean
    public FinancialCalculationDomainService financialCalculationDomainService() {
        return new FinancialCalculationDomainService();
    }

    @Bean
    public FinancialTableSupport ecbFinancialTableSupport(
            FinancialsResolver sources,
            FinancialCalculationDomainService calculations) {
        return new EcbFinancialTableSupport(sources, new EcbFinancialCalculator(calculations));
    }

    @Bean
    public FinancialTableSupport aplcFinancialTableSupport(FedFinancialSourcesResolver sources) {
        return new FedFinancialTableSupport(sources, new AplcFinancialCalculator());
    }

    @Bean
    public FinancialTableSupport reitFinancialTableSupport(FedFinancialSourcesResolver sources) {
        return new FedFinancialTableSupport(sources, new ReitFinancialCalculator());
    }

    /**
     * General Obligor / Utilities, Table 1 ({@code Q-F-GO1}): the FED leverage ratio. The only
     * calculator that reads an answer — Q-MI01, for its two industry-dependent thresholds.
     */
    @Bean
    public FinancialTableSupport goCommittedDebtFinancialTableSupport(FedFinancialSourcesResolver sources) {
        return new FedFinancialTableSupport(sources, new GoCommittedDebtCalculator());
    }

    /** General Obligor / Utilities, Table 2 ({@code Q-F-GO2}): net funded debt, high or low risk. */
    @Bean
    public FinancialTableSupport goNetFundedDebtFinancialTableSupport(FedFinancialSourcesResolver sources) {
        return new FedFinancialTableSupport(sources, new GoNetFundedDebtCalculator());
    }
}
