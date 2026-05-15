package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.bridge.port.BridgeContext;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;

/**
 * Data Cloud-backed runtime truth provider.
 *
 * @doc.type class
 * @doc.purpose Persist lifecycle runtime truth snapshots in Data Cloud platform mode
 * @doc.layer adapter
 * @doc.pattern Provider
 */
public final class DataCloudRuntimeTruthProvider extends DataCloudKernelProviderSupport {

    /**
     * Typed record for runtime truth persist requests.
     *
     * @doc.type record
     * @doc.purpose Encapsulate runtime truth persist request with tenant context
     * @doc.layer adapter
     * @doc.pattern Request
     */
    public record RuntimeTruthPersistRequest(
        String snapshotId,
        Map<String, Object> snapshotData,
        Instant capturedAt,
        String correlationId
    ) {}

    /**
     * Typed record for runtime truth persist responses.
     *
     * @doc.type record
     * @doc.purpose Encapsulate runtime truth persist response with success status
     * @doc.layer adapter
     * @doc.pattern Response
     */
    public record RuntimeTruthPersistResponse(
        boolean success,
        String snapshotId,
        String persistedAt
    ) {}

    public DataCloudRuntimeTruthProvider(DataCloudKernelAdapter adapter, BridgeContext context) {
        super(adapter, context, "kernel.runtime-truth." + context.getTenantId(), "runtime-truth");
    }

    public Promise<Void> persistRuntimeTruth(String snapshotId, Map<String, Object> snapshot) {
        return persistRecord(snapshotId, snapshot);
    }

    public Promise<RuntimeTruthPersistResponse> persistRuntimeTruthTyped(RuntimeTruthPersistRequest request) {
        Map<String, Object> snapshotMap = Map.of(
            "snapshotId", request.snapshotId(),
            "snapshotData", request.snapshotData(),
            "capturedAt", request.capturedAt().toString(),
            "correlationId", request.correlationId(),
            "tenantId", context().getTenantId(),
            "workspaceId", context().getWorkspaceId(),
            "projectId", context().getProjectId(),
            "persistedAt", Instant.now().toString()
        );
        return persistRecord(request.snapshotId(), snapshotMap)
            .map($ -> new RuntimeTruthPersistResponse(true, request.snapshotId(), Instant.now().toString()));
    }
}
