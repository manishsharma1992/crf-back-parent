package com.bnpparibas.sit.fresh.rds.rds04.crf.back.application.leverage.ports;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Fills CURRENT_LEVERAGE_TX_FLAGS from the counterparty's RMPM row.
 *
 * <p>Two of the four fields arrive as integers and are rendered through the Flag Values sets before
 * they leave here. That is the reason a panel snapshot is frozen already-rendered: {@code 2} means
 * nothing without the set that decodes it to "INR", and a later import can reword that set.
 */
@Component
@RequiredArgsConstructor
public class InfoPanelResolverImpl implements InfoPanelResolver {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Panel field name to the flag whose value set decodes it.
     *
     * <p>The two do not match by accident of authoring: the panel declares {@code leveragedFlag}
     * while the flags catalogue calls it {@code ecbLeveragedFlag}. Worth asking the BA to align the
     * names, at which point this map becomes an identity and can go.
     */
    private static final Map<String, String> DECODED_BY = Map.of(
            "leveragedFlag", "ecbLeveragedFlag",
            "covenantStructure", "ecbCovenantStructure");

    private final CounterpartyPanelDao dao;
    private final InfoPanelSelector selector;

    @Override
    public List<PanelSnapshot> resolve(DecisionTreeDefinition definition, List<InfoPanel> triggered,
                                       AnalysisSubject subject, String locale) {

        if (triggered == null || triggered.isEmpty() || subject == null || subject.rmpmid() == null) {
            return List.of();
        }
        return dao.findLeverageFlags(subject.rmpmid())
                .map(row -> snapshots(definition, triggered, row, locale))
                // No RMPM row: no panel rather than a panel of blanks, which would read as "we
                // checked and there is nothing" instead of "we could not check".
                .orElseGet(List::of);
    }

    private List<PanelSnapshot> snapshots(DecisionTreeDefinition definition, List<InfoPanel> triggered,
                                          CounterpartyPanelDao.PanelRow row, String locale) {
        List<PanelSnapshot> snapshots = new ArrayList<>();
        for (InfoPanel panel : triggered) {
            snapshots.add(new PanelSnapshot(panel.key(), title(panel, locale),
                    fields(definition, panel, row, locale)));
        }
        return List.copyOf(snapshots);
    }

    private List<PanelField> fields(DecisionTreeDefinition definition, InfoPanel panel,
                                    CounterpartyPanelDao.PanelRow row, String locale) {
        List<PanelField> fields = new ArrayList<>();
        for (String field : panel.fields()) {
            value(definition, field, row, locale)
                    .ifPresent(value -> fields.add(new PanelField(field, value)));
        }
        return List.copyOf(fields);
    }

    /**
     * A field the panel names but this build cannot read is omitted rather than blanked — a new
     * field on a re-authored panel should shorten the block, not fill it with empty rows.
     */
    private Optional<String> value(DecisionTreeDefinition definition, String field,
                                   CounterpartyPanelDao.PanelRow row, String locale) {
        return switch (field) {
            case "leveragedFlag" -> coded(definition, field, row.getLeveraged(), locale);
            case "covenantStructure" -> coded(definition, field, row.getCovenantStructure(), locale);
            case "ecbLeverageRatio" -> Optional.ofNullable(row.getEcbLeverageRatio())
                    .map(BigDecimal::toPlainString);
            case "leverageDate" -> Optional.ofNullable(row.getLeveragedDate()).map(DATE::format);
            default -> Optional.empty();
        };
    }

    /** Decoded through the Flag Values set, so "2" leaves here as "INR". */
    private Optional<String> coded(DecisionTreeDefinition definition, String field,
                                   Integer stored, String locale) {
        if (stored == null) {
            return Optional.empty();
        }
        String flagKey = DECODED_BY.get(field);
        return flagKey == null
                ? Optional.of(String.valueOf(stored))
                : Optional.of(selector.display(definition, flagKey, stored, locale));
    }

    private String title(InfoPanel panel, String locale) {
        String title = "FR".equalsIgnoreCase(locale) ? panel.titleFr() : panel.titleEn();
        return title == null || title.isBlank() ? panel.titleEn() : title;
    }
}
