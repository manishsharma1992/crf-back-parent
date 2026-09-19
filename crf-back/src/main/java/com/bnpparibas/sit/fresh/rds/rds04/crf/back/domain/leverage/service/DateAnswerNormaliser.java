package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.IsoDate;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.Question;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.QuestionType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.DecisionTreeDefinition;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.input.DataField;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.tree.input.DataFieldType;
import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rewrites every DATE answer into canonical ISO-8601, and reports the ones that will not parse.
 *
 * <p><b>Why it runs before traversal.</b> One map routes, validates and gets frozen. Normalising
 * later would leave the snapshot holding whatever the client happened to send, so two analyses of
 * the same day could be recorded differently and neither could be compared with the other.
 *
 * <p><b>Why it reports rather than throws.</b> The two call sites want different things from a
 * malformed value, and only the caller knows which:
 * <ul>
 *   <li><b>Save</b> refuses. A value that cannot be parsed must never reach a frozen answer — a
 *       record nobody can read back is worse than a refused request.</li>
 *   <li><b>Answer-as-you-type</b> carries on. Nothing is stored, and the malformed value simply
 *       matches no condition, so the question reads as unanswered and the form does not advance.
 *       That is self-correcting feedback; a 400 on every keystroke is not.</li>
 * </ul>
 *
 * <p>Pure, and shaped like {@link ChecklistCoercionDomainService} so the two sit together: same
 * arguments, insertion order preserved, unmodifiable result.
 *
 * <p><b>Covers both shapes.</b> A DATE question is keyed {@code Q-D01}; a DATE box inside a
 * DATA_ENTRY question is keyed {@code Q-F-REIT.reitOriginationDate}. The FED form has both.
 */
@DomainDrivenDesign.DomainService
public final class DateAnswerNormaliser {

    /**
     * @param answers   every parseable date rewritten to {@code yyyy-MM-dd}; an unparseable value
     *                  is left EXACTLY as it came, because rewriting one would hide a client bug
     *                  and blanking it would discard what the analyst typed
     * @param malformed answer keys holding a value that is not a date, in definition order
     */
    public record Normalised(Map<String, String> answers, List<String> malformed) {

        public Normalised {
            answers = answers == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(answers));
            malformed = malformed == null ? List.of() : List.copyOf(malformed);
        }

        public boolean hasMalformed() {
            return !malformed.isEmpty();
        }
    }

    public Normalised normalise(DecisionTreeDefinition definition, Map<String, String> answers) {
        if (answers == null || answers.isEmpty()) {
            return new Normalised(Map.of(), List.of());
        }
        Set<String> dateKeys = dateKeys(definition);
        if (dateKeys.isEmpty()) {
            return new Normalised(answers, List.of());   // no DATE in this tree; nothing to do
        }
        Map<String, String> settled = new LinkedHashMap<>(answers);
        List<String> malformed = new ArrayList<>();

        for (String key : dateKeys) {
            String raw = settled.get(key);
            if (raw == null || raw.isBlank()) {
                continue;                                // unanswered is not malformed
            }
            String iso = IsoDate.normalise(raw);
            if (iso == null) {
                malformed.add(key);
            } else {
                settled.put(key, iso);
            }
        }
        return new Normalised(settled, malformed);
    }

    /** Answer keys that hold a date: DATE questions, and DATE boxes under their owning question. */
    private Set<String> dateKeys(DecisionTreeDefinition definition) {
        Set<String> keys = new LinkedHashSet<>();
        for (Question question : definition.questions()) {
            if (question.type() == QuestionType.DATE) {
                keys.add(question.key());
            }
            for (DataField field : question.fields()) {
                if (field != null && field.type() == DataFieldType.DATE) {
                    keys.add(question.key() + '.' + field.key());
                }
            }
        }
        return keys;
    }
}