package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.bridge.port.BridgeContext;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Data Cloud-backed lifecycle health provider.
 *
 * @doc.type class
 * @doc.purpose Persist provider and lifecycle health snapshots in Data Cloud platform mode
 * @doc.layer adapter
 * @doc.pattern Provider
 */
public final class DataCloudHealthProvider extends DataCloudKernelProviderSupport {

    /**
     * Typed record for health snapshot persist requests.
     *
     * @doc.type record
     * @doc.purpose Encapsulate health snapshot persist request with tenant context
     * @doc.layer adapter
     * @doc.pattern Request
     */
    public record HealthSnapshotPersistRequest(
        String snapshotId,
        String status,
        Map<String, Object> details,
        Instant capturedAt,
        String correlationId
    ) {}

    /**
     * Typed record for health snapshot persist responses.
     *
     * @doc.type record
     * @doc.purpose Encapsulate health snapshot persist response with success status
     * @doc.layer adapter
     * @doc.pattern Response
     */
    public record HealthSnapshotPersistResponse(
        boolean success,
        String snapshotId,
        String persistedAt
    ) {}

    public DataCloudHealthProvider(DataCloudKernelAdapter adapter, BridgeContext context) {
        super(adapter, context, "kernel.health." + context.getTenantId(), "health");
    }

    public Promise<Void> persistHealthSnapshot(String snapshotId, String status, Map<String, Object> details) {
        return persistRecord(snapshotId, Map.of(
            "snapshotId", snapshotId,
            "status", status,
            "details", details != null ? details : Map.of(),
            "tenantId", context().getTenantId(),
            "capturedAt", Instant.now().toString()
        ));
    }

    public Promise<HealthSnapshotPersistResponse> persistHealthSnapshotTyped(HealthSnapshotPersistRequest request) {
        DataCloudProviderException validationError = validateHealthSnapshot(request);
        if (validationError != null) {
            return Promise.ofException(validationError);
        }

        Map<String, Object> snapshotMap = new HashMap<>();
        snapshotMap.put("snapshotId", request.snapshotId());
        snapshotMap.put("status", request.status());
        snapshotMap.put("details", request.details() != null ? request.details() : Map.of());
        snapshotMap.put("capturedAt", request.capturedAt().toString());
        snapshotMap.put("correlationId", request.correlationId());
        snapshotMap.put("tenantId", context().getTenantId());
        snapshotMap.put("workspaceId", context().getWorkspaceId());
        snapshotMap.put("projectId", context().getProjectId());
        snapshotMap.put("persistedAt", Instant.now().toString());
        return persistRecord(request.snapshotId(), snapshotMap)
            .map($ -> new HealthSnapshotPersistResponse(true, request.snapshotId(), Instant.now().toString()));
    }

    private DataCloudProviderException validateHealthSnapshot(HealthSnapshotPersistRequest request) {
        if (isBlank(request.snapshotId())) {
            return invalidHealthSnapshot("snapshotId is required");
        }
        if (isBlank(request.status())) {
            return invalidHealthSnapshot("status is required");
        }
        if (request.capturedAt() == null) {
            return invalidHealthSnapshot("capturedAt is required");
        }
        if (isBlank(request.correlationId())) {
            return invalidHealthSnapshot("correlationId is required");
        }
        return null;
    }

    private DataCloudProviderException invalidHealthSnapshot(String message) {
        return new DataCloudProviderException(
            "health",
            "persist-health-snapshot",
            message,
            DataCloudProviderException.ReasonCode.SCHEMA
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
