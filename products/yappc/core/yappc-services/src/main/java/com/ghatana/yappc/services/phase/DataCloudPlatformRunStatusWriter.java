/*
 * Copyright (c) 2026 Ghatana Inc.
 * All rights reserved.
 */
package com.ghatana.yappc.services.phase;

import com.ghatana.datacloud.DataCloudClient;
import io.activej.promise.Promise;
import org.jetbrains.annotations.NotNull;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Data Cloud-backed writer for {@code yappc_platform_runs}.
 *
 * @doc.type class
 * @doc.purpose Ingest AEP/Kernel execution events into platform run status truth
 * @doc.layer services
 * @doc.pattern Adapter
 */
public final class DataCloudPlatformRunStatusWriter implements PlatformRunStatusWriter {

    private static final String COLLECTION = "yappc_platform_runs";
    private static final String DEFAULT_PLATFORM = "data-cloud-aep";

    private final DataCloudClient dataCloudClient;

    /**
     * Creates a Data Cloud-backed platform run status writer.
     *
     * @param dataCloudClient Data Cloud client
     */
    public DataCloudPlatformRunStatusWriter(@NotNull DataCloudClient dataCloudClient) {
        this.dataCloudClient = dataCloudClient;
    }

    @Override
    public Promise<Void> record(@NotNull PlatformRunStatusRecord record) {
        validate(record);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("workspaceId", record.workspaceId());
        data.put("projectId", record.projectId());
        data.put("phase", record.phase().toUpperCase(Locale.ROOT));
        data.put("runId", record.runId());
        data.put("status", record.status().toUpperCase(Locale.ROOT));
        data.put("platform", defaultIfBlank(record.platform(), DEFAULT_PLATFORM));
        data.put("startedAt", record.startedAt().toString());
        if (record.completedAt() != null) {
            data.put("completedAt", record.completedAt().toString());
        }
        if (hasText(record.traceId())) {
            data.put("traceId", record.traceId());
        }
        data.put("evidenceIds", record.evidenceIds() == null ? List.of() : List.copyOf(record.evidenceIds()));
        if (hasText(record.sourceEventType())) {
            data.put("sourceEventType", record.sourceEventType());
        }
        if (hasText(record.correlationId())) {
            data.put("correlationId", record.correlationId());
        }
        data.put("updatedAt", Instant.now().toString());

        return dataCloudClient.save(record.tenantId(), COLLECTION, data).toVoid();
    }

    @Override
    public Promise<Void> ingestEvent(@NotNull String tenantId, @NotNull DataCloudClient.Event event) {
        if (!hasText(tenantId)) {
            return Promise.ofException(new IllegalArgumentException("tenantId is required"));
        }
        if (event == null) {
            return Promise.ofException(new IllegalArgumentException("event is required"));
        }

        Map<String, Object> payload = event.payload();
        String workspaceId = firstText(payload, "workspaceId", "workspace_id");
        String projectId = firstText(payload, "projectId", "project_id");
        String phase = defaultIfBlank(firstText(payload, "phase", "lifecyclePhase"), "RUN");
        String runId = defaultIfBlank(
                firstText(payload, "runId", "run_id", "executionId", "kernelExecutionId"),
                event.subjectId().orElse(null));
        String status = defaultIfBlank(firstText(payload, "status", "runStatus"), statusFromEventType(event.type()));
        String platform = defaultIfBlank(
                firstText(payload, "platform", "provider"),
                event.source().orElse(DEFAULT_PLATFORM));
        Instant startedAt = instantValue(payload.get("startedAt"))
                .or(() -> instantValue(payload.get("started_at")))
                .orElse(event.timestamp());
        Instant completedAt = instantValue(payload.get("completedAt"))
                .or(() -> instantValue(payload.get("completed_at")))
                .orElseGet(() -> terminalStatus(status) ? event.timestamp() : null);
        String traceId = defaultIfBlank(
                firstText(payload, "traceId", "trace_id"),
                event.traceContext().orElse(event.correlationId().orElse(null)));
        String correlationId = event.correlationId().orElse(firstText(payload, "correlationId", "correlation_id"));
        List<String> evidenceIds = stringListValue(payload.get("evidenceIds"));
        if (evidenceIds.isEmpty()) {
            evidenceIds = stringListValue(payload.get("evidence_ids"));
        }
        String evidenceId = firstText(payload, "evidenceId", "evidence_id");
        if (hasText(evidenceId) && evidenceIds.stream().noneMatch(evidenceId::equals)) {
            evidenceIds = new java.util.ArrayList<>(evidenceIds);
            evidenceIds.add(evidenceId);
        }

        return record(new PlatformRunStatusRecord(
                tenantId,
                workspaceId,
                projectId,
                phase,
                runId,
                status,
                platform,
                startedAt,
                completedAt,
                traceId,
                evidenceIds,
                event.type(),
                correlationId));
    }

    private static void validate(PlatformRunStatusRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("record is required");
        }
        requireText(record.tenantId(), "tenantId");
        requireText(record.workspaceId(), "workspaceId");
        requireText(record.projectId(), "projectId");
        requireText(record.phase(), "phase");
        requireText(record.runId(), "runId");
        requireText(record.status(), "status");
        if (record.startedAt() == null) {
            throw new IllegalArgumentException("startedAt is required");
        }
    }

    private static void requireText(String value, String fieldName) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(fieldName + " is required");
        }
    }

    private static String statusFromEventType(String eventType) {
        String normalized = eventType == null ? "" : eventType.toLowerCase(Locale.ROOT);
        if (normalized.endsWith(".started") || normalized.endsWith(".queued")) {
            return normalized.endsWith(".queued") ? "QUEUED" : "RUNNING";
        }
        if (normalized.endsWith(".completed") || normalized.endsWith(".succeeded")) {
            return "SUCCEEDED";
        }
        if (normalized.endsWith(".failed")) {
            return "FAILED";
        }
        if (normalized.endsWith(".cancelled") || normalized.endsWith(".canceled")) {
            return "CANCELLED";
        }
        return "UNKNOWN";
    }

    private static boolean terminalStatus(String status) {
        String normalized = status == null ? "" : status.toUpperCase(Locale.ROOT);
        return normalized.equals("SUCCEEDED")
                || normalized.equals("FAILED")
                || normalized.equals("CANCELLED")
                || normalized.equals("CANCELED");
    }

    private static String firstText(Map<String, Object> payload, String... keys) {
        for (String key : keys) {
            Object value = payload.get(key);
            if (value instanceof String text && !text.isBlank()) {
                return text;
            }
        }
        return null;
    }

    private static String defaultIfBlank(String value, String fallback) {
        return hasText(value) ? value : fallback;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static Optional<Instant> instantValue(Object value) {
        if (value instanceof Instant instant) {
            return Optional.of(instant);
        }
        if (value instanceof String text && !text.isBlank()) {
            return Optional.of(Instant.parse(text));
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

