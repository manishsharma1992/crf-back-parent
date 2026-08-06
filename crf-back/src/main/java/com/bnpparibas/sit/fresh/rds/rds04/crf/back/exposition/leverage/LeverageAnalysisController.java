package com.bnpparibas.sit.fresh.rds.rds04.crf.back.exposition.leverage;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.dto.FormState;

import java.util.Map;

public class LeverageAnalysisController {

    /** Locale is optional — the form's default is used when the client does not say. */
    @GetMapping("analyses/{uid}/forms/{formType}/state")
    public FormState formState(@PathVariable String uid,
                               @PathVariable LeverageFormType formType,
                               @RequestParam(required = false) String locale) {
        return getLeverageFormState.get(uid, formType, locale);
    }

    @PostMapping("analyses/{uid}/forms/{formType}/state")
    public FormState resolveFormState(@PathVariable String uid,
                                      @PathVariable LeverageFormType formType,
                                      @RequestBody StateRequest body) {
        Map<String, String> answers = body.answers() == null ? Map.of() : body.answers();
        return getLeverageFormState.resolve(uid, formType, body.version(), answers, body.locale());
    }
}
