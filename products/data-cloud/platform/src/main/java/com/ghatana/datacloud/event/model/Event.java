package com.ghatana.datacloud.event.model;

import com.ghatana.datacloud.EventRecord;
import com.ghatana.datacloud.StorageTier;
import com.ghatana.datacloud.spi.EventView;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * EventCloud-specific event extending core EventRecord.
 *
 * <p>
 * <b>Purpose</b><br>
 * Extends the core {@link EventRecord} with EventCloud-specific features:
 * <ul>
 * <li><b>Event Type</b>: Schema versioning via
 * eventTypeName/eventTypeVersion</li>
 * <li><b>Headers</b>: Event metadata and routing hints</li>
 * <li><b>Storage Tiering</b>: HOT → WARM → COOL → COLD lifecycle</li>
 * <li><b>Content Type</b>: Payload format specification</li>
 * </ul>
 *
 * <p>
 * <b>Inheritance</b><br>
 * <pre>
 * DataRecord (core)
 *   └── EventRecord (core) - immutable, partitioned, ordered
 *         └── Event (event/domain) - EventCloud-specific extensions
 * </pre>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * Event event = Event.builder()
 *     .tenantId("tenant-123")
 *     .collectionName("order-events")
 *     .streamName("orders")
 *     .partitionId(orderId.hashCode() % 8)
 *     .eventTypeName("commerce.order.created")
 *     .eventTypeVersion("1.0.0")
 *     .occurrenceTime(Instant.now())
 *     .idempotencyKey("order-" + orderId + "-created")
 *     .correlationId(requestId)
 *     .headers(Map.of("source", "checkout-service"))
 *     .payload(Map.of("orderId", orderId, "amount", 99.99))
 *     .build();
 * }</pre>
 *
 * @see EventRecord
 * @see EventType
 * @see EventStream
 * @see StorageTier
 * @doc.type class
 * @doc.purpose EventCloud event with type versioning and tiered storage
 * @doc.layer domain
 * @doc.pattern Domain Entity, Event Sourcing
 */
@jakarta.persistence.Entity
@Table(name = "events", indexes = {
    @Index(name = "idx_events_tenant_type", columnList = "tenant_id, event_type_name"),
    @Index(name = "idx_events_stream_partition_offset", columnList = "tenant_id, stream_name, partition_id, event_offset"),
    @Index(name = "idx_events_occurrence_time", columnList = "tenant_id, occurrence_time DESC"),
    @Index(name = "idx_events_detection_time", columnList = "tenant_id, detection_time DESC"),
    @Index(name = "idx_events_correlation", columnList = "tenant_id, correlation_id"),
    @Index(name = "idx_events_idempotency", columnList = "tenant_id, idempotency_key"),
    @Index(name = "idx_events_tier", columnList = "tenant_id, current_tier")
}, uniqueConstraints = {
    @UniqueConstraint(name = "uk_events_offset", columnNames = {"tenant_id", "stream_name", "partition_id", "event_offset"}),
    @UniqueConstraint(name = "uk_events_idempotency", columnNames = {"tenant_id", "idempotency_key"})
})
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class Event extends EventRecord implements EventView {

    // ==================== Event Type ====================
    /**
     * Name of the event type (schema reference).
     * <p>
     * Format: namespace.eventName (e.g., "commerce.order.created")
     */
    @NotNull
    @Column(name = "event_type_name", nullable = false, updatable = false, length = 255)
    private String eventTypeName;

    /**
     * Semantic version of the event type schema.
     * <p>
     * Used for schema evolution (e.g., "1.0.0", "2.1.0").
     */
    @NotNull
    @Column(name = "event_type_version", nullable = false, updatable = false, length = 50)
    @Builder.Default
    private String eventTypeVersion = "1.0.0";

    // ==================== Headers ====================
    /**
     * Event headers for metadata and routing.
     * <p>
     * Examples: source, contentType, priority, region, traceId
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "headers", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, String> headers = new HashMap<>();

    /**
     * Content type of the payload.
     */
    @Column(name = "content_type", nullable = false, length = 100)
    @Builder.Default
    private String contentType = "application/json";

    // ==================== Storage Tiering ====================
    /**
     * Current storage tier.
     * <p>
     * Events flow: HOT → WARM → COOL → COLD based on age and access.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "current_tier", nullable = false, length = 20)
    @Builder.Default
    private StorageTier currentTier = StorageTier.WARM;

    /**
     * When the event was last moved between tiers.
     */
    @Column(name = "tier_changed_at")
    private Instant tierChangedAt;

    // ==================== Accessors ====================
    /**
     * Gets the event payload (alias for getData()).
     *
     * @return event payload map
     */
    public Map<String, Object> getPayload() {
        return getData() != null ? getData() : new HashMap<>();
    }

    /**
     * Gets a typed value from the payload.
     *
     * @param key the payload key
     * @param type the expected type
     * @param <T> the value type
     * @return the value or null
     */
    @SuppressWarnings("unchecked")
    public <T> T getPayloadAs(String key, Class<T> type) {
        Object value = getPayload().get(key);
        return (value != null && type.isInstance(value)) ? (T) value : null;
    }

    /**
     * Gets a header value.
     *
     * @param key the header key
     * @return header value or null
     */
    public String getHeader(String key) {
        return headers != null ? headers.get(key) : null;
    }

    // ==================== Lifecycle ====================
    @Override
    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (currentTier == null) {
            currentTier = StorageTier.WARM;
        }
    }

    // ==================== Object Methods ====================
    @Override
    public String toString() {
        return "Event{"
                + "id=" + getId()
                + ", tenantId='" + getTenantId() + '\''
                + ", eventTypeName='" + eventTypeName + '\''
                + ", streamName='" + getStreamName() + '\''
                + ", partitionId=" + getPartitionId()
                + ", eventOffset=" + getEventOffset()
                + ", occurrenceTime=" + getOccurrenceTime()
                + ", detectionTime=" + getDetectionTime()
                + ", currentTier=" + currentTier
                + '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Event event = (Event) o;
        if (getId() != null && event.getId() != null) {
            return Objects.equals(getId(), event.getId());
        }
        return Objects.equals(getStreamName(), event.getStreamName())
                && Objects.equals(getPartitionId(), event.getPartitionId())
                && Objects.equals(getEventOffset(), event.getEventOffset());
    }

    @Override
    public int hashCode() {
        return getId() != null
                ? Objects.hash(getId())
                : Objects.hash(getStreamName(), getPartitionId(), getEventOffset());
    }

    // ==================== Builder ====================
    /**
     * Custom builder to support payload as alias for data.
     */
    public abstract static class EventBuilder<C extends Event, B extends EventBuilder<C, B>>
            extends EventRecordBuilder<C, B> {

        /**
         * Sets the event payload (alias for data).
         *
         * @param payload the event payload
         * @return the builder
         */
        public B payload(Map<String, Object> payload) {
            return data(payload);
        }
    }
}
