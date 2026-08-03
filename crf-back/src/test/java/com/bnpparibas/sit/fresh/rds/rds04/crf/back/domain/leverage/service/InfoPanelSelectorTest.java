package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LocalizedLabel;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;
import java.util.Map;

import static com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.LeverageTreeFixtures.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Trigger matching and code rendering — no RMPM, no database. That is the point of splitting the
 * selector from the resolver.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class InfoPanelSelectorTest {

    private InfoPanelSelector selector;

    @BeforeAll
    void setUp() {
        selector = new InfoPanelSelector();
    }

    private static final InfoPanel INR_PANEL = new InfoPanel("CURRENT_LEVERAGE_TX_FLAGS",
            new LocalizedLabel("Current Leverage Transaction Flags", "Flags de levier"),
            "COUNTERPARTY_CHARACTERISTICS",
            List.of("leveragedFlag", "covenantStructure", "ecbLeverageRatio", "leverageDate"),
            "ecbLeveragedFlag", "INR");

    private DecisionTreeDefinition withPanel(InfoPanel... panels) {
        return defWithCatalogues(LeverageFormType.ECB, "Q1",
                List.of(sc("Q1", List.of(endFlags(dflt(), Map.of())))),
                standardFlags(), standardFlagValues(), List.of(), List.of(panels));
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Triggering {

        @Test
        void a_panel_appears_when_its_flag_takes_its_value() {
            List<InfoPanel> triggered =
                    selector.triggeredBy(withPanel(INR_PANEL), Map.of("ecbLeveragedFlag", "INR"));
            assertEquals(1, triggered.size());
            assertEquals("CURRENT_LEVERAGE_TX_FLAGS", triggered.get(0).key());
        }

        @Test
        void a_different_value_on_the_same_flag_does_not_trigger_it() {
            assertTrue(selector.triggeredBy(withPanel(INR_PANEL),
                    Map.of("ecbLeveragedFlag", "ECB_LEVERAGED")).isEmpty());
        }

        /** A flag nothing has set is absent from the map — that must not be read as a match. */
        @Test
        void an_unset_flag_does_not_trigger_it() {
            assertTrue(selector.triggeredBy(withPanel(INR_PANEL), Map.of()).isEmpty());
        }

        /**
         * The panel appears as soon as the flag is filled, which is what lets Q-T01, Q-C01 and
         * Q-T03 all show it without any of them naming it.
         */
        @Test
        void any_question_that_sets_the_flag_triggers_the_same_panel() {
            Map<String, String> fromTransactionBlock = Map.of("ecbLeveragedFlag", "INR");
            Map<String, String> fromCreditEvent = Map.of("ecbLeveragedFlag", "INR", "ecbLboFlag", "NO");
            assertEquals(1, selector.triggeredBy(withPanel(INR_PANEL), fromTransactionBlock).size());
            assertEquals(1, selector.triggeredBy(withPanel(INR_PANEL), fromCreditEvent).size());
        }

        @Test
        void a_form_with_no_panels_triggers_nothing() {
            assertTrue(selector.triggeredBy(withPanel(), Map.of("ecbLeveragedFlag", "INR")).isEmpty());
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Rendering {

        /** RMPM stores 2; the analyst must read "INR". */
        @Test
        void a_stored_number_renders_through_its_value_set() {
            assertEquals("INR", selector.display(withPanel(), "ecbLeveragedFlag", 2, "EN"));
            assertEquals("ECB Not Leveraged", selector.display(withPanel(), "ecbLeveragedFlag", 0, "EN"));
        }

        @Test
        void french_is_used_when_the_analyst_works_in_french() {
            assertEquals("Sans covenant", selector.display(withPanel(), "ecbCovenantStructure", 0, "FR"));
            assertEquals("No Covenant", selector.display(withPanel(), "ecbCovenantStructure", 0, "EN"));
        }

        /** Showing a raw value beats showing nothing when the catalogue cannot decode it. */
        @Test
        void an_unknown_code_falls_back_to_the_number() {
            assertEquals("99", selector.display(withPanel(), "ecbLeveragedFlag", 99, "EN"));
        }

        @Test
        void a_non_coded_flag_falls_back_to_the_number() {
            assertEquals("15", selector.display(withPanel(), "ecbLeverageRatio", 15, "EN"));
            assertEquals("7", selector.display(withPanel(), "notAFlagAtAll", 7, "EN"));
        }

        /** The Leveraged Flag set has no French text yet; the code is better than a blank. */
        @Test
        void a_missing_translation_falls_back_to_the_code() {
            assertEquals("ECB_NOT_LEVERAGED", selector.display(withPanel(), "ecbLeveragedFlag", 0, "FR"));
        }
    }
}
