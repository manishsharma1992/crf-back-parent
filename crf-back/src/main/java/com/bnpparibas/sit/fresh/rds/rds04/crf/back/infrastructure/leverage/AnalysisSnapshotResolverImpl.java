package com.bnpparibas.sit.fresh.rds.rds04.crf.back.infrastructure.leverage;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.bnpparibas.crf.shared.domain.leverage.model.AnalysisSnapshotView;
import com.bnpparibas.crf.shared.domain.leverage.model.LeverageAnalysisStatus;
import com.bnpparibas.crf.shared.domain.leverage.model.LeverageFormType;
import com.bnpparibas.crf.shared.domain.leverage.port.AnalysisSnapshotResolver;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * BR03 adapter. Reads the frozen analysis row and maps the flags out of the JSONB
 * payload in Java.
 *
 * <p>Kept separate from the form-state resolvers for the same reason
 * FinancialsResolverImpl is separate from DerivedValueResolverImpl: this path
 * needs display-ready flag values and nothing else, and routing it through the
 * form-state machinery would mean re-running a traversal to produce data the
 * payload already holds.
 */
public class AnalysisSnapshotResolverImpl implements AnalysisSnapshotResolver {

    private static final String FLAGS_NODE = "flags";
    private static final String MAPPING_FAILURE = "Unreadable form payload for analysis %s";

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
        return snapshotRepository.findSnapshotsByAnalysisUid(analysisUid).stream()
                .map(this::toView)
                .toList();
    }

    private AnalysisSnapshotView toView(AnalysisSnapshotRow row) {
        return new AnalysisSnapshotView(
                row.getAnalysisUid(),
                row.getFinancialId(),
                LeverageFormType.valueOf(row.getFormType()),
                readFlags(row),
                row.getValidatedBy(),
                row.getValidatedTimestamp(),
                row.getChangedBy(),
                row.getChangedTimestamp(),
                LeverageAnalysisStatus.valueOf(row.getFromStatus()),
                LeverageAnalysisStatus.valueOf(row.getToStatus()));
    }

    /**
     * Flags are read positionally out of the payload rather than against a fixed
     * schema, so an analysis validated under workbook v11 still renders when v13
     * is current. LinkedHashMap throughout - definition order is what the user
     * sees, and Map.copyOf has already cost us flaky tests elsewhere.
     */
    private Map<String, String> readFlags(AnalysisSnapshotRow row) {
        String payload = row.getFormPayload();
        if (payload == null || payload.isBlank()) {
            return new LinkedHashMap<>();
        }
        try {
            JsonNode flagsNode = objectMapper.readTree(payload).path(FLAGS_NODE);
            if (!flagsNode.isObject()) {
                return new LinkedHashMap<>();
            }
            Map<String, String> flags = new LinkedHashMap<>();
            flagsNode.fields().forEachRemaining(entry ->
                    flags.put(entry.getKey(), entry.getValue().asText()));
            return flags;
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(
                    String.format(MAPPING_FAILURE, row.getAnalysisUid()), e);
        }
    }
}
