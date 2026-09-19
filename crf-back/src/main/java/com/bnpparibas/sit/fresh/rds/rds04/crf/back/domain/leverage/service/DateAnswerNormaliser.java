package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

/**
 * Rewrites every DATE answer into canonical ISO-8601 before anything reads the map.
 *
 * <p><b>Why it runs before traversal rather than at render time.</b> The same map is what routes,
 * what validates and what {@code PreliminaryResponseAssembler} freezes. Normalising later would
 * leave the frozen snapshot holding whatever the client happened to send, so two analyses of the
 * same day could be recorded differently and neither could be compared with the other.
 *
 * <p>Pure, and shaped like the coercion step it sits next to: definition and answers in, a new map
 * out, insertion order preserved. It would fold naturally into
 * {@code ChecklistCoercionDomainService} if you would rather have one coercion pass than two — the
 * signatures already match.
 *
 * <p><b>Covers both shapes.</b> A DATE question is keyed {@code Q-D01}; a DATE box inside a
 * DATA_ENTRY question is keyed {@code Q-F-REIT.reitOriginationDate}. Both exist on the FED form.
 *
 * <p><b>An unparseable value is left exactly as it came.</b> Rewriting it would hide a client bug;
 * blanking it would silently discard what the analyst typed. It stays, it fails
 * {@link IsoDate#isStorable}, and the boundary refuses the request — see the note on the controller
 * in the accompanying analysis.
 */
@Service
@DomainDrivenDesign.DomainService
public final class DateAnswerNormaliser {

    public Map<String, String> normalise(DecisionTreeDefinition definition, Map<String, String> answers) {
        if (answers == null || answers.isEmpty()) {
            return Map.of();
        }
        Map<String, String> dateKeys = dateKeys(definition);
        if (dateKeys.isEmpty()) {
            return answers;   // no DATE anywhere in this tree; nothing to do
        }
        Map<String, String> out = new LinkedHashMap<>(answers);
        dateKeys.keySet().forEach(key -> rewrite(out, key));
        return out;
    }

    private void rewrite(Map<String, String> answers, String key) {
        String raw = answers.get(key);
        if (raw == null || raw.isBlank()) {
            return;
        }
        String iso = IsoDate.normalise(raw);
        if (iso != null) {
            answers.put(key, iso);
        }
        // else: unparseable. Left untouched on purpose — see the class note.
    }

    /** Answer keys that hold a date: DATE questions, and DATE boxes under their owning question. */
    private Map<String, String> dateKeys(DecisionTreeDefinition definition) {
        Map<String, String> keys = new LinkedHashMap<>();
        for (Question question : definition.questions()) {
            if (question.type() == QuestionType.DATE) {
                keys.put(question.key(), question.key());
            }
            for (DataField field : question.fields()) {
                if (field != null && field.type() == DataFieldType.DATE) {
                    String dotted = question.key() + '.' + field.key();
                    keys.put(dotted, dotted);
                }
            }
        }
        return keys;
    }
}
