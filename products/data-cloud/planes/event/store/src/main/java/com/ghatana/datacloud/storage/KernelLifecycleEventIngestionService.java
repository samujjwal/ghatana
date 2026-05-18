package com.ghatana.datacloud.storage;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ghatana.platform.domain.eventstore.EventLogStore;
import com.ghatana.platform.domain.eventstore.TenantContext;
import com.ghatana.platform.types.identity.Offset;
import io.activej.promise.Promise;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Kernel lifecycle event ingestion backed by the Data Cloud event log store.
 *
 * @doc.type service
 * @doc.purpose Persist and query Kernel lifecycle events and artifact references through EventLogStore
 * @doc.layer product
 * @doc.pattern Adapter
 */
public final class KernelLifecycleEventIngestionService {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() { };
    private static final String LIFECYCLE_EVENT_TYPE = "kernel.lifecycle.event";
    private static final String ARTIFACT_REFERENCE_TYPE = "kernel.artifact.reference";

    private final EventLogStore eventLogStore;
    private final ConcurrentMap<String, Boolean> eventIds = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> runSequences = new ConcurrentHashMap<>();

    public KernelLifecycleEventIngestionService(EventLogStore eventLogStore) {
        this.eventLogStore = Objects.requireNonNull(eventLogStore, "eventLogStore cannot be null");
    }

    public Promise<IngestionResult> ingestLifecycleEvent(LifecycleEvent event) {
        validateLifecycleEvent(event);
        String eventKey = event.scope().tenantId() + ":" + event.eventId();
        if (eventIds.putIfAbsent(eventKey, Boolean.TRUE) != null) {
            return Promise.of(IngestionResult.duplicate(event.eventId()));
        }
        String sequenceKey = event.scope().tenantId() + ":" + event.scope().workspaceId() + ":" + event.runId();
        Long previousSequence = runSequences.get(sequenceKey);
        if (previousSequence != null && event.sequence() <= previousSequence) {
            eventIds.remove(eventKey);
            return Promise.of(IngestionResult.rejected(event.eventId(), "stale-event"));
        }
        runSequences.put(sequenceKey, event.sequence());
        return eventLogStore.append(toTenant(event.scope()), toEntry(event))
            .map(offset -> IngestionResult.ingested(event.eventId(), offset.value()));
    }

    public Promise<List<IngestionResult>> ingestLifecycleEventBatch(List<LifecycleEvent> events) {
        Objects.requireNonNull(events, "events cannot be null");
        List<IngestionResult> results = events.stream()
            .map(event -> ingestLifecycleEvent(event).getResult())
            .toList();
        return Promise.of(results);
    }

    public Promise<List<LifecycleEvent>> getLifecycleEvents(LifecycleScope scope, String runId) {
        requireScope(scope);
        requireNonBlank(runId, "runId");
        return readAll(scope).map(entries -> entries.stream()
            .filter(entry -> LIFECYCLE_EVENT_TYPE.equals(entry.eventType()))
            .filter(entry -> runId.equals(entry.headers().get("runId")))
            .map(this::decodeLifecycleEvent)
            .filter(event -> scope.workspaceId().equals(event.scope().workspaceId()))
            .sorted(Comparator.comparingLong(LifecycleEvent::sequence))
            .toList());
    }

    public Promise<List<LifecycleEvent>> getProductLifecycleTimeline(LifecycleScope scope, String productUnitId) {
        requireScope(scope);
        requireNonBlank(productUnitId, "productUnitId");
        return readAll(scope).map(entries -> entries.stream()
            .filter(entry -> LIFECYCLE_EVENT_TYPE.equals(entry.eventType()))
            .filter(entry -> productUnitId.equals(entry.headers().get("productUnitId")))
            .map(this::decodeLifecycleEvent)
            .filter(event -> scope.workspaceId().equals(event.scope().workspaceId()))
            .sorted(Comparator
                .comparing(LifecycleEvent::occurredAt)
                .thenComparingLong(LifecycleEvent::sequence)
                .thenComparing(LifecycleEvent::eventId))
            .toList());
    }

    public Promise<IngestionResult> recordArtifactReference(ArtifactReference reference) {
        validateArtifactReference(reference);
        return eventLogStore.append(toTenant(reference.scope()), toEntry(reference))
            .map(offset -> IngestionResult.ingested(reference.referenceId(), offset.value()));
    }

