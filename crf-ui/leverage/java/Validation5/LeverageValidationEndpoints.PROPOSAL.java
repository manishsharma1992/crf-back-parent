/*
 * ---------------------------------------------------------------------------
 * PROPOSAL. Two new endpoints. No existing controller method changes.
 * ---------------------------------------------------------------------------
 */
package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.api;

/**
 * <p><b>Why availability is its own endpoint rather than a field on FormState.</b>
 * Three reasons, in ascending order of how much they matter:
 *
 * <ol>
 *   <li>FormState is per-form; validation is per-analysis. The two diverge the
 *       moment a preliminary outcome asks for both ECB and FED, and a field named
 *       canValidate sitting on a single form's payload would invite exactly the
 *       wrong reading.</li>
 *   <li>It keeps GetLeverageFormStateUseCase untouched.</li>
 *   <li>It has to be this way round. AnalysisCompletenessService reads the other
 *       forms through GetLeverageFormStateUseCase; if that use case also computed
 *       completeness, each nested read would compute it again and recurse
 *       forever.</li>
 * </ol>
 *
 * <p>Cost is one extra call after each save. Acceptable given the definitions are
 * cached, and it is a read the analyst's own action triggers rather than a
 * background poll.
 */
@RestController
@RequestMapping("/leverage-analyses/{analysisUid}")
@RequiredArgsConstructor
public class LeverageValidationController {

    private final AnalysisCompletenessService completenessService;
    private final ValidateLeverageAnalysisUseCase validateUseCase;

    /** BR01. Angular refreshes this after every save and on form load. */
    @GetMapping("/validation-availability")
    public ValidationAvailability availability(@PathVariable String analysisUid) {
        return ValidationAvailability.from(completenessService.evaluate(analysisUid));
    }

    /**
     * BR02. Returns the audit row rather than 204 so the client can render the
     * validated-by / validated-at stamp without a second round trip.
     */
    @PostMapping("/validate")
    public AnalysisStatusChangeView validate(@PathVariable String analysisUid,
                                             Authentication authentication) {
        return AnalysisStatusChangeView.from(
                validateUseCase.validate(analysisUid, authentication.getName()));
    }
}


// --- exception mapping, in the existing @RestControllerAdvice -----------------
// Three new cases. Distinct statuses because the client does distinct things:
//
//   AnalysisNotValidatableException  -> 422  re-fetch availability and show why
//   ConcurrentValidationException    -> 409  reload; someone else validated it
//   AnalysisNotModifiableException   -> 409  reload; the form is locked now
//
// All three are expected outcomes of a race or a stale tab, not faults. Logging
// them at ERROR would train everyone to ignore the log.


// --- Angular ------------------------------------------------------------------
// Readonly lock needs no payload change: FormState already carries validatedAt,
// and a non-null value means the analysis is validated.
//
//   readonly = computed(() => state().validatedAt !== null)
//   canValidate = signal from the availability endpoint
//
// Both are display concerns. assertModifiable() in SaveLeverageFormUseCase is
// what actually prevents the write - a disabled control stops an honest analyst,
// not a tab left open since before someone else clicked Validate.
