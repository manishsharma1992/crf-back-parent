package com.bnpparibas.sit.fresh.rds.rds04.crf.back.exposition.leverage;

import com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.application.leverage.definitionimport
        .UnreadableWorkbookException;
import com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.application.leverage.definitionimport.WorkbookSource;
import com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.application.leverage.definitionimport
        .WorkbookSourceFactory;
import com.bnpparibas.sit.fresh.rds.rds04.crf.datasync.management.leverage.definitionimport.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;

/**
 * Uploads an authoring workbook and publishes the three decision trees it contains.
 *
 * <p>Thin on purpose. It opens the stream, calls the use case, and turns one outcome into one
 * status code. No parsing, no validation, no transaction — all of that already has a home.
 *
 * <h2>Two kinds of failure, two status codes</h2>
 * A file that is not a workbook is a bad REQUEST: nothing could be read, so there is nothing to
 * report, and the answer is 400. A workbook whose cells are wrong is a well-formed request the
 * server understood and refused — 422, carrying the full report. Collapsing the two would leave a
 * BA unable to tell "you sent the wrong file" from "row 15 has a typo".
 *
 * <h2>Dry run is the default</h2>
 * Publishing supersedes what analysts are using right now, so it is opt-in. Someone who uploads a
 * workbook to see whether it is any good gets exactly that, and has to pass
 * {@code mode=PUBLISH} to change what anyone sees.
 */
@RestController
@RequestMapping("/api/leverage/decision-trees")
public class DecisionTreeImportController {

    private static final Logger log = LoggerFactory.getLogger(DecisionTreeImportController.class);

    private final DecisionTreeImportService importService;
    private final WorkbookSourceFactory workbookFactory;

    public DecisionTreeImportController(DecisionTreeImportService importService,
                                        WorkbookSourceFactory workbookFactory) {
        this.importService = importService;
        this.workbookFactory = workbookFactory;
    }

    /**
     * @param mode DRY_RUN validates and reports without writing; PUBLISH writes all three forms
     */
    @PostMapping(path = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importWorkbook(
            @RequestPart("file") MultipartFile file,
            @RequestParam(name = "mode", defaultValue = "DRY_RUN") ImportMode mode) {

        if (file == null || file.isEmpty()) {
            return badRequest("EMPTY_UPLOAD", "No file was uploaded.");
        }

        ImportOutcome outcome;
        // try-with-resources over the PORT: the workbook is released whatever happens, and this
        // class still has no idea Apache POI exists.
        try (WorkbookSource workbook = workbookFactory.open(file.getInputStream())) {
            outcome = importService.importWorkbook(workbook, mode);
        } catch (UnreadableWorkbookException ex) {
            log.warn("Rejected upload '{}': not a readable workbook", file.getOriginalFilename(), ex);
            return badRequest("UNREADABLE_WORKBOOK",
                    "The uploaded file could not be read as an .xlsx workbook.");
        } catch (IOException ex) {
            log.warn("Could not read the uploaded stream for '{}'", file.getOriginalFilename(), ex);
            return badRequest("UPLOAD_READ_FAILED", "The uploaded file could not be read.");
        }

        DecisionTreeImportResponse body = DecisionTreeImportResponse.from(outcome);
        // A rejection is a normal answer, so it is logged at INFO with a count rather than as an
        // error — the report has already told the caller everything actionable.
        log.info("Import of '{}' finished as {} with {} report line(s)",
                file.getOriginalFilename(), outcome.status(), outcome.report().size());

        return outcome.isRejected()
                ? ResponseEntity.unprocessableEntity().body(body)
                : ResponseEntity.ok(body);
    }

    private ResponseEntity<ApiError> badRequest(String code, String message) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(code, message, Instant.now()));
    }
}
