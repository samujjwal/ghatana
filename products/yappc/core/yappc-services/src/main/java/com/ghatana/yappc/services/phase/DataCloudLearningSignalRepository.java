package com.ghatana.yappc.services.phase;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data Cloud-backed learning signal repository.
 */
public final class DataCloudLearningSignalRepository implements LearningSignalRepository {

    private static final String COLLECTION = "yappc_learning_signals";

    private final DataCloudClient dataCloudClient;

    public DataCloudLearningSignalRepository(@NotNull DataCloudClient dataCloudClient) {
        this.dataCloudClient = dataCloudClient;
    }

    @Override
    public Promise<Optional<LearningSignal>> findLatest(
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId
    ) {
        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .filters(List.of(
                        DataCloudClient.Filter.eq("workspaceId", workspaceId),
                        DataCloudClient.Filter.eq("projectId", projectId)
                ))
                .sorts(List.of(DataCloudClient.Sort.desc("createdAt")))
                .limit(1)
                .build();
        return dataCloudClient.query(tenantId, COLLECTION, query)
                .map(entities -> entities.isEmpty()
                        ? Optional.empty()
                        : Optional.of(mapSignal(entities.get(0).data(), tenantId, workspaceId, projectId)));
    }

    @Override
    public Promise<Void> save(@NotNull LearningSignal signal) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("id", signal.signalId());
        data.put("signalId", signal.signalId());
        data.put("tenantId", signal.tenantId());
        data.put("workspaceId", signal.workspaceId());
        data.put("projectId", signal.projectId());
        data.put("signal", signal.signal());
        data.put("sourceEvent", signal.sourceEvent());
        data.put("confidence", signal.confidence());
        data.put("recommendation", signal.recommendation());
        data.put("approvalState", signal.approvalState());
        data.put("rollbackPath", signal.rollbackPath());
        data.put("evidenceIds", signal.evidenceIds());
        data.put("createdAt", signal.createdAt().toString());
        return dataCloudClient.save(signal.tenantId(), COLLECTION, data).toVoid();
    }

    private static LearningSignal mapSignal(
            Map<String, Object> data,
            String tenantId,
            String workspaceId,
            String projectId
    ) {
        return new LearningSignal(
                asString(data.get("signalId"), asString(data.get("id"), "signal-unavailable")),
                tenantId,
                workspaceId,
                projectId,
                asString(data.get("signal"), "unknown"),
                asString(data.get("sourceEvent"), "No source event available"),
                asDouble(data.get("confidence"), 0.5d),
                asString(data.get("recommendation"), "Approve learning recommendation only when governance evidence is healthy."),
                asString(data.get("approvalState"), "PENDING_APPROVAL"),
                asString(data.get("rollbackPath"), "Revert to previous approved learning baseline and re-run observe checks."),
                asStringList(data.get("evidenceIds")),
                asInstant(data.get("createdAt"))
        );
    }

    private static String asString(Object value, String fallback) {
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private static double asDouble(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static List<String> asStringList(Object value) {
        if (value instanceof List<?> list) {
            return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
        }
        return List.of();
    }

    private static Instant asInstant(Object value) {
        if (value instanceof String text) {
            try {
                return Instant.parse(text);
            } catch (Exception ignored) {
                return Instant.now();
            }
        }
        return Instant.now();
    }
}
