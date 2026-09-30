package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.config;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.FinancialTableSupport;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.FinancialsResolver;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.ecb.EcbFinancialTableSupport;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.fed.FedFinancialSourcesResolver;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.fed.FedFinancialTableSupport;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.ecb.EcbFinancialCalculator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.fed.AplcFinancialCalculator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.fed.ReitFinancialCalculator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service.FinancialCalculationDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Builds the financial table supports.
 *
 * <p><b>Why these are @Bean methods and not component scanning.</b> There are THREE calculators
 * and they are not interchangeable — a support holds one specific calculator, and FED holds two
 * supports because it declares two tables. Component-scanning {@code FedFinancialTableSupport}
 * would ask Spring to choose a single {@code FinancialTableCalculator} by type, which is the
 * "expected single matching bean but found 3" failure. Naming each pairing here is also what makes
 * the registry readable: one line per financial table in the system.
 *
 * <p><b>The calculators are constructed, not injected.</b> They are pure domain services — no I/O,
 * no state, no Spring — and putting {@code @Service} on them contradicts that, the same way
 * {@link FinancialCalculationDomainService} carries only the DDD marker. Keeping the {@code new}
 * here also means a test can build one without a context, which is how every calculator test runs.
 *
 * <p>Adding a table in v15 (General Obligor / Utilities) is one more {@code @Bean} below.
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
}
