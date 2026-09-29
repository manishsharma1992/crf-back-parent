package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.config;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.FinancialTableSupport;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.FinancialsResolver;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.fed.FedFinancialSourcesResolver;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.FinancialTableCalculator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.ecb.EcbFinancialCalculator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.fed.AplcFinancialCalculator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.fed.ReitFinancialCalculator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * That every financial table in the system is actually registered.
 *
 * <p><b>Why this exists.</b> Two wiring failures in a row threw on startup, which is the good
 * case. The one that does not throw is a MISSING support: the context comes up, the form renders,
 * and that industry path quietly computes nothing — every calculated box empty, no error naming a
 * cause, reading for all the world like a workbook problem. A count is the cheapest way to make
 * that a red build instead.
 *
 * <p><b>A slice, not {@code @SpringBootTest}.</b> Only the configuration under test is loaded, and
 * the two reference-data ports are stubbed with the {@code none()} factories they already provide.
 * No database, no full context, milliseconds — so it can sit in the normal unit run rather than
 * somewhere people skip.
 *
 * <p>Counting alone would not be enough: three beans paired with the wrong calculators would pass.
 * The pairings are asserted too.
 */
@SpringJUnitConfig({
        FinancialTableSupportConfiguration.class,
        FinancialTableSupportConfigurationTest.StubbedPorts.class})
class FinancialTableSupportConfigurationTest {

    @Autowired
    private List<FinancialTableSupport> supports;

    @Test
    @DisplayName("all three financial tables are registered")
    void allThreeFinancialTablesAreRegistered() {
        // ECB Q-F01, FED Q-F-APLC, FED Q-F-REIT. General Obligor / Utilities makes it four in v15.
        assertThat(supports).hasSize(3);
    }

    @Test
    @DisplayName("each support carries its own calculator, and no two share one")
    void eachSupportCarriesItsOwnCalculator() {
        List<Class<? extends FinancialTableCalculator>> calculators = supports.stream()
                .map(FinancialTableSupport::calculator)
                .map(FinancialTableCalculator::getClass)
                .map(type -> (Class<? extends FinancialTableCalculator>) type)
                .toList();

        assertThat(calculators).containsExactlyInAnyOrder(
                EcbFinancialCalculator.class,
                AplcFinancialCalculator.class,
                ReitFinancialCalculator.class);
    }

    @Test
    @DisplayName("prefills resolve without a database and never return null")
    void prefillsAreResolvable() {
        // The stubs below answer nothing, which is the point: a support must cope with FINSTAR
        // holding no row at all, because that is an ordinary analysis rather than an error.
        assertThat(supports)
                .allSatisfy(support -> assertThat(support.prefills(null)).isNotNull());
    }

    /**
     * The two reference-data ports, answering nothing.
     *
     * <p>Both interfaces already ship a {@code none()} factory for exactly this, so there is
     * nothing to mock and no behaviour invented here that the real adapters do not have.
     */
    @Configuration
    static class StubbedPorts {

        @Bean
        FinancialsResolver financialsResolver() {
            return FinancialsResolver.none();
        }

        @Bean
        FedFinancialSourcesResolver fedFinancialSourcesResolver() {
            return FedFinancialSourcesResolver.none();
        }
    }
}
