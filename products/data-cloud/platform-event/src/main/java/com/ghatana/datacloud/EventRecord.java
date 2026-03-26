package com.ghatana.datacloud;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.time.Instant;

/**
 * Immutable event record - append-only with ordering guarantees.
 *
 * <p>
 * <b>Purpose</b><br>
 * Represents facts that happened and cannot be changed. Events are appended to
 * streams, ordered by offset within partitions, and support real-time streaming
 * and replay.
 *
 * <p>
 * <b>Features</b><br>
 * <ul>
 * <li><b>Immutability</b> - Once written, events cannot be modified</li>
 * <li><b>Ordering</b> - Events ordered by offset within partition</li>
 * <li><b>Partitioning</b> - Events distributed across partitions for
 * scalability</li>
 * <li><b>Idempotency</b> - Duplicate detection via idempotencyKey</li>
 * <li><b>Correlation</b> - Link related events via
 * correlationId/causationId</li>
 * <li><b>Dual Timestamps</b> - occurrenceTime (when it happened) vs
 * detectionTime (when recorded)</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b><br>
 * <pre>{@code
 * EventRecord orderPlaced = EventRecord.builder()
 *     .tenantId("tenant-123")
 *     .collectionName("order-events")
 *     .streamName("orders")
 *     .partitionId(orderId.hashCode() % 8)  // Partition by orderId
 *     .occurrenceTime(Instant.now())
 *     .idempotencyKey("order-" + orderId + "-placed")
 *     .correlationId(requestId)
 *     .data(Map.of(
 *         "eventType", "OrderPlaced",
 *         "orderId", orderId,
 *         "customerId", customerId,
 *         "amount", 99.99
 *     ))
 *     .build();
 *
 * // Append to stream (offset assigned by storage)
 * storagePlugin.append(orderPlaced);
 *
 * // Read from offset
 * List<EventRecord> events = storagePlugin.readRange(
 *     tenantId, "orders", partitionId, startOffset, 100);
 *
 * // Subscribe to new events
 * storagePlugin.subscribe(tenantId, "orders", options, event -> {
 *     processEvent(event);
 * });
 * }</pre>
 *
 * <p>
 * <b>Database Table</b><br>
 * <pre>
 * CREATE TABLE events (
 *     id UUID PRIMARY KEY,
 *     tenant_id VARCHAR(255) NOT NULL,
 *     collection_name VARCHAR(255) NOT NULL,
 *     record_type VARCHAR(50) NOT NULL,
 *     stream_name VARCHAR(255) NOT NULL,
 *     partition_id INTEGER NOT NULL DEFAULT 0,
 *     event_offset BIGINT NOT NULL,
 *     data JSONB,
 *     metadata JSONB,
 *     occurrence_time TIMESTAMP NOT NULL,
 *     detection_time TIMESTAMP NOT NULL,
 *     idempotency_key VARCHAR(255),
 *     correlation_id VARCHAR(255),
 *     causation_id VARCHAR(255),
 *     created_at TIMESTAMP,
 *     created_by VARCHAR(255),
 *     UNIQUE(tenant_id, stream_name, partition_id, event_offset),
 *     UNIQUE(tenant_id, idempotency_key)
 * );
 * </pre>
 *
 * @see Record
 * @see RecordType#EVENT
 * @doc.type class
 * @doc.purpose Immutable event record with ordering
 * @doc.layer core
 * @doc.pattern Event Sourcing, Domain Event
 */
