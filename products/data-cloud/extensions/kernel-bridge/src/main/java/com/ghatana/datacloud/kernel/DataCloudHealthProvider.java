package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.bridge.port.BridgeContext;
import io.activej.promise.Promise;

import java.time.Instant;
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
        Map<String, Object> snapshotMap = Map.of(
            "snapshotId", request.snapshotId(),
            "status", request.status(),
            "details", request.details() != null ? request.details() : Map.of(),
            "capturedAt", request.capturedAt().toString(),
            "correlationId", request.correlationId(),
            "tenantId", context().getTenantId(),
            "workspaceId", context().getWorkspaceId(),
            "projectId", context().getProjectId(),
            "persistedAt", Instant.now().toString()
        );
        return persistRecord(request.snapshotId(), snapshotMap)
            .map($ -> new HealthSnapshotPersistResponse(true, request.snapshotId(), Instant.now().toString()));
    }
}
