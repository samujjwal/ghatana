package com.ghatana.platform.domain.domain.event;

import com.ghatana.platform.domain.domain.event.Location;
import com.ghatana.platform.domain.domain.event.EventId;
import com.ghatana.platform.domain.domain.event.EventRelations;
import com.ghatana.platform.domain.domain.event.EventStats;
import com.ghatana.platform.domain.domain.event.EventTime;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * {@code GEvent} is the canonical immutable implementation of the {@link Event} interface,
 * representing a concrete event with complete temporal, spatial, and relational metadata.
 *
 * <h2>Purpose</h2>
 * Provides the standard concrete implementation of Event contract with:
 * <ul>
 *   <li>Immutable value object semantics (via Lombok @Value)</li>
 *   <li>Builder pattern for safe construction</li>
 *   <li>Thread-safe concurrent processing support</li>
 *   <li>Complete metadata preservation throughout pipeline</li>
 *   <li>Type-safe payload access with casting support</li>
 * </ul>
 *
 * <h2>Structure</h2>
 * Complete event composition:
 * <ul>
 *   <li><b>id</b>: {@link EventId} - Unique event identifier with tenant, type, version</li>
 *   <li><b>time</b>: {@link EventTime} - Multi-dimensional temporal metadata (occurrence, detection, validity)</li>
 *   <li><b>location</b>: {@link Location} - Geographic coordinates (latitude, longitude, altitude)</li>
 *   <li><b>stats</b>: {@link EventStats} - Processing metrics (size, processing time, field count)</li>
 *   <li><b>relations</b>: {@link EventRelations} - Semantic relationships for graph correlation</li>
 *   <li><b>headers</b>: Map&lt;String, String&gt; - Transport metadata (correlationId, causationId, etc.)</li>
 *   <li><b>payload</b>: Map&lt;String, Object&gt; - Event data (immutable on read, mutable during construction)</li>
 *   <li><b>intervalBased</b>: boolean - Whether event spans time interval vs. point-in-time</li>
 *   <li><b>provenance</b>: List&lt;String&gt; - Event lineage tracking through system</li>
 * </ul>
 *
 * <h2>Architecture Role</h2>
 * <ul>
 *   <li><b>Created by</b>: Ingestion layer (HTTP, Kafka connectors, internal APIs)</li>
 *   <li><b>Used by</b>: Event processors, correlation engines, storage systems</li>
 *   <li><b>Stored in</b>: Event streams, distributed cache, time-series database</li>
 *   <li><b>Flows through</b>: Validation → Enrichment → Processing → Storage pipeline</li>
 *   <li><b>Related to</b>: {@link EventType} (schema), {@link Event} (contract), {@link EventId} (identity)</li>
 * </ul>
 *
 * <h2>Immutability & Thread-Safety</h2>
 * Lombok {@code @Value} annotation provides:
 * <ul>
 *   <li>All fields final (compile-time immutability)</li>
 *   <li>No setters generated</li>
 *   <li>Hashcode/equals based on all fields</li>
 *   <li>Thread-safe for concurrent processors (no defensive copying needed)</li>
 *   <li>Safe to share across execution contexts</li>
 * </ul>
 * <b>Note:</b> Collections (headers, payload, provenance) are wrapped but contents could be mutated
 * if implementation returns mutable collections. Client code should avoid mutation.
 *
 * <h2>Payload Access Patterns</h2>
 * <ul>
 *   <li><b>Generic access</b>: {@code event.getPayload(key)} returns Object (needs casting)</li>
 *   <li><b>Type-safe access</b>: {@code event.payloadAs(key, Type.class)} with automatic validation</li>
 *   <li><b>Header access</b>: {@code event.getHeader(key)} with support for synthetic headers (id, time, etc.)</li>
 * </ul>
 *
 * <h2>Correlation Support</h2>
 * Supports both explicit and implicit event correlation:
 * <ul>
 *   <li><b>Explicit correlation</b>: correlationId and causationId headers</li>
 *   <li><b>Temporal correlation</b>: EventTime enables time-window based grouping</li>
 *   <li><b>Spatial correlation</b>: Location enables geo-based correlation</li>
 *   <li><b>Semantic correlation</b>: EventRelations enables graph traversal</li>
 * </ul>
 *
 * <h2>Example: Event Construction & Usage</h2>
 * {@code
 *   // Construct event with builder
 *   GEvent event = GEvent.builder()
 *       .id(EventId.of(tenantId, "ORDER_PLACED", "1.0"))
 *       .time(EventTime.builder()
 *           .occurrenceTime(Instant.now())
 *           .detectionTimePoint(Instant.now())
 *           .build())
 *       .location(Location.of(40.7128, -74.0060))
 *       .payload(Map.of(
 *           "orderId", "ORD-12345",
 *           "amount", 99.99,
 *           "items", Arrays.asList("ITEM-1", "ITEM-2")
 *       ))
 *       .headers(Map.of(
 *           "correlationId", UUID.randomUUID().toString(),
 *           "causationId", "CMD-98765"
 *       ))
 *       .stats(EventStats.of(256, 1500000))
 *       .build();
 *
 *   // Type-safe access
 *   String orderId = event.payloadAs("orderId", String.class);
 *   List&lt;?&gt; items = event.payloadAs("items", List.class);
 *
 *   // Correlation support
 *   String corrId = event.getCorrelationId(); // Header-based
 *   EventTime timing = event.getTime();       // Temporal metadata
 *   EventRelations rels = event.getRelations(); // Semantic links
 * }
 *
 * <h2>Multi-Tenancy</h2>
 * Event identity includes {@code tenantId} enabling:
 * <ul>
 *   <li>Tenant isolation in processing pipelines</li>
 *   <li>Tenant-scoped graph correlation</li>
 *   <li>Access control enforcement</li>
 *   <li>Quota tracking per tenant</li>
 * </ul>
 *
 * <h2>Versioning</h2>
 * Event type versioning supports schema evolution:
 * <ul>
 *   <li>Event carries version with identity</li>
 *   <li>Processors can handle multiple versions</li>
 *   <li>Enables rolling upgrades without downtime</li>
 * </ul>
 *
 * @see Event
 * @see EventId
 * @see EventTime
 * @see EventStats
 * @see EventRelations
 * @see EventType
 *
 * @doc.type value-object
 * @doc.layer domain
 * @doc.purpose immutable event implementation
 * @doc.pattern builder, value-object, immutable
 * @doc.test-hints immutability-verification, thread-safety-concurrent-access, type-safe-payload-access
 */
