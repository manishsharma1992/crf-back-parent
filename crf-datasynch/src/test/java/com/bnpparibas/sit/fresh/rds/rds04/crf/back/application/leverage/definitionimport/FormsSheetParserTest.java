package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.definitionimport;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.*;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.application.leverage.definitionimport.InMemoryWorkbookSource.row;
import static org.junit.jupiter.api.Assertions.*;

/** Parser tests over an in-memory workbook — no POI, no .xlsx fixture. */
class FormsSheetParserTest {

    private final FormsSheetParser parser = new FormsSheetParser(new FlagValuesSheetParser());

    private InMemoryWorkbookSource workbook() {
        return new InMemoryWorkbookSource()
                .sheet("Forms", List.of(
                        row("Forms — Metadata, Outcomes, Flags, Messages & Panels"),
                        row(),
                        row("FORM METADATA"),
                        row("Form Type", "Default Locale", "Locales", "Entry Question"),
                        row("PRELIMINARY", "EN", "EN;FR", "Q01"),
                        row("ECB", "EN", "EN;FR", "Q01"),
                        row("FED", "EN", "EN;FR", "Q01"),
                        row(),
                        row("OUTCOMES"),
                        row("Outcome Code", "Display Value", "Forms To Show", "Forced Flags"),
                        row("NOT_REQUIRED", "Not applicable", "", ""),
                        row("ECB", "ECB", "ECB", "fedLeveragedFlag=INR"),
                        row(),
                        row("FLAGS CATALOGUE"),
                        row("Form", "Flag Key", "Display EN", "Display FR", "Stored As", "Value Set"),
                        row("ECB", "ecbLeveragedFlag", "Leveraged Flag", "Flag Leveraged", "CODE", "LEVERAGED_FLAG"),
                        row("ECB", "ecbLboFlag", "LBO Flag", "Flag LBO", "BOOLEAN", ""),
                        row("Every flag listed here is always shown..."),
                        row(),
                        row("VALIDATION MESSAGES"),
                        row("Form", "Question Key", "Field Key", "Rule", "Message Key", "Severity", "Text EN", "Text FR"),
                        row("ECB", "", "", "MANDATORY", "ECB_CHECKLIST_MANDATORY", "ERROR", "Answer them", "Repondez"),
                        row("ECB", "Q-S06", "", "NOT_SELF", "ECB_ENTITY_SAME_AS_ANALYSED", "ERROR", "Not itself", "Pas elle"),
                        row(),
                        row("INFO PANELS"),
                        row("Panel Key", "Title EN", "Title FR", "Source", "Fields", "Shown When"),
                        row("CURRENT_LEVERAGE_TX_FLAGS", "Current Flags", "Flags courants",
                                "COUNTERPARTY_CHARACTERISTICS", "leveragedFlag ; leverageDate", "ecbLeveragedFlag is INR")))
                .sheet("Flag Values", List.of(
                        row("Flag Values"),
                        row("Only flags marked CODE appear here."),
                        row("Value Set", "Code", "Stored Value", "Display EN", "Display FR", "Set By"),
                        row("LEVERAGED_FLAG", "ECB_NOT_LEVERAGED", "0", "ECB Not Leveraged", "", "ECB"),
                        row("LEVERAGED_FLAG", "INR", "2", "INR", "", "BOTH"),
                        row("LEVERAGED_FLAG", "FED_LEVERAGED", "4", "FED Leveraged", "", "FED")));
    }

    @Test
    void readsAllFiveTables() {
        ImportIssues issues = new ImportIssues();
        ParsedCatalogues c = parser.parse(workbook(), issues);

        assertTrue(issues.isEmpty(), () -> issues.describeAll().toString());
        assertEquals(3, c.metadata().size());
        assertEquals(List.of("EN", "FR"), c.metadata().get(LeverageFormType.ECB).locales());
        assertEquals(2, c.outcomes().size());
        assertEquals(2, c.flagsFor(LeverageFormType.ECB).size());
        assertEquals(2, c.messagesFor(LeverageFormType.ECB).size());
        assertEquals(1, c.panelsFor(LeverageFormType.ECB).size());
        assertEquals(3, c.flagValueSets().get("LEVERAGED_FLAG").size());
    }

