package com.ghatana.datacloud.event.model;

import com.ghatana.datacloud.EntityRecord;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.*;

/**
 * Logical stream for events of a single EventType.
 *
 * <p>
 * <b>Definition</b><br>
 * An EventStream is a partitioned, ordered flow of events belonging to ONE
 * EventType. It is NOT a container for multiple EventTypes.
 *
 * <p>
 * <b>Inheritance</b><br>
 * <pre>
 * DataRecord (core)
 *   └── EntityRecord (core) - mutable, versioned
 *         └── EventStream - partitioned stream configuration
 * </pre>
 *
 * <p>
 * <b>Key Characteristics</b>:
 * <ul>
 * <li><b>Single EventType</b>: Each stream handles one event type</li>
 * <li><b>Partitioned</b>: Events distributed across partitions for
 * parallelism</li>
 * <li><b>Ordered</b>: Events ordered within each partition by offset</li>
 * <li><b>Retention</b>: Configurable retention per tier</li>
 * </ul>
 *
 * <p>
 * <b>Relationship to EventType</b>:
 * <ul>
 * <li>EventType defines WHAT (schema, validation, governance)</li>
 * <li>EventStream defines HOW (partitioning, retention, storage)</li>
 * <li>Multiple EventStreams can exist for same EventType (e.g., different
 * environments, retention policies)</li>
 * </ul>
 *
 * <p>
 * <b>Partitioning</b>: Events are assigned to partitions via partition key
 * expression:
 * <ul>
 * <li>Hash-based: hash(partitionKey) % partitionCount</li>
 * <li>Key-based: explicit partition from event</li>
 * <li>Round-robin: sequential assignment</li>
 * </ul>
 *
 * <p>
 * <b>Retention Policy</b>: Configurable per-tier retention:
 * <ul>
 * <li>HOT: Minutes (real-time buffer)</li>
 * <li>WARM: Days (primary storage)</li>
 * <li>COOL: Days/Months (analytics)</li>
 * <li>COLD: Years (archive)</li>
 * </ul>
 *
 * <p>
 * <b>Usage</b>:
 * <pre>{@code
 * EventStream stream = EventStream.builder()
 *     .tenantId("tenant-123")
 *     .collectionName("event-streams")
 *     .name("order-created-prod")
 *     .eventTypeName("order.created")
 *     .eventTypeVersion("1.0.0")
 *     .partitionCount(8)
 *     .partitionKeyExpression("payload.customerId")
 *     .warmRetentionDays(7)
 *     .coldRetentionDays(365)
 *     .build();
 * }</pre>
 *
 * @see EntityRecord
 * @see EventType
 * @see Event
 * @see Partition
 * @see ConsumerGroup
 * @doc.type class
 * @doc.purpose Logical stream for events of single type with partitioning and
 * retention
 * @doc.layer domain
 * @doc.pattern Domain Entity, Partitioned Stream, Tiered Storage
 */
