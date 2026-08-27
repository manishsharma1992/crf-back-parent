package com.bnpparibas.sit.fresh.rds.rds04.crf.back.exposition.leverage;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.AnalysisStatusChangeView;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.ValidationAvailability;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service.AnalysisCompletenessService;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service.ValidateLeverageAnalysisUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * BR01 and BR02.
 *
 * <p><b>Why availability is its own endpoint rather than a field on FormState.</b>
 * Three reasons, in ascending order of how much they matter:
 *
 * <ol>
 *   <li>FormState is per-form; validation is per-analysis. The two diverge the
 *       moment a preliminary outcome asks for both ECB and FED, and a canValidate
 *       field sitting on one form's payload would invite exactly the wrong
 *       reading.</li>
 *   <li>It leaves GetLeverageFormStateUseCase untouched.</li>
 *   <li>It has to be this way round. AnalysisCompletenessService reads the other
 *       forms through GetLeverageFormStateUseCase; if that use case also computed
 *       completeness, each nested read would compute it again and recurse
 *       forever.</li>
 * </ol>
 *
 * <p>Cost is one extra call after each save. Acceptable given the definitions are
 * cached, and it is a read the analyst's own action triggers rather than a poll.
 */
@RestController
@RequestMapping("/leverage-analyses/{analysisUid}")
@RequiredArgsConstructor
public class LeverageValidationController {

    private final AnalysisCompletenessService completenessService;
    private final ValidateLeverageAnalysisUseCase validateUseCase;

    /** BR01. Refreshed by the client after every save and on form load. */
    @GetMapping("/validation-availability")
    public ValidationAvailability availability(@PathVariable String analysisUid) {
        return ValidationAvailability.from(completenessService.evaluate(analysisUid));
    }

    /**
     * BR02.
     *
     * <p>The user comes from the security context here rather than from the
     * request body. A validated-by taken from the payload is a validated-by the
     * caller chose, which for a regulatory sign-off is not a stamp at all.
     */
    @PostMapping("/validate")
    public AnalysisStatusChangeView validate(@PathVariable String analysisUid,
                                             Authentication authentication) {
        return AnalysisStatusChangeView.from(
                validateUseCase.validate(analysisUid, authentication.getName()));
    }
}
