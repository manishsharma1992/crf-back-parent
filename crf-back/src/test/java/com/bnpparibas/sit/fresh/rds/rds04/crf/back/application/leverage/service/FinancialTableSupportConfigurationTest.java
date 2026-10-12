package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.FinancialTableSupport;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.FinancialsResolver;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports.fed.FedFinancialSourcesResolver;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.FinancialTableCalculator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.ecb.EcbFinancialCalculator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.fed.AplcFinancialCalculator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.fed.GoCommittedDebtCalculator;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.financial.fed.GoNetFundedDebtCalculator;
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
    @DisplayName("all five financial tables are registered")
    void allFiveFinancialTablesAreRegistered() {
        // ECB Q-F01; FED Q-F-APLC, Q-F-REIT, and General Obligor / Utilities' Q-F-GO1 and Q-F-GO2.
        assertThat(supports).hasSize(5);
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
                ReitFinancialCalculator.class,
                GoCommittedDebtCalculator.class,
                GoNetFundedDebtCalculator.class);
    }

    /**
     * Only Table 1 reads an answer, and only Q-MI01. A second calculator starting to read answers
     * would be new coupling to a version-scoped key, so it should show up as a deliberate change here.
     */
    @Test
    @DisplayName("only General Obligor Table 1 reads an answer, and it is Q-MI01")
    void onlyTableOneReadsAnAnswer() {
        assertThat(supports)
                .filteredOn(support -> !support.calculator().requiredAnswers().isEmpty())
                .extracting(support -> support.calculator().getClass(), support -> support.calculator().requiredAnswers())
                .containsExactly(org.assertj.core.groups.Tuple.tuple(GoCommittedDebtCalculator.class, java.util.Set.of("Q-MI01")));
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
