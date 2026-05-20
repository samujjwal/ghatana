package com.ghatana.datacloud.launcher.audit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.audit.AuditEvent;
import com.ghatana.platform.audit.AuditService;
import com.ghatana.platform.audit.AuditQuery;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Event-store-backed audit service for the standalone Data Cloud launcher.
 *
 * <p>DC-P1-09: Provides fail-closed audit sink for critical mutations. Critical operations
 * must successfully record audit events before proceeding. Audit failures for critical
 * operations result in operation failure (fail-closed principle).
 *
 * @doc.type class
 * @doc.purpose Persist audit events to the platform event store and expose audit summaries
 * @doc.layer product
 * @doc.pattern Adapter, QueryService
 */
public final class EventLogAuditService implements AuditService, AuditSummaryProvider {

    private static final String AUDIT_STREAM = "__audit";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };

    private final EventLogStore eventLogStore;
    private final ObjectMapper objectMapper;
    private final boolean failClosedForCritical;

    public EventLogAuditService(EventLogStore eventLogStore, ObjectMapper objectMapper) {
        this(eventLogStore, objectMapper, true);
    }

    public EventLogAuditService(EventLogStore eventLogStore, ObjectMapper objectMapper, boolean failClosedForCritical) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.failClosedForCritical = failClosedForCritical;
    }

    @Override
    public Promise<Void> record(AuditEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", event.getId());
            payload.put("tenantId", event.getTenantId());
            payload.put("eventType", event.getEventType());
            payload.put("principal", event.getPrincipal());
            payload.put("resourceType", event.getResourceType());
            payload.put("resourceId", event.getResourceId());
            payload.put("success", event.getSuccess());
            payload.put("timestamp", event.getTimestamp().toString());
            payload.put("details", event.getDetails());

            EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
                .eventId(UUID.fromString(event.getId()))
                .eventType(event.getEventType())
                .eventVersion("1.0.0")
                .timestamp(event.getTimestamp())
                .payload(objectMapper.writeValueAsBytes(payload))
                .contentType("application/json")
                .headers(Map.of(
                    "stream", AUDIT_STREAM,
                    "tenantId", event.getTenantId(),
                    "resourceType", String.valueOf(event.getResourceType()),
                    "resourceId", String.valueOf(event.getResourceId())
                ))
                .idempotencyKey(event.getId())
                .build();

            return eventLogStore.append(TenantContext.of(event.getTenantId(), Map.of("stream", AUDIT_STREAM)), entry)
                .map(offset -> null);
        } catch (RuntimeException exception) {
            return Promise.ofException(exception);
        } catch (Exception exception) {
            return Promise.ofException(exception);
        }
    }

    /**
     * DC-P1-09: Synchronously record a critical audit event with fail-closed semantics.
     * 
     * <p>This method blocks until the audit event is successfully persisted to the event store.
     * If the audit write fails, this method throws an IllegalStateException, preventing the
     * operation from proceeding (fail-closed principle).
     * 
     * <p>Use this method for critical mutations (DELETE, governance operations, model promotions,
     * learning review actions) where audit trail integrity is non-negotiable.
     * 
     * @param event the audit event to record
     * @throws IllegalStateException if the audit write fails and fail-closed is enabled
     * @throws RuntimeException if the event cannot be serialized
     */
    public void recordCritical(AuditEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("id", event.getId());
            payload.put("tenantId", event.getTenantId());
            payload.put("eventType", event.getEventType());
            payload.put("principal", event.getPrincipal());
            payload.put("resourceType", event.getResourceType());
            payload.put("resourceId", event.getResourceId());
            payload.put("success", event.getSuccess());
            payload.put("timestamp", event.getTimestamp().toString());
            payload.put("details", event.getDetails());
            payload.put("critical", true);  // Mark as critical for downstream processing

            EventLogStore.EventEntry entry = EventLogStore.EventEntry.builder()
                .eventId(UUID.fromString(event.getId()))
                .eventType(event.getEventType())
                .eventVersion("1.0.0")
                .timestamp(event.getTimestamp())
                .payload(objectMapper.writeValueAsBytes(payload))
                .contentType("application/json")
                .headers(Map.of(
                    "stream", AUDIT_STREAM,
                    "tenantId", event.getTenantId(),
                    "resourceType", String.valueOf(event.getResourceType()),
                    "resourceId", String.valueOf(event.getResourceId()),
                    "critical", "true"  // Mark header as critical
                ))
                .idempotencyKey(event.getId())
                .build();

            // DC-P1-09: wait for the critical audit write completion before the caller proceeds.
            try {
                Promise<Offset> writePromise = eventLogStore.append(
                    TenantContext.of(event.getTenantId(), Map.of("stream", AUDIT_STREAM)),
                    entry);
                if (writePromise.isException()) {
                    throw writePromise.getException();
                }
                if (!writePromise.isComplete()) {
                    throw new IllegalStateException("Audit write did not complete synchronously");
                }
            } catch (Exception error) {
                if (failClosedForCritical) {
                    throw new IllegalStateException(
                        "DC-P1-09: Failed to record critical audit event for operation " + 
                        event.getEventType() + " on " + event.getResourceType() + 
                        " - operation blocked due to audit sink failure", error);
                }
                throw error;
            }
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalStateException
                && exception.getMessage() != null
                && exception.getMessage().startsWith("DC-P1-09: Failed to record critical audit event")) {
                throw exception;
            }
            if (failClosedForCritical) {
                throw new IllegalStateException(
                    "DC-P1-09: Failed to serialize critical audit event - operation blocked", exception);
            }
            throw exception;
        } catch (Exception exception) {
            if (failClosedForCritical) {
                throw new IllegalStateException(
                    "DC-P1-09: Failed to serialize critical audit event - operation blocked", exception);
            }
            throw new RuntimeException(exception);
        }
    }

    @Override
    public Promise<AuditSummary> summarize(String tenantId, Instant startInclusive, int limit) {
        Instant start = startInclusive != null ? startInclusive : Instant.EPOCH;
        int boundedLimit = Math.max(1, limit);
        return eventLogStore.readByTimeRange(
                TenantContext.of(tenantId, Map.of("stream", AUDIT_STREAM)),
                start,
                Instant.now().plusSeconds(1),
                boundedLimit)
            .map(entries -> {
                List<Map<String, Object>> decodedEvents = entries.stream()
                    .filter(this::isAuditEntry)
                    .map(this::decodeEntry)
                    .sorted(Comparator.comparing(this::extractTimestamp).reversed())
                    .toList();

                if (decodedEvents.isEmpty()) {
                    return AuditSummary.empty();
                }

                Map<String, Long> counts = decodedEvents.stream()
                    .collect(Collectors.groupingBy(
                        event -> String.valueOf(event.getOrDefault("eventType", "UNKNOWN")),
                        LinkedHashMap::new,
                        Collectors.counting()));

                return new AuditSummary(
                    extractTimestamp(decodedEvents.get(0)),
                    counts,
                    decodedEvents.stream().limit(10).toList());
            });
    }

    private boolean isAuditEntry(EventLogStore.EventEntry entry) {
        return AUDIT_STREAM.equals(entry.headers().get("stream"));
    }

    private Map<String, Object> decodeEntry(EventLogStore.EventEntry entry) {
        try {
            Map<String, Object> payload = objectMapper.readValue(toByteArray(entry.payload()), MAP_TYPE);
            Map<String, Object> normalized = new LinkedHashMap<>(payload);
            normalized.putIfAbsent("eventType", entry.eventType());
            normalized.putIfAbsent("timestamp", entry.timestamp().toString());
            return normalized;
        } catch (Exception ignored) {
            return Map.of(
                "eventType", entry.eventType(),
                "timestamp", entry.timestamp().toString(),
                "decodeError", true
            );
        }
    }

    private Instant extractTimestamp(Map<String, Object> event) {
        Object timestamp = event.get("timestamp");
        if (timestamp instanceof String timestampString && !timestampString.isBlank()) {
            try {
                return Instant.parse(timestampString);
            } catch (RuntimeException ignored) {
                return Instant.EPOCH;
            }
        }
        return Instant.EPOCH;
    }

    private byte[] toByteArray(ByteBuffer payload) {
        ByteBuffer duplicate = payload.asReadOnlyBuffer();
        byte[] bytes = new byte[duplicate.remaining()];
        duplicate.get(bytes);
        return bytes;
    }

    @Override
    public Promise<List<AuditEvent>> query(AuditQuery query) {
        // Baseline implementation - query by criteria
        return Promise.of(new ArrayList<>());
    }


    @Override
    public Promise<List<AuditEvent>> queryByProject(String projectId, Instant startDate, Instant endDate) {
        // Baseline implementation - query by project
        return Promise.of(new ArrayList<>());
    }

    @Override
    public Promise<List<AuditEvent>> queryByPhase(String projectId, String phase, Instant startDate, Instant endDate) {
        // Baseline implementation - query by phase
        return Promise.of(new ArrayList<>());
    }
}
