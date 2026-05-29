package com.ghatana.yappc.services.phase;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Queries and enriches phase packet project state.
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
                            return Promise.of(Map.of(
                                    "projectId", projectId,
                                    "workspaceId", workspaceId,
                                    "tenantId", tenantId,
                                    "degraded", true,
                                    "degradedReason", "PROJECT_STATE_QUERY_FAILED"
                            ));
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
                            return Promise.of(Map.of(
                                    "projectId", projectId,
                                    "workspaceId", workspaceId,
                                    "tenantId", tenantId,
                                    "degraded", true,
                                    "degradedReason", "PROJECT_STATE_NOT_FOUND"
                            ));
                        }

                        Map<String, Object> state = new HashMap<>(data);
                        state.putIfAbsent("projectId", projectId);
                        state.putIfAbsent("workspaceId", workspaceId);
                        state.putIfAbsent("tenantId", tenantId);
                        state.putIfAbsent("name", "Project-" + projectId);
                        state.putIfAbsent("tier", "PRO");
                        state.putIfAbsent("status", "active");
                        state.putIfAbsent("createdAt", Instant.now().toString());
                        return phaseFeatureFlagProvider.enrichProjectStateWithTenantFlags(tenantId, state);
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
            return Promise.of(Map.of(
                    "projectId", projectId,
                    "workspaceId", workspaceId,
                    "tenantId", tenantId,
                    "degraded", true,
                    "degradedReason", "PROJECT_STATE_QUERY_FAILED"
            ));
        }
    }
}
