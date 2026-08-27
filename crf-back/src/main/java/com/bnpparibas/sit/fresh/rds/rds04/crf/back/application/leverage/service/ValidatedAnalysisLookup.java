package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.service;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.port.LeverageAnalysisRepository;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageAnalysis;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.ValidatedAnswer;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.rating.value.LeverageAnalysisReference;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * What the rating side calls to read values out of a validated analysis.
 *
 * <h2>Prefer flags to question keys</h2>
 *
 * <p>Flags are catalogued and survive a workbook re-import; question keys are
 * authored per version and do not. A rating reading ECB_LEVERAGED keeps working
 * when the tree is republished; one reading Q-S06 breaks the day the key is
 * renumbered - and it breaks by returning EMPTY rather than by failing, which is
 * the worst shape a regulatory defect can take. Where the rating needs something
 * not catalogued, the durable fix is to ask for a flag; {@link #answer} is the
 * escape hatch until then.
 *
 * <h2>Read the batch methods before reaching for the single ones</h2>
 *
 * <p>Every single-value call loads the whole analysis, jsonb payload and all. Five
 * values through {@link #answer} is five loads of the same row. When the rating
 * needs more than one thing - which it usually will - use {@link #answers} or
 * {@link #flags}, which load once.
 *
 * <h2>Structure: entry points annotated, workers private</h2>
 *
 * <p>Each public method delegates to a private worker rather than to another
 * public method. That is not stylistic. A self-call goes straight to the instance
 * and never touches the Spring proxy, so an overload delegating to an annotated
 * sibling would run with NO transaction at all - the readOnly semantics declared
 * on the target would silently not apply. Sonar flags the pattern for exactly
 * this reason.
 *
 * <p>The rule to carry elsewhere: a public @Transactional method must never call
 * another public @Transactional method on the same bean. Extract the shared work
 * into a private method and annotate only the entry points.
 *
 * <h2>Caching</h2>
 *
 * <p>A validated analysis is immutable, so this is the rare case where a cache has
 * no invalidation problem: an entry can never go stale, because the row it
 * describes can never change. If the rating reads these often, {@code @Cacheable}
 * on a load method keyed by analysisUid is safe with nothing but a size bound.
 * Left out because cache names and sizing are project-wide decisions, and because
 * it should be measured first.
 *
 * <h2>Empty means three things</h2>
 *
 * <p>No such validated analysis; the analysis was never routed to that form; or
 * the walk never reached that question. None is an error - a question on a branch
 * the analyst did not enter was simply never asked - but only the caller's own
 * rule can say what it means for a rating.
 */
@Service
@DomainDrivenDesign.ApplicationService
@RequiredArgsConstructor
public class ValidatedAnalysisLookup {

    private final LeverageAnalysisRepository analyses;

    // ---------------------------------------------------------- entry points

    /** The preferred read: a catalogued flag from a validated analysis. */
    @Transactional(readOnly = true)
    public Optional<String> flag(String analysisUid, LeverageFormType formType, String flagName) {
        return readFlag(analysisUid, formType, flagName);
    }

    /**
     * Reads several flags in one load. Absent flags are omitted rather than mapped
     * to null or blank - the grammar's rule throughout is that a flag nothing set
     * stays absent, and flattening that here would let a caller treat "not set" and
     * "set to nothing" as the same thing.
     */
    @Transactional(readOnly = true)
    public Map<String, String> flags(String analysisUid,
                                     LeverageFormType formType,
                                     Collection<String> flagNames) {
        return readFlags(analysisUid, formType, flagNames);
    }

    /**
     * The escape hatch: a raw question answer, carrying the form and workbook
     * version it came from so the caller can record what it actually read.
     */
    @Transactional(readOnly = true)
    public Optional<ValidatedAnswer> answer(String analysisUid,
                                            LeverageFormType formType,
                                            String questionKey) {
        return readAnswer(analysisUid, formType, questionKey);
    }

    /**
     * Reads several answers in one load, keyed by question key.
     *
     * <p>LinkedHashMap, so results come back in the order the caller asked - the
     * same insertion-order rule the rest of the module follows, and the reason this
     * is not Map.copyOf.
     *
     * <p>Questions the walk never reached are absent from the map. A caller telling
     * "not asked" from "answered blank" checks containsKey, not the value.
     */
    @Transactional(readOnly = true)
    public Map<String, ValidatedAnswer> answers(String analysisUid,
                                                LeverageFormType formType,
                                                Collection<String> questionKeys) {
        return readAnswers(analysisUid, formType, questionKeys);
    }

    // ------------------------------------- entry points: reading back a rating

    /**
     * Re-reads the exact analysis a rating consumed.
     *
     * <p>Takes the reference rather than a bare uid so the call site says what it
     * means: the analysis THIS rating was built on, not whichever is current. Since
     * the reference is what the rating stored in model_specific_data, passing it
     * whole also removes the chance of reaching for the wrong string field.
     */
    @Transactional(readOnly = true)
    public Optional<String> flag(LeverageAnalysisReference reference,
                                 LeverageFormType formType,
                                 String flagName) {
        return readFlag(reference.analysisUid(), formType, flagName);
    }

    @Transactional(readOnly = true)
    public Map<String, String> flags(LeverageAnalysisReference reference,
                                     LeverageFormType formType,
                                     Collection<String> flagNames) {
        return readFlags(reference.analysisUid(), formType, flagNames);
    }

    @Transactional(readOnly = true)
    public Optional<ValidatedAnswer> answer(LeverageAnalysisReference reference,
                                            LeverageFormType formType,
                                            String questionKey) {
        return readAnswer(reference.analysisUid(), formType, questionKey);
    }

    @Transactional(readOnly = true)
    public Map<String, ValidatedAnswer> answers(LeverageAnalysisReference reference,
                                                LeverageFormType formType,
                                                Collection<String> questionKeys) {
        return readAnswers(reference.analysisUid(), formType, questionKeys);
    }

    // ---------------------------------------------------------------- workers

    private Optional<String> readFlag(String analysisUid,
                                      LeverageFormType formType,
                                      String flagName) {
        return load(analysisUid).flatMap(analysis -> analysis.validatedFlag(formType, flagName));
    }

    private Map<String, String> readFlags(String analysisUid,
                                          LeverageFormType formType,
                                          Collection<String> flagNames) {
        Optional<LeverageAnalysis> analysis = load(analysisUid);
        if (analysis.isEmpty()) {
            return Map.of();
        }
        Map<String, String> found = new LinkedHashMap<>();
        for (String flagName : flagNames) {
            analysis.get().validatedFlag(formType, flagName)
                    .ifPresent(value -> found.put(flagName, value));
        }
        return found;
    }

    private Optional<ValidatedAnswer> readAnswer(String analysisUid,
                                                 LeverageFormType formType,
                                                 String questionKey) {
        return load(analysisUid)
                .flatMap(analysis -> analysis.validatedAnswerTo(formType, questionKey));
    }

    private Map<String, ValidatedAnswer> readAnswers(String analysisUid,
                                                     LeverageFormType formType,
                                                     Collection<String> questionKeys) {
        Optional<LeverageAnalysis> analysis = load(analysisUid);
        if (analysis.isEmpty()) {
            return Map.of();
        }
        Map<String, ValidatedAnswer> found = new LinkedHashMap<>();
        for (String questionKey : questionKeys) {
            analysis.get().validatedAnswerTo(formType, questionKey)
                    .ifPresent(answer -> found.put(questionKey, answer));
        }
        return found;
    }

    /**
     * Returns empty rather than throwing when the analysis is absent or still
     * DRAFT. A counterparty with no validated leverage conclusion is a normal state
     * of the world, not an exceptional one - the rating has to be able to ask and
     * be told no.
     *
     * <p>The status sits in the finder's WHERE clause, so a draft never arrives
     * here at all. The aggregate still guards independently: two checks, because
     * this one is a convenience and that one is the invariant.
     */
    private Optional<LeverageAnalysis> load(String analysisUid) {
        return analyses.findValidatedByAnalysisUid(analysisUid);
    }
}
