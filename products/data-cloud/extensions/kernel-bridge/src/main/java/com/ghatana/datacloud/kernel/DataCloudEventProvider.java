package com.ghatana.datacloud.kernel;

import com.ghatana.kernel.adapter.datacloud.DataCloudKernelAdapter;
import com.ghatana.kernel.bridge.port.BridgeContext;
import io.activej.promise.Promise;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Data Cloud-backed lifecycle event provider with kernel lifecycle event integration.
 *
 * @doc.type class
 * @doc.purpose Persist kernel lifecycle events in Data Cloud platform mode with typed contracts
 * @doc.layer adapter
 * @doc.pattern Provider
 */
public final class DataCloudEventProvider extends DataCloudKernelProviderSupport {

    private static final Set<String> PHR_HEALTHCARE_GATE_IDS = Set.of(
        "consent",
        "pii-classification",
        "audit-evidence",
        "fhir-contract-validation",
        "tenant-data-sovereignty"
    );

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

    /**
     * Kernel lifecycle event record for structured persistence.
     *
     * @doc.type record
     * @doc.purpose Kernel lifecycle event structure for Data Cloud persistence
     * @doc.layer adapter
     * @doc.pattern Event
     */
    public record KernelLifecycleEventRecord(
        String eventId,
        String schemaVersion,
        String eventType,
        String productUnitId,
        String runId,
        String phase,
        String timestamp,
        String source,
        String tenantId,
        String workspaceId,
        String projectId,
        String correlationId,
        Map<String, Object> payload,
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

    /**
     * Append a kernel lifecycle event with structured contract compliance.
     *
     * @param eventId Unique event identifier
     * @param schemaVersion Event schema version (should be "1.0.0")
     * @param eventType Kernel lifecycle event type
     * @param productUnitId Product unit identifier
     * @param runId Lifecycle run identifier
     * @param phase Lifecycle phase
     * @param source Event source (e.g., "kernel-lifecycle")
     * @param correlationId Correlation identifier for tracing
     * @param payload Event payload according to kernel lifecycle contracts
     * @return Promise of event append response
     */
    public Promise<EventAppendResponse> appendKernelLifecycleEvent(
        String eventId,
        String schemaVersion,
        String eventType,
        String productUnitId,
        String runId,
        String phase,
        String source,
        String correlationId,
        Map<String, Object> payload
    ) {
        DataCloudProviderException validationError = validateKernelLifecycleEvent(
            eventId,
            eventType,
            productUnitId,
            correlationId,
            payload
        );
        if (validationError != null) {
            return Promise.ofException(validationError);
        }

        KernelLifecycleEventRecord eventRecord = new KernelLifecycleEventRecord(
            eventId,
            schemaVersion,
            eventType,
            productUnitId,
            runId,
            phase,
            Instant.now().toString(),
            source,
            context().getTenantId(),
            context().getWorkspaceId(),
            context().getProjectId(),
            correlationId,
            payload,
            Instant.now().toString()
        );

        Map<String, Object> eventMap = new HashMap<>();
        eventMap.put("eventId", eventRecord.eventId());
        eventMap.put("schemaVersion", eventRecord.schemaVersion());
        eventMap.put("eventType", eventRecord.eventType());
        eventMap.put("productUnitId", eventRecord.productUnitId());
        eventMap.put("runId", eventRecord.runId());
        eventMap.put("phase", eventRecord.phase());
        eventMap.put("timestamp", eventRecord.timestamp());
        eventMap.put("source", eventRecord.source());
        eventMap.put("tenantId", eventRecord.tenantId());
        eventMap.put("workspaceId", eventRecord.workspaceId());
        eventMap.put("projectId", eventRecord.projectId());
        eventMap.put("correlationId", eventRecord.correlationId());
        eventMap.put("payload", eventRecord.payload());
        eventMap.put("persistedAt", eventRecord.persistedAt());
        eventMap.put("eventCategory", "kernel-lifecycle");
        eventMap.put("dataPlane", "lifecycle-events");

        return persistRecord(eventId, eventMap)
            .map($ -> new EventAppendResponse(true, eventId, eventRecord.persistedAt()));
    }

    private DataCloudProviderException validateKernelLifecycleEvent(
        String eventId,
        String eventType,
        String productUnitId,
        String correlationId,
        Map<String, Object> payload
    ) {
        if (isBlank(eventId)) {
            return invalidLifecycleEvent("eventId is required");
        }
        if (isBlank(eventType)) {
            return invalidLifecycleEvent("eventType is required");
        }
        if (isBlank(productUnitId)) {
            return invalidLifecycleEvent("productUnitId is required");
        }
        if (isBlank(correlationId)) {
            return invalidLifecycleEvent("correlationId is required");
        }
        if ("phr".equals(productUnitId) && "lifecycle.gate.evaluated".equals(eventType)) {
            Object gateId = payload == null ? null : payload.get("gateId");
            if (gateId instanceof String gate && PHR_HEALTHCARE_GATE_IDS.contains(gate)) {
                Object evidenceRefs = payload.get("evidenceRefs");
                if (!(evidenceRefs instanceof List<?> refs) || refs.isEmpty()) {
                    return invalidLifecycleEvent("PHR healthcare gate events require evidenceRefs");
                }
            }
        }
        return null;
    }

    private DataCloudProviderException invalidLifecycleEvent(String message) {
        return new DataCloudProviderException(
            "events",
            "append-kernel-lifecycle-event",
            message,
            DataCloudProviderException.ReasonCode.SCHEMA
        );
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /**
     * Query kernel lifecycle events by product unit and run.
     *
     * @param productUnitId Product unit identifier
     * @param runId Lifecycle run identifier (optional)
     * @param limit Maximum number of events to return
     * @return Promise of list of kernel lifecycle events
     */
    public Promise<List<Map<String, Object>>> queryKernelLifecycleEvents(
        String productUnitId,
        String runId,
        int limit
    ) {
        Map<String, Object> parameters = Map.of(
            "eventCategory", "kernel-lifecycle",
            "productUnitId", productUnitId,
            "dataPlane", "lifecycle-events"
        );
        
        if (runId != null && !runId.trim().isEmpty()) {
            Map<String, Object> runIdParams = new HashMap<>(parameters);
            runIdParams.put("runId", runId);
            return queryRecords(runIdParams, limit);
        }
        
        return queryRecords(parameters, limit);
    }

    /**
     * Query kernel lifecycle events by event type.
     *
     * @param eventType Kernel lifecycle event type
     * @param productUnitId Product unit identifier (optional)
     * @param limit Maximum number of events to return
     * @return Promise of list of kernel lifecycle events
     */
    public Promise<List<Map<String, Object>>> queryKernelLifecycleEventsByType(
        String eventType,
        String productUnitId,
        int limit
    ) {
        Map<String, Object> parameters = Map.of(
            "eventCategory", "kernel-lifecycle",
            "eventType", eventType,
            "dataPlane", "lifecycle-events"
        );
        
        if (productUnitId != null && !productUnitId.trim().isEmpty()) {
            Map<String, Object> productUnitParams = new HashMap<>(parameters);
            productUnitParams.put("productUnitId", productUnitId);
            return queryRecords(productUnitParams, limit);
        }
        
        return queryRecords(parameters, limit);
    }

    /**
     * Query kernel lifecycle events by phase.
     *
     * @param phase Lifecycle phase
     * @param productUnitId Product unit identifier (optional)
     * @param limit Maximum number of events to return
     * @return Promise of list of kernel lifecycle events
     */
    public Promise<List<Map<String, Object>>> queryKernelLifecycleEventsByPhase(
        String phase,
        String productUnitId,
        int limit
    ) {
        Map<String, Object> parameters = Map.of(
            "eventCategory", "kernel-lifecycle",
            "phase", phase,
            "dataPlane", "lifecycle-events"
        );
        
        if (productUnitId != null && !productUnitId.trim().isEmpty()) {
            Map<String, Object> productUnitParams = new HashMap<>(parameters);
            productUnitParams.put("productUnitId", productUnitId);
            return queryRecords(productUnitParams, limit);
        }
        
        return queryRecords(parameters, limit);
    }
}
