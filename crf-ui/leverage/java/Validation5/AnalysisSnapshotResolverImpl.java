package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage.persistence;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.AnalysisSnapshotView;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.FormSnapshot;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.value.LeverageFormType;
import com.bnpparibas.sit.fresh.rds.rds04.crf.back.domain.leverage.port.AnalysisSnapshotResolver;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * BR03 adapter.
 *
 * <p>Applicable forms are derived from the definition-id columns, not from a
 * formType discriminator: preliminary is always present, ECB and FED are present
 * independently, and an analysis routed to both carries both. A single formType
 * field could not represent that case at all.
 *
 * <p>Flags are read out of the responses payload in Java rather than with SQL
 * jsonb path expressions, so an analysis validated under an earlier workbook
 * still renders when a newer definition is current.
 */
public class AnalysisSnapshotResolverImpl implements AnalysisSnapshotResolver {

    private static final String FLAGS_NODE = "flags";
    private static final String MAPPING_FAILURE = "Unreadable responses payload for analysis %s";

    private final AnalysisSnapshotJpaRepository snapshotRepository;
    private final ObjectMapper objectMapper;

    public AnalysisSnapshotResolverImpl(AnalysisSnapshotJpaRepository snapshotRepository,
                                        ObjectMapper objectMapper) {
        this.snapshotRepository = snapshotRepository;
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<AnalysisSnapshotView> findByAnalysisUid(String analysisUid) {
        return findHistory(analysisUid).stream().findFirst();
    }

    @Override
    public List<AnalysisSnapshotView> findHistory(String analysisUid) {
        return snapshotRepository.findValidatedSnapshots(analysisUid).stream()
                .map(this::toView)
                .toList();
    }

    private AnalysisSnapshotView toView(AnalysisSnapshotRow row) {
        return new AnalysisSnapshotView(
                row.analysisUid(),
                row.financialArchiveId(),
                row.recommendedOutcome(),
                toForms(row),
                row.validatedBy(),
                row.validatedTimestamp(),
                row.changedBy(),
                row.changedTimestamp(),
                row.fromStatus(),
                row.toStatus());
    }

    /**
     * One entry per applicable form, in display order. A null definition id means
     * the analysis was never routed to that form.
     */
    private List<FormSnapshot> toForms(AnalysisSnapshotRow row) {
        JsonNode responses = parseResponses(row);
        List<FormSnapshot> forms = new ArrayList<>();
        addIfApplicable(forms, LeverageFormType.PRELIMINARY, row.preliminaryDefinitionId(), responses);
        addIfApplicable(forms, LeverageFormType.ECB, row.ecbDefinitionId(), responses);
        addIfApplicable(forms, LeverageFormType.FED, row.fedDefinitionId(), responses);
        return forms;
    }

    private void addIfApplicable(List<FormSnapshot> forms,
                                 LeverageFormType formType,
                                 Long definitionId,
                                 JsonNode responses) {
        if (definitionId == null) {
            return;
        }
        forms.add(new FormSnapshot(formType, definitionId, readFlags(responses, formType)));
    }

    /**
     * CONFIRM THE PAYLOAD SHAPE. This assumes responses is keyed by form, each
     * form holding a flags object:
     *
     * <pre>{ "ECB": { "flags": { "leveraged": "Y", ... } }, "FED": { ... } }</pre>
     *
     * If flags are instead flat across the whole analysis, drop the per-form
     * lookup and give every FormSnapshot the same map. Either way the change is
     * confined to this method.
     */
    private Map<String, String> readFlags(JsonNode responses, LeverageFormType formType) {
        JsonNode flagsNode = responses.path(formType.name()).path(FLAGS_NODE);
        if (!flagsNode.isObject()) {
            return new LinkedHashMap<>();
        }
        Map<String, String> flags = new LinkedHashMap<>();
        flagsNode.fields().forEachRemaining(entry ->
                flags.put(entry.getKey(), entry.getValue().asText()));
        return flags;
    }

    /**
     * Only needed while responses is projected as a String. If the entity already
     * maps the jsonb column onto a typed object, this method and the ObjectMapper
     * dependency both disappear.
     */
    private JsonNode parseResponses(AnalysisSnapshotRow row) {
        String payload = row.responses();
        if (payload == null || payload.isBlank()) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(String.format(MAPPING_FAILURE, row.analysisUid()), e);
        }
    }
}
