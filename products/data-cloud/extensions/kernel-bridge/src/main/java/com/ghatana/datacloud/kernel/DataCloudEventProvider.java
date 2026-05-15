package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.bridge.port.BridgeContext;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.Map;

/**
 * Data Cloud-backed lifecycle event provider.
 *
 * @doc.type class
 * @doc.purpose Persist kernel lifecycle events in Data Cloud platform mode
 * @doc.layer adapter
 * @doc.pattern Provider
 */
public final class DataCloudEventProvider extends DataCloudKernelProviderSupport {

    /**
     * Typed record for event append requests.
     *
     * @doc.type record
     * @doc.purpose Encapsulate event append request with tenant context
     * @doc.layer adapter
     * @doc.pattern Request
     */
    public record EventAppendRequest(
        String eventId,
        String eventType,
        Map<String, Object> eventData,
        Instant occurredAt,
        String correlationId
    ) {}

    /**
     * Typed record for event append responses.
     *
     * @doc.type record
     * @doc.purpose Encapsulate event append response with success status
     * @doc.layer adapter
     * @doc.pattern Response
     */
    public record EventAppendResponse(
        boolean success,
        String eventId,
        String persistedAt
    ) {}

    public DataCloudEventProvider(DataCloudKernelAdapter adapter, BridgeContext context) {
        super(adapter, context, "kernel.events." + context.getTenantId(), "events");
    }

    public Promise<Void> appendEvent(String eventId, Map<String, Object> event) {
        return persistRecord(eventId, event);
    }

    public Promise<EventAppendResponse> appendEventTyped(EventAppendRequest request) {
        Map<String, Object> eventMap = Map.of(
            "eventId", request.eventId(),
            "eventType", request.eventType(),
            "eventData", request.eventData(),
            "occurredAt", request.occurredAt().toString(),
            "correlationId", request.correlationId(),
            "tenantId", context().getTenantId(),
            "workspaceId", context().getWorkspaceId(),
            "projectId", context().getProjectId(),
            "persistedAt", Instant.now().toString()
        );
        return persistRecord(request.eventId(), eventMap)
            .map($ -> new EventAppendResponse(true, request.eventId(), Instant.now().toString()));
    }
}
