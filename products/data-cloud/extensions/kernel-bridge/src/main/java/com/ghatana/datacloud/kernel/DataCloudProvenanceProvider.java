package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.bridge.port.BridgeContext;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;

/**
 * Data Cloud-backed provenance provider.
 *
 * @doc.type class
 * @doc.purpose Persist lifecycle provenance records in Data Cloud platform mode
 * @doc.layer adapter
 * @doc.pattern Provider
 */
public final class DataCloudProvenanceProvider extends DataCloudKernelProviderSupport {

    /**
     * Typed record for provenance persist requests.
     *
     * @doc.type record
     * @doc.purpose Encapsulate provenance persist request with tenant context
     * @doc.layer adapter
     * @doc.pattern Request
     */
    public record ProvenancePersistRequest(
        String provenanceId,
        Map<String, Object> provenanceData,
        Instant recordedAt,
        String correlationId
    ) {}

    /**
     * Typed record for provenance persist responses.
     *
     * @doc.type record
     * @doc.purpose Encapsulate provenance persist response with success status
     * @doc.layer adapter
     * @doc.pattern Response
     */
    public record ProvenancePersistResponse(
        boolean success,
        String provenanceId,
        String persistedAt
    ) {}

    public DataCloudProvenanceProvider(DataCloudKernelAdapter adapter, BridgeContext context) {
        super(adapter, context, "kernel.provenance." + context.getTenantId(), "provenance");
    }

    public Promise<Void> persistProvenance(String provenanceId, Map<String, Object> provenance) {
        return persistRecord(provenanceId, provenance);
    }

    public Promise<ProvenancePersistResponse> persistProvenanceTyped(ProvenancePersistRequest request) {
        Map<String, Object> provenanceMap = Map.of(
            "provenanceId", request.provenanceId(),
            "provenanceData", request.provenanceData(),
            "recordedAt", request.recordedAt().toString(),
            "correlationId", request.correlationId(),
            "tenantId", context().getTenantId(),
            "workspaceId", context().getWorkspaceId(),
            "projectId", context().getProjectId(),
            "persistedAt", Instant.now().toString()
        );
        return persistRecord(request.provenanceId(), provenanceMap)
            .map($ -> new ProvenancePersistResponse(true, request.provenanceId(), Instant.now().toString()));
    }
}
