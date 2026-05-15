package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.bridge.port.BridgeContext;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;

/**
 * Data Cloud-backed memory provider.
 *
 * @doc.type class
 * @doc.purpose Persist lifecycle and agent memory records in Data Cloud platform mode
 * @doc.layer adapter
 * @doc.pattern Provider
 */
public final class DataCloudMemoryProvider extends DataCloudKernelProviderSupport {

    /**
     * Typed record for memory remember requests.
     *
     * @doc.type record
     * @doc.purpose Encapsulate memory remember request with tenant context
     * @doc.layer adapter
     * @doc.pattern Request
     */
    public record MemoryRememberRequest(
        String memoryId,
        Map<String, Object> memoryData,
        Instant rememberedAt,
        String correlationId
    ) {}

    /**
     * Typed record for memory remember responses.
     *
     * @doc.type record
     * @doc.purpose Encapsulate memory remember response with success status
     * @doc.layer adapter
     * @doc.pattern Response
     */
    public record MemoryRememberResponse(
        boolean success,
        String memoryId,
        String persistedAt
    ) {}

    public DataCloudMemoryProvider(DataCloudKernelAdapter adapter, BridgeContext context) {
        super(adapter, context, "kernel.memory." + context.getTenantId(), "memory");
    }

    public Promise<Void> remember(String memoryId, Map<String, Object> memory) {
        return persistRecord(memoryId, memory);
    }

    public Promise<MemoryRememberResponse> rememberTyped(MemoryRememberRequest request) {
        Map<String, Object> memoryMap = Map.of(
            "memoryId", request.memoryId(),
            "memoryData", request.memoryData(),
            "rememberedAt", request.rememberedAt().toString(),
            "correlationId", request.correlationId(),
            "tenantId", context().getTenantId(),
            "workspaceId", context().getWorkspaceId(),
            "projectId", context().getProjectId(),
            "persistedAt", Instant.now().toString()
        );
        return persistRecord(request.memoryId(), memoryMap)
            .map($ -> new MemoryRememberResponse(true, request.memoryId(), Instant.now().toString()));
    }
}
