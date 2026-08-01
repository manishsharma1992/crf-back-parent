package com.bnpparibas.sit.fresh.rds.rds04.crf.back.exposition.leverage;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.application.leverage.definitionimport
        .UnreadableWorkbookException;
import com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.application.leverage.definitionimport.WorkbookSource;
import com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.application.leverage.definitionimport
        .WorkbookSourceFactory;
import com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.management.leverage.definitionimport.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Web-layer tests with the use case mocked.
 *
 * <p>Standalone MockMvc rather than {@code @WebMvcTest}: the controller has two collaborators and
 * no Spring wiring worth exercising, so a full application context would only slow the suite down.
 * The behaviour under test is entirely status-code and payload mapping.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DecisionTreeImportControllerTest {

    private static final Instant NOW = Instant.parse("2026-07-31T09:00:00Z");

    private MockMvc mockMvc;
    private DecisionTreeImportService importService;
    private WorkbookSourceFactory workbookFactory;

    @BeforeAll
    void setUp() {
        importService = mock(DecisionTreeImportService.class);
        workbookFactory = mock(WorkbookSourceFactory.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new DecisionTreeImportController(importService, workbookFactory))
                .build();
    }

    private MockMultipartFile upload() {
        return new MockMultipartFile("file", "leverage-decision-tree.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "not really an xlsx, the factory is mocked".getBytes());
    }

    private void factoryReturnsAWorkbook() {
        reset(workbookFactory, importService);
        when(workbookFactory.open(any())).thenReturn(mock(WorkbookSource.class));
    }

    private void serviceAnswers(ImportOutcome outcome) {
        when(importService.importWorkbook(any(), any())).thenReturn(outcome);
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Success {

        @Test
        void publishing_returns_200_with_the_versions() throws Exception {
            factoryReturnsAWorkbook();
            serviceAnswers(new ImportOutcome(ImportStatus.PUBLISHED, List.of(),
                    Map.of(LeverageFormType.PRELIMINARY, 4, LeverageFormType.ECB, 4,
                            LeverageFormType.FED, 4), NOW));

            mockMvc.perform(multipart("/api/leverage/decision-trees/import")
                            .file(upload()).param("mode", "PUBLISH"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("PUBLISHED"))
                    .andExpect(jsonPath("$.publishedVersions.ECB").value(4))
                    .andExpect(jsonPath("$.report").isEmpty());
        }

        /** Publishing supersedes what analysts are using, so it must be asked for explicitly. */
        @Test
        void omitting_the_mode_rehearses_rather_than_publishes() throws Exception {
            factoryReturnsAWorkbook();
            serviceAnswers(new ImportOutcome(ImportStatus.VALIDATED, List.of(), Map.of(), NOW));

            mockMvc.perform(multipart("/api/leverage/decision-trees/import").file(upload()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("VALIDATED"));

            verify(importService).importWorkbook(any(), eq(ImportMode.DRY_RUN));
        }

        @Test
        void the_workbook_is_closed_even_on_the_happy_path() throws Exception {
            reset(workbookFactory, importService);
            WorkbookSource workbook = mock(WorkbookSource.class);
            when(workbookFactory.open(any())).thenReturn(workbook);
            serviceAnswers(new ImportOutcome(ImportStatus.VALIDATED, List.of(), Map.of(), NOW));

            mockMvc.perform(multipart("/api/leverage/decision-trees/import").file(upload()));

            verify(workbook).close();
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class Rejection {

        /**
         * 422, not 400: the request was well formed and understood, and the server refused it.
         * The distinction is what tells a BA "row 15 has a typo" apart from "wrong file".
         */
        @Test
        void a_bad_workbook_returns_422_with_the_whole_report() throws Exception {
            factoryReturnsAWorkbook();
            serviceAnswers(new ImportOutcome(ImportStatus.REJECTED, List.of(
                    "Sheet 'ECB Q', row 15, column 'Branches' (line 2) — [UNKNOWN_GOTO] no such question",
                    "Sheet 'Fields', row 7, column 'Field Key' — [DATA_FIELD_DUPLICATE_IN_FORM] duplicate"),
                    Map.of(), NOW));

            mockMvc.perform(multipart("/api/leverage/decision-trees/import")
                            .file(upload()).param("mode", "PUBLISH"))
                    .andExpect(status().isUnprocessableEntity())
                    .andExpect(jsonPath("$.status").value("REJECTED"))
                    .andExpect(jsonPath("$.report.length()").value(2))
                    .andExpect(jsonPath("$.report[0]").value(
                            org.hamcrest.Matchers.containsString("row 15")))
                    .andExpect(jsonPath("$.publishedVersions").isEmpty());
        }

        @Test
        void the_summary_counts_the_problems() throws Exception {
            factoryReturnsAWorkbook();
            serviceAnswers(new ImportOutcome(ImportStatus.REJECTED, List.of("a", "b", "c"), Map.of(), NOW));

            mockMvc.perform(multipart("/api/leverage/decision-trees/import").file(upload()))
                    .andExpect(jsonPath("$.summary").value(
                            org.hamcrest.Matchers.containsString("3 problem(s)")));
        }
    }

    @Nested
    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    class BadUpload {

        /** Nothing could be read, so there are no cells to report on — a request problem, not a workbook one. */
        @Test
        void a_file_that_is_not_a_workbook_returns_400() throws Exception {
            reset(workbookFactory, importService);
            when(workbookFactory.open(any()))
                    .thenThrow(new UnreadableWorkbookException("bad zip", new RuntimeException()));

            mockMvc.perform(multipart("/api/leverage/decision-trees/import").file(upload()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("UNREADABLE_WORKBOOK"));

            verifyNoInteractions(importService);
        }

        @Test
        void an_empty_upload_never_reaches_the_importer() throws Exception {
            reset(workbookFactory, importService);
            MockMultipartFile empty = new MockMultipartFile("file", "empty.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", new byte[0]);

            mockMvc.perform(multipart("/api/leverage/decision-trees/import").file(empty))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("EMPTY_UPLOAD"));

            verifyNoInteractions(workbookFactory, importService);
        }
    }
}
