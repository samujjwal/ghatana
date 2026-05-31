package com.ghatana.yappc.services.phase;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Queries and enriches phase packet project state.
 *
 * @doc.type class
 * @doc.purpose Queries and enriches phase packet project state from DataCloud
 * @doc.layer service
 * @doc.pattern Service
 */
public final class PhaseProjectStateService {

    private static final Logger log = LoggerFactory.getLogger(PhaseProjectStateService.class);

    private final DataCloudClient dataCloudClient;
    private final PhaseFeatureFlagProvider phaseFeatureFlagProvider;

    public PhaseProjectStateService(
            @NotNull DataCloudClient dataCloudClient,
            @NotNull PhaseFeatureFlagProvider phaseFeatureFlagProvider
    ) {
        this.dataCloudClient = Objects.requireNonNull(dataCloudClient, "dataCloudClient");
        this.phaseFeatureFlagProvider = Objects.requireNonNull(phaseFeatureFlagProvider, "phaseFeatureFlagProvider");
    }

    Promise<Map<String, Object>> queryProjectState(
            String phase,
            String projectId,
            String workspaceId,
            String tenantId,
            String correlationId
    ) {
        return queryProjectSnapshot(phase, projectId, workspaceId, tenantId, correlationId)
                .map(ProjectLifecycleSnapshot::state);
    }

    Promise<ProjectLifecycleSnapshot> queryProjectSnapshot(
            String phase,
            String projectId,
            String workspaceId,
            String tenantId,
            String correlationId
    ) {
        try {
            return dataCloudClient.findById(tenantId, "projects", projectId)
                    .then((entityOpt, error) -> {
                        if (error != null) {
                            log.error(
                                    "DataCloud query failed for project: tenantId={}, workspaceId={}, projectId={}, phase={}, correlationId={}",
                                    tenantId,
                                    workspaceId,
                                    projectId,
                                    phase,
                                    correlationId,
                                    error);
                            return Promise.of(degradedSnapshot(projectId, workspaceId, tenantId, "PROJECT_STATE_QUERY_FAILED"));
                        }

                        Map<String, Object> data = entityOpt.isPresent() ? entityOpt.get().data() : Map.of();
                        if (data.isEmpty()) {
                            log.warn(
                                    "Project state not found: tenantId={}, workspaceId={}, projectId={}, phase={}, correlationId={}",
                                    tenantId,
                                    workspaceId,
                                    projectId,
                                    phase,
                                    correlationId);
                            return Promise.of(degradedSnapshot(projectId, workspaceId, tenantId, "PROJECT_STATE_NOT_FOUND"));
                        }

                        Map<String, Object> state = new HashMap<>(data);
                        state.putIfAbsent("projectId", projectId);
                        state.putIfAbsent("workspaceId", workspaceId);
                        state.putIfAbsent("tenantId", tenantId);
                        state.putIfAbsent("name", "Project-" + projectId);
                        state.putIfAbsent("tier", "PRO");
                        state.putIfAbsent("status", "active");
                        state.putIfAbsent("createdAt", Instant.now().toString());
                        return phaseFeatureFlagProvider.enrichProjectStateWithTenantFlags(tenantId, state)
                                .map(PhaseProjectStateService::toSnapshot);
                    });
        } catch (Exception exception) {
            log.error(
                    "Unexpected error in queryProjectState: tenantId={}, workspaceId={}, projectId={}, phase={}, correlationId={}",
                    tenantId,
                    workspaceId,
                    projectId,
                    phase,
                    correlationId,
                    exception);
                        return Promise.of(degradedSnapshot(projectId, workspaceId, tenantId, "PROJECT_STATE_QUERY_FAILED"));
        }
    }

        private static ProjectLifecycleSnapshot degradedSnapshot(
                        String projectId,
                        String workspaceId,
                        String tenantId,
                        String reason
        ) {
                Map<String, Object> degradedState = Map.of(
                                "projectId", projectId,
                                "workspaceId", workspaceId,
                                "tenantId", tenantId,
                                "degraded", true,
                                "degradedReason", reason,
                                "name", "Project-" + projectId,
                                "workspaceName", "Workspace-" + workspaceId,
                                "lifecyclePhase", "intent",
                                "tier", "FREE",
                                "status", "degraded");
                return toSnapshot(degradedState);
        }

        private static ProjectLifecycleSnapshot toSnapshot(Map<String, Object> state) {
                Set<String> enabledFlags = extractEnabledFlags(state);
                return new ProjectLifecycleSnapshot(
                                asString(state.get("tenantId"), "unknown-tenant"),
                                asString(state.get("workspaceId"), "unknown-workspace"),
                                asString(state.get("projectId"), "unknown-project"),
                                asString(state.get("name"), "Unnamed Project"),
                                asString(state.get("workspaceName"), "Workspace-" + asString(state.get("workspaceId"), "unknown-workspace")),
                                asString(state.get("lifecyclePhase"), asString(state.get("currentPhase"), "intent")),
                                asString(state.get("tier"), "FREE"),
                                asString(state.get("status"), "active"),
                                Boolean.TRUE.equals(state.get("degraded")),
                                asString(state.get("degradedReason"), ""),
                                enabledFlags,
                                state
                );
        }

        private static Set<String> extractEnabledFlags(Map<String, Object> state) {
                Object rawFlags = state.get("enabledPhaseFlags");
                if (rawFlags instanceof Iterable<?> iterable) {
                        ArrayList<String> values = new ArrayList<>();
                        for (Object value : iterable) {
                                if (value instanceof String text && !text.isBlank()) {
                                        values.add(text);
                                }
                        }
                        return Set.copyOf(values);
                }
                return Set.of();
        }

        private static String asString(Object value, String fallback) {
                if (value instanceof String text && !text.isBlank()) {
                        return text;
                }
                return fallback;
        }
}