    @Test
    void banner_and_footnote_rows_do_not_leak_into_a_table() {
        ParsedCatalogues c = parser.parse(workbook(), new ImportIssues());
        // The flags table is followed by an italic footnote and a banner; neither is a flag.
        assertEquals(List.of("ecbLeveragedFlag", "ecbLboFlag"),
                List.copyOf(c.flagsFor(LeverageFormType.ECB).keySet()));
    }

    @Test
    void storedValueZero_survives_poi_style_number_text() {
        ParsedCatalogues c = parser.parse(workbook(), new ImportIssues());
        FlagValue notLeveraged = c.flagValueSets().get("LEVERAGED_FLAG").get(0);
        assertEquals(0, notLeveraged.storedValue());
    }

    @Test
    void both_expands_to_every_form() {
        ParsedCatalogues c = parser.parse(workbook(), new ImportIssues());
        FlagValue inr = c.flagValueSets().get("LEVERAGED_FLAG").get(1);
        assertEquals(List.of(LeverageFormType.values()).size(), inr.setBy().size());
    }

    @Test
    void tables_are_found_by_header_not_by_row_number() {
        InMemoryWorkbookSource shifted = workbook();
        // Prepending rows must not matter: locate() scans for the header signature.
        ParsedCatalogues c = parser.parse(shifted, new ImportIssues());
        assertNotNull(c.metadata().get(LeverageFormType.PRELIMINARY));
    }

    @Test
    void malformedPanelTrigger_isReported_notThrown() {
        InMemoryWorkbookSource wb = new InMemoryWorkbookSource()
                .sheet("Forms", List.of(
                        row("Form", "Flag Key", "Display EN", "Display FR", "Stored As", "Value Set"),
                        row("ECB", "ecbLeveragedFlag", "Leveraged Flag", "FR", "CODE", "LEVERAGED_FLAG"),
                        row(),
                        row("Panel Key", "Title EN", "Title FR", "Source", "Fields", "Shown When"),
                        row("P1", "T", "T", "SRC", "a ; b", "ecbLeveragedFlag INR")))
                .sheet("Flag Values", List.of(
                        row("Value Set", "Code", "Stored Value", "Display EN", "Display FR", "Set By"),
                        row("LEVERAGED_FLAG", "INR", "2", "INR", "", "BOTH")));
        ImportIssues issues = new ImportIssues();
        parser.parse(wb, issues);
        assertTrue(issues.all().stream().anyMatch(i -> i.code().equals("PANEL_TRIGGER_MALFORMED")),
                () -> issues.describeAll().toString());
    }

    @Test
    void duplicateStoredNumber_inOneSet_isReported() {
        InMemoryWorkbookSource wb = new InMemoryWorkbookSource()
                .sheet("Flag Values", List.of(
                        row("Value Set", "Code", "Stored Value", "Display EN", "Display FR", "Set By"),
                        row("COVENANT_STRUCTURE", "NONE", "0", "No Covenant", "Sans covenant", "ECB"),
                        row("COVENANT_STRUCTURE", "FULL", "0", "Full Covenant", "Full Covenant", "ECB")));
        ImportIssues issues = new ImportIssues();
        new FlagValuesSheetParser().parse(wb, issues);
        assertTrue(issues.all().stream().anyMatch(i -> i.code().equals("FLAG_VALUE_NUMBER_CLASH")),
                () -> issues.describeAll().toString());
    }

    @Test
    void emptyRightHandSideInForcedFlags_isRejected() {
        InMemoryWorkbookSource wb = new InMemoryWorkbookSource()
                .sheet("Forms", List.of(
                        row("Outcome Code", "Display Value", "Forms To Show", "Forced Flags"),
                        row("ECB", "ECB", "ECB", "ecbCovenantStructure=")))
                .sheet("Flag Values", List.of(row("Value Set", "Code", "Stored Value", "Set By")));
        ImportIssues issues = new ImportIssues();
        parser.parse(wb, issues);
        assertTrue(issues.all().stream().anyMatch(i -> i.code().equals("FLAG_ASSIGNMENT_MALFORMED")),
                () -> issues.describeAll().toString());
    }
}