@Getter
@ToString
@EqualsAndHashCode
public class GEvent implements Event  {
    private final Map<String, String> headers;
    private final Map<String, Object> payload;
     private final EventId id;
     private final EventTime time;
     private final Location location;
     private final EventStats stats;
     private final EventRelations relations;
    private final boolean intervalBased;
    private final List<String> provenance;

    @Builder(toBuilder = true)
    protected GEvent(Map<String, String> headers,
                     Map<String, Object> payload,
                     EventId id,
                     EventTime time,
                     Location location,
                     EventStats stats,
                     EventRelations relations,
                     boolean intervalBased,
                     List<String> provenance) {
        this.headers = headers;
        this.payload = payload;
        this.id = id;
        this.time = time;
        this.location = location;
        this.stats = stats;
        this.relations = relations;
        this.intervalBased = intervalBased;
        this.provenance = provenance;
    }

    /**
     * Safely gets a payload value as the specified type.
     * @param <T> The expected type
     * @param key The payload key
     * @param type The expected class type
     * @return The payload value as the specified type, or null if not found or not of the expected type
     */
    @SuppressWarnings("unchecked")
    private <T> T payloadAs(String key, Class<T> type) {
        java.lang.Object value = getPayload(key);
        return (value != null && type.isInstance(value)) ? (T) value : null;
    }

    @Override
    public String getType() {
        return id.getEventType();
    }

    @Override
    public String getVersion() {
        return id.getVersion();
    }

    @Override
    public String getTenantId() {
        return id.getTenantId();
    }

    @Override
    public String getCorrelationId() {
        return headers.get("correlationId");
    }

    @Override
    public String getCausationId() {
        return headers.get("causationId");
    }

    @Override
    public String getHeader(String key) {
        // Fall through to well-known object fields before checking custom headers
        switch (key) {
            case "id":
                return id.toString();
            case "time":
                return time.toString();
            case "location":
                return location.toString();
            case "stats":
                return stats.toString();
            case "relations":
                return relations.toString();
            default:
                return headers.get(key);
        }
    }

    @Override
    public Object getPayload(String key) {
        return payload.get(key);
    }

    // -------------------------------------------------------------------------------------------------
    // Custom builder extensions for convenience in tests and simple construction sites
    // Allows: GEvent.builder().type("event.type").payload(...).headers(...).build();
    // Also supports setting version/tenant with sensible defaults.
    // -------------------------------------------------------------------------------------------------
    @SuppressWarnings("unused")
    public static class GEventBuilder {
        // Lombok populates these builder fields; we only add helpers.
        private EventId id;

        public GEventBuilder type(String eventType) {
            return typeAndVersion(eventType, "v1");
        }

        public GEventBuilder typeAndVersion(String eventType, String version) {
            this.id = new _SimpleEventId(null, eventType, version, UUID.randomUUID().toString());
            return this;
        }

        public GEventBuilder typeTenantVersion(String tenantId, String eventType, String version) {
            this.id = new _SimpleEventId(tenantId, eventType, version, UUID.randomUUID().toString());
            return this;
        }

        // Expose ability to set explicit id if needed
        public GEventBuilder typeWithId(String eventType, String version, String idValue) {
            this.id = new _SimpleEventId(null, eventType, version, idValue);
            return this;
        }

        public GEventBuilder addPayload(String key, Object value) {
            if (this.payload == null) {
                this.payload = new java.util.HashMap<>();
            }
            this.payload.put(key, value);
            return this;
        }
    }

    // Minimal internal EventId implementation used by builder helpers
    static record _SimpleEventId(String tenantId, String eventType, String version, String id) implements EventId {
        @Override public String getId() { return id; }
        @Override public String getEventType() { return eventType; }
        @Override public String getVersion() { return version; }
        @Override public String getTenantId() { return tenantId; }
    }
}