    public Promise<List<ArtifactReference>> getArtifactReferencesByRun(LifecycleScope scope, String runId) {
        requireScope(scope);
        requireNonBlank(runId, "runId");
        return readAll(scope).map(entries -> entries.stream()
            .filter(entry -> ARTIFACT_REFERENCE_TYPE.equals(entry.eventType()))
            .filter(entry -> runId.equals(entry.headers().get("runId")))
            .map(this::decodeArtifactReference)
            .filter(reference -> scope.workspaceId().equals(reference.scope().workspaceId()))
            .sorted(Comparator.comparing(ArtifactReference::recordedAt).thenComparing(ArtifactReference::referenceId))
            .toList());
    }

    public Promise<List<ArtifactReference>> getArtifactLineage(LifecycleScope scope, String artifactId) {
        requireScope(scope);
        requireNonBlank(artifactId, "artifactId");
        return readAll(scope).map(entries -> entries.stream()
            .filter(entry -> ARTIFACT_REFERENCE_TYPE.equals(entry.eventType()))
            .filter(entry -> artifactId.equals(entry.headers().get("artifactId")))
            .map(this::decodeArtifactReference)
            .filter(reference -> scope.workspaceId().equals(reference.scope().workspaceId()))
            .sorted(Comparator.comparing(ArtifactReference::recordedAt).thenComparing(ArtifactReference::referenceId))
            .toList());
    }

    private Promise<List<EventLogStore.EventEntry>> readAll(LifecycleScope scope) {
        return eventLogStore.read(toTenant(scope), Offset.zero(), Integer.MAX_VALUE);
    }

    private EventLogStore.EventEntry toEntry(LifecycleEvent event) {
        return EventLogStore.EventEntry.builder()
            .eventId(UUID.nameUUIDFromBytes(event.eventId().getBytes(StandardCharsets.UTF_8)))
            .eventType(LIFECYCLE_EVENT_TYPE)
            .eventVersion(event.schemaVersion())
            .timestamp(event.occurredAt())
            .payload(toJson(event.payload()))
            .headers(Map.of(
                "tenantId", event.scope().tenantId(),
                "workspaceId", event.scope().workspaceId(),
                "productUnitId", event.productUnitId(),
                "runId", event.runId(),
                "eventId", event.eventId(),
                "correlationId", event.correlationId().orElse("")))
            .idempotencyKey(event.eventId())
            .build();
    }

    private EventLogStore.EventEntry toEntry(ArtifactReference reference) {
        return EventLogStore.EventEntry.builder()
            .eventId(UUID.nameUUIDFromBytes(reference.referenceId().getBytes(StandardCharsets.UTF_8)))
            .eventType(ARTIFACT_REFERENCE_TYPE)
            .eventVersion("1.0.0")
            .timestamp(reference.recordedAt())
            .payload(toJson(reference.payload()))
            .headers(Map.of(
                "tenantId", reference.scope().tenantId(),
                "workspaceId", reference.scope().workspaceId(),
                "productUnitId", reference.productUnitId(),
                "runId", reference.runId(),
                "artifactId", reference.artifactId(),
                "referenceId", reference.referenceId(),
                "eventId", reference.eventId().orElse("")))
            .idempotencyKey(reference.referenceId())
            .build();
    }

    private LifecycleEvent decodeLifecycleEvent(EventLogStore.EventEntry entry) {
        Map<String, Object> payload = fromJson(entry.payload());
        return new LifecycleEvent(
            new LifecycleScope(entry.headers().get("tenantId"), entry.headers().get("workspaceId")),
            entry.headers().get("eventId"),
            entry.eventVersion(),
            stringPayload(payload, "productUnitId", entry.headers().get("productUnitId")),
            stringPayload(payload, "runId", entry.headers().get("runId")),
            stringPayload(payload, "phase", ""),
            stringPayload(payload, "eventName", entry.eventType()),
            numberPayload(payload, "sequence"),
            entry.timestamp(),
            Optional.ofNullable(emptyToNull(entry.headers().get("correlationId"))),
            payload);
    }

