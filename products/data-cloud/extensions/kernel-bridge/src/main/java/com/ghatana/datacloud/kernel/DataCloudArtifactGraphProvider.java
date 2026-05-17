package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.bridge.port.BridgeContext;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;

/**
 * Data Cloud-backed artifact graph provider.
 *
 * @doc.type class
 * @doc.purpose Persist artifact graph summaries with nodes and edges in Data Cloud platform mode
 * @doc.layer adapter
 * @doc.pattern Provider
 */
public final class DataCloudArtifactGraphProvider extends DataCloudKernelProviderSupport {

    /**
     * Typed record for artifact graph persist requests.
     *
     * @doc.type record
     * @doc.purpose Encapsulate artifact graph persist request with tenant context
     * @doc.layer adapter
     * @doc.pattern Request
     */
    public record ArtifactGraphPersistRequest(
        String graphId,
        Map<String, Object> graphData,
        Instant capturedAt,
        String correlationId
    ) {}

    /**
     * Typed record for artifact graph persist responses.
     *
     * @doc.type record
     * @doc.purpose Encapsulate artifact graph persist response with success status
     * @doc.layer adapter
     * @doc.pattern Response
     */
    public record ArtifactGraphPersistResponse(
        boolean success,
        String graphId,
        String persistedAt
    ) {}

    public DataCloudArtifactGraphProvider(DataCloudKernelAdapter adapter, BridgeContext context) {
        super(adapter, context, "kernel.artifact-graph." + context.getTenantId(), "artifact-graph");
    }

    public Promise<Void> persistArtifactGraph(String graphId, Map<String, Object> graph) {
        return persistRecord(graphId, graph);
    }

    public Promise<ArtifactGraphPersistResponse> persistArtifactGraphTyped(ArtifactGraphPersistRequest request) {
        Map<String, Object> graphMap = Map.of(
            "graphId", request.graphId(),
            "graphData", request.graphData(),
            "capturedAt", request.capturedAt().toString(),
            "correlationId", request.correlationId(),
            "tenantId", context().getTenantId(),
            "workspaceId", context().getWorkspaceId(),
            "projectId", context().getProjectId(),
            "persistedAt", Instant.now().toString()
        );
        return persistRecord(request.graphId(), graphMap)
            .map($ -> new ArtifactGraphPersistResponse(true, request.graphId(), Instant.now().toString()));
    }
}
