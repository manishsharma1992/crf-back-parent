package com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.response;

import com.bnpparibas.sit.pact.annotations.design.domain.DomainDrivenDesign;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One form's answers, frozen against the definition version that produced them.
 *
 * <p><b>Changed for the ECB form: {@code flags} is new.</b> The preliminary form's conclusion is a
 * {@code RecommendationOutcome} and the aggregate already holds it. ECB and FED have no outcome at
 * all — their entire result is the flags a terminal branch set, and today there is nowhere to put
 * them. Without this, an ECB analysis could be completed and the conclusion lost.
 *
 * <p>Flags are frozen HERE as well as being written to {@code counterparty_characteristics},
 * because those two answer different questions. The counterparty row says what is true of the
 * counterparty NOW; this says what THIS analysis concluded, and stays true after the next analysis
 * overwrites the row.
 *
 * @param definitionVersion the version walked — the analysis can be replayed exactly
 * @param locale            "EN" or "FR": the language the frozen labels are in
 * @param flags             output flags at the terminal. A catalogued flag nothing set is ABSENT,
 *                          never blank — the same rule the whole grammar rests on.
 * @param panels            info panels as they were DISPLAYED. RMPM data moves, so reading it live
 *                          on reopening would show today's figures against yesterday's decision.
 * @param completion        share of this form's questions answered, 0-100, at the moment of saving
 */
@DomainDrivenDesign.ValueObject
public record FormResponses(int definitionVersion,
                            String locale,
                            List<Answer> answers,
                            Map<String, String> flags,
                            List<PanelSnapshot> panels,
                            int completion) {

    public FormResponses {
        answers = answers == null ? List.of() : List.copyOf(answers);
        flags = flags == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(flags));
        panels = panels == null ? List.of() : List.copyOf(panels);
    }
}
