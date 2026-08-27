package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisStatus;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageAnalysis;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * ADAPT ME. The aggregate's construction is the one thing in this suite I could
 * not write for you - LeverageAnalysis has a shape only the codebase knows.
 *
 * <p>Kept in one class on purpose: every test that needs an aggregate comes
 * through here, so wiring it up is a single edit rather than a sweep.
 *
 * <p>If the aggregate has no public constructor or builder, a package-private test
 * factory on the aggregate itself is preferable to reflection - reflection here
 * would keep the suite green through a refactor that ought to break it.
 */
public final class LeverageAnalysisTestBuilder {

    private LeverageAnalysisTestBuilder() {
    }

    public static LeverageAnalysis draft(String analysisUid) {
        return build(analysisUid, AnalysisStatus.DRAFT, null);
    }

    public static LeverageAnalysis validated(String analysisUid) {
        return build(analysisUid, AnalysisStatus.VALIDATED, null);
    }

    public static LeverageAnalysis validatedWithEcbAnswer(String analysisUid, int version,
                                                          String questionKey, String value) {
        return build(analysisUid, AnalysisStatus.VALIDATED,
                ecbOnly(version, answers(questionKey, value), Map.of()));
    }

    public static LeverageAnalysis draftWithEcbAnswer(String analysisUid, int version,
                                                      String questionKey, String value) {
        return build(analysisUid, AnalysisStatus.DRAFT,
                ecbOnly(version, answers(questionKey, value), Map.of()));
    }

    public static LeverageAnalysis validatedWithEcbFlag(String analysisUid,
                                                        String flagName, String value) {
        Map<String, String> flags = new LinkedHashMap<>();
        flags.put(flagName, value);
        return build(analysisUid, AnalysisStatus.VALIDATED, ecbOnly(12, List.of(), flags));
    }

    private static LeverageResponses ecbOnly(int version, List<Answer> answers,
                                             Map<String, String> flags) {
        FormResponses ecb = new FormResponses(version, "EN", answers, flags, List.of());
        return new LeverageResponses(selection(), null, ecb, null);
    }

    private static List<Answer> answers(String questionKey, String value) {
        // ADAPT: Answer's constructor and its provenance type.
        return List.of(new Answer(questionKey, value, AnswerProvenance.ANSWERED));
    }

    private static SpreadsheetSelection selection() {
        return new SpreadsheetSelection("fin.pdf", "ARCH-42", "FINSTAR", "ANNUAL",
                null, null, 12, "CONSO", "IFRS", "EUR", "CLEAN", "RM-1", "ACME SA");
    }

    private static LeverageAnalysis build(String analysisUid, AnalysisStatus status,
                                          LeverageResponses responses) {
        return LeverageAnalysis.builder()
                .analysisUid(analysisUid)
                .status(status)
                .responses(responses)
                .build();
    }
}
