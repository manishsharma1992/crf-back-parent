package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Settles checklist items before a save is recorded
 *
 * <p>A YES settles the block: the remianing items stop being required, and what the analyst left
 * blank is NOT the same as what they considered and rejected. Saving those blanks as blank would
 * lose that distinction from the export, so they are materialized as NOT_APPLICABLE here - which is
 * also the only place NOT_APPLICABLE is ever assigned, keeping "NA is system-assigned only" true by
 * construction.
 * <p>Thr three scenario, in the settled working</p>
 * <ul>
 *     <li><b>ANY_YES</b> - unanswered items become NOT_APPLICABLE</li>
 *     <li><b>ALL_NO</b> - every item answered, none YES: saved exactly as given</li>
 *     <li><b>Blanks with no YES</b> - left blank on purpose. {@link DecisionTreeTraversalService}
 *      then reports PENDING_INPUT, which is what stops the save advancing. Coercing them here
 *      would fabricate an answer nobody gave and hide an incomplete form.
 *     </li>
 * </ul>
 * <p>Runs before traversal, harmlessly: with a YES present the aggregate is ANY_YES either way, and
 *  without one nothing is written, so the walk sees exactly what it would have seen.
 * </p>
 * </p>
 */

@Service
@DomainDrivenDesgin.DomainService
public final class ChecklistCoercionDomainService {

    public Map<String, String> coerce(DecisionTreeDefinition definition, Map<String, String> answers) {
        Map<String, String> settled = new LinkedHashMap<>(answers == null ? Map.of(): answers);
        for(Question question: definition.questions()) {
            settleChecklist(settled, question);
        }
        return Collections.unmodifiableMap(settled);
    }

    private void settleChecklist(Map<String, String> settled, Question question) {
        String prefix = question.key() + '.';
        if(!hasYes(settled, question, prefix)) {
            return;
        }
        for(ChecklistItem item: questions.items()) {
            String key = prefix + item.key();
            if(isBlank(settled.get(key))) {
                settled.put(key, ItemAnswer.NOT_APPLICABLE.name());
            }
        }
    }

    private boolean hasYes(Map<String, String> settled, Question question, String prefix) {
        return question.items().stream()
                .map(item -> settled.get(prefix + item.key()))
                .anyMatch(ChecklistCoercionDomainService::isYes);
    }

    private static boolean isYes(String value) {
        return value != null && ItemAnswer.YES.name().equalsIgnoreCase(value.trim().replace(' ', '_'));
    }

    private static boolean isBlank(String value) { return value == null || value.trim().isBlank(); }
}