@jakarta.persistence.Entity
@Table(name = "event_streams", indexes = {
    @Index(name = "idx_event_streams_tenant", columnList = "tenant_id"),
    @Index(name = "idx_event_streams_name", columnList = "tenant_id, name"),
    @Index(name = "idx_event_streams_type", columnList = "tenant_id, event_type_name"),
    @Index(name = "idx_event_streams_collection", columnList = "tenant_id, collection_name")
})
@Getter
@Setter
@SuperBuilder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class EventStream extends EntityRecord {

    // ==================== Identity ====================
    /**
     * Unique name for this stream within tenant.
     *
     * <p>
     * Format: lowercase, hyphenated (e.g., "order-created-prod")
     */
    @NotBlank
    @Column(nullable = false, length = 255)
    private String name;

    /**
     * Optional description of the stream's purpose.
     */
    @Column(columnDefinition = "TEXT")
    private String description;

    // ==================== EventType Reference ====================
    /**
     * The single EventType this stream handles.
     *
     * <p>
     * NOT a list - one stream = one type. References by name (not FK for
     * performance).
     */
    @NotBlank
    @Column(name = "event_type_name", nullable = false, length = 255)
    private String eventTypeName;

    /**
     * Optional version filter for the event type.
     *
     * <p>
     * If set, only events of this version flow through. If null, all versions
     * are accepted.
     */
    @Column(name = "event_type_version", length = 50)
    private String eventTypeVersion;

    // ==================== Partitioning ====================
    /**
     * Number of partitions for parallel processing.
     *
     * <p>
     * Defaults to 8. Cannot be changed after events exist.
     */
    @Column(name = "partition_count", nullable = false)
    @Builder.Default
    private Integer partitionCount = 8;

    /**
     * Expression for extracting partition key from event.
     *
     * <p>
     * Uses JSONPath-like syntax. Examples: "payload.customerId",
     * "headers.region", "eventId"
     */
    @Column(name = "partition_key_expression", length = 255)
    private String partitionKeyExpression;

    /**
     * Partitions belonging to this stream.
     *
     * <p>
     * Created automatically based on partitionCount.
     */
    @OneToMany(mappedBy = "stream", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<Partition> partitions = new ArrayList<>();

    // ==================== Retention (Per-Tier) ====================
    /**
     * HOT tier retention in minutes.
     *
     * <p>
     * How long events stay in real-time buffer (Redis). Default: 5 minutes
     */
    @Column(name = "hot_retention_minutes")
    @Builder.Default
    private Integer hotRetentionMinutes = 5;

    /**
     * WARM tier retention in days.
     *
     * <p>
     * How long events stay in primary storage (PostgreSQL). Default: 7 days
     */
    @Column(name = "warm_retention_days")
    @Builder.Default
    private Integer warmRetentionDays = 7;

    /**
     * COOL tier retention in days.
     *
     * <p>
     * How long events stay in analytics storage (Iceberg). Default: 30 days
     */
    @Column(name = "cool_retention_days")
    @Builder.Default
    private Integer coolRetentionDays = 30;

    /**
     * COLD tier retention in days.
     *
     * <p>
     * How long events stay in archive storage (S3). Default: 365 days (1 year)
     */
    @Column(name = "cold_retention_days")
    @Builder.Default
    private Integer coldRetentionDays = 365;

    // ==================== Compaction ====================
    /**
     * Whether to enable log compaction.
     *
     * <p>
     * When enabled, older events with same compaction key are removed, keeping
     * only the latest event per key.
     */
    @Column(name = "enable_compaction")
    @Builder.Default
    private Boolean enableCompaction = false;

    /**
     * Expression for extracting compaction key.
     *
     * <p>
     * Only relevant when enableCompaction is true. Example: "payload.entityId"
     */
    @Column(name = "compaction_key_expression", length = 255)
    private String compactionKeyExpression;

    // ==================== Storage Mapping ====================
    /**
     * Physical storage connector mappings per tier.
     *
     * <p>
     * Flexible JSONB for storage configuration:
     * <pre>{@code
     * {
     *   "hot": { "connector": "redis", "ttl": 300 },
     *   "warm": { "connector": "postgres", "table": "events_order" },
     *   "cool": { "connector": "iceberg", "table": "order_events" },
     *   "cold": { "connector": "s3", "bucket": "archive", "prefix": "orders/" }
     * }
     * }</pre>
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "storage_mapping", columnDefinition = "jsonb")
    @Builder.Default
    private Map<String, Object> storageMapping = new HashMap<>();

    // ==================== Metadata ====================
    /**
     * Whether this stream is active.
     *
     * <p>
     * Inactive streams don't accept new events.
     */
    @Column(nullable = false)
    @Builder.Default
    private Boolean active = true;

    // ==================== Lifecycle Callbacks ====================
    @Override
    @PrePersist
    protected void onCreate() {
        super.onCreate();
    }

    @Override
    @PreUpdate
    protected void onUpdate() {
        super.onUpdate();
    }

    // ==================== Stream Operations ====================
    /**
     * Deactivates this stream.
     *
     * <p>
     * Stops accepting new events. Existing events are preserved.
     */
    public void deactivate() {
        this.active = false;
    }

    /**
     * Reactivates this stream.
     */
    public void reactivate() {
        this.active = true;
    }

    /**
     * Gets total retention in days (sum of all tiers).
     *
     * @return total days events are retained
     */
    public int getTotalRetentionDays() {
        return warmRetentionDays + coolRetentionDays + coldRetentionDays;
    }

    /**
     * Gets storage mapping, ensuring it's never null.
     *
     * @return storage mapping map (empty if not set)
     */
    public Map<String, Object> getStorageMapping() {
        if (storageMapping == null) {
            storageMapping = new HashMap<>();
        }
        return storageMapping;
    }

    /**
     * Gets partitions, ensuring it's never null.
     *
     * @return partitions list (empty if not set)
     */
    public List<Partition> getPartitions() {
        if (partitions == null) {
            partitions = new ArrayList<>();
        }
        return partitions;
    }

    // ==================== Object Methods ====================
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        EventStream that = (EventStream) o;
        if (getId() != null && that.getId() != null) {
            return Objects.equals(getId(), that.getId());
        }
        return Objects.equals(getTenantId(), that.getTenantId())
                && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return getId() != null
                ? Objects.hash(getId())
                : Objects.hash(getTenantId(), name);
    }

    @Override
    public String toString() {
        return "EventStream{"
                + "id=" + getId()
                + ", tenantId='" + getTenantId() + '\''
                + ", name='" + name + '\''
                + ", eventTypeName='" + eventTypeName + '\''
                + ", partitionCount=" + partitionCount
                + ", active=" + active
                + '}';
    }
}