@Entity
@Table(name = "events",
        indexes = {
            @Index(name = "idx_events_tenant", columnList = "tenant_id"),
            @Index(name = "idx_events_stream", columnList = "tenant_id, stream_name"),
            @Index(name = "idx_events_partition_offset", columnList = "tenant_id, stream_name, partition_id, event_offset"),
            @Index(name = "idx_events_detection_time", columnList = "tenant_id, stream_name, detection_time DESC"),
            @Index(name = "idx_events_correlation", columnList = "tenant_id, correlation_id")
        },
        uniqueConstraints = {
            @UniqueConstraint(name = "uk_events_offset", columnNames = {"tenant_id", "stream_name", "partition_id", "event_offset"}),
            @UniqueConstraint(name = "uk_events_idempotency", columnNames = {"tenant_id", "idempotency_key"})
        }
)
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class EventRecord extends DataRecord {

    /**
     * Stream name this event belongs to.
     * <p>
     * Streams group related events (e.g., "orders", "payments",
     * "user-activity").
     */
    @Column(name = "stream_name", nullable = false, length = 255)
    private String streamName;

    /**
     * Partition within the stream.
     * <p>
     * Events are ordered within a partition. Use consistent hashing on a key
     * field to ensure related events go to the same partition.
     */
    @Column(name = "partition_id", nullable = false)
    @Builder.Default
    private Integer partitionId = 0;

    /**
     * Sequential offset within the partition.
     * <p>
     * Assigned by storage layer during append. Monotonically increasing.
     */
    @Column(name = "event_offset", nullable = false)
    private Long eventOffset;

    /**
     * When the event actually occurred in the real world.
     * <p>
     * May differ from detectionTime if there's processing delay.
     */
    @Column(name = "occurrence_time", nullable = false)
    private Instant occurrenceTime;

    /**
     * When the event was recorded/detected by the system.
     * <p>
     * Used for ordering and time-range queries.
     */
    @Column(name = "detection_time", nullable = false)
    private Instant detectionTime;

    /**
     * Idempotency key for duplicate detection.
     * <p>
     * If provided, duplicate appends with same key are rejected.
     */
    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    /**
     * Correlation ID linking related events across services.
     * <p>
     * Typically the original request ID that triggered the event chain.
     */
    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    /**
     * Causation ID linking to the direct cause of this event.
     * <p>
     * The ID of the event or command that directly caused this event.
     */
    @Column(name = "causation_id", length = 255)
    private String causationId;

    @Override
    public RecordType getRecordType() {
        return RecordType.EVENT;
    }

    @Override
    @PrePersist
    protected void onCreate() {
        super.onCreate();
        if (recordType == null) {
            recordType = RecordType.EVENT;
        }
        if (partitionId == null) {
            partitionId = 0;
        }
        if (occurrenceTime == null) {
            occurrenceTime = Instant.now();
        }
        if (detectionTime == null) {
            detectionTime = Instant.now();
        }
    }

    /**
     * Get the event type from data payload.
     * <p>
     * Convention: data should contain "eventType" field.
     *
     * @return event type string or null
     */
    public String getEventType() {
        return getDataValue("eventType", String.class);
    }

    /**
     * Set the event type in data payload.
     *
     * @param eventType the event type
     * @return this record for chaining
     */
    public EventRecord eventType(String eventType) {
        setDataValue("eventType", eventType);
        return this;
    }

    /**
     * Calculate partition ID based on a key using consistent hashing.
     *
     * @param key the partition key
     * @param numPartitions total number of partitions
     * @return partition ID (0 to numPartitions-1)
     */
    public static int calculatePartition(String key, int numPartitions) {
        if (key == null || numPartitions <= 1) {
            return 0;
        }
        return Math.abs(key.hashCode() % numPartitions);
    }

    /**
     * Check if this event has an idempotency key set.
     *
     * @return true if idempotency key is present
     */
    public boolean hasIdempotencyKey() {
        return idempotencyKey != null && !idempotencyKey.isEmpty();
    }

    @Override
    public String toString() {
        return "EventRecord{"
                + "id=" + id
                + ", tenantId='" + tenantId + '\''
                + ", streamName='" + streamName + '\''
                + ", partitionId=" + partitionId
                + ", eventOffset=" + eventOffset
                + ", eventType='" + getEventType() + '\''
                + '}';
    }
}
