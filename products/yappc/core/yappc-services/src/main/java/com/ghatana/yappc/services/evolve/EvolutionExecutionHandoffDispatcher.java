package com.ghatana.yappc.services.evolve;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Dispatches queued evolve handoffs to lifecycle execution and writes dispatch outcomes.
 *
 * @doc.type class
 * @doc.purpose Consume queued evolve execution handoffs and record dispatched/failed outcomes
 * @doc.layer service
 * @doc.pattern Worker
 */
public final class EvolutionExecutionHandoffDispatcher {

    static final String HANDOFF_COLLECTION = "yappc_evolution_execution_handoffs";

    private final DataCloudClient dataCloudClient;
    private final EvolutionLifecycleExecutionDispatcher lifecycleDispatcher;

    public EvolutionExecutionHandoffDispatcher(
            @NotNull DataCloudClient dataCloudClient,
            @NotNull EvolutionLifecycleExecutionDispatcher lifecycleDispatcher
    ) {
        this.dataCloudClient = dataCloudClient;
        this.lifecycleDispatcher = lifecycleDispatcher;
    }

    public Promise<DispatchSummary> dispatchQueued(@NotNull String tenantId, int limit) {
        int boundedLimit = limit <= 0 ? 25 : limit;
        return dataCloudClient.query(tenantId, HANDOFF_COLLECTION, DataCloudClient.Query.limit(boundedLimit))
                .then(entities -> {
                    List<DataCloudClient.Entity> queued = new ArrayList<>();
                    for (DataCloudClient.Entity entity : entities) {
                        if ("QUEUED".equalsIgnoreCase(asString(entity.data().get("status"), ""))) {
                            queued.add(entity);
                        }
                    }

                    if (queued.isEmpty()) {
                        return Promise.of(new DispatchSummary(0, 0, 0));
                    }

                    int[] dispatched = new int[] {0};
                    int[] failed = new int[] {0};

                    Promise<Void> chain = Promise.complete();
                    for (DataCloudClient.Entity entity : queued) {
                        chain = chain.then(() -> dispatchOne(tenantId, entity)
                                .map(outcome -> {
                                    if (outcome.dispatched()) {
                                        dispatched[0]++;
                                    } else {
                                        failed[0]++;
                                    }
                                    return null;
                                }));
                    }

                    return chain.map(ignored -> new DispatchSummary(
                            queued.size(),
                            dispatched[0],
                            failed[0]
                    ));
                });
    }

    private Promise<DispatchOutcome> dispatchOne(String tenantId, DataCloudClient.Entity entity) {
        EvolutionLifecycleExecutionDispatcher.EvolutionLifecycleExecutionRequest request = toDispatchRequest(entity.data());
        return lifecycleDispatcher.dispatch(request)
                .then((executionId, error) -> {
                    if (error != null) {
                        return persistFailure(tenantId, entity.data(), error)
                                .map(ignored -> DispatchOutcome.failed(request.handoffId()));
                    }

                    return persistDispatched(tenantId, entity.data(), executionId)
                            .map(ignored -> DispatchOutcome.dispatched(request.handoffId(), executionId));
                });
    }

    private Promise<Void> persistDispatched(String tenantId, Map<String, Object> original, String executionId) {
        Map<String, Object> updated = new LinkedHashMap<>(original);
        updated.put("status", "DISPATCHED");
        updated.put("dispatchedAt", Instant.now().toString());
        updated.put("executionId", executionId);
        return dataCloudClient.save(tenantId, HANDOFF_COLLECTION, updated).toVoid();
    }

    private Promise<Void> persistFailure(String tenantId, Map<String, Object> original, Throwable error) {
        Map<String, Object> updated = new LinkedHashMap<>(original);
        updated.put("status", "FAILED");
        updated.put("failedAt", Instant.now().toString());
        updated.put("failureReason", error == null ? "Unknown dispatch failure" : asString(error.getMessage(), "Unknown dispatch failure"));
        return dataCloudClient.save(tenantId, HANDOFF_COLLECTION, updated).toVoid();
    }

    @SuppressWarnings("unchecked")
    private static EvolutionLifecycleExecutionDispatcher.EvolutionLifecycleExecutionRequest toDispatchRequest(Map<String, Object> data) {
        Object phases = data.get("phases");
        List<String> normalizedPhases = phases instanceof List<?> list
                ? list.stream().map(String::valueOf).toList()
                : List.of("validate", "generate", "run");

        Object metadata = data.get("metadata");
        Map<String, Object> normalizedMetadata = metadata instanceof Map<?, ?> map
                ? (Map<String, Object>) map
                : Map.of();

        return new EvolutionLifecycleExecutionDispatcher.EvolutionLifecycleExecutionRequest(
                asString(data.get("handoffId"), asString(data.get("id"), "handoff-unknown")),
                asString(data.get("proposalId"), "proposal-unknown"),
                asString(data.get("tenantId"), "tenant-unknown"),
                asString(data.get("projectId"), "project-unknown"),
                asString(data.get("productUnitIntentRef"), "intent-unknown"),
                asString(data.get("requestedBy"), "system"),
                normalizedPhases,
                normalizedMetadata
        );
    }

    private static String asString(Object value, String fallback) {
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return fallback;
    }

    public record DispatchSummary(int queued, int dispatched, int failed) {
    }

    private record DispatchOutcome(String handoffId, boolean dispatched, String executionId) {
        static DispatchOutcome dispatched(String handoffId, String executionId) {
            return new DispatchOutcome(handoffId, true, executionId);
        }

        static DispatchOutcome failed(String handoffId) {
            return new DispatchOutcome(handoffId, false, null);
        }
    }
}
