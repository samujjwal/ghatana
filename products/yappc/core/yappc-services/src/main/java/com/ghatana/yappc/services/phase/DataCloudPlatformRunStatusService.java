/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.phase;

import com.ghatana.datacloud.DataCloudClient;
import com.ghatana.yappc.api.PhasePacket;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Data Cloud-backed platform run status reader.
 *
 * @doc.type class
 * @doc.purpose Read tenant-scoped YAPPC platform run status from Data Cloud
 * @doc.layer services
 * @doc.pattern Adapter
 */
public final class DataCloudPlatformRunStatusService implements PlatformRunStatusService {

    private static final Logger log = LoggerFactory.getLogger(DataCloudPlatformRunStatusService.class);
    private static final String COLLECTION = "yappc_platform_runs";

    private final DataCloudClient dataCloudClient;

    /**
     * Creates a Data Cloud-backed run status service.
     *
     * @param dataCloudClient Data Cloud client
     */
    public DataCloudPlatformRunStatusService(@NotNull DataCloudClient dataCloudClient) {
        this.dataCloudClient = dataCloudClient;
    }

    @Override
    public Promise<Optional<PhasePacket.PlatformRunStatus>> findLatest(
            @NotNull String tenantId,
            @NotNull String workspaceId,
            @NotNull String projectId,
            @NotNull String phase
    ) {
        DataCloudClient.Query query = DataCloudClient.Query.builder()
                .filters(List.of(
                        DataCloudClient.Filter.eq("workspaceId", workspaceId),
                        DataCloudClient.Filter.eq("projectId", projectId),
                        DataCloudClient.Filter.eq("phase", phase.toUpperCase())
                ))
                .limit(25)
                .build();

        return dataCloudClient.query(tenantId, COLLECTION, query)
                .map(entities -> entities.stream()
                        .map(DataCloudClient.Entity::data)
                        .map(this::toPlatformRunStatus)
                        .flatMap(Optional::stream)
                        .max(Comparator.comparing(PhasePacket.PlatformRunStatus::startedAt)))
                .then((status, error) -> {
                    if (error != null) {
                        log.error(
                                "Failed to read platform run status: tenantId={}, workspaceId={}, projectId={}, phase={}",
                                tenantId,
                                workspaceId,
                                projectId,
                                phase,
                                error
                        );
                        return Promise.of(Optional.of(degradedStatus(projectId, phase, error)));
                    }
                    return Promise.of(status);
                });
    }

    private PhasePacket.PlatformRunStatus degradedStatus(String projectId, String phase, Throwable error) {
        return new PhasePacket.PlatformRunStatus(
                "degraded-" + projectId + "-" + phase.toLowerCase(),
                "DEGRADED_RUNTIME_TRUTH",
                "data-cloud-aep",
                Instant.now(),
                null,
                "data-cloud-run-status-query-failed",
                List.of("runtime-truth-query-failed:" + error.getClass().getSimpleName()));
    }

    private Optional<PhasePacket.PlatformRunStatus> toPlatformRunStatus(Map<String, Object> data) {
        String runId = stringValue(data.get("runId"));
        String status = stringValue(data.get("status"));
        if (runId == null || status == null) {
            return Optional.empty();
        }

        Instant startedAt = instantValue(data.get("startedAt")).orElse(Instant.EPOCH);
        Instant completedAt = instantValue(data.get("completedAt")).orElse(null);
        String platform = stringValue(data.getOrDefault("platform", "data-cloud-aep"));
        String traceId = stringValue(data.get("traceId"));
        List<String> evidenceIds = stringListValue(data.get("evidenceIds"));

        return Optional.of(new PhasePacket.PlatformRunStatus(
                runId,
                status,
                platform != null ? platform : "data-cloud-aep",
                startedAt,
                completedAt,
                traceId,
                evidenceIds
        ));
    }

    private static String stringValue(Object value) {
        if (value instanceof String text && !text.isBlank()) {
            return text;
        }
        return null;
    }

    private static Optional<Instant> instantValue(Object value) {
        if (value instanceof Instant instant) {
            return Optional.of(instant);
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Optional.of(Instant.parse(text));
            } catch (RuntimeException e) {
                log.warn("Ignoring invalid platform run timestamp: value={}", text, e);
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    private static List<String> stringListValue(Object value) {
        if (value instanceof List<?> values) {
            return values.stream()
                    .filter(String.class::isInstance)
                    .map(String.class::cast)
                    .filter(item -> !item.isBlank())
                    .toList();
        }
        if (value instanceof String text && !text.isBlank()) {
            return List.of(text);
        }
        return List.of();
    }
}
