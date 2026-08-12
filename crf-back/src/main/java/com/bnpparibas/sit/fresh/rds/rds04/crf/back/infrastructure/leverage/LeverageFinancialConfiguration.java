package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.FinancialsResolver;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service.FinancialTableResolver;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service.FinancialCalculationDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wiring for the financial table.
 *
 * <p>Two beans declared here rather than annotated in place, for the reason the rest of the domain
 * follows: {@link FinancialCalculationDomainService} carries no Spring annotation, because a
 * domain service must be constructible in a plain unit test with {@code new}. Its test does
 * exactly that.
 *
 * <p>{@link FinancialTableResolver} is an application service and could carry {@code @Service},
 * but declaring it beside the calculator keeps the financial table's whole wiring in one place —
 * useful when the next reader asks where the EBITDA comes from.
 *
 * <p>Add these methods to the existing leverage configuration rather than creating a second class
 * if one already declares {@code ChecklistCoercionDomainService} and friends.
 */
@Configuration
public class LeverageFinancialConfiguration {

    @Bean
    public FinancialCalculationDomainService financialCalculationDomainService() {
        return new FinancialCalculationDomainService();
    }

    @Bean
    public FinancialTableResolver financialTableResolver(FinancialsResolver financialsResolver,
                                                         FinancialCalculationDomainService calculator) {
        return new FinancialTableResolver(financialsResolver, calculator);
    }
}