    private ArtifactReference decodeArtifactReference(EventLogStore.EventEntry entry) {
        Map<String, Object> payload = fromJson(entry.payload());
        return new ArtifactReference(
            new LifecycleScope(entry.headers().get("tenantId"), entry.headers().get("workspaceId")),
            entry.headers().get("referenceId"),
            stringPayload(payload, "artifactId", entry.headers().get("artifactId")),
            stringPayload(payload, "productUnitId", entry.headers().get("productUnitId")),
            stringPayload(payload, "runId", entry.headers().get("runId")),
            stringPayload(payload, "kind", "artifact"),
            stringPayload(payload, "uri", ""),
            Optional.ofNullable(emptyToNull(entry.headers().get("eventId"))),
            Optional.ofNullable(emptyToNull(stringPayload(payload, "deploymentId", ""))),
            Optional.ofNullable(emptyToNull(stringPayload(payload, "healthRef", ""))),
            entry.timestamp(),
            payload);
    }

    private byte[] toJson(Map<String, Object> payload) {
        try {
            return OBJECT_MAPPER.writeValueAsBytes(payload);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to serialize event payload", ex);
        }
    }

    private Map<String, Object> fromJson(java.nio.ByteBuffer payload) {
        try {
            java.nio.ByteBuffer duplicate = payload.asReadOnlyBuffer();
            byte[] bytes = new byte[duplicate.remaining()];
            duplicate.get(bytes);
            return OBJECT_MAPPER.readValue(bytes, MAP_TYPE);
        } catch (Exception ex) {
            throw new IllegalArgumentException("Unable to deserialize event payload", ex);
        }
    }

    private static TenantContext toTenant(LifecycleScope scope) {
        requireScope(scope);
        return TenantContext.of(scope.tenantId());
    }

    private static void validateLifecycleEvent(LifecycleEvent event) {
        Objects.requireNonNull(event, "event cannot be null");
        requireScope(event.scope());
        requireNonBlank(event.eventId(), "eventId");
        requireNonBlank(event.productUnitId(), "productUnitId");
        requireNonBlank(event.runId(), "runId");
        requireNonBlank(event.phase(), "phase");
        requireNonBlank(event.eventName(), "eventName");
        if (event.sequence() < 0) {
            throw new IllegalArgumentException("sequence must be non-negative");
        }
    }

    private static void validateArtifactReference(ArtifactReference reference) {
        Objects.requireNonNull(reference, "reference cannot be null");
        requireScope(reference.scope());
        requireNonBlank(reference.referenceId(), "referenceId");
        requireNonBlank(reference.artifactId(), "artifactId");
        requireNonBlank(reference.productUnitId(), "productUnitId");
        requireNonBlank(reference.runId(), "runId");
        requireNonBlank(reference.uri(), "uri");
    }

    private static void requireScope(LifecycleScope scope) {
        Objects.requireNonNull(scope, "scope cannot be null");
        requireNonBlank(scope.tenantId(), "tenantId");
        requireNonBlank(scope.workspaceId(), "workspaceId");
    }

    private static void requireNonBlank(String value, String name) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(name + " is required");
        }
    }

    private static String stringPayload(Map<String, Object> payload, String key, String fallback) {
        Object value = payload.get(key);
        return value instanceof String text ? text : fallback;
    }

    private static long numberPayload(Map<String, Object> payload, String key) {
        Object value = payload.get(key);
        return value instanceof Number number ? number.longValue() : 0L;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    public record LifecycleScope(String tenantId, String workspaceId) {}

    public record LifecycleEvent(
        LifecycleScope scope,
        String eventId,
        String schemaVersion,
        String productUnitId,
        String runId,
        String phase,
        String eventName,
        long sequence,
        Instant occurredAt,
        Optional<String> correlationId,
        Map<String, Object> payload
    ) {}

    public record ArtifactReference(
        LifecycleScope scope,
        String referenceId,
        String artifactId,
        String productUnitId,
        String runId,
        String kind,
        String uri,
        Optional<String> eventId,
        Optional<String> deploymentId,
        Optional<String> healthRef,
        Instant recordedAt,
        Map<String, Object> payload
    ) {}

    public record IngestionResult(
        boolean accepted,
        boolean duplicate,
        String id,
        String offset,
        String reason
    ) {
        static IngestionResult ingested(String id, String offset) {
            return new IngestionResult(true, false, id, offset, null);
        }

        static IngestionResult duplicate(String id) {
            return new IngestionResult(true, true, id, null, "duplicate-event");
        }

        static IngestionResult rejected(String id, String reason) {
            return new IngestionResult(false, false, id, null, reason);
        }
    }
}
